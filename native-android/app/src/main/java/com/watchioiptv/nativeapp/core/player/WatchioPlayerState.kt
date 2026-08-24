package com.watchioiptv.nativeapp.core.player

sealed interface WatchioPlayerState {
    val metadata: WatchioPlayerMetadata

    data class Idle(override val metadata: WatchioPlayerMetadata = WatchioPlayerMetadata()) : WatchioPlayerState
    data class Connecting(override val metadata: WatchioPlayerMetadata) : WatchioPlayerState
    data class Buffering(override val metadata: WatchioPlayerMetadata) : WatchioPlayerState
    data class Playing(override val metadata: WatchioPlayerMetadata) : WatchioPlayerState
    data class Paused(override val metadata: WatchioPlayerMetadata) : WatchioPlayerState
    data class Ended(override val metadata: WatchioPlayerMetadata) : WatchioPlayerState
    data class Recovering(val message: String, override val metadata: WatchioPlayerMetadata) : WatchioPlayerState
    data class Failed(val message: String, override val metadata: WatchioPlayerMetadata) : WatchioPlayerState
    data class Released(override val metadata: WatchioPlayerMetadata = WatchioPlayerMetadata()) : WatchioPlayerState
}
