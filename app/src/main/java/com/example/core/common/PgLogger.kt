package com.example.core.common

import android.util.Log
import com.example.BuildConfig

/**
 * Enterprise logging contract representing severity channels.
 */
interface PgLogger {
    fun d(tag: String, msg: String)
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String, tr: Throwable? = null)
    fun e(tag: String, msg: String, tr: Throwable? = null)
}

/**
 * Standard implementation routing log events securely to Logcat and diagnostic consoles.
 */
class PgLoggerImpl : PgLogger {
    override fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg)
        }
    }

    override fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, msg)
        }
    }

    override fun w(tag: String, msg: String, tr: Throwable?) {
        if (tr != null) {
            Log.w(tag, msg, tr)
        } else {
            Log.w(tag, msg)
        }
    }

    override fun e(tag: String, msg: String, tr: Throwable?) {
        if (tr != null) {
            Log.e(tag, msg, tr)
        } else {
            Log.e(tag, msg)
        }
    }
}
