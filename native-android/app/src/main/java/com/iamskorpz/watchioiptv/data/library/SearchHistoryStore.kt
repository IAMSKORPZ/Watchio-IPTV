package com.iamskorpz.watchioiptv.data.library

import android.content.SharedPreferences
import com.iamskorpz.watchioiptv.core.model.ProviderId
import org.json.JSONArray

interface SearchHistoryStore {
    fun get(providerId: ProviderId): List<String>
    fun put(providerId: ProviderId, queries: List<String>)
}

object EmptySearchHistoryStore : SearchHistoryStore {
    override fun get(providerId: ProviderId) = emptyList<String>()
    override fun put(providerId: ProviderId, queries: List<String>) = Unit
}

class SharedPreferencesSearchHistoryStore(private val preferences: SharedPreferences) : SearchHistoryStore {
    override fun get(providerId: ProviderId): List<String> = runCatching {
        val rows = JSONArray(preferences.getString(key(providerId), "[]"))
        List(rows.length()) { index -> rows.getString(index) }.filter(String::isNotBlank)
    }.getOrDefault(emptyList())

    override fun put(providerId: ProviderId, queries: List<String>) {
        preferences.edit().putString(key(providerId), JSONArray(queries).toString()).apply()
    }

    private fun key(providerId: ProviderId) = "recent_${providerId.value}"
}
