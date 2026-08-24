package com.watchioiptv.nativeapp.core.logging

import android.util.Log
import com.watchioiptv.nativeapp.BuildConfig
import com.watchioiptv.nativeapp.core.security.SensitiveUrlMasker

object WatchioLogger {
    fun debug(tag: String, message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        Log.d(tag, SensitiveUrlMasker.mask(message), throwable)
    }

    fun warning(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, SensitiveUrlMasker.mask(message), throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, SensitiveUrlMasker.mask(message), throwable)
    }
}
