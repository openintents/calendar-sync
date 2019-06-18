package org.openintents.calendar.sync.model

import android.content.ContentResolver
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.*
import net.fortuna.ical4j.model.property.*
import java.net.URISyntaxException

data class LocalEvent(
    var id: Int,
    var uid: String,
    var accountName: String,
    var accountType: String,
    var title: String,
    var allDay: Short,
    var dtStart: Long,
    var dtEnd: Long,
    var duration: String?,
    var description: String?,
    var calendarTimeZone: String?,
    var eventTimeZone: String?,
    var eventEndTimeZone: String?,
    var eventLocation: String?
) {

    var dirty: Int = 0
    var exdate: String? = null
    var exrule: String? = null
    var rrule: String? = null
    var rdate: String? = null
    var originalId: String? = null
    var originalAllDay: Short? = null
    var originalInstanceTime: Long? = null
    var originalSyncId: String? = null
    val reminders = ComponentList<VAlarm>()
    val attendees = PropertyList<Property>()

    constructor(c: Cursor, contentResolver: ContentResolver) : this(
        id = c.getInt(c.getColumnIndex(CalendarContract.Events._ID)),
        uid = c.getString(c.getColumnIndex(CalendarContract.Events.CAL_SYNC1)),
        accountName = c.getString(c.getColumnIndex(CalendarContract.Events.ACCOUNT_NAME)),
        accountType = c.getString(c.getColumnIndex(CalendarContract.Events.ACCOUNT_TYPE)),
        title = c.getString(c.getColumnIndex(CalendarContract.Events.TITLE)),
        allDay = c.getShort(c.getColumnIndex(CalendarContract.Events.ALL_DAY)),
        dtStart = c.getLong(c.getColumnIndex(CalendarContract.Events.DTSTART)),
        dtEnd = c.getLong(c.getColumnIndex(CalendarContract.Events.DTEND)),
        duration = c.getString(c.getColumnIndex(CalendarContract.Events.DURATION)),
        description = c.getString(c.getColumnIndex(CalendarContract.Events.DESCRIPTION)),
        calendarTimeZone = c.getString(c.getColumnIndex(CalendarContract.Events.CALENDAR_TIME_ZONE)),
        eventTimeZone = c.getString(c.getColumnIndex(CalendarContract.Events.EVENT_TIMEZONE)),
        eventEndTimeZone = c.getString(c.getColumnIndex(CalendarContract.Events.EVENT_END_TIMEZONE)),
        eventLocation = c.getString(c.getColumnIndex(CalendarContract.Events.EVENT_LOCATION))
    ) {

        this.rdate = c.getString(c.getColumnIndex(CalendarContract.Events.RDATE))
        this.rrule = c.getString(c.getColumnIndex(CalendarContract.Events.RRULE))
        this.exrule = c.getString(c.getColumnIndex(CalendarContract.Events.EXRULE))
        this.exdate = c.getString(c.getColumnIndex(CalendarContract.Events.EXDATE))
        this.dirty = c.getInt(c.getColumnIndex(CalendarContract.Events.DIRTY))
        this.originalId = c.getString(c.getColumnIndex(CalendarContract.Events.ORIGINAL_ID))
        this.originalAllDay = c.getShort(c.getColumnIndex(CalendarContract.Events.ORIGINAL_ALL_DAY))
        this.originalInstanceTime = c.getLong(c.getColumnIndex(CalendarContract.Events.ORIGINAL_INSTANCE_TIME))
        this.originalSyncId = c.getString(c.getColumnIndex(CalendarContract.Events.ORIGINAL_SYNC_ID))

        var selection = "(" + CalendarContract.Attendees.EVENT_ID + " = ?)"
        var selectionArgs = arrayOf(id.toString())
        val cAttendees =
            contentResolver.query(CalendarContract.Attendees.CONTENT_URI, null, selection, selectionArgs, null)
        if (cAttendees != null) {
            getAttendees(cAttendees)
            cAttendees.close()
        }

        selection = "(" + CalendarContract.Reminders.EVENT_ID + " = ?)"
        selectionArgs = arrayOf(id.toString())
        val cReminders =
            contentResolver.query(CalendarContract.Reminders.CONTENT_URI, null, selection, selectionArgs, null)

        if (cReminders != null) {
            getReminders(cReminders)
            cReminders.close()
        }
    }

    private fun getAttendees(cur: Cursor): Boolean {
        var attendee: Attendee?
        var organizer: Organizer?
        var paraList: ParameterList?

        var name: String
        var cn: Cn?

        var email: String?

        var relationship: Int

        var status: Int
        var partstat: PartStat?

        var type: Int
        var role: Role?

        try {
            while (cur.moveToNext()) {
                name = cur.getString(cur.getColumnIndex(CalendarContract.Attendees.ATTENDEE_NAME))
                email = cur.getString(cur.getColumnIndex(CalendarContract.Attendees.ATTENDEE_EMAIL))
                relationship = cur.getInt(cur.getColumnIndex(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP))
                type = cur.getInt(cur.getColumnIndex(CalendarContract.Attendees.ATTENDEE_TYPE))
                status = cur.getInt(cur.getColumnIndex(CalendarContract.Attendees.ATTENDEE_STATUS))

                if (relationship == CalendarContract.Attendees.RELATIONSHIP_ORGANIZER) {
                    organizer = Organizer()
                    organizer.setValue("mailto:$email")
                    paraList = organizer.getParameters()
                    attendees.add(organizer)
                } else {
                    attendee = Attendee()
                    attendee.setValue("mailto:$email")
                    paraList = attendee.parameters
                    attendees.add(attendee)
                }

                val rsvp = Rsvp(true)
                paraList!!.add(rsvp)

                cn = Cn(name)
                paraList.add(cn)

                if (status == CalendarContract.Attendees.ATTENDEE_STATUS_INVITED) {
                    partstat = PartStat(PartStat.NEEDS_ACTION.value)
                } else if (status == CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED) {
                    partstat = PartStat(PartStat.ACCEPTED.value)
                } else if (status == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) {
                    partstat = PartStat(PartStat.DECLINED.value)
                } else if (status == CalendarContract.Attendees.ATTENDEE_STATUS_NONE) {
                    partstat = PartStat(PartStat.COMPLETED.value)
                } else if (status == CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE) {
                    partstat = PartStat(PartStat.TENTATIVE.value)
                } else {
                    partstat = PartStat(PartStat.NEEDS_ACTION.value)
                }
                paraList.add(partstat)

                if (type == CalendarContract.Attendees.TYPE_OPTIONAL) {
                    role = Role(Role.OPT_PARTICIPANT.value)
                } else if (type == CalendarContract.Attendees.TYPE_NONE) {
                    role = Role(
                        Role.NON_PARTICIPANT
                            .value
                    ) //regular participants in android are non required?
                } else if (type == CalendarContract.Attendees.TYPE_REQUIRED) {
                    role = Role(Role.REQ_PARTICIPANT.value)
                } else {
                    role = Role(Role.NON_PARTICIPANT.value)
                }
                paraList.add(role)
            }

        } catch (e: URISyntaxException) {
            Log.e(TAG, e.message)
        }

        return true
    }


    private fun getReminders(cur: Cursor): Boolean {
        var method: Int
        var minutes: Int
        var reminder: VAlarm
        while (cur.moveToNext()) {
            reminder = VAlarm()
            method = cur.getInt(cur.getColumnIndex(CalendarContract.Reminders.METHOD))
            minutes = cur.getInt(cur.getColumnIndex(CalendarContract.Reminders.MINUTES)) * -1

            val dur = Dur(0, 0, minutes, 0)
            val tri = Trigger(dur)
            val `val` = Value(Duration.DURATION)
            tri.parameters.add(`val`)
            reminder.properties.add(tri)

            val desc = Description()
            desc.value = "OI Calendar"
            reminder.properties.add(desc)

            if (method == CalendarContract.Reminders.METHOD_EMAIL) {
                reminder.properties.add(Action.EMAIL)
            } else {
                reminder.properties.add(Action.DISPLAY)
            }

            reminders.add(reminder)
        }
        return true
    }

    companion object {
        private val TAG = LocalEvent::class.java.simpleName
    }

}

