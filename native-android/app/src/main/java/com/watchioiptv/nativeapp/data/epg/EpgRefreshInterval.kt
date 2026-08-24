package com.watchioiptv.nativeapp.data.epg

enum class EpgRefreshInterval(val persisted: String, val days: Long, val label: String) {
    OneDay("1_day", 1, "1 Day"),
    ThreeDays("3_days", 3, "3 Days"),
    SevenDays("7_days", 7, "7 Days");

    companion object {
        val Default = ThreeDays

        fun fromPersisted(value: String?): EpgRefreshInterval =
            entries.firstOrNull { it.persisted == value } ?: Default
    }
}
