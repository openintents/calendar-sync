/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openintents.calendar.sync

import android.Manifest
import android.accounts.Account
import android.annotation.TargetApi
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.BaseColumns
import android.provider.CalendarContract
import android.text.format.Time
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.PermissionChecker
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.fragment.app.ListFragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.openintents.calendar.common.accounts.GenericAccountService

private val REQUEST_CODE_PERMISSIONS: Int = 1

class EntryListFragment : ListFragment(), LoaderManager.LoaderCallbacks<Cursor> {

    /**
     * Cursor adapter for calendars
     */
    private var mAdapter: SimpleCursorAdapter? = null

    /**
     * Handle to a SyncObserver. The ProgressBar element is visible until the SyncObserver reports
     * that the sync is complete.
     *
     *
     * This allows us to delete our SyncObserver once the application is no longer in the
     * foreground.
     */
    private var mSyncObserverHandle: Any? = null

    /**
     * Options menu used to populate ActionBar.
     */
    private var mOptionsMenu: Menu? = null

    /**
     * Create a new anonymous SyncStatusObserver. It's attached to the app's ContentResolver in
     * onResume(), and removed in onPause(). If status changes, it sets the state of the Refresh
     * button. If a sync is active or pending, the Refresh button is replaced by an indeterminate
     * ProgressBar; otherwise, the button itself is displayed.
     */
    private val mSyncStatusObserver = object : android.content.SyncStatusObserver {

        var account: Account? = null
        override fun onStatusChanged(which: Int) {
            activity!!.runOnUiThread(
                Runnable
                /**
                 * The SyncAdapter runs on a background thread. To update the UI, onStatusChanged()
                 * runs on the UI thread.
                 */
                {
                    // Create a handle to the account that was created by
                    // SyncService.createSyncAccount(). This will be used to query the system to
                    // see how the sync status has changed.
                    GlobalScope.launch(Dispatchers.Main) {
                        if (account == null) {
                            account = GenericAccountService.getAccount(activity!!, SyncUtils.ACCOUNT_TYPE)
                        }
                        if (account != null) {
                            // Test the ContentResolver to see if the sync adapter is active or pending.
                            // Set the state of the refresh button accordingly.
                            val syncActive = ContentResolver.isSyncActive(
                                account, CalendarContract.AUTHORITY
                            )
                            val syncPending = ContentResolver.isSyncPending(
                                account, CalendarContract.AUTHORITY
                            )
                            setRefreshActionButtonState(syncActive || syncPending)
                        }
                    }

                })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    /**
     * Create SyncAccount at launch, if needed.
     *
     *
     * This will create a new account with the system for our application, register our
     * [SyncService] with it, and establish a sync schedule.
     */
    override fun onAttach(context: Context?) {
        super.onAttach(context)

        GlobalScope.launch {
            // Create account, if needed
            SyncUtils.createSyncAccount(context!!)
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mAdapter = SimpleCursorAdapter(
            activity!!, // Current context
            android.R.layout.simple_list_item_activated_2, null, // Cursor
            FROM_COLUMNS, // Cursor columns to use
            TO_FIELDS, // Layout fields to use
            0                    // No flags
        )// Layout for individual rows
        mAdapter!!.viewBinder = SimpleCursorAdapter.ViewBinder { view, cursor, i ->
            if (i == COLUMN_PUBLISHED) {
                // Convert timestamp to human-readable date
                val t = Time()
                t.set(cursor.getLong(i))
                (view as TextView).text = t.format("%Y-%m-%d %H:%M")
                true
            } else {
                // Let SimpleCursorAdapter handle other fields automatically
                false
            }
        }
        listAdapter = mAdapter
        setEmptyText(getText(R.string.app_name))
        if (PermissionChecker.checkSelfPermission(
                activity!!,
                Manifest.permission.READ_CALENDAR
            ) == PERMISSION_GRANTED
        ) {

            loaderManager.initLoader(0, null, this)
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                REQUEST_CODE_PERMISSIONS
            );
        }
    }

    override fun onResume() {
        super.onResume()
        mSyncStatusObserver.onStatusChanged(0)

        // Watch for sync state changes
        val mask = ContentResolver.SYNC_OBSERVER_TYPE_PENDING or ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
        mSyncObserverHandle = ContentResolver.addStatusChangeListener(mask, mSyncStatusObserver)
    }

    override fun onPause() {
        super.onPause()
        if (mSyncObserverHandle != null) {
            ContentResolver.removeStatusChangeListener(mSyncObserverHandle)
            mSyncObserverHandle = null
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            for (grantResult in grantResults) {
                if (grantResult != PermissionChecker.PERMISSION_GRANTED) {
                    return
                }
            }
            loaderManager.initLoader(0, null, this)
        }
    }

    /**
     * Query the content provider for data.
     *
     *
     * Loaders do queries in a background thread. They also provide a ContentObserver that is
     * triggered when data in the content provider changes. When the sync adapter updates the
     * content provider, the ContentObserver responds by resetting the loader and then reloading
     * it.
     */
    override fun onCreateLoader(i: Int, bundle: Bundle?): Loader<Cursor> {
        // We only have one loader, so we can ignore the value of i.
        // (It'll be '0', as set in onCreate().)

        return CursorLoader(
            activity!!, // Context
            CalendarContract.Calendars.CONTENT_URI, // URI
            PROJECTION, null, null, // Selection args
            CalendarContract.Calendars.DEFAULT_SORT_ORDER + " desc"
        )// Projection
        // Selection
        // Sort

    }

    /**
     * Move the Cursor returned by the query into the ListView adapter. This refreshes the existing
     * UI with the data in the Cursor.
     */
    override fun onLoadFinished(cursorLoader: Loader<Cursor>, cursor: Cursor) {
        mAdapter!!.changeCursor(cursor)
    }

    /**
     * Called when the ContentObserver defined for the content provider detects that data has
     * changed. The ContentObserver resets the loader, and then re-runs the loader. In the adapter,
     * set the Cursor value to null. This removes the reference to the Cursor, allowing it to be
     * garbage-collected.
     */
    override fun onLoaderReset(cursorLoader: Loader<Cursor>) {
        mAdapter!!.changeCursor(null)
    }

    /**
     * Create the ActionBar.
     */
    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        mOptionsMenu = menu
        inflater!!.inflate(R.menu.account_details, menu)
    }

    /**
     * Respond to user gestures on the ActionBar.
     */
    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            // If the user clicks the "Refresh" button.
            R.id.menu_refresh -> {
                GlobalScope.launch {
                    SyncUtils.triggerRefresh(activity!!)
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Load an article in the default browser when selected by the user.
     */
    override fun onListItemClick(listView: ListView?, view: View?, position: Int, id: Long) {
        super.onListItemClick(listView, view, position, id)

        // Get a URI for the selected item, then start an Activity that displays the URI. Any
        // Activity that filters for ACTION_VIEW and a URI can accept this. In most cases, this will
        // be a browser.

        // Get the item at the selected position, in the form of a Cursor.
        val c = mAdapter!!.getItem(position) as Cursor
        // Get the link to the article represented by the item.
        val articleUrlString = c.getString(COLUMN_URL_STRING)
        if (articleUrlString == null) {
            Log.e(TAG, "Attempt to launch entry with null link")
            return
        }

        Log.i(TAG, "Opening URL: $articleUrlString")
        // Get a Uri object for the URL string
        val articleURL = Uri.parse(articleUrlString)
        val i = Intent(Intent.ACTION_VIEW, articleURL)
        startActivity(i)
    }

    /**
     * Set the state of the Refresh button. If a sync is active, turn on the ProgressBar widget.
     * Otherwise, turn it off.
     *
     * @param refreshing True if an active sync is occuring, false otherwise
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    fun setRefreshActionButtonState(refreshing: Boolean) {
        if (mOptionsMenu == null) {
            return
        }

        val refreshItem = mOptionsMenu!!.findItem(R.id.menu_refresh)
        if (refreshItem != null) {
            if (refreshing) {
                refreshItem.setActionView(R.layout.actionbar_indeterminate_progress)
            } else {
                refreshItem.actionView = null
            }
        }
    }

    companion object {

        private val TAG = "EntryListFragment"

        /**
         * Projection for querying the content provider.
         */
        private val PROJECTION = arrayOf(
            BaseColumns._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )

        // Column indexes. The index of a column in the Cursor is the same as its relative position in
        // the projection.
        /** Column index for _ID  */
        private val COLUMN_ID = 0
        /** Column index for title  */
        private val COLUMN_TITLE = 1
        /** Column index for link  */
        private val COLUMN_URL_STRING = 2
        /** Column index for published  */
        private val COLUMN_PUBLISHED = 3

        /**
         * List of Cursor columns to read from when preparing an adapter to populate the ListView.
         */
        private val FROM_COLUMNS =
            arrayOf(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME)

        /**
         * List of Views which will be populated by Cursor data.
         */
        private val TO_FIELDS = intArrayOf(android.R.id.text1, android.R.id.text2)
    }

}