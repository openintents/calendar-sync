package org.openintents.calendar.sync

import android.content.Context
import androidx.preference.PreferenceManager
import org.blockstack.android.sdk.SessionStore

class SessionStoreProvider {

    companion object {
        var instance: SessionStore? = null
        fun getInstance(context: Context): SessionStore {
            var sessionStore = instance
            if (sessionStore == null) {
                sessionStore = SessionStore(PreferenceManager.getDefaultSharedPreferences(context))
                instance = sessionStore
            }
            return sessionStore
        }

    }
}