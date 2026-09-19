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
import com.iamskorpz.watchioiptv.data.epg.EpgRepository
import com.iamskorpz.watchioiptv.data.epg.EpgRefreshCoordinator
import com.iamskorpz.watchioiptv.data.epg.EpgAutoRefreshScheduler
import com.iamskorpz.watchioiptv.data.live.LiveTvRepository
import com.iamskorpz.watchioiptv.data.library.MyListRepository
import com.iamskorpz.watchioiptv.data.library.SearchRepository

import com.iamskorpz.watchioiptv.data.m3u.M3uRepository
import com.iamskorpz.watchioiptv.data.movies.MoviesRepository
import com.iamskorpz.watchioiptv.data.series.SeriesRepository
import com.iamskorpz.watchioiptv.data.xtream.XtreamPlaybackUrlResolver
import com.iamskorpz.watchioiptv.data.xtream.XtreamRepository
import com.iamskorpz.watchioiptv.data.xtream.WatchioEndpointManager
import com.iamskorpz.watchioiptv.data.xtream.AndroidWatchioEndpointConfigSource
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
import com.iamskorpz.watchioiptv.feature.sports.UitestFootballScheduleSource

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
        .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
        .build()

    val settingsRepository = WatchioSettingsRepository(appContext.watchioDataStore)
    val secretStore: SecretStore = AndroidSecretStore(appContext)
    val providerCredentialStore = ProviderCredentialStore(secretStore)
    val networkModule = NetworkModule()
    val endpointManager = WatchioEndpointManager(AndroidWatchioEndpointConfigSource(appContext, networkModule.okHttpClient))

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
    val sportsRepository = SportsRepository(footballScheduleSource, tvGuideRepository)
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
