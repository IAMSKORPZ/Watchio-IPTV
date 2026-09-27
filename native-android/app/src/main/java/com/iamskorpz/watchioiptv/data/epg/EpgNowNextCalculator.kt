package com.iamskorpz.watchioiptv.data.epg

import com.iamskorpz.watchioiptv.core.database.EpgProgrammeEntity
import com.iamskorpz.watchioiptv.data.live.LiveTvNowNext

object EpgNowNextCalculator {
    fun calculate(programmes: List<EpgProgrammeEntity>, nowEpochMs: Long): LiveTvNowNext {
        if (programmes.isEmpty()) return LiveTvNowNext(null, null, 0f)

        val validProgrammes = programmes.asSequence()
            .filter { it.endTimeEpochMs > it.startTimeEpochMs }
            .sortedWith(compareBy<EpgProgrammeEntity> { it.startTimeEpochMs }.thenBy { it.endTimeEpochMs })
            .toList()

        if (validProgrammes.isEmpty()) return LiveTvNowNext(null, null, 0f)

        // Find NOW: programme active at nowEpochMs
        val nowProg = validProgrammes
            .filter { it.startTimeEpochMs <= nowEpochMs && it.endTimeEpochMs > nowEpochMs }
            .maxByOrNull { it.startTimeEpochMs }

        // Find NEXT: first programme after NOW (or after nowEpochMs if no NOW programme)
        val nextProg = if (nowProg != null) {
            validProgrammes.firstOrNull { it.startTimeEpochMs >= nowProg.endTimeEpochMs }
                ?: validProgrammes.firstOrNull { it.startTimeEpochMs > nowEpochMs && it.programmeId != nowProg.programmeId }
        } else {
            validProgrammes.firstOrNull { it.startTimeEpochMs > nowEpochMs }
        }

        // Find LATER: first programme after NEXT
        val laterProg = if (nextProg != null) {
            validProgrammes.firstOrNull { it.startTimeEpochMs >= nextProg.endTimeEpochMs && it.programmeId != (nowProg?.programmeId) }
                ?: validProgrammes.firstOrNull {
                    it.startTimeEpochMs > nextProg.startTimeEpochMs &&
                        it.programmeId != nextProg.programmeId &&
                        it.programmeId != (nowProg?.programmeId)
                }
        } else {
            null
        }

        val progress = if (nowProg != null) {
            val duration = nowProg.endTimeEpochMs - nowProg.startTimeEpochMs
            if (duration <= 0L) 0f else ((nowEpochMs - nowProg.startTimeEpochMs).toFloat() / duration).coerceIn(0f, 1f)
        } else {
            0f
        }

        return LiveTvNowNext(
            currentTitle = nowProg?.title?.takeIf { it.isNotBlank() },
            nextTitle = nextProg?.title?.takeIf { it.isNotBlank() },
            progress = progress,
            currentDescription = nowProg?.description?.takeIf { it.isNotBlank() },
            currentStartEpochMs = nowProg?.startTimeEpochMs,
            currentEndEpochMs = nowProg?.endTimeEpochMs,
            nextStartEpochMs = nextProg?.startTimeEpochMs,
            nextEndEpochMs = nextProg?.endTimeEpochMs,
            laterTitle = laterProg?.title?.takeIf { it.isNotBlank() },
            laterStartEpochMs = laterProg?.startTimeEpochMs,
            laterEndEpochMs = laterProg?.endTimeEpochMs,
        )
    }
}
