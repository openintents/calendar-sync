/*
 * Copyright 2013 Google Inc.
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
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.CalendarContract
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import org.openintents.calendar.common.accounts.GenericAccountService


/**
 * Static helper methods for working with the sync framework.
 */
object SyncUtils {
    private val SYNC_FREQUENCY = (60 * 60).toLong()  // 1 hour (in seconds)
    private val CONTENT_AUTHORITY = CalendarContract.AUTHORITY
    private val PREF_SETUP_COMPLETE = "setup_complete"
    // Value below must match the account type specified in res/xml/syncadapter.xml
    val ACCOUNT_TYPE = "org.openintents.calendar.account"

    /**
     * Create an entry for this application in the system account list, if it isn't already there.
     *
     * @param context Context
     */
    fun createSyncAccount(context: Context, accountName: String?): Account? {
        var newAccount = false
        val setupComplete = PreferenceManager
            .getDefaultSharedPreferences(context).getBoolean(PREF_SETUP_COMPLETE, false)

        // Create account, if it's missing. (Either first run, or user has deleted account.)
        val account = if (accountName != null) {
            Account(accountName, ACCOUNT_TYPE)
        } else {
            GenericAccountService.getAccount(context, ACCOUNT_TYPE)
        }
        val accountManager = context.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
        if (account != null && accountManager.addAccountExplicitly(account, null, null)) {
            // Inform the system that this account supports sync
            ContentResolver.setIsSyncable(account, CONTENT_AUTHORITY, 1)
            // Inform the system that this account is eligible for auto sync when the network is up
            ContentResolver.setSyncAutomatically(account, CONTENT_AUTHORITY, true)
            // Recommend a schedule for automatic synchronization. The system may modify this based
            // on other scheduled syncs and network utilization.
            ContentResolver.addPeriodicSync(
                account, CONTENT_AUTHORITY, Bundle(), SYNC_FREQUENCY
            )
            newAccount = true
        }

        // Schedule an initial sync if we detect problems with either our account or our local
        // data has been deleted. (Note that it's possible to clear app data WITHOUT affecting
        // the account list, so wee need to check both.)
        if (newAccount || !setupComplete) {
            triggerRefresh(account!!)
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(PREF_SETUP_COMPLETE, true).apply()
        }
        return account
    }

    /**
     * Helper method to trigger an immediate sync ("refresh").
     *
     *
     * This should only be used when we need to preempt the normal sync schedule. Typically, this
     * means the user has pressed the "refresh" button.
     *
     * Note that SYNC_EXTRAS_MANUAL will cause an immediate sync, without any optimization to
     * preserve battery life. If you know new data is available (perhaps via a GCM notification),
     * but the user is not actively waiting for that data, you should omit this flag; this will give
     * the OS additional freedom in scheduling your sync request.
     */
    fun triggerRefresh(context: Context) {

        val account = GenericAccountService.getAccount(context, ACCOUNT_TYPE) // Sync account
        if (account != null) {
           triggerRefresh(account)
        }
    }

    fun triggerRefresh(account: Account) {
        Log.d("SyncUtils", "triggerRefresh")
        // Disable sync backoff and ignore sync preferences. In other words...perform sync NOW!
        val b = Bundle()
        b.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
        b.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        ContentResolver.requestSync(
            account,
            CalendarContract.AUTHORITY, // Content authority
            b
        )
    }


    fun removeAccount(context: Context, accountName: String?) {
        if (accountName != null) {
            val account = Account(accountName, ACCOUNT_TYPE)
            val accountManager = context.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                accountManager.removeAccountExplicitly(account)
            } else {
                Toast.makeText(context, R.string.remove_account_min_sdk, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
