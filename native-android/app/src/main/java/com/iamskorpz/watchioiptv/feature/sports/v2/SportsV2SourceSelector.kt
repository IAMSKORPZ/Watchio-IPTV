package com.iamskorpz.watchioiptv.feature.sports.v2

enum class SportsV2SourceSelection { FootballData, ApiFootball }

class SportsV2SourceSelector(
    private val footballData: FixtureSource,
    private val apiFootball: FixtureSource,
    private val selection: SportsV2SourceSelection = SportsV2SourceSelection.FootballData,
) {
    fun selected(): FixtureSource = when (selection) {
        SportsV2SourceSelection.FootballData -> footballData
        SportsV2SourceSelection.ApiFootball -> apiFootball
    }
}
