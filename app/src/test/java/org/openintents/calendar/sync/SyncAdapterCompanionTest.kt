package org.openintents.calendar.sync

import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertThat
import org.junit.Test
import org.openintents.calendar.sync.SyncAdapter.Companion.format
import org.openintents.calendar.sync.SyncAdapter.Companion.parseDt

val aDateString = "2019-06-17T14:00:00.000Z"

val aDate = 1560780000000L

class SyncAdapterCompanionTest {
    @Test
    fun parseFormat() {
        val dateTime = parseDt(aDateString)
        val dateTimeString = format(dateTime ?: 0)
        assertThat(dateTimeString, `is`(aDateString))
    }

    @Test
    fun formatParse() {
        val dateTimeString = format(aDate)
        val dateTime = parseDt(dateTimeString)
        assertThat(dateTime, `is`(aDate))
    }
}
