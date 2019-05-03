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

package org.openintents.calendar.common.accounts

import android.accounts.*
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.blockstack.android.sdk.BlockstackSession
import org.openintents.calendar.sync.AccountActivity
import org.openintents.calendar.sync.blockstackConfig
import org.openintents.calendar.sync.executorFactory
import org.openintents.calendar.sync.j2v8Dispatcher

class GenericAccountService : Service() {
    private var mAuthenticator: Authenticator? = null

    override fun onCreate() {
        Log.i(TAG, "Service created")
        mAuthenticator = Authenticator(this)
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent): IBinder? {
        return mAuthenticator!!.iBinder
    }

    inner class Authenticator(private val context: Context) : AbstractAccountAuthenticator(context) {

        override fun editProperties(
            accountAuthenticatorResponse: AccountAuthenticatorResponse,
            s: String
        ): Bundle {
            throw UnsupportedOperationException()
        }

        @Throws(NetworkErrorException::class)
        override fun addAccount(
            accountAuthenticatorResponse: AccountAuthenticatorResponse,
            accountType: String, authTokenType: String, requiredFeatures: Array<String>, options: Bundle
        ): Bundle? {
            val b = Bundle()
            b.putParcelable(AccountManager.KEY_INTENT, Intent(context, AccountActivity::class.java).putExtra("action", "addAccount"))
            return b
        }

        @Throws(NetworkErrorException::class)
        override fun confirmCredentials(
            accountAuthenticatorResponse: AccountAuthenticatorResponse,
            account: Account, bundle: Bundle
        ): Bundle? {
            return null
        }

        @Throws(NetworkErrorException::class)
        override fun getAuthToken(
            accountAuthenticatorResponse: AccountAuthenticatorResponse,
            account: Account, s: String, bundle: Bundle
        ): Bundle {
            throw UnsupportedOperationException()
        }

        override fun getAuthTokenLabel(s: String): String {
            throw UnsupportedOperationException()
        }

        @Throws(NetworkErrorException::class)
        override fun updateCredentials(
            accountAuthenticatorResponse: AccountAuthenticatorResponse,
            account: Account, s: String, bundle: Bundle
        ): Bundle {
            throw UnsupportedOperationException()
        }

        @Throws(NetworkErrorException::class)
        override fun hasFeatures(
            accountAuthenticatorResponse: AccountAuthenticatorResponse,
            account: Account, strings: Array<String>
        ): Bundle {
            throw UnsupportedOperationException()
        }
    }

    companion object {
        private val TAG = "GenericAccountService"
        fun getAccount(context: Context, accountType: String): Account? {

            var accountName:String? = null
            runBlocking(j2v8Dispatcher) {
               val blockstack = BlockstackSession(context, blockstackConfig, executor = executorFactory(context))
                if (blockstack.isUserSignedIn()) {
                    accountName = blockstack.loadUserData()?.json?.getString("username")
                }
            }
            if (accountName != null) {
                return Account(accountName, accountType)
            } else {
                return null
            }
        }
    }

}

