package org.openintents.calendar.sync

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.Intent
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue

import org.junit.Test

import org.junit.Assert.*
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class SyncServiceTest {

	@get:Rule
	val serviceRule = ServiceTestRule()

	@Test
	fun testPerformSync() {
		val serviceIntent = Intent()
		val binder = serviceRule.bindService(serviceIntent)
		assertThat(binder, `is`(notNullValue()))
	}
}
