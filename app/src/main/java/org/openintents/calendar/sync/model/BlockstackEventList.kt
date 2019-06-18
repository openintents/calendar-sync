package org.openintents.calendar.sync.model

import org.json.JSONObject

class BlockstackEventList(val blockstackCalendar: BlockstackCalendar, val events: JSONObject) : JSONObject() {
    init {
        this.put("events", events)
        this.put("calendarId", blockstackCalendar.calendarId)
    }
}
