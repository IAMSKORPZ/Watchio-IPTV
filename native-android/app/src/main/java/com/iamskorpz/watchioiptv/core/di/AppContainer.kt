package com.iamskorpz.watchioiptv.core.di

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.iamskorpz.watchioiptv.BuildConfig
import com.iamskorpz.watchioiptv.core.database.WatchioDatabase
import com.iamskorpz.watchioiptv.core.database.WatchioMigrations
import com.iamskorpz.watchioiptv.core.datastore.WatchioSettingsRepository
import com.iamskorpz.watchioiptv.core.network.NetworkModule
import com.iamskorpz.watchioiptv.core.player.Media3WatchioPlayerManager
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerManager
import com.iamskorpz.watchioiptv.core.security.AndroidSecretStore
import com.iamskorpz.watchioiptv.core.security.ProviderCredentialStore
import com.iamskorpz.watchioiptv.core.security.SecretStore
import com.iamskorpz.watchioiptv.core.util.SystemWatchioClock
import com.iamskorpz.watchioiptv.data.RoomCatalogRepository
import com.iamskorpz.watchioiptv.data.RoomFavoritesRepository
import com.iamskorpz.watchioiptv.data.RoomHistoryRepository
import com.iamskorpz.watchioiptv.data.RoomProviderRepository
import com.iamskorpz.watchioiptv.data.announcements.AnnouncementRepository
import com.iamskorpz.watchioiptv.data.announcements.DataStoreAnnouncementLocalStore
import com.iamskorpz.watchioiptv.data.epg.EpgAutoRefreshScheduler
import com.iamskorpz.watchioiptv.data.epg.EpgRefreshCoordinator
import com.iamskorpz.watchioiptv.data.epg.EpgRepository
import com.iamskorpz.watchioiptv.data.downloads.OfflineMediaStore
import com.iamskorpz.watchioiptv.data.downloads.OfflineMovieDownloadRepository
import com.iamskorpz.watchioiptv.data.downloads.MovieDownloadAuthorizationResolver
import com.iamskorpz.watchioiptv.data.downloads.PlaylistMovieDownloadAuthorizationResolver
import com.iamskorpz.watchioiptv.data.downloads.UitestMovieDownloadAuthorizationResolver
import com.iamskorpz.watchioiptv.data.library.MyListRepository
import com.iamskorpz.watchioiptv.data.library.SearchRepository
import com.iamskorpz.watchioiptv.data.live.LiveTvRepository
import com.iamskorpz.watchioiptv.data.m3u.M3uRepository
import com.iamskorpz.watchioiptv.data.movies.MoviesRepository
import com.iamskorpz.watchioiptv.data.refresh.AndroidNetworkConnectivityChecker
import com.iamskorpz.watchioiptv.data.refresh.CatalogAutoRefreshScheduler
import com.iamskorpz.watchioiptv.data.refresh.CatalogRefreshCoordinator
import com.iamskorpz.watchioiptv.data.series.SeriesRepository
import com.iamskorpz.watchioiptv.data.xtream.WatchioEndpointManager
import com.iamskorpz.watchioiptv.data.xtream.PlaybackEndpointRecoveryCoordinator
import com.iamskorpz.watchioiptv.data.xtream.XtreamPlaybackUrlResolver
import com.iamskorpz.watchioiptv.data.xtream.XtreamRepository
import com.iamskorpz.watchioiptv.domain.playback.PlaybackUrlResolver
import com.iamskorpz.watchioiptv.domain.repository.CatalogRepository
import com.iamskorpz.watchioiptv.domain.repository.FavoritesRepository
import com.iamskorpz.watchioiptv.domain.repository.HistoryRepository
import com.iamskorpz.watchioiptv.domain.repository.ProviderRepository
import com.iamskorpz.watchioiptv.feature.sports.FootballDataApi
import com.iamskorpz.watchioiptv.feature.sports.FootballDataScheduleSource
import com.iamskorpz.watchioiptv.feature.sports.PersistentSportsMatchCache
import com.iamskorpz.watchioiptv.feature.sports.RemoteFootballDataCredentialValidator
import com.iamskorpz.watchioiptv.feature.sports.SecureFootballDataCredentialStore
import com.iamskorpz.watchioiptv.feature.sports.SportsReminderRepository
import com.iamskorpz.watchioiptv.feature.sports.SportsRepository
import com.iamskorpz.watchioiptv.feature.sports.UitestFootballScheduleSource
import com.iamskorpz.watchioiptv.feature.sports.WorkManagerSportsReminderScheduler
import com.iamskorpz.watchioiptv.feature.sports.v2.AndroidSportsSettingsStore
import com.iamskorpz.watchioiptv.feature.sports.v2.ApiFootballApi
import com.iamskorpz.watchioiptv.feature.sports.v2.ApiFootballFixtureSource
import com.iamskorpz.watchioiptv.feature.sports.v2.BroadcastRepository
import com.iamskorpz.watchioiptv.feature.sports.v2.BroadcasterAliasCatalogue
import com.iamskorpz.watchioiptv.feature.sports.v2.CachedFixtureRepository
import com.iamskorpz.watchioiptv.feature.sports.v2.FootballDataV2FixtureSource
import com.iamskorpz.watchioiptv.feature.sports.v2.PersistentSportsBroadcastCache
import com.iamskorpz.watchioiptv.feature.sports.v2.ProviderChannelMatcherV2
import com.iamskorpz.watchioiptv.feature.sports.v2.RoomSportsFixtureCache
import com.iamskorpz.watchioiptv.feature.sports.v2.SecureApiFootballCredentialStore
import com.iamskorpz.watchioiptv.feature.sports.v2.SecureSoccersApiCredentialStore
import com.iamskorpz.watchioiptv.feature.sports.v2.SecureTheSportsDbCredentialStore
import com.iamskorpz.watchioiptv.feature.sports.v2.SoccersApiBroadcastApi
import com.iamskorpz.watchioiptv.feature.sports.v2.SoccersApiBroadcastSource
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsCapability
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsDataSource
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsSourceError
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsSourceResult
import com.iamskorpz.watchioiptv.feature.sports.v2.TheSportsDbBroadcastApi
import com.iamskorpz.watchioiptv.feature.sports.v2.TheSportsDbBroadcastSource
import com.iamskorpz.watchioiptv.feature.sports.v2.enabledSources
import com.iamskorpz.watchioiptv.feature.tvguide.TvGuideRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private val Context.watchioDataStore by preferencesDataStore(name = "watchio_native_settings")

@SuppressLint("UnsafeOptInUsageError")
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: WatchioDatabase = Room.databaseBuilder(
        appContext,
        WatchioDatabase::class.java,
        "watchio_native.db",
    )
        .addMigrations(WatchioMigrations.MIGRATION_3_4)
        .addMigrations(WatchioMigrations.MIGRATION_4_5)
        .addMigrations(WatchioMigrations.MIGRATION_5_6)
        .addMigrations(WatchioMigrations.MIGRATION_6_7)
        .addMigrations(WatchioMigrations.MIGRATION_7_8)
        .addMigrations(WatchioMigrations.MIGRATION_8_9)
        .addMigrations(WatchioMigrations.MIGRATION_9_10)
        .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
        .build()

    val settingsRepository = WatchioSettingsRepository(appContext.watchioDataStore)
    val secretStore: SecretStore = AndroidSecretStore(appContext)
    val providerCredentialStore = ProviderCredentialStore(secretStore)
    val networkModule = NetworkModule()
    val endpointManager = WatchioEndpointManager(
        AppVariantBindings.createEndpointConfigSource(appContext, networkModule.okHttpClient),
    )
    private val mutablePlaybackAccountBlocked = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val playbackAccountBlocked = mutablePlaybackAccountBlocked.asSharedFlow()

    private val announcementRemote = AppVariantBindings.createAnnouncementRemoteDataSource(
        appContext,
        networkModule.okHttpClient,
    )
    val announcementRepository = AnnouncementRepository(
        remote = announcementRemote,
        local = DataStoreAnnouncementLocalStore(appContext.watchioDataStore),
    )
    val playerManager: WatchioPlayerManager by lazy {
        Media3WatchioPlayerManager(
            appContext,
            settingsRepository,
            offlineMovieDownloadRepository.mediaStore.playbackDataSourceFactory,
            onRemotePlaybackAvailable = endpointManager::reportStreamingAvailable,
            onRemotePlaybackFailure = endpointManager::reportStreamingFailure,
            endpointRecovery = playbackEndpointRecovery,
            failoverDiagnostics = AppVariantBindings.createPlaybackFailoverDiagnostics(),
        )
    }
    val providerRepository: ProviderRepository = RoomProviderRepository(
        providerDao = database.providerDao(),
        credentialStore = providerCredentialStore,
    )
    val catalogRepository: CatalogRepository = RoomCatalogRepository(
        categoryDao = database.categoryDao(),
        liveStreamDao = database.liveStreamDao(),
        vodDao = database.vodDao(),
        seriesDao = database.seriesDao(),
        clock = SystemWatchioClock,
    )
    val favoritesRepository: FavoritesRepository = RoomFavoritesRepository(database.favoriteDao())
    val historyRepository: HistoryRepository = RoomHistoryRepository(database.watchHistoryDao())
    val xtreamRepository = XtreamRepository(
        database = database,
        credentialStore = providerCredentialStore,
        settingsRepository = settingsRepository,
        retrofitFactory = networkModule::retrofit,
        clock = SystemWatchioClock,
        endpointManager = endpointManager,
    )
    val m3uRepository = M3uRepository(
        database = database,
        okHttpClient = networkModule.okHttpClient,
        settingsRepository = settingsRepository,
        clock = SystemWatchioClock,
        openLocalInputStream = { uri -> appContext.contentResolver.openInputStream(uri.toUri()) },
    )
    val catalogRefreshCoordinator = CatalogRefreshCoordinator(
        database = database,
        xtreamRepository = xtreamRepository,
        m3uRepository = m3uRepository,
        clock = SystemWatchioClock,
    )
    val catalogAutoRefreshScheduler by lazy {
        CatalogAutoRefreshScheduler(
            database = database,
            settingsRepository = settingsRepository,
            coordinator = catalogRefreshCoordinator,
            clock = SystemWatchioClock,
            connectivityChecker = AndroidNetworkConnectivityChecker(appContext),
            playerStateFlow = playerManager.state,
        )
    }
    val epgRepository = EpgRepository(
        database = database,
        okHttpClient = networkModule.okHttpClient,
        credentialStore = providerCredentialStore,
        clock = SystemWatchioClock,
    )
    val epgRefreshCoordinator = EpgRefreshCoordinator(
        database = database,
        epgRepository = epgRepository,
    )
    val epgAutoRefreshScheduler = EpgAutoRefreshScheduler(appContext)
    val playbackUrlResolver: PlaybackUrlResolver = XtreamPlaybackUrlResolver(
        providerRepository = providerRepository,
        credentialStore = providerCredentialStore,
        settingsRepository = settingsRepository,
    )
    val playbackEndpointRecovery by lazy {
        PlaybackEndpointRecoveryCoordinator(
            providerUrl = { providerRepository.getProvider(it)?.serverUrl },
            validateAccount = xtreamRepository::validateAccount,
            resolveUrl = playbackUrlResolver::resolve,
            connectivityChecker = AndroidNetworkConnectivityChecker(appContext),
            onAccountBlocked = { mutablePlaybackAccountBlocked.tryEmit(it); Unit },
        )
    }
    val liveTvRepository = LiveTvRepository(
        database = database,
        settingsRepository = settingsRepository,
        favoritesRepository = favoritesRepository,
        historyRepository = historyRepository,
        playbackUrlResolver = playbackUrlResolver,
    )
    val tvGuideRepository = TvGuideRepository(
        database = database,
        liveTvRepository = liveTvRepository,
        epgRepository = epgRepository,
        epgRefreshCoordinator = epgRefreshCoordinator,
    )
    private val footballDataApi = networkModule.retrofit("https://api.football-data.org/").create(FootballDataApi::class.java)
    val footballDataCredentialStore = SecureFootballDataCredentialStore(secretStore)
    val footballDataCredentialValidator = RemoteFootballDataCredentialValidator(footballDataApi)
    private val footballScheduleSource = if (BuildConfig.APPLICATION_ID.endsWith(".uitest")) {
        UitestFootballScheduleSource()
    } else {
        FootballDataScheduleSource(
            footballDataApi,
            footballDataCredentialStore,
        )
    }
    fun invalidateSportsCache() {
        (footballScheduleSource as? FootballDataScheduleSource)?.invalidateCache()
    }
    fun invalidateSportsBroadcastCache() {
        soccersApiBroadcastSource.invalidateCache()
        sportsBroadcastRepository.invalidateCache()
        sportsRepository.invalidateBroadcastDiscoveryCache()
    }
    val apiFootballCredentialStore = SecureApiFootballCredentialStore(secretStore)
    val sportsSettingsStore = AndroidSportsSettingsStore(appContext)
    private val apiFootballApi = networkModule.retrofit("https://v3.football.api-sports.io/").create(ApiFootballApi::class.java)
    private val footballDataV2Source = FootballDataV2FixtureSource(footballDataApi, footballDataCredentialStore)
    private val apiFootballV2Source = ApiFootballFixtureSource(apiFootballApi, apiFootballCredentialStore)
    val sportsV2Repository = CachedFixtureRepository(
        source = footballDataV2Source,
        cache = RoomSportsFixtureCache(database.sportsCacheDao()),
    )
    private val apiFootballSportsRepository = CachedFixtureRepository(
        source = apiFootballV2Source,
        cache = RoomSportsFixtureCache(database.sportsCacheDao()),
    )
    val soccersApiCredentialStore = SecureSoccersApiCredentialStore(secretStore)
    val theSportsDbCredentialStore = SecureTheSportsDbCredentialStore(secretStore)
    private val soccersApiBroadcastSource = SoccersApiBroadcastSource(
        networkModule.retrofit("https://api.soccersapi.com/").create(SoccersApiBroadcastApi::class.java),
        soccersApiCredentialStore,
    )
    private val theSportsDbBroadcastSource = TheSportsDbBroadcastSource(
        networkModule.retrofit("https://www.thesportsdb.com/").create(TheSportsDbBroadcastApi::class.java),
        theSportsDbCredentialStore,
    )
    val sportsBroadcastRepository = BroadcastRepository(
        primary = soccersApiBroadcastSource,
        secondary = theSportsDbBroadcastSource,
        cache = PersistentSportsBroadcastCache(appContext),
        sourceOrder = { sportsSettingsStore.read().enabledSources(SportsCapability.BROADCASTS) },
        refreshInterval = { java.time.Duration.ofMinutes(sportsSettingsStore.read().refreshMinutes.toLong()) },
    )
    private val sportsBroadcasterAliases = BroadcasterAliasCatalogue.parse(
        appContext.assets.open("sports_broadcaster_aliases.json").bufferedReader().use { it.readText() },
    )
    val sportsRepository = SportsRepository(
        scheduleSource = footballScheduleSource,
        tvGuideRepository = tvGuideRepository,
        footballV2Repository = sportsV2Repository,
        apiFootballV2Repository = apiFootballSportsRepository,
        apiFootballCredentialStore = apiFootballCredentialStore,
        broadcastRepository = sportsBroadcastRepository,
        matcherV2 = ProviderChannelMatcherV2(sportsBroadcasterAliases),
        settingsStore = sportsSettingsStore,
        matchCache = PersistentSportsMatchCache(appContext),
    )

    suspend fun testSportsSource(source: SportsDataSource): SportsSourceResult<*> = when (source) {
        SportsDataSource.FootballData -> footballDataV2Source.fixtures(java.time.LocalDate.now(), java.time.LocalDate.now())
        SportsDataSource.ApiFootball -> apiFootballV2Source.fixtures(java.time.LocalDate.now(), java.time.LocalDate.now())
        SportsDataSource.SoccersApi -> soccersApiBroadcastSource.testConnection()
        SportsDataSource.TheSportsDb -> theSportsDbBroadcastSource.testConnection()
        else -> SportsSourceResult.Failure(SportsSourceError.Unsupported)
    }
    val sportsReminderRepository = SportsReminderRepository(
        appContext.watchioDataStore,
        WorkManagerSportsReminderScheduler(appContext),
    )
    val moviesRepository = MoviesRepository(
        database = database,
        settingsRepository = settingsRepository,
        favoritesRepository = favoritesRepository,
        historyRepository = historyRepository,
        playbackUrlResolver = playbackUrlResolver,
        credentialStore = providerCredentialStore,
        retrofitFactory = networkModule::retrofit,
        tmdbRetrofitFactory = networkModule::retrofit,
        clock = SystemWatchioClock,
    )
    val seriesRepository = SeriesRepository(
        database = database,
        settingsRepository = settingsRepository,
        favoritesRepository = favoritesRepository,
        historyRepository = historyRepository,
        playbackUrlResolver = playbackUrlResolver,
        credentialStore = providerCredentialStore,
        retrofitFactory = networkModule::retrofit,
        tmdbRetrofitFactory = networkModule::retrofit,
        clock = SystemWatchioClock,
    )
    val offlineMovieDownloadRepository: OfflineMovieDownloadRepository by lazy {
        OfflineMovieDownloadRepository(
            context = appContext,
            dao = database.offlineDownloadDao(),
            mediaStore = OfflineMediaStore(
                appContext,
                database.offlineDownloadDao(),
                AppVariantBindings.createOfflineDownloadSourceResolver(moviesRepository, seriesRepository),
            ),
        )
    }
    val movieDownloadAuthorizationResolver: MovieDownloadAuthorizationResolver =
        if (com.iamskorpz.watchioiptv.BuildConfig.APPLICATION_ID.endsWith(".uitest")) {
            UitestMovieDownloadAuthorizationResolver
        } else {
            PlaylistMovieDownloadAuthorizationResolver
        }
    val searchRepository = SearchRepository(
        database = database,
        settingsRepository = settingsRepository,
        historyStore = com.iamskorpz.watchioiptv.data.library.SharedPreferencesSearchHistoryStore(
            appContext.getSharedPreferences("watchio_search_history", android.content.Context.MODE_PRIVATE),
        ),
    )
    val myListRepository = MyListRepository(
        database = database,
        settingsRepository = settingsRepository,
        favoritesRepository = favoritesRepository,
        historyRepository = historyRepository,
    )
    val updateRepository = AppVariantBindings.createUpdateRepository(appContext, networkModule.okHttpClient)

    init {
        xtreamRepository.onLiveUpdated = {
            searchRepository.invalidate(it)
            sportsRepository.invalidateProviderChannelCache(it)
        }
        m3uRepository.onLiveUpdated = {
            searchRepository.invalidate(it)
            sportsRepository.invalidateProviderChannelCache(it)
        }
        xtreamRepository.onMoviesUpdated = {
            moviesRepository.invalidateCache(it)
            searchRepository.invalidate(it)
        }
        m3uRepository.onMoviesUpdated = {
            moviesRepository.invalidateCache(it)
            searchRepository.invalidate(it)
        }
        xtreamRepository.onSeriesUpdated = {
            seriesRepository.invalidateCache(it)
            searchRepository.invalidate(it)
        }
        m3uRepository.onSeriesUpdated = {
            seriesRepository.invalidateCache(it)
            searchRepository.invalidate(it)
        }
    }
}
