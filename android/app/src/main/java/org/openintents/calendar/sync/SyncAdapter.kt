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

import android.accounts.Account
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.RemoteException
import android.provider.BaseColumns
import android.provider.CalendarContract
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import org.blockstack.android.sdk.BlockstackSession
import org.blockstack.android.sdk.Executor
import org.blockstack.android.sdk.Result
import org.blockstack.android.sdk.Scope
import org.blockstack.android.sdk.model.GetFileOptions
import org.blockstack.android.sdk.model.toBlockstackConfig
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.lang.String.format
import java.net.MalformedURLException
import java.text.ParseException
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

val blockstackConfig = "https://cal.openintents.org".toBlockstackConfig(arrayOf(Scope.StoreWrite), redirectPath = "/")
val j2v8Dispatcher = newSingleThreadContext("j2v8")
fun executorFactory(context: Context): Executor {
    return object : Executor {
        override fun onMainThread(function: (Context) -> Unit) {
            GlobalScope.launch(Dispatchers.Main) {
                function(context)
            }
        }

        override fun onNetworkThread(function: suspend () -> Unit) {
            GlobalScope.launch(j2v8Dispatcher) {
                function()
            }
        }

        override fun onV8Thread(function: () -> Unit) {
            GlobalScope.launch(j2v8Dispatcher) {
                function()
            }
        }
    }
}

/**
 * Define a sync adapter for the app.
 *
 *
 * This class is instantiated in [SyncService], which also binds SyncAdapter to the system.
 * SyncAdapter should only be initialized in SyncService, never anywhere else.
 *
 *
 * The system calls onPerformSync() via an RPC call through the IBinder object supplied by
 * SyncService.
 */
internal class SyncAdapter : AbstractThreadedSyncAdapter {

    /**
     * Content resolver, for performing database operations.
     */
    private lateinit var mContentResolver: ContentResolver

    private lateinit var mBlockstackSession: BlockstackSession

    /**
     * Constructor. Obtains handle to content resolver for later use.
     */
    constructor(context: Context, autoInitialize: Boolean) : super(context, autoInitialize) {
        initValues(context)
    }

    /**
     * Constructor. Obtains handle to content resolver for later use.
     */
    constructor(context: Context, autoInitialize: Boolean, allowParallelSyncs: Boolean) : super(
        context,
        autoInitialize,
        allowParallelSyncs
    ) {
        initValues(context)
    }

    fun initValues(context: Context) {
        mContentResolver = context.contentResolver
        GlobalScope.launch(j2v8Dispatcher) {
            mBlockstackSession = BlockstackSession(context, blockstackConfig, executor = executorFactory(context))
        }

    }

    /* Called by the Android system in response to a request to run the sync adapter. The work
     * required to read data from the network, parse it, and store it in the content provider is
     * done here. Extending AbstractThreadedSyncAdapter ensures that all methods within SyncAdapter
     * run on a background thread. For this reason, blocking I/O and other long-running tasks can be
     * run <em>in situ</em>, and you don't have to set up a separate thread for them.
     .
     *
     * <p>This is where we actually perform any work required to perform a sync.
     * {@link android.content.AbstractThreadedSyncAdapter} guarantees that this will be called on a non-UI thread,
     * so it is safe to peform blocking I/O here.
     *
     * <p>The syncResult argument allows you to pass information back to the method that triggered
     * the sync.
     */
    override fun onPerformSync(
        account: Account, extras: Bundle, authority: String,
        provider: ContentProviderClient, syncResult: SyncResult
    ) {

        Log.i(TAG, "Beginning network synchronization " + extras + " " + authority)
        GlobalScope.launch(j2v8Dispatcher) {
            if (extras.getString("feed") != null) {
                if (extras.getBoolean("upload")) {

                } else {
                    fetchEvents(extras.getString("feed"))
                }
            } else {
                val calendars = fetchCalendars(syncResult)
                if (calendars != null) {
                    updateLocalCalendarList(calendars, account, syncResult)
                }
            }
            Log.i(TAG, "Network synchronization complete")
        }
    }

    suspend fun fetchEvents(id: String, syncResult: SyncResult) {
        try {
            if (mBlockstackSession.isUserSignedIn()) {
                val calendars = suspendCoroutine<JSONArray> { cont ->
                    val callback: (Result<Any>) -> Unit = {
                        if (it.hasValue) {

                            val calendarsAsString = it.value as String
                            val calendars = JSONArray(calendarsAsString)
                            for (i in 0 until calendars.length()) {
                                val calendar = calendars.getJSONObject(i)
                                Log.d(TAG, calendar.toString())
                            }
                            cont.resume(calendars)
                        } else {
                            Log.e(TAG, "Failed to fetch calendars: " + it.error)
                            cont.resumeWithException(IOException(it.error))
                        }
                    }

                    mBlockstackSession.getFile("Calendars", GetFileOptions(), callback)
                }
                Log.d(TAG, calendars.toString())
                return calendars
            } else {
                Log.e(TAG, "User not logged in")
                syncResult.databaseError = true
            }
        } catch (e: MalformedURLException) {
            Log.e(TAG, "Feed URL is malformed", e)
            syncResult.stats.numParseExceptions++
        } catch (e: IOException) {
            Log.e(TAG, "Error reading from network: $e")
            syncResult.stats.numIoExceptions++
        } catch (e: XmlPullParserException) {
            Log.e(TAG, "Error parsing feed: $e")
            syncResult.stats.numParseExceptions++
        } catch (e: ParseException) {
            Log.e(TAG, "Error parsing feed: $e")
            syncResult.stats.numParseExceptions++
        } catch (e: RemoteException) {
            Log.e(TAG, "Error updating database: $e")
            syncResult.databaseError = true
        } catch (e: OperationApplicationException) {
            Log.e(TAG, "Error updating database: $e")
            syncResult.databaseError = true
        }
        return null
    }

    suspend fun fetchCalendars(syncResult: SyncResult): JSONArray? {
        try {
            if (mBlockstackSession.isUserSignedIn()) {
                val calendars = suspendCoroutine<JSONArray> { cont ->
                    val callback: (Result<Any>) -> Unit = {
                        if (it.hasValue) {

                            val calendarsAsString = it.value as String
                            val calendars = JSONArray(calendarsAsString)
                            for (i in 0 until calendars.length()) {
                                val calendar = calendars.getJSONObject(i)
                                Log.d(TAG, calendar.toString())
                            }
                            cont.resume(calendars)
                        } else {
                            Log.e(TAG, "Failed to fetch calendars: " + it.error)
                            cont.resumeWithException(IOException(it.error))
                        }
                    }

                    mBlockstackSession.getFile("Calendars", GetFileOptions(), callback)
                }
                Log.d(TAG, calendars.toString())
                return calendars
            } else {
                Log.e(TAG, "User not logged in")
                syncResult.databaseError = true
            }
        } catch (e: MalformedURLException) {
            Log.e(TAG, "Feed URL is malformed", e)
            syncResult.stats.numParseExceptions++
        } catch (e: IOException) {
            Log.e(TAG, "Error reading from network: $e")
            syncResult.stats.numIoExceptions++
        } catch (e: XmlPullParserException) {
            Log.e(TAG, "Error parsing feed: $e")
            syncResult.stats.numParseExceptions++
        } catch (e: ParseException) {
            Log.e(TAG, "Error parsing feed: $e")
            syncResult.stats.numParseExceptions++
        } catch (e: RemoteException) {
            Log.e(TAG, "Error updating database: $e")
            syncResult.databaseError = true
        } catch (e: OperationApplicationException) {
            Log.e(TAG, "Error updating database: $e")
            syncResult.databaseError = true
        }
        return null
    }

    fun asSyncAdapter(uri: Uri, account: String, accountType: String): Uri {
        return uri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, accountType).build()
    }


    /**
     * Read XML from an input stream, storing it into the content provider.
     *
     *
     * This is where incoming data is persisted, committing the results of a sync. In order to
     * minimize (expensive) disk operations, we compare incoming data with what's already in our
     * database, and compute a merge. Only changes (insert/update/delete) will result in a database
     * write.
     *
     *
     * As an additional optimization, we use a batch operation to perform all database writes at
     * once.
     *
     *
     * Merge strategy:
     * 1. Get cursor to all items in feed<br></br>
     * 2. For each item, check if it's in the incoming data.<br></br>
     * a. YES: Remove from "incoming" list. Check if data has mutated, if so, perform
     * database UPDATE.<br></br>
     * b. NO: Schedule DELETE from database.<br></br>
     * (At this point, incoming database only contains missing items.)<br></br>
     * 3. For any items remaining in incoming list, ADD to database.
     */
    @Throws(
        IOException::class,
        XmlPullParserException::class,
        RemoteException::class,
        OperationApplicationException::class,
        ParseException::class
    )
    private fun updateLocalCalendarList(calendars: JSONArray, account: Account, syncResult: SyncResult) {

        val contentResolver = context.contentResolver

        val batch = ArrayList<ContentProviderOperation>()

        // Build hash table of incoming entries
        val entryMap = HashMap<String, JSONObject>()
        for (i in 0..calendars.length() - 1) {
            val calendar = calendars.getJSONObject(i)
            val id = calendar.optString("uid", UUID.randomUUID().toString())
            calendar.put("uid", id)
            entryMap[id] = calendar
        }

        logCalendars(contentResolver)

        Log.i(TAG, "Fetching local entries for merge")
        val uri = asSyncAdapter(CalendarContract.Calendars.CONTENT_URI, account.name, account.type)
        val c = contentResolver.query(uri, PROJECTION, null, null, null)!!
        Log.i(TAG, "Found " + c.count + " local entries. Computing merge solution...")

        // Find stale data
        var id: Int
        var uid: String
        var type: String
        var data: String
        var accountName: String
        var accountType: String
        var displayName: String
        var color: Int
        while (c.moveToNext()) {
            syncResult.stats.numEntries++
            id = c.getInt(COLUMN_ID)
            uid = c.getString(COLUMN_UID)
            type = c.getString(COLUMN_TYPE)
            data = c.getString(COLUMN_DATA)
            accountName = c.getString(COLUMN_ACCOUNT_NAME)
            accountType = c.getString(COLUMN_ACCOUNT_TYPE)
            displayName = c.getString(COLUMN_DISPLAY_NAME)
            color = c.getInt(COLUMN_COLOR)

            val match = entryMap[uid]
            if (match != null && !accountName.equals("friedger.id")) {
                // Entry exists. Remove from entry map to prevent insert later.
                entryMap.remove(uid)
                // Check to see if the entry needs to be updated
                val existingUri =
                    asSyncAdapter(CalendarContract.Calendars.CONTENT_URI, account.name, account.type).buildUpon()
                        .appendPath(Integer.toString(id)).build()
                if ((TextUtils.isEmpty(match.optString("name")) && !match.getString("name").equals(displayName)) ||
                    (TextUtils.isEmpty(match.optString("hexColor")) && match.getString("hexColor").equals(color.toHexColor()))
                ) {
                    // Update existing record
                    Log.i(TAG, "Scheduling update: $existingUri")
                    batch.add(
                        ContentProviderOperation.newUpdate(existingUri)
                            .withValue(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, match.getString("name"))
                            .build()
                    )
                    syncResult.stats.numUpdates++
                } else {
                    Log.i(TAG, "No action: $existingUri")
                }
            } else {
                // Entry doesn't exist. Remove it from the database.
                val deleteUri =
                    asSyncAdapter(CalendarContract.Calendars.CONTENT_URI, account.name, account.type).buildUpon()
                        .appendPath(Integer.toString(id)).build()
                Log.i(TAG, "Scheduling delete: $deleteUri")
                batch.add(ContentProviderOperation.newDelete(deleteUri).build())
                syncResult.stats.numDeletes++
            }
        }
        c.close()

        // Add new items
        for (e in entryMap.values) {
            uid = e.getString("uid")
            Log.i(TAG, "Scheduling insert: uid=" + uid + " " + e.optString("hexColor", "#000000"))


            batch.add(
                ContentProviderOperation.newInsert(
                    asSyncAdapter(
                        CalendarContract.Calendars.CONTENT_URI,
                        account.name,
                        account.type
                    )
                )
                    .withValue(CalendarContract.Calendars._SYNC_ID, uid)
                    .withValue(CalendarContract.Calendars.CAL_SYNC1, e.getString("uid"))
                    .withValue(CalendarContract.Calendars.CAL_SYNC2, e.getString("type"))
                    .withValue(CalendarContract.Calendars.CAL_SYNC3, e.getJSONObject("data").toString())
                    .withValue(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, e.getString("name"))
                    .withValue(
                        CalendarContract.Calendars.CALENDAR_COLOR,
                        Color.parseColor(e.optString("hexColor", "#000000"))
                    )
                    .withValue(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
                    .withValue(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
                    .withValue(CalendarContract.Calendars.OWNER_ACCOUNT, account.name)
                    .withValue(CalendarContract.Calendars.SYNC_EVENTS, 1)
                    .withValue(
                        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                        CalendarContract.Calendars.CAL_ACCESS_OWNER
                    )
                    .withValue(CalendarContract.Calendars.VISIBLE, 1)
                    .build()
            )
            syncResult.stats.numInserts++
        }
        Log.i(TAG, "Merge solution ready. Applying batch update")
        mContentResolver.applyBatch(CalendarContract.Calendars.CONTENT_URI.authority!!, batch)
        mContentResolver.notifyChange(
            CalendarContract.Calendars.CONTENT_URI, null, // No local observer
            false
        )
    }

    private fun logCalendars(contentResolver: ContentResolver) {

        Log.i(TAG, "Fetching local entries for merge")
        val uri = CalendarContract.Calendars.CONTENT_URI
        val c = contentResolver.query(uri, PROJECTION, null, null, null)!!
        Log.i(TAG, "Found " + c.count + " local entries. Computing merge solution...")

        // Find stale data
        var id: Int
        var uid: String
        var type: String
        var data: String
        var accountName: String
        var accountType: String
        var displayName: String
        var color: Int
        while (c.moveToNext()) {
            id = c.getInt(COLUMN_ID)
            accountName = c.getString(COLUMN_ACCOUNT_NAME)
            accountType = c.getString(COLUMN_ACCOUNT_TYPE)
            displayName = c.getString(COLUMN_DISPLAY_NAME)
            color = c.getInt(COLUMN_COLOR)
            Log.d(TAG, format("%s %s %s %s", id, accountName, displayName, color.toHexColor()))
        }
    }

    companion object {
        val TAG = "SyncAdapter"
        /**
         * Project used when querying content provider. Returns all known fields.
         */
        private val PROJECTION = arrayOf(
            BaseColumns._ID,
            CalendarContract.Calendars._SYNC_ID,
            CalendarContract.Calendars.CAL_SYNC1,
            CalendarContract.Calendars.CAL_SYNC2,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR
        )

        // Constants representing column positions from PROJECTION.
        val COLUMN_ID = 0
        val COLUMN_UID = 1
        val COLUMN_TYPE = 2
        val COLUMN_DATA = 3
        val COLUMN_ACCOUNT_NAME = 4
        val COLUMN_ACCOUNT_TYPE = 5
        val COLUMN_DISPLAY_NAME = 6
        val COLUMN_COLOR = 7
    }
}

private fun Int.toHexColor(): String {
    return String.format("#%02X%02X%02X", Color.red(this), Color.green(this), Color.blue(this))
}
