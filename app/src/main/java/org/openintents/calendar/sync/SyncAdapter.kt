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
import android.annotation.SuppressLint
import android.content.*
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.RemoteException
import android.provider.BaseColumns
import android.provider.CalendarContract
import android.text.TextUtils
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import org.blockstack.android.sdk.*
import org.blockstack.android.sdk.model.BlockstackConfig
import org.blockstack.android.sdk.model.GetFileOptions
import org.json.JSONArray
import org.json.JSONObject
import org.openintents.calendar.sync.model.BlockstackCalendar
import org.openintents.calendar.sync.model.BlockstackEventList
import org.openintents.calendar.sync.model.LocalEvent
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.lang.String.format
import java.net.MalformedURLException
import java.net.URI
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

fun String.toBlockstackConfig(
    scopes: Array<Scope>,
    redirectPath: String = "/redirect",
    manifestPath: String = "/manifest.json"
): BlockstackConfig =
    BlockstackConfig(
        URI(this),
        redirectPath,
        manifestPath,
        scopes
    )

val blockstackConfig =
    "https://cal.openintents.org".toBlockstackConfig(
        arrayOf(BaseScope.StoreWrite.scope),
        redirectPath = "/"
    )

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

    private lateinit var sessionStore: SessionStore
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
        sessionStore = SessionStoreProvider.getInstance(context)
        mBlockstackSession = BlockstackSession(sessionStore, blockstackConfig)

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

        Log.i(TAG, "Beginning network synchronization $extras $authority")
        CoroutineScope(Dispatchers.IO).launch {
            val feed = extras.getString("feed")
            val metaFeedOnly = extras.getBoolean("metafeedonly")
            val upload = extras.getBoolean("upload")

            if (feed != null) {
                // sync calendar events for one calendar
                val result = fetchEvents(feed, syncResult)
                if (result != null) {
                    if (upload) {
                        updateRemoteEvents(result, account, syncResult)
                    } else {
                        updateLocalEvents(result, account, syncResult)
                    }
                }
            } else {
                // sync calendar list

                val calendars = fetchCalendars(syncResult)
                if (calendars != null) {
                    updateLocalCalendarList(calendars, account, syncResult)
                    if (!metaFeedOnly) {
                        for (i in 0..calendars.length()) {
                            val calendar = calendars.getJSONObject(i)
                            val feed = calendar.getString("uid")
                            val result = fetchEvents(feed, syncResult)
                            if (result != null) {
                                if (upload) {
                                    updateRemoteEvents(result, account, syncResult)
                                } else {
                                    updateLocalEvents(result, account, syncResult)
                                }
                            }
                        }
                    }
                }
            }
        }
        Log.i(TAG, "Network synchronization complete $extras")

    }

    private suspend fun fetchEvents(uid: String, syncResult: SyncResult): BlockstackEventList? {
        try {
            if (mBlockstackSession.isUserSignedIn()) {
                val blockstackCalendar = getBlockstackCalendar(uid)
                if (blockstackCalendar.error != null) {
                    syncResult.databaseError = true
                    return null
                }

                val events = suspendCoroutine<JSONObject> { cont ->
                    val callback: (Result<out Any>) -> Unit = {
                        if (it.hasValue) {
                            val eventsAsString = it.value as String
                            Log.d(TAG, "events from gaia\n$eventsAsString")
                            val events = JSONObject(eventsAsString)
                            cont.resume(events)
                        } else {
                            Log.e(TAG, "Failed to fetch events: " + it.error)
                            cont.resumeWithException(IOException(it.error?.message))
                        }
                    }
                    when (blockstackCalendar.type) {
                        "private" -> {
                            val path = blockstackCalendar.data.optString("src")
                            Log.d(TAG, "events for ${blockstackCalendar.type} from $path")
                            CoroutineScope(Dispatchers.IO).launch {
                                val result = mBlockstackSession.getFile(path, GetFileOptions())
                                callback(result)
                            }

                        }
                        "blockstack-user" -> {
                            val path = blockstackCalendar.data.optString("src")
                            Log.d(
                                TAG,
                                "events for ${blockstackCalendar.type} from $path ignored"
                            )
                            /*mBlockstackSession.getFile(
                                path,
                                GetFileOptions(username = data.optString("user"), decrypt = false),
                                callback
                            )*/
                        }
                        "ics" -> {
                            // TODO
                            Log.d(
                                TAG,
                                "events for ${blockstackCalendar.type} ignored (${blockstackCalendar.data.optString(
                                    "src"
                                )}"
                            )
                        }
                    }
                }
                return BlockstackEventList(blockstackCalendar, events)
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

    @SuppressLint("MissingPermission")
    private fun getBlockstackCalendar(uid: String): BlockstackCalendar {
        val c = mContentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            CALENDAR_PROJECTION,
            "${CalendarContract.Calendars.CAL_SYNC1} = ?",
            arrayOf(uid),
            null
        )
        if (c == null || !c.moveToFirst()) {
            c?.close()
            return InvalidBlockstackCalendar("User not logged in")
        }

        val type = c.getString(COLUMN_TYPE)
        val data = JSONObject(c.getString(COLUMN_DATA))
        val calendarId = c.getString(COLUMN_ID)
        c.close()
        return BlockstackCalendar(type, data, calendarId)
    }

    @Suppress("FunctionName")
    private fun InvalidBlockstackCalendar(error: String): BlockstackCalendar {
        return BlockstackCalendar(null, JSONObject(), null, error)
    }

    suspend fun fetchCalendars(syncResult: SyncResult): JSONArray? {
        try {
            if (mBlockstackSession.isUserSignedIn()) {
                val calendars =
                    CoroutineScope(Dispatchers.IO).async {
                        Log.d(
                            SyncAdapter.javaClass.simpleName,
                            mBlockstackSession.loadUserData().appPrivateKey
                        )
                        val result = mBlockstackSession.getFile("Calendars", GetFileOptions())
                        if (result.hasValue) {
                            val calendarsAsString = result.value as String
                            val calendars = JSONArray(calendarsAsString)
                            for (i in 0 until calendars.length()) {
                                val calendar = calendars.getJSONObject(i)
                                Log.d(TAG, calendar.toString())
                            }
                            calendars
                        } else {
                            Log.e(TAG, "Failed to fetch calendars: " + result.error)
                            throw IOException(result.error?.message)
                        }
                    }.await()
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
     * Storing calendar from JSON into the content provider.
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
    private fun updateLocalCalendarList(
        calendars: JSONArray,
        account: Account,
        syncResult: SyncResult
    ) {

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

        Log.i(TAG, "Fetching local entries for merge")
        val uri =
            asSyncAdapter(CalendarContract.Calendars.CONTENT_URI, account.name, account.type)
        val c = contentResolver.query(uri, CALENDAR_PROJECTION, null, null, null)!!
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
            if (match != null) {
                // Entry exists. Remove from entry map to prevent insert later.
                entryMap.remove(uid)
                // Check to see if the entry needs to be updated
                val existingUri =
                    asSyncAdapter(
                        CalendarContract.Calendars.CONTENT_URI,
                        account.name,
                        account.type
                    ).buildUpon()
                        .appendPath(Integer.toString(id)).build()
                if ((TextUtils.isEmpty(match.optString("name")) && !match.getString("name").equals(
                        displayName
                    )) ||
                    (TextUtils.isEmpty(match.optString("hexColor")) && match.getString("hexColor").equals(
                        color.toHexColor()
                    ))
                ) {
                    // Update existing record
                    Log.i(TAG, "Scheduling update: $existingUri")
                    batch.add(
                        ContentProviderOperation.newUpdate(existingUri)
                            .withValue(
                                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                                match.getString("name")
                            )
                            .build()
                    )

                    syncResult.stats.numUpdates++
                } else {
                    Log.i(TAG, "No action: $existingUri")
                }
            } else {
                // Entry doesn't exist. Remove it from the database.
                val deleteUri =
                    asSyncAdapter(
                        CalendarContract.Calendars.CONTENT_URI,
                        account.name,
                        account.type
                    ).buildUpon()
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
            Log.i(
                TAG,
                "Scheduling insert: uid=" + uid + " " + e.optString("hexColor", "#000000")
            )


            val calendarType = e.getString("type")
            val syncableType = "private" == calendarType
            batch.add(
                ContentProviderOperation.newInsert(
                    asSyncAdapter(
                        CalendarContract.Calendars.CONTENT_URI,
                        account.name,
                        account.type
                    )
                )
                    .withValue(CalendarContract.Calendars._SYNC_ID, uid)
                    .withValue(CalendarContract.Calendars.CAL_SYNC1, uid)
                    .withValue(CalendarContract.Calendars.CAL_SYNC2, calendarType)
                    .withValue(
                        CalendarContract.Calendars.CAL_SYNC3,
                        e.getJSONObject("data").toString()
                    )
                    .withValue(
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                        e.getString("name")
                    )
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
                    .withValue(
                        CalendarContract.Calendars.VISIBLE, if (syncableType) {
                            1
                        } else {
                            0
                        }
                    )
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


    @SuppressLint("MissingPermission")
    private suspend fun updateRemoteEvents(
        eventsResult: BlockstackEventList,
        account: Account,
        syncResult: SyncResult
    ) {
        val blockstackEvents = BlockstackEvents(eventsResult, mBlockstackSession)
        val calendarId = eventsResult.blockstackCalendar.calendarId
        val contentResolver = context.contentResolver

        var selection =
            "(" + CalendarContract.Events.DIRTY + " = ?) AND (" + CalendarContract.Events.CALENDAR_ID + " = ?)"
        var selectionArgs = arrayOf("1", calendarId)
        val curEvent =
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                null,
                selection,
                selectionArgs,
                null
            )
        var eventID: Long

        if (curEvent === null) {
            return
        }

        var curAttendee: Cursor?
        var curReminder: Cursor?
        var event: LocalEvent
        var syncId: String?
        var eventUri: Uri
        var rowInsert = 0
        var rowDelete = 0
        var rowUpdate = 0
        var rowDirty = 0

        val notifyList = arrayListOf<Uri>()
        val contentValues = ContentValues()

        while (curEvent.moveToNext()) {
            eventID = curEvent.getLong(curEvent.getColumnIndex(CalendarContract.Events._ID))

            event = LocalEvent(curEvent, contentResolver)
            eventUri =
                CalendarContract.Events.CONTENT_URI.buildUpon().appendPath(eventID.toString())
                    .build()
            syncId =
                curEvent.getString(curEvent.getColumnIndex(CalendarContract.Events._SYNC_ID))

            var Deleted = false
            var intDeleted = 0
            intDeleted =
                curEvent.getInt(curEvent.getColumnIndex(CalendarContract.Events.DELETED))
            Deleted = intDeleted == 1

            if (syncId == null) {
                val uid = UUID.randomUUID().toString()

                if (blockstackEvents.createEvent(uid, event)) {

                    val values = ContentValues()
                    values.put(CalendarContract.Events._SYNC_ID, uid)
                    values.put(CalendarContract.Events.DIRTY, 0)

                    val rowCount = contentResolver
                        .update(
                            asSyncAdapter(
                                eventUri, account.name,
                                account.type
                            ), values, null, null
                        )
                    if (rowCount == 1) {
                        rowInsert += 1
                        notifyList.add(eventUri)
                    }
                } else {
                    rowDirty += 1
                }
            } else if (Deleted) {
                if (blockstackEvents.deleteEvent(syncId)) {
                    val mSelectionClause = "(" + CalendarContract.Events._ID + "= ?)"
                    val mSelectionArgs = arrayOf(eventID.toString())

                    val countDeleted = contentResolver
                        .delete(
                            asSyncAdapter(
                                CalendarContract.Events.CONTENT_URI, account.name,
                                account.type
                            ), mSelectionClause, mSelectionArgs
                        )

                    if (countDeleted == 1) {
                        rowDelete += 1
                        notifyList.add(eventUri)
                    }
                } else {
                    rowDirty += 1
                }
            } else {
                if (blockstackEvents.updateEvent(syncId, event)) {
                    contentValues.clear()
                    contentValues.put(CalendarContract.Events.DIRTY, 0)

                    val rowCount = contentResolver
                        .update(
                            asSyncAdapter(
                                eventUri, account.name,
                                account.type
                            ), contentValues, null, null
                        )

                    if (rowCount == 1) {
                        rowUpdate += 1
                        notifyList.add(eventUri)
                    }
                } else {
                    rowDirty += 1
                }
            }
        }

        blockstackEvents.save {
            if (!it.hasErrors) {
                syncResult.stats.numDeletes += rowDelete.toLong()
                syncResult.stats.numInserts += rowInsert.toLong()
                syncResult.stats.numUpdates += rowUpdate.toLong()
                syncResult.stats.numSkippedEntries += rowDirty.toLong()
                syncResult.stats.numEntries += (rowInsert + rowUpdate + rowDelete).toLong()
            } else {
                syncResult.stats.numSkippedEntries += (rowDirty + rowInsert + rowUpdate + rowDelete).toLong()
            }
            curEvent.close()
        }
    }

    /**
     * Storing events from JSON into the content provider.
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
    private fun updateLocalEvents(
        eventsResult: BlockstackEventList,
        account: Account,
        syncResult: SyncResult
    ) {

        val events = eventsResult.events
        val calendarId = eventsResult.blockstackCalendar.calendarId
        val contentResolver = context.contentResolver

        val newEventUri = asSyncAdapter(
            CalendarContract.Events.CONTENT_URI,
            account.name,
            account.type
        )
        val batch = ArrayList<ContentProviderOperation>()

        // Build hash table of incoming entries
        val entryMap = HashMap<String, JSONObject>()
        for (key in events.keys()) {
            val event = events.getJSONObject(key)
            val id = event.optString("uid", key)
            event.put("uid", id)
            entryMap[id] = event
        }

        Log.i(TAG, "Fetching local event entries for merge")
        val uri = asSyncAdapter(CalendarContract.Events.CONTENT_URI, account.name, account.type)
        val c =
            contentResolver.query(
                uri,
                null,
                "${CalendarContract.Events.CALENDAR_ID} = ?",
                arrayOf(calendarId),
                null
            )!!
        Log.i(TAG, "Found " + c.count + " local entries. Computing merge solution...")

        var localEvent: LocalEvent
        var localEventId: String
        // Find stale data
        while (c.moveToNext()) {
            syncResult.stats.numEntries++
            localEvent = LocalEvent(c, contentResolver)
            localEventId = Integer.toString(localEvent.id)
            val match = entryMap[localEvent.uid]
            if (match != null) {
                // Entry exists. Remove from entry map to prevent insert later.
                entryMap.remove(localEvent.uid)
                // Check to see if the entry needs to be updated
                val existingUri =
                    asSyncAdapter(
                        CalendarContract.Events.CONTENT_URI,
                        account.name,
                        account.type
                    ).buildUpon()
                        .appendPath(localEventId).build()

                if (needsUpdate(localEvent, match)) {
                    // Update existing record
                    Log.i(TAG, "Scheduling update: $existingUri")
                    batch.add(newUpdateEventOperation(existingUri, match))
                    batch.addAll(
                        remindersOperations(
                            localEventId,
                            remindersFrom(localEventId, match)
                        )
                    )
                    batch.addAll(
                        attendeesOperations(
                            localEventId,
                            attendeesFrom(localEventId, match)
                        )
                    )
                    syncResult.stats.numUpdates++
                } else {
                    Log.i(TAG, "No action: $existingUri")
                }
            } else {
                // Entry doesn't exist. Remove it from the database.
                val deleteUri =
                    asSyncAdapter(
                        CalendarContract.Events.CONTENT_URI,
                        account.name,
                        account.type
                    ).buildUpon()
                        .appendPath(Integer.toString(localEvent.id)).build()
                Log.i(TAG, "Scheduling delete: $deleteUri")
                batch.add(ContentProviderOperation.newDelete(deleteUri).build())
                syncResult.stats.numDeletes++
            }
        }
        c.close()

        // Add new items
        var uid: String
        var b: ContentProviderOperation
        for (e in entryMap.values) {
            uid = e.getString("uid")
            Log.i(
                TAG,
                "Scheduling insert: uid=" + uid + " " + e.optString("title", "-no title-")
            )

            b = newInsertEventOperation(newEventUri, uid, calendarId, e)
            batch.add(b)
            syncResult.stats.numInserts++
        }
        Log.i(TAG, "Merge solution ready. Applying batch update")
        for (b in batch) {
            mContentResolver.applyBatch(
                CalendarContract.Events.CONTENT_URI.authority!!,
                arrayListOf(b)
            )
        }

        batch.clear()

        for (e in entryMap.values) {
            uid = e.getString("uid")
            Log.i(
                TAG,
                "Scheduling reminders/attendees: uid=" + uid + " " + e.optString(
                    "guests",
                    "-no guests-"
                ) + e.optBoolean("reminderEnabled", false)
            )
            val eventId = getLocalEventIdFor(uid)
            if (eventId != null) {
                localEventId = eventId
                batch.addAll(remindersOperations(localEventId, remindersFrom(localEventId, e)))
                batch.addAll(attendeesOperations(localEventId, attendeesFrom(localEventId, e)))
                syncResult.stats.numInserts++
            }
        }

        for (b in batch) {
            Log.d(TAG, "${b.uri} ${b}")
            mContentResolver.applyBatch(
                CalendarContract.Events.CONTENT_URI.authority!!,
                arrayListOf(b)
            )
        }

        mContentResolver.notifyChange(
            CalendarContract.Events.CONTENT_URI, null, // No local observer
            false
        )
    }

    @SuppressLint("MissingPermission")
    private fun getLocalEventIdFor(uid: String): String? {
        val c = mContentResolver.query(
            CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events._SYNC_ID} = ?", arrayOf(uid), null
        )
        if (c != null) {
            c.moveToFirst()
            val localEventId = c.getString(0)
            c.close()
            return localEventId
        } else {
            return null
        }
    }

    private fun remindersFrom(eventId: String, match: JSONObject): List<ContentValues> {
        val reminders = arrayListOf<ContentValues>()
        if (match.optBoolean("reminderEnabled", false)) {
            val reminder = ContentValues()
            reminder.put(CalendarContract.Reminders.EVENT_ID, eventId)
            reminder.put(
                CalendarContract.Reminders.METHOD,
                CalendarContract.Reminders.METHOD_ALERT
            )
            val minutes = when (match.optString("reminderTimeUnit", "minutes")) {
                "minutes" -> match.optInt("time", 10)
                "hours" -> match.optInt("time", 1) * 60
                else -> 10
            }
            reminder.put(CalendarContract.Reminders.MINUTES, minutes)
            reminders.add(reminder)
        }
        return reminders
    }

    private fun attendeesFrom(eventId: String, match: JSONObject): List<ContentValues> {
        val attendees = arrayListOf<ContentValues>()
        val guests = match.optString("guests")
        if (guests != null && guests !== JSONObject.NULL) {
            var attendeeIds = guests.split(",")

            var attendee: ContentValues
            for (attendeeId in attendeeIds) {
                attendee = ContentValues()

                attendee.put(CalendarContract.Attendees.EVENT_ID, eventId)

                attendee.put(CalendarContract.Attendees.ATTENDEE_NAME, attendeeId)
                attendee.put(CalendarContract.Attendees.ATTENDEE_ID_NAMESPACE, "blockstack")
                attendee.put(CalendarContract.Attendees.ATTENDEE_IDENTITY, attendeeId)

                attendee.put(
                    CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                    CalendarContract.Attendees.RELATIONSHIP_NONE
                )
                attendee.put(
                    CalendarContract.Attendees.ATTENDEE_STATUS,
                    CalendarContract.Attendees.ATTENDEE_STATUS_INVITED
                )

                attendees.add(attendee)
            }
        }
        return attendees
    }


    private fun newInsertEventOperation(
        newEventUri: Uri,
        uid: String,
        calendarId: String?,
        e: JSONObject
    ): ContentProviderOperation {
        return ContentProviderOperation.newInsert(
            newEventUri
        )
            .withValue(CalendarContract.Events._SYNC_ID, uid)
            .withValue(CalendarContract.Events.CALENDAR_ID, calendarId)
            .withValue(CalendarContract.Events.DTSTART, parseDt(e.optString("start")))
            .withValue(CalendarContract.Events.DTEND, parseDt(e.optString("end")))
            .withValue(CalendarContract.Events.TITLE, e.optString("title"))
            .withValue(CalendarContract.Events.DESCRIPTION, e.optString("notes"))
            .withValue(
                CalendarContract.Events.ALL_DAY, if (e.optBoolean("allData", false)) {
                    1
                } else {
                    0
                }
            )
            .withValue(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            .withValue(CalendarContract.Events.DIRTY, 0)
            .withValue(CalendarContract.Events.HAS_ALARM, 0)
            .withValue(CalendarContract.Events.DELETED, 0)
            .withValue(CalendarContract.Events.ORIGINAL_ALL_DAY, 0)
            .build()
    }

    private fun newUpdateEventOperation(
        existingUri: Uri?,
        match: JSONObject
    ): ContentProviderOperation {
        return ContentProviderOperation.newUpdate(existingUri)
            .withValue(CalendarContract.Events.TITLE, match.optString("title", ""))
            .withValue(CalendarContract.Events.DESCRIPTION, match.optString("notes", ""))
            .withValue(
                CalendarContract.Events.ALL_DAY, if (match.optBoolean("allDay", false)) {
                    1
                } else {
                    0
                }
            )
            .withValue(CalendarContract.Events.DTSTART, parseDt(match.getString("start")))
            .withValue(CalendarContract.Events.DTEND, parseDt(match.getString("end")))
            .build()
    }


    private fun attendeesOperations(
        eventId: String,
        attendees: List<ContentValues>
    ): List<ContentProviderOperation> {

        val operations = ArrayList<ContentProviderOperation>()
        val mSelectionClause = "(" + CalendarContract.Attendees.EVENT_ID + " = ?)"
        val mSelectionArgs = arrayOf(eventId)
        operations.add(
            ContentProviderOperation.newDelete(CalendarContract.Attendees.CONTENT_URI).withSelection(
                mSelectionClause, mSelectionArgs
            ).build()
        )

        for (attendee in attendees) {
            operations.add(
                ContentProviderOperation.newInsert(CalendarContract.Attendees.CONTENT_URI).withValues(
                    attendee
                ).build()
            )
        }
        return operations
    }


    private fun remindersOperations(
        eventId: String,
        reminders: List<ContentValues>
    ): List<ContentProviderOperation> {
        val operations = ArrayList<ContentProviderOperation>()
        val mSelectionClause = "(" + CalendarContract.Reminders.EVENT_ID + " = ?)"
        val mSelectionArgs = arrayOf(eventId)
        operations.add(
            ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI).withSelection(
                mSelectionClause, mSelectionArgs
            ).build()
        )

        for (reminder in reminders) {
            operations.add(
                ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI).withValues(
                    reminder
                ).build()
            )
        }
        return operations
    }

    private fun needsUpdate(localEvent: LocalEvent, jsonEvent: JSONObject): Boolean {
        // check any difference in data
        return true
    }

    @SuppressLint("MissingPermission")
    private fun logCalendars(contentResolver: ContentResolver) {
        Log.i(TAG, "Fetching local entries for merge")
        val c = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            CALENDAR_PROJECTION,
            null,
            null,
            null
        )!!
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
        c.close()
    }

    companion object {
        val TAG = "SyncAdapter"
        val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fun parseDt(dateTime: String?): Long? {
            if (dateTime == null) {
                return null
            }
            return try {
                LocalDateTime.parse(dateTime, format).toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (e: ParseException) {
                dateTime.toLong()
            }
        }


        fun format(dateTime: Long): String {
            val zoneId = ZoneId.of("Z")
            val localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(dateTime), zoneId)
            return localDateTime.format(format)
        }

        /**
         * Project used when querying content provider. Returns all known fields.
         */
        private val CALENDAR_PROJECTION = arrayOf(
            BaseColumns._ID,
            CalendarContract.Calendars._SYNC_ID,
            //CalendarContract.Calendars.CAL_SYNC1,
            CalendarContract.Calendars.CAL_SYNC2,
            CalendarContract.Calendars.CAL_SYNC3,
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
