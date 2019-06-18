package org.openintents.calendar.sync.model

import org.json.JSONObject

data class BlockstackCalendar(val type: String?, val data: JSONObject, val calendarId: String?, val error:String? = null)
