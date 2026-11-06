package com.iamskorpz.watchioiptv.syntheticfailovertest

import android.content.Context
import androidx.core.content.edit
import com.iamskorpz.watchioiptv.data.xtream.WatchioEndpointConfigSource

/** Local-only endpoint source. No remote, cached, or production fallback exists. */
internal class SyntheticFailoverEndpointConfigSource(context: Context) : WatchioEndpointConfigSource {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override suspend fun remote(): String? = null
    override suspend fun cached(): String? = null
    override suspend fun bundled(): String = CONFIG
    override suspend fun saveCache(raw: String) = Unit
    override fun lastWorking(): String? = preferences.getString(KEY_LAST_WORKING, null)
    override fun saveLastWorking(url: String) {
        require(url == PRIMARY_URL || url == SECONDARY_URL) { "Synthetic endpoint must be localhost-only" }
        preferences.edit { putString(KEY_LAST_WORKING, url) }
    }
    override fun clearLastWorking() = preferences.edit { remove(KEY_LAST_WORKING) }

    private companion object {
        const val PREFERENCES = "watchio_synthetic_failover_endpoint_config"
        const val KEY_LAST_WORKING = "last_working_endpoint"
        const val PRIMARY_URL = "http://127.0.0.1:18081"
        const val SECONDARY_URL = "http://127.0.0.1:18082"
        const val CONFIG = """{"version":1,"endpoints":[{"id":"synthetic-primary","url":"$PRIMARY_URL","priority":0,"enabled":true},{"id":"synthetic-secondary","url":"$SECONDARY_URL","priority":1,"enabled":true}]}"""
    }
}
