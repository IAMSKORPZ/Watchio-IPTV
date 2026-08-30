package com.watchioiptv.nativeapp.core.di

import android.content.Context
import android.annotation.SuppressLint
import androidx.datastore.preferences.preferencesDataStore
import android.net.Uri
import androidx.room.Room
import com.watchioiptv.nativeapp.BuildConfig
import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import com.watchioiptv.nativeapp.core.database.WatchioMigrations
import com.watchioiptv.nativeapp.core.datastore.WatchioSettingsRepository
import com.watchioiptv.nativeapp.core.network.NetworkModule
import com.watchioiptv.nativeapp.core.player.Media3WatchioPlayerManager
import com.watchioiptv.nativeapp.core.player.WatchioPlayerManager
import com.watchioiptv.nativeapp.core.security.AndroidSecretStore
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.core.security.SecretStore
import com.watchioiptv.nativeapp.core.util.SystemWatchioClock
import com.watchioiptv.nativeapp.data.RoomCatalogRepository
import com.watchioiptv.nativeapp.data.RoomFavoritesRepository
import com.watchioiptv.nativeapp.data.RoomHistoryRepository
import com.watchioiptv.nativeapp.data.RoomProviderRepository
import com.watchioiptv.nativeapp.data.epg.EpgRepository
import com.watchioiptv.nativeapp.data.epg.EpgRefreshCoordinator
import com.watchioiptv.nativeapp.data.epg.EpgAutoRefreshScheduler
import com.watchioiptv.nativeapp.data.live.LiveTvRepository
import com.watchioiptv.nativeapp.data.library.MyListRepository
import com.watchioiptv.nativeapp.data.library.SearchRepository
import com.watchioiptv.nativeapp.data.m3u.M3uRepository
import com.watchioiptv.nativeapp.data.movies.MoviesRepository
import com.watchioiptv.nativeapp.data.series.SeriesRepository
import com.watchioiptv.nativeapp.data.updates.UpdateRepository
import com.watchioiptv.nativeapp.data.xtream.XtreamPlaybackUrlResolver
import com.watchioiptv.nativeapp.data.xtream.XtreamRepository
import com.watchioiptv.nativeapp.domain.playback.PlaybackUrlResolver
import com.watchioiptv.nativeapp.domain.repository.CatalogRepository
import com.watchioiptv.nativeapp.domain.repository.FavoritesRepository
import com.watchioiptv.nativeapp.domain.repository.HistoryRepository
import com.watchioiptv.nativeapp.domain.repository.ProviderRepository
import com.watchioiptv.nativeapp.feature.tvguide.TvGuideRepository

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
    val updateRepository = UpdateRepository(
        context = appContext,
        okHttpClient = networkModule.okHttpClient,
        manifestUrl = if (BuildConfig.APPLICATION_ID.endsWith(".uitest")) UpdateRepository.UITEST_MANIFEST_URL else null,
    )

    init {
        xtreamRepository.onMoviesUpdated = { moviesRepository.invalidateCache(it) }
        m3uRepository.onMoviesUpdated = { moviesRepository.invalidateCache(it) }
        xtreamRepository.onSeriesUpdated = { seriesRepository.invalidateCache(it) }
        m3uRepository.onSeriesUpdated = { seriesRepository.invalidateCache(it) }
    }
}
