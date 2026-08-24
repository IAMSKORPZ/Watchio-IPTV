package com.watchioiptv.nativeapp.data.epg

import java.io.InputStream
import java.security.MessageDigest
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class XmlTvParser {
    suspend fun parse(
        inputStream: InputStream,
        onChannel: suspend (EpgChannel) -> Unit,
        onProgramme: suspend (EpgProgramme) -> Unit,
    ): Pair<Int, Int> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(inputStream, Charsets.UTF_8.name())
        var channels = 0
        var programmes = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            currentCoroutineContext().ensureActive()
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> parseChannel(parser)?.let {
                        onChannel(it)
                        channels++
                    }
                    "programme" -> parseProgramme(parser)?.let {
                        onProgramme(it)
                        programmes++
                    }
                }
            }
            event = parser.next()
        }
        return channels to programmes
    }

    private fun parseChannel(parser: XmlPullParser): EpgChannel? {
        val id = parser.getAttributeValue(null, "id")?.trim()?.takeIf { it.isNotBlank() } ?: return null
        var displayName: String? = null
        var icon: String? = null
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.name == "channel")) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "display-name" -> if (displayName == null) displayName = parser.nextText().trim().takeIf { it.isNotBlank() }
                    "icon" -> icon = parser.getAttributeValue(null, "src")?.trim()?.takeIf { it.isNotBlank() }
                }
            }
            event = parser.next()
        }
        return EpgChannel(id, displayName ?: id, icon)
    }

    private fun parseProgramme(parser: XmlPullParser): EpgProgramme? {
        val channel = parser.getAttributeValue(null, "channel")?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val start = parseXmlTvTime(parser.getAttributeValue(null, "start")) ?: return null
        val stop = parseXmlTvTime(parser.getAttributeValue(null, "stop")) ?: return null
        if (stop <= start) return null
        var title: String? = null
        var desc: String? = null
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.name == "programme")) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title" -> if (title == null) title = parser.nextText().collapse()
                    "desc" -> if (desc == null) desc = parser.nextText().collapse()
                    else -> skipTag(parser)
                }
            }
            event = parser.next()
        }
        val safeTitle = title?.takeIf { it.isNotBlank() } ?: "Untitled"
        return EpgProgramme(
            channelId = channel,
            programmeId = programmeId(channel, start, stop, safeTitle),
            title = safeTitle,
            description = desc?.takeIf { it.isNotBlank() },
            startEpochMs = start,
            endEpochMs = stop,
        )
    }

    private fun skipTag(parser: XmlPullParser) {
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
            }
        }
    }

    fun parseXmlTvTime(value: String?): Long? {
        val match = Regex("^(\\d{14})(?:\\s*([+-])(\\d{2})(\\d{2}))?").find(value?.trim().orEmpty()) ?: return null
        val raw = match.groupValues[1]
        val year = raw.substring(0, 4).toInt()
        val month = raw.substring(4, 6).toInt()
        val day = raw.substring(6, 8).toInt()
        val hour = raw.substring(8, 10).toInt()
        val minute = raw.substring(10, 12).toInt()
        val second = raw.substring(12, 14).toInt()
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            isLenient = false
            clear()
            set(year, month - 1, day, hour, minute, second)
        }
        val baseMillis = try {
            utc.timeInMillis
        } catch (_: IllegalArgumentException) {
            return null
        }
        val sign = match.groups[2]?.value ?: return baseMillis
        val hours = match.groupValues[3].toInt()
        val minutes = match.groupValues[4].toInt()
        val offsetMillis = (hours * 3_600_000L + minutes * 60_000L) * if (sign == "+") 1 else -1
        return baseMillis - offsetMillis
    }

    private fun programmeId(channel: String, start: Long, stop: Long, title: String): String =
        sha256("$channel|$start|$stop|${title.trim().lowercase()}").take(32)

    private fun String.collapse(): String = replace(Regex("\\s+"), " ").trim()

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
