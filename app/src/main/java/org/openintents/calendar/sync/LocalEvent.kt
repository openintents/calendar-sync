package org.openintents.calendar.sync

import android.database.Cursor
import android.provider.CalendarContract

data class LocalEvent(
    var id: Int,
    var uid: String,
    var accountName: String,
    var accountType: String,
    var title: String,
    var allDay: Short,
    var dtStart: Int,
    var dtEnd: Int,
    var duration: String,
    var description: String
) {

    constructor(c: Cursor) : this(
        id = c.getInt(c.getColumnIndex(CalendarContract.Events._ID)),
        uid = c.getString(c.getColumnIndex(CalendarContract.Events.CAL_SYNC1)),
        accountName = c.getString(c.getColumnIndex(CalendarContract.Events.ACCOUNT_NAME)),
        accountType = c.getString(c.getColumnIndex(CalendarContract.Events.ACCOUNT_TYPE)),
        title = c.getString(c.getColumnIndex(CalendarContract.Events.TITLE)),
        allDay = c.getShort(c.getColumnIndex(CalendarContract.Events.ALL_DAY)),
        dtStart = c.getInt(c.getColumnIndex(CalendarContract.Events.DTSTART)),
        dtEnd = c.getInt(c.getColumnIndex(CalendarContract.Events.DTEND)),
        duration = c.getString(c.getColumnIndex(CalendarContract.Events.DURATION)),
        description = c.getString(c.getColumnIndex(CalendarContract.Events.DESCRIPTION))
    )
}

