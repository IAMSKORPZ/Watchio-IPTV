package com.watchioiptv.nativeapp

import android.view.ViewGroup
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.player.PlaybackMedia
import com.watchioiptv.nativeapp.core.player.WatchioAudioTrack
import com.watchioiptv.nativeapp.core.player.WatchioPlayerManager
import com.watchioiptv.nativeapp.core.player.WatchioPlayerMetadata
import com.watchioiptv.nativeapp.core.player.WatchioPlayerState
import com.watchioiptv.nativeapp.core.player.WatchioSubtitleTrack
import com.watchioiptv.nativeapp.core.util.WatchioClock
import com.watchioiptv.nativeapp.data.epg.EpgImportResult
import com.watchioiptv.nativeapp.data.epg.EpgRefreshCoordinator
import com.watchioiptv.nativeapp.data.live.LiveTvCategory
import com.watchioiptv.nativeapp.data.live.LiveTvCategoryKind
import com.watchioiptv.nativeapp.data.live.LiveTvChannel
import com.watchioiptv.nativeapp.data.live.LiveTvNowNext
import com.watchioiptv.nativeapp.data.live.LiveTvPlaybackRequest
import com.watchioiptv.nativeapp.data.live.LiveTvRepository
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.domain.model.StreamFormat
import com.watchioiptv.nativeapp.domain.repository.FavoriteItem
import com.watchioiptv.nativeapp.domain.repository.FavoritesRepository
import com.watchioiptv.nativeapp.domain.repository.HistoryItem
import com.watchioiptv.nativeapp.domain.repository.HistoryRepository
import com.watchioiptv.nativeapp.domain.repository.LiveTvBrowsingState
import com.watchioiptv.nativeapp.domain.repository.PlayerSettings
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import com.watchioiptv.nativeapp.domain.repository.VideoScalingMode
import com.watchioiptv.nativeapp.feature.live.LiveTvViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvBrowsingStateTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun testChannel(
        providerId: ProviderId,
        id: String,
        name: String,
        categoryId: String,
    ) = LiveTvChannel(
        providerId = providerId,
        providerType = ProviderType.Xtream,
        id = id,
        name = name,
        logoUrl = null,
        categoryId = categoryId,
        epgChannelId = null,
        extension = "ts",
        directUrl = null,
        headers = emptyMap(),
        serverOrder = 1,
        isFavorite = false,
    )

    private class FakeSettingsRepository : SettingsRepository {
        var currentProviderId: ProviderId? = ProviderId("provider-1")
        val browsingStates = mutableMapOf<String, LiveTvBrowsingState>()
        var playerSettingsValue = PlayerSettings(
            autoPlayLiveChannel = false,
            rememberLastLiveChannel = true,
        )

        override val selectedProviderId: Flow<ProviderId?>
            get() = flowOf(currentProviderId)
        override val inputMode: Flow<InputMode>
            get() = flowOf(InputMode.TvRemote)
        override val streamFormat: Flow<StreamFormat>
            get() = flowOf(StreamFormat.Auto)
        override val playerSettings: Flow<PlayerSettings>
            get() = flowOf(playerSettingsValue)

        override suspend fun setSelectedProviderId(providerId: ProviderId?) {
            currentProviderId = providerId
        }
        override suspend fun setInputMode(inputMode: InputMode) = Unit
        override suspend fun setStreamFormat(streamFormat: StreamFormat) = Unit

        override fun observeLiveBrowsingState(providerId: ProviderId): Flow<LiveTvBrowsingState> =
            flowOf(browsingStates[providerId.value] ?: LiveTvBrowsingState())

        override suspend fun saveLiveBrowsingState(providerId: ProviderId, state: LiveTvBrowsingState) {
            browsingStates[providerId.value] = state
        }
    }

    private class FakeFavoritesRepository : FavoritesRepository {
        override suspend fun toggle(favorite: FavoriteItem): Boolean = false
        override suspend fun isFavorite(providerId: ProviderId, contentType: ContentType, contentId: String, subContentId: String?): Boolean = false
        override suspend fun getFavorites(providerId: ProviderId): List<FavoriteItem> = emptyList()
    }

    private class FakeHistoryRepository : HistoryRepository {
        override suspend fun upsert(item: HistoryItem) = Unit
        override suspend fun find(providerId: ProviderId, contentType: ContentType, contentId: String, subContentId: String?): HistoryItem? = null
        override suspend fun recent(providerId: ProviderId): List<HistoryItem> = emptyList()
    }

    private class FakePlayerManager : WatchioPlayerManager {
        private var metadata = WatchioPlayerMetadata()
        private val mutableState = MutableStateFlow<WatchioPlayerState>(WatchioPlayerState.Idle(metadata))
        override val state: StateFlow<WatchioPlayerState> = mutableState
        var lastLoadedMedia: PlaybackMedia? = null

        override suspend fun load(media: PlaybackMedia) {
            lastLoadedMedia = media
            metadata = metadata.copy(currentMedia = media, isSeekable = !media.isLive)
            mutableState.value = WatchioPlayerState.Playing(metadata)
        }
        override fun play() {
            mutableState.value = WatchioPlayerState.Playing(metadata)
        }
        override fun pause() {
            mutableState.value = WatchioPlayerState.Paused(metadata)
        }
        override fun stop() {
            metadata = metadata.copy(currentMedia = null)
            mutableState.value = WatchioPlayerState.Idle(metadata)
        }
        override fun retry() = Unit
        override fun seekTo(positionMs: Long) {
            metadata = metadata.copy(positionMs = positionMs)
        }
        override fun seekBy(deltaMs: Long) {
            metadata = metadata.copy(positionMs = (metadata.positionMs + deltaMs).coerceAtLeast(0L))
        }
        override fun selectAudioTrack(track: WatchioAudioTrack) {
            metadata = metadata.copy(selectedAudioTrack = track)
        }
        override fun selectSubtitleTrack(track: WatchioSubtitleTrack?) {
            metadata = metadata.copy(selectedSubtitleTrack = track)
        }
        override fun setVideoScalingMode(mode: VideoScalingMode) {
            metadata = metadata.copy(videoScalingMode = mode)
        }
        override fun setPlaybackSpeed(speed: Float) {
            metadata = metadata.copy(playbackSpeed = speed)
        }
        override fun setMuted(muted: Boolean) {
            metadata = metadata.copy(isMuted = muted)
        }
        override fun restart() {
            seekTo(0L)
            play()
        }
        override fun snapshot(): WatchioPlayerMetadata = metadata
        override fun attachSurface(container: ViewGroup) = Unit
        override fun detachSurface(container: ViewGroup) = Unit
        override fun release() = Unit
    }

    private class FakeLiveTvRepo(
        private val categoriesMap: Map<String, List<LiveTvCategory>>,
        private val channelsMap: Map<String, Map<String, List<LiveTvChannel>>>,
    ) : LiveTvRepository() {
        var selectedProvId: ProviderId? = ProviderId("provider-1")

        override suspend fun selectedProviderId(): ProviderId? = selectedProvId

        override suspend fun categories(providerId: ProviderId): List<LiveTvCategory> =
            categoriesMap[providerId.value].orEmpty()

        override suspend fun channels(providerId: ProviderId, category: LiveTvCategory): List<LiveTvChannel> =
            channelsMap[providerId.value]?.get(category.id).orEmpty()

        override suspend fun playback(channel: LiveTvChannel): LiveTvPlaybackRequest =
            LiveTvPlaybackRequest(channel, "http://example.com/${channel.id}.ts", emptyMap())

        override suspend fun nowNext(channel: LiveTvChannel, nowEpochMs: Long): LiveTvNowNext =
            LiveTvNowNext("Now on ${channel.name}", "Next on ${channel.name}", 0.5f)
    }

    private class FakeEpgCoordinator : EpgRefreshCoordinator() {
        override suspend fun refreshProvider(providerId: String): EpgImportResult =
            EpgImportResult(providerId, 0, 0)
    }

    @Test
    fun restoresLastCategoryAndChannelById() = runTest(testDispatcher) {
        val providerId = ProviderId("provider-1")
        val catAll = LiveTvCategory("all", "ALL CHANNELS", LiveTvCategoryKind.All)
        val catMovies = LiveTvCategory("cat-movies", "Sky Movies", LiveTvCategoryKind.Provider)
        val catSports = LiveTvCategory("cat-sports", "Sports", LiveTvCategoryKind.Provider)

        val ch1 = testChannel(providerId, "ch-1", "Sky Premiere", "cat-movies")
        val ch2 = testChannel(providerId, "ch-2", "Sky Comedy", "cat-movies")
        val ch3 = testChannel(providerId, "ch-3", "Sky Action", "cat-movies")

        val categoriesMap = mapOf("provider-1" to listOf(catAll, catMovies, catSports))
        val channelsMap = mapOf(
            "provider-1" to mapOf(
                "all" to listOf(ch1, ch2, ch3),
                "cat-movies" to listOf(ch1, ch2, ch3),
                "cat-sports" to emptyList(),
            )
        )

        val settingsRepo = FakeSettingsRepository().apply {
            currentProviderId = providerId
            browsingStates[providerId.value] = LiveTvBrowsingState(
                categoryId = "cat-movies",
                categoryName = "Sky Movies",
                channelId = "ch-2",
                channelName = "Sky Comedy",
                channelIndex = 1,
                scrollIndex = 1,
            )
        }

        val fakeRepo = FakeLiveTvRepo(categoriesMap, channelsMap)
        val playerManager = FakePlayerManager()

        val viewModel = LiveTvViewModel(
            liveTvRepository = fakeRepo,
            favoritesRepository = FakeFavoritesRepository(),
            historyRepository = FakeHistoryRepository(),
            settingsRepository = settingsRepo,
            epgRefreshCoordinator = FakeEpgCoordinator(),
            playerManager = playerManager,
            clock = object : WatchioClock { override fun nowEpochMs(): Long = 1000L },
        )

        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals("cat-movies", state.selectedCategory?.id)
        assertEquals("Sky Movies", state.selectedCategory?.name)
        assertEquals("ch-2", state.selectedChannel?.id)
        assertEquals("Sky Comedy", state.selectedChannel?.name)
        assertEquals(1, state.initialScrollIndex)
        // Auto-play was false, so playerManager did not load
        assertNull(playerManager.lastLoadedMedia)
        viewModel.leaveLiveTv()
    }

    @Test
    fun intentionallySelectedAllChannelsIsPersistedAndRestored() = runTest(testDispatcher) {
        val providerId = ProviderId("provider-1")
        val catAll = LiveTvCategory("all", "ALL CHANNELS", LiveTvCategoryKind.All)
        val catNews = LiveTvCategory("cat-news", "News", LiveTvCategoryKind.Provider)

        val ch1 = testChannel(providerId, "ch-1", "BBC News", "cat-news")
        val ch2 = testChannel(providerId, "ch-2", "CNN", "cat-news")

        val categoriesMap = mapOf("provider-1" to listOf(catAll, catNews))
        val channelsMap = mapOf(
            "provider-1" to mapOf(
                "all" to listOf(ch1, ch2),
                "cat-news" to listOf(ch1, ch2),
            )
        )

        val settingsRepo = FakeSettingsRepository().apply {
            currentProviderId = providerId
            browsingStates[providerId.value] = LiveTvBrowsingState(
                categoryId = "all",
                categoryName = "ALL CHANNELS",
                channelId = "ch-2",
                channelName = "CNN",
                channelIndex = 1,
            )
        }

        val fakeRepo = FakeLiveTvRepo(categoriesMap, channelsMap)
        val viewModel = LiveTvViewModel(
            liveTvRepository = fakeRepo,
            favoritesRepository = FakeFavoritesRepository(),
            historyRepository = FakeHistoryRepository(),
            settingsRepository = settingsRepo,
            epgRefreshCoordinator = FakeEpgCoordinator(),
            playerManager = FakePlayerManager(),
            clock = object : WatchioClock { override fun nowEpochMs(): Long = 1000L },
        )

        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals("all", state.selectedCategory?.id)
        assertEquals("ch-2", state.selectedChannel?.id)
        viewModel.leaveLiveTv()
    }

    @Test
    fun staleCategoryFallsBackToDefaultAllChannels() = runTest(testDispatcher) {
        val providerId = ProviderId("provider-1")
        val catAll = LiveTvCategory("all", "ALL CHANNELS", LiveTvCategoryKind.All)
        val catSports = LiveTvCategory("cat-sports", "Sports", LiveTvCategoryKind.Provider)

        val ch1 = testChannel(providerId, "ch-1", "Sky Sports Main Event", "cat-sports")

        val categoriesMap = mapOf("provider-1" to listOf(catAll, catSports))
        val channelsMap = mapOf(
            "provider-1" to mapOf(
                "all" to listOf(ch1),
                "cat-sports" to listOf(ch1),
            )
        )

        val settingsRepo = FakeSettingsRepository().apply {
            currentProviderId = providerId
            browsingStates[providerId.value] = LiveTvBrowsingState(
                categoryId = "deleted-category",
                categoryName = "Deleted Category",
                channelId = "ch-1",
            )
        }

        val fakeRepo = FakeLiveTvRepo(categoriesMap, channelsMap)
        val viewModel = LiveTvViewModel(
            liveTvRepository = fakeRepo,
            favoritesRepository = FakeFavoritesRepository(),
            historyRepository = FakeHistoryRepository(),
            settingsRepository = settingsRepo,
            epgRefreshCoordinator = FakeEpgCoordinator(),
            playerManager = FakePlayerManager(),
            clock = object : WatchioClock { override fun nowEpochMs(): Long = 1000L },
        )

        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals("all", state.selectedCategory?.id)
        assertEquals("ch-1", state.selectedChannel?.id)
        viewModel.leaveLiveTv()
    }

    @Test
    fun staleChannelFallsBackToIndexOrFirstChannel() = runTest(testDispatcher) {
        val providerId = ProviderId("provider-1")
        val catMovies = LiveTvCategory("cat-movies", "Movies", LiveTvCategoryKind.Provider)

        val ch1 = testChannel(providerId, "ch-1", "Movie 1", "cat-movies")
        val ch2 = testChannel(providerId, "ch-2", "Movie 2", "cat-movies")

        val categoriesMap = mapOf("provider-1" to listOf(catMovies))
        val channelsMap = mapOf(
            "provider-1" to mapOf(
                "cat-movies" to listOf(ch1, ch2),
            )
        )

        val settingsRepo = FakeSettingsRepository().apply {
            currentProviderId = providerId
            browsingStates[providerId.value] = LiveTvBrowsingState(
                categoryId = "cat-movies",
                categoryName = "Movies",
                channelId = "deleted-channel",
                channelName = "Deleted Channel",
                channelIndex = 1,
            )
        }

        val fakeRepo = FakeLiveTvRepo(categoriesMap, channelsMap)
        val viewModel = LiveTvViewModel(
            liveTvRepository = fakeRepo,
            favoritesRepository = FakeFavoritesRepository(),
            historyRepository = FakeHistoryRepository(),
            settingsRepository = settingsRepo,
            epgRefreshCoordinator = FakeEpgCoordinator(),
            playerManager = FakePlayerManager(),
            clock = object : WatchioClock { override fun nowEpochMs(): Long = 1000L },
        )

        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals("ch-2", state.selectedChannel?.id)
        assertEquals("Movie 2", state.selectedChannel?.name)
        viewModel.leaveLiveTv()
    }

    @Test
    fun changingCategoryAndSurfingChannelsPersistsAuthoritativeState() = runTest(testDispatcher) {
        val providerId = ProviderId("provider-1")
        val catMovies = LiveTvCategory("cat-movies", "Movies", LiveTvCategoryKind.Provider)
        val catSports = LiveTvCategory("cat-sports", "Sports", LiveTvCategoryKind.Provider)

        val ch1 = testChannel(providerId, "ch-1", "Movie 1", "cat-movies")
        val ch2 = testChannel(providerId, "ch-2", "Movie 2", "cat-movies")
        val ch3 = testChannel(providerId, "ch-3", "Movie 3", "cat-movies")
        val chSport = testChannel(providerId, "ch-sport", "Sport 1", "cat-sports")

        val categoriesMap = mapOf("provider-1" to listOf(catMovies, catSports))
        val channelsMap = mapOf(
            "provider-1" to mapOf(
                "cat-movies" to listOf(ch1, ch2, ch3),
                "cat-sports" to listOf(chSport),
            )
        )

        val settingsRepo = FakeSettingsRepository().apply {
            currentProviderId = providerId
        }

        val fakeRepo = FakeLiveTvRepo(categoriesMap, channelsMap)
        val playerManager = FakePlayerManager()

        val viewModel = LiveTvViewModel(
            liveTvRepository = fakeRepo,
            favoritesRepository = FakeFavoritesRepository(),
            historyRepository = FakeHistoryRepository(),
            settingsRepository = settingsRepo,
            epgRefreshCoordinator = FakeEpgCoordinator(),
            playerManager = playerManager,
            clock = object : WatchioClock { override fun nowEpochMs(): Long = 1000L },
        )

        testScheduler.runCurrent()

        viewModel.selectChannel(ch1)
        testScheduler.runCurrent()

        var saved = settingsRepo.browsingStates[providerId.value]
        assertEquals("ch-1", saved?.channelId)
        assertEquals("cat-movies", saved?.categoryId)

        viewModel.selectNextChannel()
        testScheduler.runCurrent()

        saved = settingsRepo.browsingStates[providerId.value]
        assertEquals("ch-2", saved?.channelId)
        assertEquals("Movie 2", saved?.channelName)
        assertEquals(1, saved?.channelIndex)

        viewModel.selectNextChannel()
        testScheduler.runCurrent()

        saved = settingsRepo.browsingStates[providerId.value]
        assertEquals("ch-3", saved?.channelId)
        assertEquals(2, saved?.channelIndex)

        viewModel.selectCategory(catSports)
        testScheduler.runCurrent()

        saved = settingsRepo.browsingStates[providerId.value]
        assertEquals("cat-sports", saved?.categoryId)
        assertEquals("ch-sport", saved?.channelId)
        viewModel.leaveLiveTv()
    }

    @Test
    fun providerStateIsIsolated() = runTest(testDispatcher) {
        val providerA = ProviderId("provider-A")
        val providerB = ProviderId("provider-B")

        val catA = LiveTvCategory("cat-a", "Cat A", LiveTvCategoryKind.Provider)
        val catB = LiveTvCategory("cat-b", "Cat B", LiveTvCategoryKind.Provider)

        val chA = testChannel(providerA, "ch-a", "Channel A", "cat-a")
        val chB = testChannel(providerB, "ch-b", "Channel B", "cat-b")

        val categoriesMap = mapOf(
            "provider-A" to listOf(catA),
            "provider-B" to listOf(catB),
        )
        val channelsMap = mapOf(
            "provider-A" to mapOf("cat-a" to listOf(chA)),
            "provider-B" to mapOf("cat-b" to listOf(chB)),
        )

        val settingsRepo = FakeSettingsRepository().apply {
            browsingStates["provider-A"] = LiveTvBrowsingState(
                categoryId = "cat-a",
                channelId = "ch-a",
            )
            browsingStates["provider-B"] = LiveTvBrowsingState(
                categoryId = "cat-b",
                channelId = "ch-b",
            )
        }

        val fakeRepo = FakeLiveTvRepo(categoriesMap, channelsMap).apply {
            selectedProvId = providerA
        }

        val viewModelA = LiveTvViewModel(
            liveTvRepository = fakeRepo,
            favoritesRepository = FakeFavoritesRepository(),
            historyRepository = FakeHistoryRepository(),
            settingsRepository = settingsRepo,
            epgRefreshCoordinator = FakeEpgCoordinator(),
            playerManager = FakePlayerManager(),
            clock = object : WatchioClock { override fun nowEpochMs(): Long = 1000L },
        )

        testScheduler.runCurrent()
        assertEquals("cat-a", viewModelA.uiState.value.selectedCategory?.id)
        assertEquals("ch-a", viewModelA.uiState.value.selectedChannel?.id)
        viewModelA.leaveLiveTv()

        fakeRepo.selectedProvId = providerB
        settingsRepo.currentProviderId = providerB

        val viewModelB = LiveTvViewModel(
            liveTvRepository = fakeRepo,
            favoritesRepository = FakeFavoritesRepository(),
            historyRepository = FakeHistoryRepository(),
            settingsRepository = settingsRepo,
            epgRefreshCoordinator = FakeEpgCoordinator(),
            playerManager = FakePlayerManager(),
            clock = object : WatchioClock { override fun nowEpochMs(): Long = 1000L },
        )

        testScheduler.runCurrent()
        assertEquals("cat-b", viewModelB.uiState.value.selectedCategory?.id)
        assertEquals("ch-b", viewModelB.uiState.value.selectedChannel?.id)
        viewModelB.leaveLiveTv()
    }
}