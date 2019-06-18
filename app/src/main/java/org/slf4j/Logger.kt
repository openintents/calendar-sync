package org.slf4j

import android.util.Log

class Logger(clazz: Class<*>) {
    fun debug(s: String) {
        Log.d(TAG, s)
    }


    private val TAG = clazz::class.java.simpleName

    fun isDebugEnabled(): Boolean {
        return false
    }

    fun error(s: String, e: Exception) {
        Log.e(TAG, s, e)
    }

    fun info(s: String) {
        Log.i(TAG, s)
    }

    fun info(s: String, e: Exception) {
        Log.i(TAG, s, e)
    }

    fun isTraceEnabled(): Boolean {
        return false
    }

    fun trace(s: String) {
        Log.v(TAG, s)
    }

    fun warn(s: String, e: Exception) {
        Log.w(TAG, s, e)
    }

    fun warn(s: String) {
        Log.w(TAG, s)
    }
}
