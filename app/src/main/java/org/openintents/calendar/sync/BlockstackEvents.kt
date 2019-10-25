package org.openintents.calendar.sync

import android.util.Log
import org.blockstack.android.sdk.BlockstackSession
import org.blockstack.android.sdk.Result
import org.blockstack.android.sdk.model.PutFileOptions
import org.json.JSONObject
import org.openintents.calendar.sync.SyncAdapter.Companion.format
import org.openintents.calendar.sync.model.BlockstackEventList
import org.openintents.calendar.sync.model.LocalEvent

class BlockstackEvents(
    private val eventsResult: BlockstackEventList,
    private val blockstackSession: BlockstackSession
) {
    fun createEvent(uid: String, localEvent: LocalEvent): Boolean {
        val bsEvent = JSONObject()
        updateFromLocalEvent(bsEvent, localEvent)
        eventsResult.events.put(uid, bsEvent)
        return true
    }

    fun deleteEvent(uid: String): Boolean {
        val bsEvent = eventsResult.events.remove(uid)
        return bsEvent != null
    }

    fun updateEvent(uid: String, localEvent: LocalEvent): Boolean {
        val bsEvent = eventsResult.events.optJSONObject(uid)
        if (bsEvent == null) {
            return false
        } else {
            updateFromLocalEvent(bsEvent, localEvent)
            return true
        }
    }

    private fun updateFromLocalEvent(
        bsEvent: JSONObject,
        localEvent: LocalEvent
    ) {

        val eventTimeZone = localEvent.eventTimeZone ?: localEvent.calendarTimeZone ?: "UTC"
        val eventEndTimeZone = localEvent.eventEndTimeZone ?: eventTimeZone

        Log.d(TAG, "$bsEvent ${localEvent.dtStart} ${eventTimeZone}")

        bsEvent.put("title", localEvent.title)
        bsEvent.put("notes", localEvent.description)
        bsEvent.put("allDay", localEvent.allDay == 1.toShort())
        bsEvent.put("start", format(localEvent.dtStart))
        bsEvent.put("end", format(localEvent.dtEnd))
        Log.d(TAG, bsEvent.toString())
    }

    suspend fun save(callback: (Result<String>) -> Unit) {
        val path = eventsResult.blockstackCalendar.data.optString("src")
        if (path != null) {
            blockstackSession.putFile(
                path,
                eventsResult.events.toString(),
                PutFileOptions(contentType = "application/json"),
                callback
            )
        } else {
            Log.d(TAG, "No src found in ${eventsResult.blockstackCalendar.data}")
        }
    }

    companion object {
        val TAG = BlockstackEvents.javaClass.simpleName
    }
}
