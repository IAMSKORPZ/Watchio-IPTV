package com.iamskorpz.watchioiptv.core.di

import android.content.Context
import android.annotation.SuppressLint
import androidx.datastore.preferences.preferencesDataStore
import android.net.Uri
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
import com.iamskorpz.watchioiptv.data.announcements.GitHubAnnouncementRemoteDataSource
import com.iamskorpz.watchioiptv.data.epg.EpgRepository
import com.iamskorpz.watchioiptv.data.epg.EpgRefreshCoordinator
import com.iamskorpz.watchioiptv.data.epg.EpgAutoRefreshScheduler
import com.iamskorpz.watchioiptv.data.live.LiveTvRepository
import com.iamskorpz.watchioiptv.data.library.MyListRepository
import com.iamskorpz.watchioiptv.data.library.SearchRepository

import com.iamskorpz.watchioiptv.data.m3u.M3uRepository
import com.iamskorpz.watchioiptv.data.movies.MoviesRepository
import com.iamskorpz.watchioiptv.data.series.SeriesRepository
import com.iamskorpz.watchioiptv.data.updates.UpdateRepository
import com.iamskorpz.watchioiptv.data.xtream.XtreamPlaybackUrlResolver
import com.iamskorpz.watchioiptv.data.xtream.XtreamRepository
import com.iamskorpz.watchioiptv.data.xtream.WatchioEndpointManager
import com.iamskorpz.watchioiptv.data.xtream.AndroidWatchioEndpointConfigSource
import com.iamskorpz.watchioiptv.data.xtream.HttpWatchioDnsResolver
import com.iamskorpz.watchioiptv.data.xtream.WATCHIO_DNS_RESOLVER_URL
import com.iamskorpz.watchioiptv.domain.playback.PlaybackUrlResolver
import com.iamskorpz.watchioiptv.domain.repository.CatalogRepository
import com.iamskorpz.watchioiptv.domain.repository.FavoritesRepository
import com.iamskorpz.watchioiptv.domain.repository.HistoryRepository
import com.iamskorpz.watchioiptv.domain.repository.ProviderRepository
import com.iamskorpz.watchioiptv.feature.tvguide.TvGuideRepository
import com.iamskorpz.watchioiptv.feature.sports.FootballDataApi
import com.iamskorpz.watchioiptv.feature.sports.RemoteFootballDataCredentialValidator
import com.iamskorpz.watchioiptv.feature.sports.SecureFootballDataCredentialStore
import com.iamskorpz.watchioiptv.feature.sports.FootballDataScheduleSource
import com.iamskorpz.watchioiptv.feature.sports.SportsRepository
import com.iamskorpz.watchioiptv.feature.sports.SportsReminderRepository
import com.iamskorpz.watchioiptv.feature.sports.WorkManagerSportsReminderScheduler
import com.iamskorpz.watchioiptv.feature.sports.UitestFootballScheduleSource
import com.iamskorpz.watchioiptv.feature.sports.v2.CachedFixtureRepository
import com.iamskorpz.watchioiptv.feature.sports.v2.ApiFootballApi
import com.iamskorpz.watchioiptv.feature.sports.v2.ApiFootballFixtureSource
import com.iamskorpz.watchioiptv.feature.sports.v2.BroadcastRepository
import com.iamskorpz.watchioiptv.feature.sports.v2.BroadcasterAliasCatalogue
import com.iamskorpz.watchioiptv.feature.sports.v2.FootballDataV2FixtureSource
import com.iamskorpz.watchioiptv.feature.sports.v2.RoomSportsFixtureCache
import com.iamskorpz.watchioiptv.feature.sports.v2.SecureApiFootballCredentialStore
import com.iamskorpz.watchioiptv.feature.sports.v2.SecureSoccersApiCredentialStore
import com.iamskorpz.watchioiptv.feature.sports.v2.SecureTheSportsDbCredentialStore
import com.iamskorpz.watchioiptv.feature.sports.v2.SoccersApiBroadcastApi
import com.iamskorpz.watchioiptv.feature.sports.v2.SoccersApiBroadcastSource
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsV2SourceSelector
import com.iamskorpz.watchioiptv.feature.sports.v2.ProviderChannelMatcherV2
import com.iamskorpz.watchioiptv.feature.sports.v2.TheSportsDbBroadcastApi
import com.iamskorpz.watchioiptv.feature.sports.v2.TheSportsDbBroadcastSource

private val Context.watchioDataStore by preferencesDataStore(name = "watchio_native_settings")

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
        .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
        .build()

    val settingsRepository = WatchioSettingsRepository(appContext.watchioDataStore)
    val secretStore: SecretStore = AndroidSecretStore(appContext)
    val providerCredentialStore = ProviderCredentialStore(secretStore)
    val networkModule = NetworkModule()
    val endpointManager = WatchioEndpointManager(AndroidWatchioEndpointConfigSource(appContext, networkModule.okHttpClient))
    val dnsResolver = HttpWatchioDnsResolver(networkModule.okHttpClient, WATCHIO_DNS_RESOLVER_URL)

    private val announcementRemote = AppVariantBindings.createAnnouncementRemoteDataSource(
        appContext,
        networkModule.okHttpClient,
    )
    val announcementRepository = AnnouncementRepository(
        remote = announcementRemote,
        local = DataStoreAnnouncementLocalStore(appContext.watchioDataStore),
    )
    @SuppressLint("UnsafeOptInUsageError")
    val playerManager: WatchioPlayerManager = Media3WatchioPlayerManager(appContext, settingsRepository)
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
        openLocalInputStream = { uri -> appContext.contentResolver.openInputStream(Uri.parse(uri)) },
    )
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
    val apiFootballCredentialStore = SecureApiFootballCredentialStore(secretStore)
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
    val sportsBroadcastRepository = BroadcastRepository(soccersApiBroadcastSource, theSportsDbBroadcastSource)
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
    )
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
        xtreamRepository.onMoviesUpdated = { moviesRepository.invalidateCache(it) }
        m3uRepository.onMoviesUpdated = { moviesRepository.invalidateCache(it) }
        xtreamRepository.onSeriesUpdated = { seriesRepository.invalidateCache(it) }
        m3uRepository.onSeriesUpdated = { seriesRepository.invalidateCache(it) }
    }

}
