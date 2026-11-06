package com.iamskorpz.watchioiptv.uitest

import android.content.Context
import androidx.core.content.edit
import com.iamskorpz.watchioiptv.data.xtream.WatchioEndpointConfigSource

/** Isolated endpoint source used only by the UITEST application ID. */
internal class UitestWatchioEndpointConfigSource(context: Context) : WatchioEndpointConfigSource {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override suspend fun remote(): String? = null
    override suspend fun cached(): String? = null
    override suspend fun bundled(): String = CONFIG
    override suspend fun saveCache(raw: String) = Unit
    override fun lastWorking(): String? = preferences.getString(KEY_LAST_WORKING, null)
    override fun saveLastWorking(url: String) = preferences.edit { putString(KEY_LAST_WORKING, url) }
    override fun clearLastWorking() = preferences.edit { remove(KEY_LAST_WORKING) }

    private companion object {
        const val PREFERENCES = "watchio_uitest_endpoint_config"
        const val KEY_LAST_WORKING = "last_working_endpoint"
        const val CONFIG = """{"version":1,"endpoints":[{"id":"uitest-primary","url":"http://127.0.0.1:18081","priority":0,"enabled":true},{"id":"uitest-secondary","url":"http://127.0.0.1:18082","priority":1,"enabled":true}]}"""
    }
}
