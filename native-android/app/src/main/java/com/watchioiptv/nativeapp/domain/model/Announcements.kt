package com.watchioiptv.nativeapp.domain.model

enum class AnnouncementType { GENERAL, FEATURE, MAINTENANCE, IMPORTANT, UPDATE }

enum class AnnouncementPriority { NORMAL, IMPORTANT, CRITICAL }

enum class AnnouncementScreen(val route: String) {
    HOME("home"),
    LIVE_TV("live"),
    MOVIES("movies"),
    SERIES("series"),
    SETTINGS("settings"),
}

sealed interface AnnouncementAction {
    val label: String

    data class OpenUrl(val url: String, override val label: String = "OPEN") : AnnouncementAction
    data class OpenScreen(val screen: AnnouncementScreen, override val label: String = "VIEW") : AnnouncementAction
    data class OpenUpdater(override val label: String = "UPDATE NOW") : AnnouncementAction
}

data class Announcement(
    val id: String,
    val title: String,
    val body: String,
    val publishedAt: String,
    val type: AnnouncementType,
    val priority: AnnouncementPriority,
    val action: AnnouncementAction? = null,
    val dismissible: Boolean = true,
    val expiresAt: String? = null,
)

data class AnnouncementItem(
    val announcement: Announcement,
    val isRead: Boolean,
    val isDismissed: Boolean,
)

data class AnnouncementSnapshot(
    val items: List<AnnouncementItem> = emptyList(),
    val hasCachedFeed: Boolean = false,
) {
    val unreadCount: Int get() = items.count { !it.isRead && !it.isDismissed }
}
