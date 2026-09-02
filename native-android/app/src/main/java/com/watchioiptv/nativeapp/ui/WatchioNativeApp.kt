package com.watchioiptv.nativeapp.ui

import android.content.Intent
import android.app.Activity
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.watchioiptv.nativeapp.core.di.AppContainer
import com.watchioiptv.nativeapp.data.epg.EpgRefreshInterval
import com.watchioiptv.nativeapp.data.m3u.M3uImportState
import com.watchioiptv.nativeapp.data.xtream.XtreamImportState
import com.watchioiptv.nativeapp.BuildConfig
import com.watchioiptv.nativeapp.feature.home.HomeViewModel
import com.watchioiptv.nativeapp.feature.announcements.AnnouncementsScreen
import com.watchioiptv.nativeapp.feature.announcements.AnnouncementsViewModel
import com.watchioiptv.nativeapp.feature.bootstrap.BootstrapDestination
import com.watchioiptv.nativeapp.feature.bootstrap.BootstrapViewModel
import com.watchioiptv.nativeapp.feature.live.FullscreenPlayerScreen
import com.watchioiptv.nativeapp.feature.live.LiveTvScreen
import com.watchioiptv.nativeapp.feature.live.LiveTvViewModel
import com.watchioiptv.nativeapp.feature.library.GlobalSearchScreen
import com.watchioiptv.nativeapp.feature.library.GlobalSearchViewModel
import com.watchioiptv.nativeapp.feature.library.MyListScreen
import com.watchioiptv.nativeapp.feature.library.MyListViewModel
import com.watchioiptv.nativeapp.feature.library.contentRoute
import com.watchioiptv.nativeapp.feature.movies.MovieDetailsScreen
import com.watchioiptv.nativeapp.feature.movies.MoviePlayerScreen
import com.watchioiptv.nativeapp.feature.movies.MoviesScreen
import com.watchioiptv.nativeapp.feature.movies.MoviesViewModel
import com.watchioiptv.nativeapp.feature.movies.openYoutubeTrailer
import com.watchioiptv.nativeapp.feature.player.PlayerContentContext
import com.watchioiptv.nativeapp.feature.player.WatchioFullscreenPlayerScreen
import com.watchioiptv.nativeapp.feature.series.SeriesDetailsScreen
import com.watchioiptv.nativeapp.feature.series.SeriesScreen
import com.watchioiptv.nativeapp.feature.series.SeriesViewModel
import com.watchioiptv.nativeapp.feature.provider.M3uProviderViewModel
import com.watchioiptv.nativeapp.feature.provider.ProviderManagementUiState
import com.watchioiptv.nativeapp.feature.provider.ProviderManagementViewModel
import com.watchioiptv.nativeapp.feature.provider.ProviderRowUiState
import com.watchioiptv.nativeapp.feature.provider.XtreamProviderViewModel
import com.watchioiptv.nativeapp.feature.provider.QuickLoginUiState
import com.watchioiptv.nativeapp.feature.provider.QuickLoginViewModel
import com.watchioiptv.nativeapp.feature.settings.AccountInformationUiState
import com.watchioiptv.nativeapp.feature.settings.AccountInformationViewModel
import com.watchioiptv.nativeapp.feature.settings.SettingsUiState
import com.watchioiptv.nativeapp.feature.settings.SettingsViewModel
import com.watchioiptv.nativeapp.feature.settings.UpdatesScreen
import com.watchioiptv.nativeapp.feature.settings.UpdatesViewModel
import com.watchioiptv.nativeapp.feature.tvguide.TvGuideScreen
import com.watchioiptv.nativeapp.feature.tvguide.TvGuideViewModel
import com.watchioiptv.nativeapp.core.util.SystemWatchioClock
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.AnnouncementAction
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.domain.model.StreamFormat
import com.watchioiptv.nativeapp.domain.repository.ControlAutoHideDelay
import com.watchioiptv.nativeapp.domain.repository.VideoScalingMode
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.watchioiptv.nativeapp.ui.components.WatchioButton
import com.watchioiptv.nativeapp.ui.components.WatchioButtonVariant
import com.watchioiptv.nativeapp.ui.components.WatchioCard
import com.watchioiptv.nativeapp.ui.components.WatchioFocusableCard
import com.watchioiptv.nativeapp.ui.components.WatchioPageHeader
import com.watchioiptv.nativeapp.ui.components.WatchioProgressBar
import com.watchioiptv.nativeapp.ui.components.WatchioScreenHeader
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioColors
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioComponentSizes
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioIconSizes
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioRadii
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioSpacing
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioTypography
import com.watchioiptv.nativeapp.ui.theme.WatchioTheme
import com.watchioiptv.nativeapp.ui.theme.WatchioThemeState
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun WatchioNativeApp(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    val themeState by container.settingsRepository.theme.collectAsStateWithLifecycle(initialValue = com.watchioiptv.nativeapp.ui.theme.WatchioThemeState())
    val inputMode by container.settingsRepository.inputMode.collectAsStateWithLifecycle(initialValue = InputMode.Auto)
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val context = LocalContext.current
    val announcementsViewModel: AnnouncementsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AnnouncementsViewModel(container.announcementRepository) as T
        },
    )
    val announcementsState by announcementsViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { announcementsViewModel.refresh() }
    var backgroundPlaybackHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
    AppBackgroundPlaybackEffect(
        onBackground = { (backgroundPlaybackHandler ?: container.playerManager::pause).invoke() },
    )
    TvRootExitBackHandler(
        enabled = shouldRequireTvDoubleBackExit(
            inputMode = inputMode,
            route = currentBackStackEntry?.destination?.route,
            hasPreviousBackStackEntry = navController.previousBackStackEntry != null,
        ),
        onExit = { (context as? Activity)?.finish() },
    )
    WatchioTheme(themeState = themeState) {
        NavHost(
            navController = navController,
            startDestination = "bootstrap",
            modifier = Modifier.testTag("watchio-nav-host"),
        ) {
            composable("bootstrap") {
                val bootstrapViewModel: BootstrapViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return BootstrapViewModel(container.providerRepository, container.settingsRepository) as T
                        }
                    },
                )
                val destination by bootstrapViewModel.destination.collectAsStateWithLifecycle()
                LaunchedEffect(destination) {
                    when (destination) {
                        BootstrapDestination.Ready -> navController.navigate("home") {
                            popUpTo("bootstrap") { inclusive = true }
                        }
                        BootstrapDestination.NeedsXtreamLogin -> navController.navigate("providers/xtream/add") {
                            popUpTo("bootstrap") { inclusive = true }
                        }
                        else -> Unit
                    }
                }
                when (destination) {
                    BootstrapDestination.Loading -> BootstrapLoadingScreen()
                    BootstrapDestination.NeedsDeviceMode -> DeviceModeScreen(
                        onMobile = bootstrapViewModel::chooseMobile,
                        onTv = bootstrapViewModel::chooseTv,
                    )
                    BootstrapDestination.NeedsXtreamLogin,
                    BootstrapDestination.Ready -> BootstrapLoadingScreen()
                }
            }
            composable("onboarding/providers") {
                ProviderTypeSetupScreen(
                    onAddXtreamProvider = { navController.navigate("providers/xtream/add") },
                    onAddM3uUrlProvider = { navController.navigate("providers/m3u/url/add") },
                    onAddM3uFileProvider = { navController.navigate("providers/m3u/file/add") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("home") {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return HomeViewModel(
                                container.providerRepository,
                                container.settingsRepository,
                                container.xtreamRepository,
                                container.m3uRepository,
                            ) as T
                        }
                    },
                )
                val state by homeViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.providersLoaded, state.providerType) {
                    if (state.providersLoaded && state.providerType != ProviderType.Xtream) {
                        navController.navigate("providers/xtream/add") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                }
                if (!state.providersLoaded || state.providerType != ProviderType.Xtream) {
                    BootstrapLoadingScreen()
                } else HomeScreen(
                    providerSummary = state.providerSummary,
                    providerCount = state.providerCount,
                    liveCount = state.liveCount,
                    movieCount = state.movieCount,
                    seriesCount = state.seriesCount,
                    liveRefreshAtEpochMs = state.liveRefreshAtEpochMs,
                    moviesRefreshAtEpochMs = state.moviesRefreshAtEpochMs,
                    seriesRefreshAtEpochMs = state.seriesRefreshAtEpochMs,
                    providerExpiryEpochMs = state.providerExpiryEpochMs,
                    liveRefreshing = state.liveRefreshing,
                    moviesRefreshing = state.moviesRefreshing,
                    seriesRefreshing = state.seriesRefreshing,
                    refreshMessage = state.refreshMessage,
                    onAddXtreamProvider = { navController.navigate("providers/xtream/add") },
                    onAddM3uUrlProvider = { navController.navigate("providers/m3u/url/add") },
                    onAddM3uFileProvider = { navController.navigate("providers/m3u/file/add") },
                    onProviders = { navController.navigate("providers/home") },
                    onSettings = { navController.navigate("settings") },
                    onLiveTv = { navController.navigate("live") },
                    onTvGuide = { navController.navigate("tv-guide") },
                    onMovies = { navController.navigate("movies") },
                    onSeries = { navController.navigate("series") },
                    onSearch = { navController.navigate("search") },
                    onSports = { navController.navigate("sports-placeholder") },
                    onAnnouncements = { navController.navigate("announcements") },
                    announcementUnreadCount = announcementsState.snapshot.unreadCount,
                    onRefreshLive = homeViewModel::refreshLive,
                    onRefreshMovies = homeViewModel::refreshMovies,
                    onRefreshSeries = homeViewModel::refreshSeries,
                )
            }
            composable("sports-placeholder") {
                HomePlaceholderScreen("Sports", "Sports will be added in a later Watchio phase.", onBack = { navController.popBackStack() })
            }
            composable("announcements") {
                LaunchedEffect(Unit) { announcementsViewModel.refresh() }
                AnnouncementsScreen(
                    state = announcementsState,
                    onBack = { navController.popBackStack() },
                    onRefresh = announcementsViewModel::refresh,
                    onOpen = announcementsViewModel::open,
                    onCloseDetails = announcementsViewModel::closeDetails,
                    onDismiss = announcementsViewModel::dismiss,
                    onToggleArchived = announcementsViewModel::toggleArchived,
                    onAction = { action ->
                        when (action) {
                            is AnnouncementAction.OpenUpdater -> navController.navigate("settings/updates")
                            is AnnouncementAction.OpenScreen -> navController.navigate(action.screen.route) { launchSingleTop = true }
                            is AnnouncementAction.OpenUrl -> runCatching {
                                val uri = Uri.parse(action.url)
                                require(uri.scheme == "https" || uri.scheme == "http")
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        }
                    },
                )
            }
            composable("providers") {
                val providersViewModel: ProviderManagementViewModel = viewModel(factory = providersFactory(container))
                val state by providersViewModel.state.collectAsStateWithLifecycle()
                ProviderManagementScreen(
                    state = state,
                    onSelect = providersViewModel::select,
                    onRefresh = providersViewModel::refresh,
                    onDelete = providersViewModel::delete,
                    onAddXtreamProvider = { navController.navigate("providers/xtream/add") },
                    onAddM3uUrlProvider = { navController.navigate("providers/m3u/url/add") },
                    onAddM3uFileProvider = { navController.navigate("providers/m3u/file/add") },
                    onBack = {
                        if (state.providers.isNotEmpty()) {
                            navController.navigate("home") {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                                launchSingleTop = true
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                )
            }
            composable("providers/home") {
                val providersViewModel: ProviderManagementViewModel = viewModel(factory = providersFactory(container))
                val state by providersViewModel.state.collectAsStateWithLifecycle()
                ProviderManagementScreen(
                    state = state,
                    onSelect = providersViewModel::select,
                    onRefresh = providersViewModel::refresh,
                    onDelete = providersViewModel::delete,
                    onAddXtreamProvider = { navController.navigate("providers/xtream/add") },
                    onAddM3uUrlProvider = { navController.navigate("providers/m3u/url/add") },
                    onAddM3uFileProvider = { navController.navigate("providers/m3u/file/add") },
                    onBack = {
                        navController.navigate("home") {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable("providers/settings") {
                val providersViewModel: ProviderManagementViewModel = viewModel(factory = providersFactory(container))
                val state by providersViewModel.state.collectAsStateWithLifecycle()
                ProviderManagementScreen(
                    state = state,
                    onSelect = providersViewModel::select,
                    onRefresh = providersViewModel::refresh,
                    onDelete = providersViewModel::delete,
                    onAddXtreamProvider = { navController.navigate("providers/xtream/add") },
                    onAddM3uUrlProvider = { navController.navigate("providers/m3u/url/add") },
                    onAddM3uFileProvider = { navController.navigate("providers/m3u/file/add") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("search") {
                val searchViewModel: GlobalSearchViewModel = viewModel(factory = searchFactory(container))
                val state by searchViewModel.state.collectAsStateWithLifecycle()
                GlobalSearchScreen(
                    state = state,
                    onQuery = searchViewModel::setQuery,
                    onScope = searchViewModel::setScope,
                    onResult = { result -> navController.navigate(contentRoute(result.contentType, result.contentId)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("my-list") {
                val myListViewModel: MyListViewModel = viewModel(factory = myListFactory(container))
                val state by myListViewModel.state.collectAsStateWithLifecycle()
                MyListScreen(
                    state = state,
                    onContinue = { item -> navController.navigate(contentRoute(item.contentType, item.contentId)) },
                    onFavorite = { item -> navController.navigate(contentRoute(item.contentType, item.contentId)) },
                    onRemoveFavorite = myListViewModel::removeFavorite,
                    onHistory = { item -> navController.navigate(contentRoute(item.contentType, item.contentId)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("series") {
                val seriesViewModel: SeriesViewModel = viewModel(factory = seriesFactory(container))
                val state by seriesViewModel.seriesState.collectAsStateWithLifecycle()
                SeriesScreen(
                    state = state,
                    onCategory = seriesViewModel::selectCategory,
                    onSearch = seriesViewModel::updateSearch,
                    onLoadMore = seriesViewModel::loadMore,
                    onSeries = { item ->
                        val targetEp = item.targetEpisodeId?.let { java.net.URLEncoder.encode(it, "UTF-8") }
                        if (targetEp != null) {
                            navController.navigate("series/${item.series.id}?episodeId=$targetEp")
                        } else {
                            navController.navigate("series/${item.series.id}")
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "series/{seriesId}?episodeId={episodeId}",
                arguments = listOf(
                    navArgument("seriesId") { type = NavType.StringType },
                    navArgument("episodeId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                val seriesViewModel: SeriesViewModel = viewModel(factory = seriesFactory(container))
                val seriesId = backStackEntry.arguments?.getString("seriesId").orEmpty()
                val rawEpisodeId = backStackEntry.arguments?.getString("episodeId")
                val episodeId = rawEpisodeId?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                LaunchedEffect(seriesId, episodeId) { seriesViewModel.loadDetails(seriesId, targetEpisodeId = episodeId) }
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, seriesId) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            seriesViewModel.refreshDetails()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                val state by seriesViewModel.detailsState.collectAsStateWithLifecycle()
                val context = LocalContext.current
                SeriesDetailsScreen(
                    state = state,
                    onPlay = { resume ->
                        seriesViewModel.playTopLevel(resume)
                        navController.navigate("series/player/$seriesId")
                    },
                    onTrailer = { key -> openYoutubeTrailer(context, key) },
                    onFavorite = seriesViewModel::toggleFavorite,
                    onSeason = seriesViewModel::selectSeason,
                    onTab = seriesViewModel::selectTab,
                    onEpisode = { episode ->
                        seriesViewModel.playEpisode(episode, true)
                        navController.navigate("series/player/$seriesId")
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("series/player/{seriesId}") { backStackEntry ->
                val seriesBackStackEntry = remember(backStackEntry) {
                    runCatching { navController.previousBackStackEntry }.getOrNull()
                }
                val seriesViewModel: SeriesViewModel = if (seriesBackStackEntry != null) {
                    viewModel(viewModelStoreOwner = seriesBackStackEntry, factory = seriesFactory(container))
                } else {
                    viewModel(factory = seriesFactory(container))
                }
                PlaybackBackgroundHandler(
                    onBackground = seriesViewModel::pauseForBackground,
                    setHandler = { backgroundPlaybackHandler = it },
                )
                val playerState by container.playerManager.state.collectAsStateWithLifecycle()
                val playerSettings by container.settingsRepository.playerSettings.collectAsStateWithLifecycle(initialValue = com.watchioiptv.nativeapp.domain.repository.PlayerSettings())
                val detailsState by seriesViewModel.detailsState.collectAsStateWithLifecycle()
                val nextEpisodeState by seriesViewModel.nextEpisodeState.collectAsStateWithLifecycle()
                val episode = seriesViewModel.currentEpisode
                val contentContext = PlayerContentContext.Episode(
                    seriesTitle = detailsState.details?.title ?: "TV Show",
                    seasonNumber = episode?.seasonNumber ?: (detailsState.selectedSeasonNumber ?: 1),
                    episodeNumber = episode?.episodeNumber ?: 1,
                    episodeTitle = episode?.title ?: "Episode",
                    duration = episode?.duration,
                    posterUrl = episode?.imageUrl ?: detailsState.details?.posterUrl,
                    hasPreviousEpisode = seriesViewModel.hasPreviousEpisode(),
                    hasNextEpisode = seriesViewModel.hasNextEpisode(),
                    onPreviousEpisode = seriesViewModel::playPreviousEpisode,
                    onNextEpisode = seriesViewModel::playNextEpisode,
                    nextEpisodeState = nextEpisodeState,
                    onPlayNext = seriesViewModel::playNextEpisode,
                    onCancelNext = seriesViewModel::dismissNextEpisodeForCurrentEpisode,
                )
                WatchioFullscreenPlayerScreen(
                    playerState = playerState,
                    playerSettings = playerSettings,
                    playerManager = container.playerManager,
                    contentContext = contentContext,
                    onPlayPause = seriesViewModel::playPause,
                    onSeek = seriesViewModel::seekBy,
                    onRestart = seriesViewModel::restartPlayback,
                    onRetry = container.playerManager::retry,
                    onClose = {
                        seriesViewModel.stopPlayback()
                        navController.popBackStack()
                    },
                )
            }
            composable("movies") {
                val moviesViewModel: MoviesViewModel = viewModel(factory = moviesFactory(container))
                val state by moviesViewModel.moviesState.collectAsStateWithLifecycle()
                MoviesScreen(
                    state = state,
                    onCategory = moviesViewModel::selectCategory,
                    onCategorySearch = moviesViewModel::updateCategorySearch,
                    onSearch = moviesViewModel::updateSearch,
                    onLoadMore = moviesViewModel::loadMore,
                    onMovie = { movie -> navController.navigate("movies/${movie.id}") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("movies/{movieId}") { backStackEntry ->
                val moviesViewModel: MoviesViewModel = viewModel(factory = moviesFactory(container))
                val movieId = backStackEntry.arguments?.getString("movieId").orEmpty()
                LaunchedEffect(movieId) { moviesViewModel.loadDetails(movieId) }
                val state by moviesViewModel.detailsState.collectAsStateWithLifecycle()
                val context = LocalContext.current
                MovieDetailsScreen(
                    state = state,
                    onPlay = { resume ->
                        moviesViewModel.play(resume)
                        navController.navigate("movies/player/$movieId")
                    },
                    onTrailer = { key -> openYoutubeTrailer(context, key) },
                    onFavorite = moviesViewModel::toggleFavorite,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("movies/player/{movieId}") { backStackEntry ->
                val moviesBackStackEntry = remember(backStackEntry) {
                    runCatching { navController.previousBackStackEntry }.getOrNull()
                }
                val moviesViewModel: MoviesViewModel = if (moviesBackStackEntry != null) {
                    viewModel(viewModelStoreOwner = moviesBackStackEntry, factory = moviesFactory(container))
                } else {
                    viewModel(factory = moviesFactory(container))
                }
                val movieId = backStackEntry.arguments?.getString("movieId").orEmpty()
                LaunchedEffect(movieId) { moviesViewModel.loadDetails(movieId) }
                PlaybackBackgroundHandler(
                    onBackground = moviesViewModel::pauseForBackground,
                    setHandler = { backgroundPlaybackHandler = it },
                )
                val playerState by container.playerManager.state.collectAsStateWithLifecycle()
                val playerSettings by container.settingsRepository.playerSettings.collectAsStateWithLifecycle(initialValue = com.watchioiptv.nativeapp.domain.repository.PlayerSettings())
                val detailsState by moviesViewModel.detailsState.collectAsStateWithLifecycle()
                val movie = detailsState.details?.movie
                val contentContext = PlayerContentContext.Movie(
                    title = detailsState.details?.title ?: movie?.name ?: "Movie",
                    year = detailsState.details?.releaseDate ?: movie?.rating,
                    rating = detailsState.details?.rating ?: movie?.rating,
                    runtime = detailsState.details?.runtime,
                    genre = detailsState.details?.genre ?: movie?.genre,
                    posterUrl = detailsState.details?.posterUrl ?: movie?.posterUrl,
                )
                WatchioFullscreenPlayerScreen(
                    playerState = playerState,
                    playerSettings = playerSettings,
                    playerManager = container.playerManager,
                    contentContext = contentContext,
                    onPlayPause = moviesViewModel::playPause,
                    onSeek = moviesViewModel::seekBy,
                    onRestart = moviesViewModel::restartPlayback,
                    onRetry = container.playerManager::retry,
                    onClose = {
                        moviesViewModel.stopPlayback()
                        navController.popBackStack()
                    },
                )
            }
            composable("live") {
                val liveViewModel: LiveTvViewModel = viewModel(factory = liveTvFactory(container))
                val state by liveViewModel.combinedState.collectAsStateWithLifecycle()
                PlaybackBackgroundHandler(
                    onBackground = liveViewModel::pauseForBackground,
                    setHandler = { backgroundPlaybackHandler = it },
                )
                LiveTvScreen(
                    uiState = state.first,
                    playerState = state.second,
                    playerManager = container.playerManager,
                    onCategory = liveViewModel::selectCategory,
                    onCategorySearch = liveViewModel::updateCategorySearch,
                    onLiveSearch = liveViewModel::updateLiveSearch,
                    onChannel = liveViewModel::selectChannel,
                    onFavorite = liveViewModel::toggleFavorite,
                    onRetry = liveViewModel::retry,
                    onRefreshEpg = liveViewModel::refreshEpg,
                    onFullscreen = { navController.navigate("live/fullscreen") },
                    onBack = {
                        liveViewModel.leaveLiveTv()
                        navController.popBackStack()
                    },
                )
            }
            composable("live/{channelId}") { backStackEntry ->
                val channelId = backStackEntry.arguments?.getString("channelId")
                val liveViewModel: LiveTvViewModel = viewModel(factory = liveTvFactory(container))
                LaunchedEffect(channelId) {
                    if (!channelId.isNullOrBlank()) {
                        liveViewModel.selectChannelById(channelId)
                    }
                }
                val state by liveViewModel.combinedState.collectAsStateWithLifecycle()
                PlaybackBackgroundHandler(
                    onBackground = liveViewModel::pauseForBackground,
                    setHandler = { backgroundPlaybackHandler = it },
                )
                LiveTvScreen(
                    uiState = state.first,
                    playerState = state.second,
                    playerManager = container.playerManager,
                    onCategory = liveViewModel::selectCategory,
                    onCategorySearch = liveViewModel::updateCategorySearch,
                    onLiveSearch = liveViewModel::updateLiveSearch,
                    onChannel = liveViewModel::selectChannel,
                    onFavorite = liveViewModel::toggleFavorite,
                    onRetry = liveViewModel::retry,
                    onRefreshEpg = liveViewModel::refreshEpg,
                    onFullscreen = { navController.navigate("live/fullscreen") },
                    onBack = {
                        liveViewModel.leaveLiveTv()
                        navController.popBackStack()
                    },
                )
            }
            composable("live/fullscreen") { backStackEntry ->
                val liveBackStackEntry = remember(backStackEntry) {
                    runCatching { navController.getBackStackEntry("live") }.getOrNull()
                        ?: runCatching { navController.previousBackStackEntry }.getOrNull()
                }
                val liveViewModel: LiveTvViewModel = if (liveBackStackEntry != null) {
                    viewModel(viewModelStoreOwner = liveBackStackEntry, factory = liveTvFactory(container))
                } else {
                    viewModel(factory = liveTvFactory(container))
                }
                val liveState by liveViewModel.uiState.collectAsStateWithLifecycle()
                val playerState by container.playerManager.state.collectAsStateWithLifecycle()
                val playerSettings by container.settingsRepository.playerSettings.collectAsStateWithLifecycle(initialValue = com.watchioiptv.nativeapp.domain.repository.PlayerSettings())
                PlaybackBackgroundHandler(
                    onBackground = liveViewModel::pauseForBackground,
                    setHandler = { backgroundPlaybackHandler = it },
                )
                val selected = liveState.selectedChannel
                val nowNext = liveState.nowNext
                val contentContext = PlayerContentContext.Live(
                    channelName = selected?.name ?: "Live TV",
                    channelLogoUrl = selected?.logoUrl,
                    programmeTitle = nowNext.currentTitle ?: "Live Broadcast",
                    programmeStartTime = formatEpochToTime(nowNext.currentStartEpochMs),
                    programmeEndTime = formatEpochToTime(nowNext.currentEndEpochMs),
                    programmeProgress = nowNext.progress,
                    hasPreviousChannel = liveViewModel.hasPreviousChannel(),
                    hasNextChannel = liveViewModel.hasNextChannel(),
                    onChannelsClick = { navController.popBackStack() },
                    onPreviousChannel = liveViewModel::selectPreviousChannel,
                    onNextChannel = liveViewModel::selectNextChannel,
                )
                WatchioFullscreenPlayerScreen(
                    playerState = playerState,
                    playerSettings = playerSettings,
                    playerManager = container.playerManager,
                    contentContext = contentContext,
                    onPlayPause = liveViewModel::playPause,
                    onSeek = { delta -> container.playerManager.seekBy(delta) },
                    onRestart = container.playerManager::restart,
                    onRetry = container.playerManager::retry,
                    onClose = { navController.popBackStack() },
                )
            }
            composable("tv-guide") {
                val guideViewModel: TvGuideViewModel = viewModel(factory = tvGuideFactory(container))
                val state by guideViewModel.state.collectAsStateWithLifecycle()
                TvGuideScreen(
                    state = state,
                    onJumpToNow = guideViewModel::jumpToNow,
                    onDay = guideViewModel::selectDay,
                    onCategory = guideViewModel::selectCategory,
                    onRefresh = guideViewModel::refreshEpg,
                    onChannel = guideViewModel::selectChannel,
                    onProgramme = guideViewModel::selectProgramme,
                    onPlayLive = {
                        guideViewModel.playLive {
                            navController.navigate("live/fullscreen")
                        }
                    },
                    onCloseDetails = guideViewModel::closeDetails,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("providers/xtream/add") {
                val providerViewModel: XtreamProviderViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return XtreamProviderViewModel(container.xtreamRepository) as T
                        }
                    },
                )
                val state by providerViewModel.state.collectAsStateWithLifecycle()
                XtreamProviderScreen(
                    state = state,
                    onProviderName = providerViewModel::updateProviderName,
                    onServerUrl = providerViewModel::updateServerUrl,
                    onUsername = providerViewModel::updateUsername,
                    onPassword = providerViewModel::updatePassword,
                    onConnect = {
                        providerViewModel.connect {
                            navController.navigate("home") {
                                popUpTo("providers") { inclusive = true }
                            }
                        }
                    },
                    onQuickLogin = { navController.navigate("quick-login") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("quick-login") {
                val quickLoginViewModel: QuickLoginViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return QuickLoginViewModel(
                                providerRepository = container.providerRepository,
                                settingsRepository = container.settingsRepository,
                                credentialStore = container.providerCredentialStore,
                                xtreamRepository = container.xtreamRepository,
                            ) as T
                        }
                    },
                )
                val state by quickLoginViewModel.state.collectAsStateWithLifecycle()
                QuickLoginScreen(
                    state = state,
                    onStartTvPairing = quickLoginViewModel::startTvPairing,
                    onScannedCode = quickLoginViewModel::sendScannedCode,
                    onBack = { navController.popBackStack() },
                    onComplete = {
                        navController.navigate("home") {
                            popUpTo("quick-login") { inclusive = true }
                        }
                    },
                )
            }
            composable("providers/m3u/url/add") {
                val providerViewModel: M3uProviderViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return M3uProviderViewModel(container.m3uRepository) as T
                        }
                    },
                )
                val state by providerViewModel.state.collectAsStateWithLifecycle()
                M3uUrlProviderScreen(
                    state = state,
                    onProviderName = providerViewModel::updateProviderName,
                    onPlaylistUrl = providerViewModel::updatePlaylistUrl,
                    onUserAgent = providerViewModel::updateUserAgent,
                    onConnect = {
                        providerViewModel.connectUrl {
                            navController.navigate("home") {
                                popUpTo("providers") { inclusive = true }
                            }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("providers/m3u/file/add") {
                val providerViewModel: M3uProviderViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return M3uProviderViewModel(container.m3uRepository) as T
                        }
                    },
                )
                val state by providerViewModel.state.collectAsStateWithLifecycle()
                M3uFileProviderScreen(
                    state = state,
                    onProviderName = providerViewModel::updateProviderName,
                    onFileUri = providerViewModel::updateFileUri,
                    onConnect = {
                        providerViewModel.connectFile {
                            navController.navigate("home") {
                                popUpTo("providers") { inclusive = true }
                            }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("settings") {
                SettingsRootScreen(
                    onProviderManagement = { navController.navigate("providers/settings") },
                    onAccount = { navController.navigate("settings/account") },
                    onQuickLogin = { navController.navigate("quick-login") },
                    onPlayer = { navController.navigate("settings/player") },
                    onEpg = { navController.navigate("settings/epg") },
                    onParental = { navController.navigate("settings/parental") },
                    onStreamFormat = { navController.navigate("settings/stream-format") },
                    onInputMode = { navController.navigate("settings/input-mode") },
                    onAppearance = { navController.navigate("settings/appearance") },
                    onBackup = { navController.navigate("settings/backup") },
                    onUpdates = { navController.navigate("settings/updates") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("settings/account") {
                val accountViewModel: AccountInformationViewModel = viewModel(factory = accountInformationFactory(container))
                val state by accountViewModel.state.collectAsStateWithLifecycle()
                SettingsDetailScreen("Account Information", onBack = { navController.popBackStack() }) {
                    AccountInformationContent(state = state)
                }
            }
            composable("settings/player") {
                val settingsViewModel: SettingsViewModel = viewModel(factory = settingsFactory(container))
                val state by settingsViewModel.state.collectAsStateWithLifecycle()
                SettingsDetailScreen("Player Settings", onBack = { navController.popBackStack() }) {
                    PlayerSettingsContent(
                        state = state,
                        onAutoResume = settingsViewModel::setAutoResume,
                        onAutoPlayNextEpisode = settingsViewModel::setAutoPlayNextEpisode,
                        onAutoPlayLive = settingsViewModel::setAutoPlayLiveChannel,
                        onRememberLastLive = settingsViewModel::setRememberLastLiveChannel,
                        onShowControls = settingsViewModel::setShowPlayerControls,
                        onAutoHideDelay = settingsViewModel::setControlAutoHideDelay,
                        onAutoRetry = settingsViewModel::setAutoRetryStreams,
                        onRetryAttempts = settingsViewModel::setRetryAttempts,
                        onVideoScaling = settingsViewModel::setVideoScalingMode,
                    )
                }
            }
            composable("settings/parental") {
                SettingsPlaceholderScreen("Parental Controls", "Parental controls are not configured yet.", onBack = { navController.popBackStack() })
            }
            composable("settings/backup") {
                SettingsPlaceholderScreen("Backup & Restore", "Backup and restore is not configured yet.", onBack = { navController.popBackStack() })
            }
            composable("settings/updates") {
                val updatesViewModel: UpdatesViewModel = viewModel(factory = updatesFactory(container))
                val state by updatesViewModel.state.collectAsStateWithLifecycle()
                UpdatesScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onCheck = updatesViewModel::checkForUpdates,
                    onDownload = updatesViewModel::downloadUpdate,
                    onPermissionRequired = updatesViewModel::installPermissionRequired,
                )
            }
            composable("settings/appearance") {
                val settingsViewModel: SettingsViewModel = viewModel(factory = settingsFactory(container))
                val state by settingsViewModel.state.collectAsStateWithLifecycle()
                SettingsDetailScreen("Appearance", onBack = { navController.popBackStack() }) {
                    AppearanceSettingsContent(state = state, onTheme = settingsViewModel::setTheme)
                }
            }
            composable("settings/input-mode") {
                val settingsViewModel: SettingsViewModel = viewModel(factory = settingsFactory(container))
                val state by settingsViewModel.state.collectAsStateWithLifecycle()
                SettingsDetailScreen("Input Mode", onBack = { navController.popBackStack() }) {
                    InputModeSettingsContent(state = state, onInputMode = settingsViewModel::setInputMode)
                }
            }
            composable("settings/stream-format") {
                val settingsViewModel: SettingsViewModel = viewModel(factory = settingsFactory(container))
                val state by settingsViewModel.state.collectAsStateWithLifecycle()
                SettingsDetailScreen("Stream Format", onBack = { navController.popBackStack() }) {
                    StreamFormatSettingsContent(state = state, onStreamFormat = settingsViewModel::setStreamFormat)
                }
            }
            composable("settings/epg") {
                val settingsViewModel: SettingsViewModel = viewModel(factory = settingsFactory(container))
                val state by settingsViewModel.state.collectAsStateWithLifecycle()
                SettingsDetailScreen("EPG Settings", onBack = { navController.popBackStack() }) {
                    EpgSettingsContent(
                        state = state,
                        onEpgAutoRefresh = settingsViewModel::setEpgAutoRefreshEnabled,
                        onEpgRefreshInterval = settingsViewModel::setEpgRefreshInterval,
                        onRefreshEpgNow = settingsViewModel::refreshEpgNow,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBackgroundPlaybackEffect(onBackground: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, onBackground) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                onBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun PlaybackBackgroundHandler(
    onBackground: () -> Unit,
    setHandler: ((() -> Unit)?) -> Unit,
) {
    DisposableEffect(onBackground) {
        setHandler(onBackground)
        onDispose { setHandler(null) }
    }
}

@Composable
private fun BootstrapLoadingScreen() {
    val colors = LocalWatchioColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Loading Watchio",
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(color = colors.focusGlow)
    }
}

@Composable
private fun DeviceModeScreen(
    onMobile: () -> Unit,
    onTv: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("How will you use Watchio?", color = colors.textPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                WatchioFocusableCard(
                    title = "MOBILE / TOUCH\nPhones & Tablets",
                    accent = colors.liveTvAccent,
                    modifier = Modifier.width(300.dp).height(180.dp).focusRequester(firstFocus).testTag("device-mode-mobile"),
                    onClick = onMobile,
                )
                WatchioFocusableCard(
                    title = "TV / REMOTE\nAndroid TV, Fire TV & Remote",
                    accent = colors.seriesAccent,
                    modifier = Modifier.width(340.dp).height(180.dp).testTag("device-mode-tv"),
                    onClick = onTv,
                )
            }
        }
    }
}

@Composable
private fun ProviderTypeSetupScreen(
    onAddXtreamProvider: () -> Unit,
    onAddM3uUrlProvider: () -> Unit,
    onAddM3uFileProvider: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    Column(
        modifier = Modifier.fillMaxSize().background(colors.surfaceBase).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ADD PLAYLIST", color = colors.textPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            WatchioFocusableCard("XTREAM CODES", modifier = Modifier.focusRequester(firstFocus), accent = colors.seriesAccent, onClick = onAddXtreamProvider)
            WatchioFocusableCard("M3U URL", accent = colors.liveTvAccent, onClick = onAddM3uUrlProvider)
            WatchioFocusableCard("LOCAL M3U", accent = colors.moviesAccent, onClick = onAddM3uFileProvider)
            WatchioFocusableCard("Back", accent = colors.focusGlow, onClick = onBack)
        }
    }
}

@Composable
private fun HomeScreen(
    providerSummary: String,
    providerCount: Int,
    liveCount: Int,
    movieCount: Int,
    seriesCount: Int,
    liveRefreshAtEpochMs: Long?,
    moviesRefreshAtEpochMs: Long?,
    seriesRefreshAtEpochMs: Long?,
    providerExpiryEpochMs: Long?,
    liveRefreshing: Boolean,
    moviesRefreshing: Boolean,
    seriesRefreshing: Boolean,
    refreshMessage: String?,
    onAddXtreamProvider: () -> Unit,
    onAddM3uUrlProvider: () -> Unit,
    onAddM3uFileProvider: () -> Unit,
    onProviders: () -> Unit,
    onSettings: () -> Unit,
    onLiveTv: () -> Unit,
    onTvGuide: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onSearch: () -> Unit,
    onSports: () -> Unit,
    onAnnouncements: () -> Unit,
    announcementUnreadCount: Int,
    onRefreshLive: () -> Unit,
    onRefreshMovies: () -> Unit,
    onRefreshSeries: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val sizes = LocalWatchioComponentSizes.current
    val firstFocus = remember { FocusRequester() }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(30_000L)
        }
    }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home-screen"),
    ) {
        WatchioHomeBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = sizes.tvSafePadding + spacing.lg, vertical = sizes.tvSafePadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            HomeTopBar(
                now = now,
                onSearch = onSearch,
                onSports = onSports,
                onAnnouncements = onAnnouncements,
                onProviders = onProviders,
                announcementUnreadCount = announcementUnreadCount,
            )
            Spacer(Modifier.height(spacing.md))
            if (providerCount == 0) {
                NoProviderHome(
                    onProviders = onProviders,
                    onAddXtreamProvider = onAddXtreamProvider,
                    onAddM3uUrlProvider = onAddM3uUrlProvider,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("home-reference-grid"),
                    horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                ) {
                    val liveAction = HomeAction("LIVE TV", "Watch Live TV Channels", formatHomeUpdatedTime(liveRefreshAtEpochMs), HomeIconKind.Live, colors.liveTvAccent, onLiveTv)
                    val movieAction = HomeAction("MOVIES", "Browse a wide selection", formatHomeUpdatedTime(moviesRefreshAtEpochMs), HomeIconKind.Movie, colors.moviesAccent, onMovies)
                    val seriesAction = HomeAction("SERIES", "Discover and binge-watch", formatHomeUpdatedTime(seriesRefreshAtEpochMs), HomeIconKind.Series, colors.seriesAccent, onSeries)
                    HomePrimaryCard(
                        action = liveAction,
                        refreshing = liveRefreshing,
                        onRefresh = onRefreshLive,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .focusRequester(firstFocus)
                            .testTag("home-live-tv"),
                    )
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(spacing.lg),
                    ) {
                        HomePrimaryCard(
                            action = movieAction,
                            refreshing = moviesRefreshing,
                            onRefresh = onRefreshMovies,
                            modifier = Modifier
                                .weight(0.72f)
                                .fillMaxWidth()
                                .testTag("home-movies"),
                        )
                        HomeSecondaryPill(
                            action = HomeAction("TV Guide", "Now and Next", "", HomeIconKind.Guide, colors.liveTvAccent, onTvGuide),
                            modifier = Modifier
                                .weight(0.28f)
                                .fillMaxWidth()
                                .testTag("home-tv-guide"),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(spacing.lg),
                    ) {
                        HomePrimaryCard(
                            action = seriesAction,
                            refreshing = seriesRefreshing,
                            onRefresh = onRefreshSeries,
                            modifier = Modifier
                                .weight(0.72f)
                                .fillMaxWidth()
                                .testTag("home-series"),
                        )
                        HomeSecondaryPill(
                            action = HomeAction("Settings", "App Preferences", "", HomeIconKind.Settings, colors.focusGlow, onSettings),
                            modifier = Modifier
                                .weight(0.28f)
                                .fillMaxWidth()
                                .testTag("home-settings"),
                        )
                    }
                }
            }
            Spacer(Modifier.height(spacing.sm))
            HomeFooter(providerSummary = providerSummary, providerExpiryEpochMs = providerExpiryEpochMs)
        }
    }
}

@Composable
private fun ProviderManagementScreen(
    state: ProviderManagementUiState,
    onSelect: (com.watchioiptv.nativeapp.core.model.ProviderId) -> Unit,
    onRefresh: (com.watchioiptv.nativeapp.core.model.ProviderId) -> Unit,
    onDelete: (com.watchioiptv.nativeapp.core.model.ProviderId) -> Unit,
    onAddXtreamProvider: () -> Unit,
    onAddM3uUrlProvider: () -> Unit,
    onAddM3uFileProvider: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    var pendingDelete by remember { mutableStateOf<ProviderRowUiState?>(null) }
    val firstFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    LaunchedEffect(state.providers.firstOrNull()?.provider?.id) {
        if (state.providers.isNotEmpty()) firstFocus.requestFocus()
    }
    Column(
        modifier = Modifier.fillMaxSize().background(colors.surfaceBase).padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        WatchioPageHeader(title = "PROVIDER MANAGEMENT", onBack = onBack, testTagPrefix = "providers")
        Text(state.message ?: "Select, switch, refresh, or remove saved providers.", color = colors.textSecondary)
        Spacer(Modifier.height(20.dp))
        if (state.providers.isEmpty()) {
            Text("No providers configured", color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            val columns = if (maxWidth < 720.dp) 1 else 2
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.providers) { row ->
                    ProviderCard(
                        row = row,
                        selected = row.provider.id == state.selectedProviderId,
                        refreshing = row.provider.id == state.refreshingProviderId,
                        onSelect = { onSelect(row.provider.id) },
                        onRefresh = { onRefresh(row.provider.id) },
                        onDelete = { pendingDelete = row },
                        modifier = if (row == state.providers.first()) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WatchioFocusableCard("Add Xtream", accent = colors.seriesAccent, onClick = onAddXtreamProvider)
            WatchioFocusableCard("Add M3U URL", accent = colors.liveTvAccent, onClick = onAddM3uUrlProvider)
            WatchioFocusableCard("Add Local M3U", accent = colors.moviesAccent, onClick = onAddM3uFileProvider)
            WatchioFocusableCard("Back", accent = colors.focusGlow, onClick = onBack, modifier = Modifier.testTag("providers-back"))
        }
    }
    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("REMOVE PROVIDER?") },
            text = { Text("Remove ${row.provider.displayName}? Provider library, EPG, favorites, history, resume data, and secrets for this provider will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(row.provider.id)
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProviderCard(
    row: ProviderRowUiState,
    selected: Boolean,
    refreshing: Boolean,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    val title = buildString {
        if (selected) append("ACTIVE\n")
        append(row.provider.displayName)
        append("\n")
        append(row.typeLabel)
        append("\nLive ${row.liveCount}  Movies ${row.movieCount}  Series ${row.seriesCount}")
        append("\n")
        append(row.refreshState)
    }
    Column {
        WatchioFocusableCard(title = title, accent = if (selected) colors.focusGlow else colors.seriesAccent, onClick = onSelect, modifier = modifier)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WatchioFocusableCard(title = if (refreshing) "Refreshing..." else "Refresh", accent = colors.liveTvAccent, onClick = onRefresh)
            WatchioFocusableCard(title = "Remove", accent = colors.moviesAccent, onClick = onDelete)
        }
    }
}

@Composable
private fun XtreamProviderScreen(
    state: com.watchioiptv.nativeapp.feature.provider.XtreamProviderFormState,
    onProviderName: (String) -> Unit,
    onServerUrl: (String) -> Unit,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConnect: () -> Unit,
    onQuickLogin: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val firstFocus = remember { FocusRequester() }
    val serverFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        disabledTextColor = colors.textMuted,
        cursorColor = colors.focusBorder,
        focusedBorderColor = colors.focusBorder,
        unfocusedBorderColor = colors.surfaceElevated,
        focusedLabelColor = colors.textPrimary,
        unfocusedLabelColor = colors.textSecondary,
    )
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    ProviderFormContainer {
        Text("XTREAM CODES", color = colors.textPrimary, fontWeight = FontWeight.Bold)
        Text("Add provider", color = colors.textSecondary)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = state.providerName,
            onValueChange = onProviderName,
            label = { Text("Provider Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { serverFocus.requestFocus() }),
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth().focusRequester(firstFocus).bringIntoViewOnFocus().testTag("xtream-provider-name"),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = onServerUrl,
            label = { Text("Server URL") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { usernameFocus.requestFocus() }),
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth().focusRequester(serverFocus).bringIntoViewOnFocus().testTag("xtream-server-url"),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.username,
            onValueChange = onUsername,
            label = { Text("Username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth().focusRequester(usernameFocus).bringIntoViewOnFocus().testTag("xtream-username"),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = onPassword,
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConnect() }),
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus).bringIntoViewOnFocus().testTag("xtream-password"),
        )
        Spacer(Modifier.height(16.dp))
        state.errorMessage?.let { Text(it, color = colors.liveTvAccent) }
        when (val importState = state.importState) {
            is XtreamImportState.Importing -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = colors.seriesAccent)
                    Text(importState.stage.label, color = colors.textSecondary)
                }
                Text(
                    "Live ${importState.liveCount}  Movies ${importState.movieCount}  Series ${importState.seriesCount}",
                    color = colors.textMuted,
                )
            }
            is XtreamImportState.Success -> Text(
                "Imported ${importState.liveCount} live, ${importState.movieCount} movies, ${importState.seriesCount} series",
                color = colors.textSecondary,
            )
            is XtreamImportState.Failure -> Text(importState.message, color = colors.liveTvAccent)
            XtreamImportState.Idle -> Unit
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WatchioFocusableCard("Connect", accent = colors.seriesAccent, onClick = onConnect, modifier = Modifier.bringIntoViewOnFocus())
            WatchioFocusableCard("Quick Login", accent = colors.liveTvAccent, onClick = onQuickLogin, modifier = Modifier.bringIntoViewOnFocus())
            WatchioFocusableCard("Cancel", accent = colors.focusGlow, onClick = onBack, modifier = Modifier.bringIntoViewOnFocus())
        }
    }
}

@Composable
private fun QuickLoginScreen(
    state: QuickLoginUiState,
    onStartTvPairing: () -> Unit,
    onScannedCode: (String) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val context = LocalContext.current
    val isPhone = state.inputMode == InputMode.Touch
    var nowEpochMs by remember(state.expiresAtEpochMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isPhone) {
        if (!isPhone) onStartTvPairing()
    }
    LaunchedEffect(state.received) {
        if (state.received) onComplete()
    }
    LaunchedEffect(state.expiresAtEpochMs) {
        while (state.expiresAtEpochMs != null && nowEpochMs < state.expiresAtEpochMs) {
            delay(1_000)
            nowEpochMs = System.currentTimeMillis()
        }
    }
    ProviderFormContainer {
        Text("QUICK LOGIN", color = colors.textPrimary, fontWeight = FontWeight.Bold)
        Text(
            if (isPhone) "Scan QR code shown on your TV." else "Use Watchio on your phone to scan this code.",
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(20.dp))
        if (isPhone) {
            Text("Your password never appears in QR code.", color = colors.textMuted)
            Spacer(Modifier.height(20.dp))
            WatchioFocusableCard(
                title = if (state.isBusy) "Sending login..." else "Scan TV QR Code",
                accent = colors.seriesAccent,
                onClick = {
                    if (!state.isBusy) {
                        val options = GmsBarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .enableAutoZoom()
                            .build()
                        GmsBarcodeScanning.getClient(context, options).startScan()
                            .addOnSuccessListener { barcode -> barcode.rawValue?.let(onScannedCode) }
                    }
                },
                modifier = Modifier.bringIntoViewOnFocus(),
            )
        } else {
            state.invitation?.let { invitation ->
                QuickLoginQrCode(invitation, modifier = Modifier.align(Alignment.CenterHorizontally).size(260.dp).background(Color.White).padding(12.dp))
                Spacer(Modifier.height(16.dp))
            }
            state.expiresAtEpochMs?.let { expiresAtEpochMs ->
                Text("QR expires in ${formatQuickLoginRemaining(expiresAtEpochMs - nowEpochMs)}", color = colors.textMuted)
            }
            if (state.isBusy) CircularProgressIndicator(color = colors.seriesAccent)
            if (state.invitation == null && state.errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                WatchioFocusableCard("Start New Code", accent = colors.seriesAccent, onClick = onStartTvPairing, modifier = Modifier.bringIntoViewOnFocus())
            }
        }
        if (state.status.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(state.status, color = colors.textSecondary)
        }
        state.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = colors.liveTvAccent)
        }
        Spacer(Modifier.height(24.dp))
        WatchioFocusableCard("Back", accent = colors.focusGlow, onClick = onBack, modifier = Modifier.bringIntoViewOnFocus())
    }
}

@Composable
private fun QuickLoginQrCode(value: String, modifier: Modifier = Modifier) {
    val matrix = remember(value) { QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 0, 0) }
    Canvas(modifier = modifier.semantics { contentDescription = "Watchio Quick Login QR code" }) {
        val moduleSize = minOf(size.width / matrix.width, size.height / matrix.height)
        val left = (size.width - (matrix.width * moduleSize)) / 2f
        val top = (size.height - (matrix.height * moduleSize)) / 2f
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                if (matrix[x, y]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(left + (x * moduleSize), top + (y * moduleSize)),
                        size = Size(moduleSize, moduleSize),
                    )
                }
            }
        }
    }
}

private fun formatQuickLoginRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs.coerceAtLeast(0) / 1_000).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun M3uUrlProviderScreen(
    state: com.watchioiptv.nativeapp.feature.provider.M3uProviderFormState,
    onProviderName: (String) -> Unit,
    onPlaylistUrl: (String) -> Unit,
    onUserAgent: (String) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    ProviderFormContainer {
        Text("M3U URL", color = colors.textPrimary, fontWeight = FontWeight.Bold)
        Text("Add playlist provider", color = colors.textSecondary)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = state.providerName,
            onValueChange = onProviderName,
            label = { Text("Provider Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(firstFocus).bringIntoViewOnFocus(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.playlistUrl,
            onValueChange = onPlaylistUrl,
            label = { Text("Playlist URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.userAgent,
            onValueChange = onUserAgent,
            label = { Text("Custom User-Agent") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
        )
        Spacer(Modifier.height(16.dp))
        M3uImportStatus(state)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WatchioFocusableCard("Connect", accent = colors.seriesAccent, onClick = onConnect, modifier = Modifier.bringIntoViewOnFocus())
            WatchioFocusableCard("Cancel", accent = colors.focusGlow, onClick = onBack, modifier = Modifier.bringIntoViewOnFocus())
        }
    }
}

@Composable
private fun M3uFileProviderScreen(
    state: com.watchioiptv.nativeapp.feature.provider.M3uProviderFormState,
    onProviderName: (String) -> Unit,
    onFileUri: (String) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val context = LocalContext.current
    val firstFocus = remember { FocusRequester() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        onFileUri(uri.toString())
    }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    ProviderFormContainer {
        Text("LOCAL M3U FILE", color = colors.textPrimary, fontWeight = FontWeight.Bold)
        Text("Add playlist provider", color = colors.textSecondary)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = state.providerName,
            onValueChange = onProviderName,
            label = { Text("Provider Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(firstFocus).bringIntoViewOnFocus(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (state.fileUri.isBlank()) "No file selected" else "File selected",
            color = colors.textMuted,
        )
        Spacer(Modifier.height(16.dp))
        M3uImportStatus(state)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WatchioFocusableCard("Choose File", accent = colors.liveTvAccent, onClick = { launcher.launch(arrayOf("*/*")) }, modifier = Modifier.bringIntoViewOnFocus())
            WatchioFocusableCard("Connect", accent = colors.seriesAccent, onClick = onConnect, modifier = Modifier.bringIntoViewOnFocus())
            WatchioFocusableCard("Cancel", accent = colors.focusGlow, onClick = onBack, modifier = Modifier.bringIntoViewOnFocus())
        }
    }
}

@Composable
private fun ProviderFormContainer(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalWatchioColors.current
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .verticalScroll(scrollState)
            .imePadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.bringIntoViewOnFocus(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    bringIntoViewRequester(requester).onFocusChanged { state ->
        if (state.isFocused) {
            scope.launch { requester.bringIntoView() }
        }
    }
}

@Composable
private fun M3uImportStatus(state: com.watchioiptv.nativeapp.feature.provider.M3uProviderFormState) {
    val colors = LocalWatchioColors.current
    state.errorMessage?.let { Text(it, color = colors.liveTvAccent) }
    when (val importState = state.importState) {
        is M3uImportState.Importing -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = colors.seriesAccent)
                Text(importState.stage.label, color = colors.textSecondary)
            }
            Text(
                "Live ${importState.liveCount}  Movies ${importState.movieCount}  Series ${importState.seriesCount}",
                color = colors.textMuted,
            )
        }
        is M3uImportState.Success -> Text(
            "Imported ${importState.liveCount} live, ${importState.movieCount} movies, ${importState.seriesCount} series",
            color = colors.textSecondary,
        )
        is M3uImportState.Failure -> Text(importState.message, color = colors.liveTvAccent)
        M3uImportState.Idle -> Unit
    }
}

private data class HomeAction(
    val title: String,
    val subtitle: String,
    val status: String,
    val icon: HomeIconKind,
    val accent: androidx.compose.ui.graphics.Color,
    val onClick: () -> Unit,
)

private enum class HomeIconKind {
    Live,
    Movie,
    Series,
    Guide,
    Settings,
    List,
    Search,
    Provider,
    Sports,
    Announcement,
    Back,
}

@Composable
private fun WatchioHomeBackground() {
    val colors = LocalWatchioColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.surfaceBase,
                        colors.surfaceCard,
                        colors.surfaceBase,
                    ),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(colors.liveTvAccent.copy(alpha = 0.22f), radius = size.minDimension * 0.42f, center = Offset(size.width * 0.12f, size.height * 0.46f))
            drawCircle(colors.moviesAccent.copy(alpha = 0.22f), radius = size.minDimension * 0.46f, center = Offset(size.width * 0.46f, size.height * 0.36f))
            drawCircle(colors.seriesAccent.copy(alpha = 0.20f), radius = size.minDimension * 0.42f, center = Offset(size.width * 0.88f, size.height * 0.46f))
            drawCircle(colors.liveTvAccent.copy(alpha = 0.12f), radius = size.minDimension * 0.22f, center = Offset(size.width * 0.02f, size.height * 0.18f))
            drawCircle(colors.seriesAccent.copy(alpha = 0.11f), radius = size.minDimension * 0.24f, center = Offset(size.width * 0.98f, size.height * 0.24f))
            for (index in 0..10) {
                val y = size.height * (0.18f + index * 0.08f)
                drawLine(
                    color = when (index % 3) {
                        0 -> colors.liveTvAccent
                        1 -> colors.moviesAccent
                        else -> colors.seriesAccent
                    }.copy(alpha = 0.06f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y + (index % 3 - 1) * 54f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
            }
            drawRect(Color.Black.copy(alpha = 0.58f), size = size)
        }
    }
}

@Composable
internal fun HomeTopBar(
    now: LocalDateTime,
    onSearch: () -> Unit,
    onSports: () -> Unit,
    onAnnouncements: () -> Unit,
    onProviders: () -> Unit,
    announcementUnreadCount: Int = 0,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    val timeText = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val dateText = now.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().testTag("home-header")) {
        val compact = maxWidth < 720.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.width(if (compact) 128.dp else 190.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                WatchioLogoMark()
                Column {
                    Text("Watchio", color = colors.textPrimary, style = type.screenTitle, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (!compact) Text("IPTV", color = colors.textSecondary, style = type.body, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            Column(modifier = Modifier.width(if (compact) 92.dp else 132.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(timeText, color = colors.textPrimary, style = type.screenTitle, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(dateText, color = colors.liveTvAccent, style = type.label, maxLines = 1)
            }
            Row(
                modifier = Modifier.width(if (compact) 224.dp else 450.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeTopAction("Search", HomeIconKind.Search, colors.textPrimary, onSearch, contentDescription = "Search", testTag = "home-search")
                HomeTopAction("Sports", HomeIconKind.Sports, colors.liveTvAccent, onSports)
                HomeTopAction("Announcements", HomeIconKind.Announcement, colors.moviesAccent, onAnnouncements, badgeCount = announcementUnreadCount)
                HomeTopAction("Playlist", HomeIconKind.Provider, colors.seriesAccent, onProviders)
            }
        }
    }
}

@Composable
private fun WatchioLogoMark() {
    val colors = LocalWatchioColors.current
    val icons = LocalWatchioIconSizes.current
    val radii = LocalWatchioRadii.current
    Box(
        modifier = Modifier
            .size(icons.lg + 18.dp)
            .background(colors.surfaceCard.copy(alpha = 0.72f), RoundedCornerShape(radii.lg))
            .border(1.dp, colors.liveTvAccent.copy(alpha = 0.72f), RoundedCornerShape(radii.lg)),
        contentAlignment = Alignment.Center,
    ) {
        HomeVectorIcon(HomeIconKind.Live, colors.liveTvAccent, Modifier.size(icons.lg))
    }
}

@Composable
private fun NoProviderHome(
    onProviders: () -> Unit,
    onAddXtreamProvider: () -> Unit,
    onAddM3uUrlProvider: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    Box(modifier = modifier.testTag("home-no-provider"), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text("Add a provider to start watching", color = colors.textPrimary, style = type.cardTitle, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                WatchioButton("Add Provider", onClick = onProviders, variant = WatchioButtonVariant.Primary, modifier = Modifier.testTag("home-add-provider"))
                WatchioButton("Add Xtream", onClick = onAddXtreamProvider, variant = WatchioButtonVariant.Secondary)
                WatchioButton("Add M3U", onClick = onAddM3uUrlProvider, variant = WatchioButtonVariant.Secondary)
            }
        }
    }
}

@Composable
private fun HomePlaceholderScreen(title: String, message: String, onBack: () -> Unit) {
    val colors = LocalWatchioColors.current
    Column(
        modifier = Modifier.fillMaxSize().background(colors.surfaceBase).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WatchioScreenHeader(title = title, subtitle = message)
        Spacer(Modifier.height(20.dp))
        WatchioButton("Back", onClick = onBack, variant = WatchioButtonVariant.Secondary)
    }
}

@Composable
private fun HomeTopAction(
    label: String,
    icon: HomeIconKind,
    tint: Color,
    onClick: () -> Unit,
    contentDescription: String = label,
    testTag: String = "home-action-${label.lowercase()}",
    badgeCount: Int = 0,
) {
    val spacing = LocalWatchioSpacing.current
    val colors = LocalWatchioColors.current
    Box(Modifier.size(52.dp)) {
        WatchioCard(
            modifier = Modifier.fillMaxSize().testTag(testTag),
            accent = tint,
            minWidth = 0.dp,
            minHeight = 44.dp,
            contentDescription = contentDescription,
            onClick = onClick,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = spacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeVectorIcon(icon, tint, Modifier.size(24.dp))
            }
        }
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .background(colors.moviesAccent, CircleShape)
                    .testTag("home-announcements-badge"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                    color = colors.surfaceBase,
                    fontWeight = FontWeight.Bold,
                    style = LocalWatchioTypography.current.label,
                )
            }
        }
    }
}

@Composable
private fun TvRootExitBackHandler(
    enabled: Boolean,
    onExit: () -> Unit,
) {
    val gate = remember { TvDoubleBackExitGate() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(enabled) { gate.reset() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) gate.reset()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = enabled) {
        if (gate.onBack(SystemClock.elapsedRealtime())) {
            onExit()
        } else {
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun HomePrimaryCard(
    action: HomeAction,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    val radii = LocalWatchioRadii.current
    WatchioCard(
        modifier = modifier.shadow(10.dp, RoundedCornerShape(radii.lg)),
        accent = action.accent,
        minWidth = 0.dp,
        minHeight = 0.dp,
        contentDescription = "${action.title}, ${action.status}",
        onClick = action.onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(colors.surfaceCard.copy(alpha = 0.72f), colors.surfaceElevated.copy(alpha = 0.88f))))
                .padding(top = spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeVectorIcon(action.icon, action.accent, Modifier.size(52.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(action.title, color = colors.textPrimary, style = type.screenTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(action.subtitle, color = colors.textSecondary, style = type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Color.Black.copy(alpha = 0.22f))
                    .padding(horizontal = spacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (refreshing) "Refreshing..." else action.status,
                    color = colors.textPrimary,
                    style = type.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(action.accent.copy(alpha = 0.28f))
                        .border(1.dp, action.accent.copy(alpha = 0.70f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onRefresh)
                        .semantics { contentDescription = "Refresh ${action.title}" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(color = action.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        HomeRefreshIcon(action.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSecondaryPill(action: HomeAction, modifier: Modifier = Modifier) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    WatchioCard(
        modifier = modifier,
        accent = action.accent,
        minWidth = 0.dp,
        minHeight = 0.dp,
        contentDescription = action.title,
        onClick = action.onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(spacing.md),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeVectorIcon(action.icon, colors.textPrimary, Modifier.size(32.dp))
            Spacer(Modifier.width(spacing.md))
            Column {
                Text(action.title.uppercase(), color = colors.textPrimary, style = type.cardTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(action.subtitle, color = colors.textSecondary, style = type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HomeFooter(providerSummary: String, providerExpiryEpochMs: Long?) {
    val colors = LocalWatchioColors.current
    val type = LocalWatchioTypography.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = LocalWatchioSpacing.current.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(formatHomeExpiration(providerExpiryEpochMs), color = colors.textSecondary, style = type.body)
        Text("v0.1.0", color = colors.textMuted, style = type.body)
        Text("Active Provider: $providerSummary", color = colors.textSecondary, style = type.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 300.dp))
    }
}

private fun formatHomeUpdatedTime(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0L) return "Updated last: Never"
    val local = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
    return "Updated last: ${local.format(DateTimeFormatter.ofPattern("h:mm a"))}"
}

private fun formatHomeExpiration(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0L) return "Expiration: Not available"
    val local = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
    return "Expiration: ${local.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
}

private fun formatAccountDate(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0L) return "Not available"
    val local = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
    return local.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
}

private fun formatAccountDateTime(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0L) return "Not available"
    val local = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
    return local.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a"))
}

@Composable
private fun HomeVectorIcon(kind: HomeIconKind, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * 0.10f, cap = StrokeCap.Round)
        when (kind) {
            HomeIconKind.Live, HomeIconKind.Guide -> {
                drawRoundRect(tint, topLeft = Offset(size.width * 0.16f, size.height * 0.26f), size = Size(size.width * 0.68f, size.height * 0.48f), style = stroke)
                drawLine(tint, Offset(size.width * 0.36f, size.height * 0.26f), Offset(size.width * 0.26f, size.height * 0.08f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.62f, size.height * 0.26f), Offset(size.width * 0.74f, size.height * 0.08f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                val path = Path().apply {
                    moveTo(size.width * 0.44f, size.height * 0.40f)
                    lineTo(size.width * 0.44f, size.height * 0.62f)
                    lineTo(size.width * 0.62f, size.height * 0.51f)
                    close()
                }
                drawPath(path, tint)
            }
            HomeIconKind.Movie -> {
                val path = Path().apply {
                    moveTo(size.width * 0.34f, size.height * 0.22f)
                    lineTo(size.width * 0.34f, size.height * 0.78f)
                    lineTo(size.width * 0.76f, size.height * 0.50f)
                    close()
                }
                drawPath(path, tint)
            }
            HomeIconKind.Search -> {
                val strokeWidth = size.minDimension * 0.10f
                val radius = size.width * 0.30f
                val centerOffset = Offset(size.width * 0.42f, size.height * 0.42f)
                drawCircle(
                    color = tint,
                    radius = radius,
                    center = centerOffset,
                    style = Stroke(width = strokeWidth),
                )
                val handleStart = Offset(
                    centerOffset.x + (radius * 0.7071f),
                    centerOffset.y + (radius * 0.7071f),
                )
                val handleEnd = Offset(
                    size.width * 0.88f,
                    size.height * 0.88f,
                )
                drawLine(
                    color = tint,
                    start = handleStart,
                    end = handleEnd,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            HomeIconKind.Series -> {
                drawRoundRect(tint, topLeft = Offset(size.width * 0.18f, size.height * 0.30f), size = Size(size.width * 0.64f, size.height * 0.48f))
                drawLine(Color.Black.copy(alpha = 0.65f), Offset(size.width * 0.22f, size.height * 0.30f), Offset(size.width * 0.74f, size.height * 0.30f), strokeWidth = size.minDimension * 0.08f)
                for (i in 0..3) {
                    drawLine(Color.Black.copy(alpha = 0.65f), Offset(size.width * (0.25f + i * 0.13f), size.height * 0.18f), Offset(size.width * (0.31f + i * 0.13f), size.height * 0.30f), strokeWidth = size.minDimension * 0.06f)
                }
            }
            HomeIconKind.Settings -> {
                drawCircle(tint, radius = size.minDimension * 0.18f, center = center, style = stroke)
                for (i in 0..7) {
                    val angle = Math.toRadians((i * 45).toDouble())
                    val start = Offset(center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.25f, center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.25f)
                    val end = Offset(center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.42f, center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.42f)
                    drawLine(tint, start, end, strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            HomeIconKind.List, HomeIconKind.Provider -> {
                for (i in 0..2) {
                    val y = size.height * (0.30f + i * 0.20f)
                    drawCircle(tint, radius = size.minDimension * 0.045f, center = Offset(size.width * 0.22f, y))
                    drawLine(tint, Offset(size.width * 0.34f, y), Offset(size.width * 0.80f, y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            HomeIconKind.Sports -> {
                drawCircle(tint, radius = size.minDimension * 0.34f, center = center, style = stroke)
                drawCircle(tint, radius = size.minDimension * 0.08f, center = center)
                drawLine(tint, Offset(size.width * 0.28f, size.height * 0.32f), Offset(size.width * 0.72f, size.height * 0.68f), strokeWidth = stroke.width * 0.72f, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.72f, size.height * 0.32f), Offset(size.width * 0.28f, size.height * 0.68f), strokeWidth = stroke.width * 0.72f, cap = StrokeCap.Round)
            }
            HomeIconKind.Announcement -> {
                val horn = Path().apply {
                    moveTo(size.width * 0.20f, size.height * 0.42f)
                    lineTo(size.width * 0.68f, size.height * 0.24f)
                    lineTo(size.width * 0.68f, size.height * 0.76f)
                    lineTo(size.width * 0.20f, size.height * 0.58f)
                    close()
                }
                drawPath(horn, tint, style = stroke)
                drawLine(tint, Offset(size.width * 0.22f, size.height * 0.58f), Offset(size.width * 0.32f, size.height * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.78f, size.height * 0.36f), Offset(size.width * 0.88f, size.height * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.80f, size.height * 0.64f), Offset(size.width * 0.90f, size.height * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            HomeIconKind.Back -> {
                drawLine(tint, Offset(size.width * 0.72f, size.height * 0.18f), Offset(size.width * 0.28f, size.height * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(size.width * 0.28f, size.height * 0.50f), Offset(size.width * 0.72f, size.height * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun HomeRefreshIcon(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        drawArc(tint, startAngle = 35f, sweepAngle = 285f, useCenter = false, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
        val path = Path().apply {
            moveTo(size.width * 0.78f, size.height * 0.20f)
            lineTo(size.width * 0.88f, size.height * 0.44f)
            lineTo(size.width * 0.63f, size.height * 0.38f)
            close()
        }
        drawPath(path, tint)
    }
}

private fun moviesFactory(container: AppContainer): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MoviesViewModel(
                moviesRepository = container.moviesRepository,
                favoritesRepository = container.favoritesRepository,
                historyRepository = container.historyRepository,
                settingsRepository = container.settingsRepository,
                playerManager = container.playerManager,
                clock = SystemWatchioClock,
            ) as T
        }
    }

private fun seriesFactory(container: AppContainer): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SeriesViewModel(
                seriesRepository = container.seriesRepository,
                favoritesRepository = container.favoritesRepository,
                historyRepository = container.historyRepository,
                settingsRepository = container.settingsRepository,
                playerManager = container.playerManager,
                clock = SystemWatchioClock,
            ) as T
        }
    }

private fun searchFactory(container: AppContainer): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GlobalSearchViewModel(container.searchRepository) as T
        }
    }

private fun myListFactory(container: AppContainer): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MyListViewModel(container.myListRepository) as T
        }
    }

private fun tvGuideFactory(container: AppContainer): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TvGuideViewModel(
                repository = container.tvGuideRepository,
                playerManager = container.playerManager,
                clock = SystemWatchioClock,
            ) as T
        }
    }

private fun accountInformationFactory(container: AppContainer): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AccountInformationViewModel(
                providerRepository = container.providerRepository,
                settingsRepository = container.settingsRepository,
                credentialStore = container.providerCredentialStore,
            ) as T
        }
    }

private fun updatesFactory(container: AppContainer): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UpdatesViewModel(container.updateRepository) as T
        }
    }

@Composable
private fun SettingsRootScreen(
    onProviderManagement: () -> Unit,
    onAccount: () -> Unit,
    onQuickLogin: () -> Unit,
    onPlayer: () -> Unit,
    onEpg: () -> Unit,
    onParental: () -> Unit,
    onStreamFormat: () -> Unit,
    onInputMode: () -> Unit,
    onAppearance: () -> Unit,
    onBackup: () -> Unit,
    onUpdates: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val firstFocus = remember { FocusRequester() }
    val categories = remember(colors) {
        listOf(
            SettingsCategory("Provider Management", "Manage IPTV providers", HomeIconKind.Provider, colors.liveTvAccent, onProviderManagement, "settings-provider-management"),
            SettingsCategory("Account Information", "View your account details", HomeIconKind.Provider, colors.moviesAccent, onAccount, "settings-account-information"),
            SettingsCategory("Quick Login", "Move your login from phone to TV", HomeIconKind.Provider, colors.seriesAccent, onQuickLogin, "settings-quick-login"),
            SettingsCategory("Player Settings", "Playback and video settings", HomeIconKind.Movie, colors.seriesAccent, onPlayer, "settings-player-settings"),
            SettingsCategory("EPG Settings", "Guide and programme settings", HomeIconKind.Guide, colors.liveTvAccent, onEpg, "settings-epg-settings"),
            SettingsCategory("Parental Controls", "Restrict content and settings", HomeIconKind.Settings, colors.moviesAccent, onParental, "settings-parental-controls"),
            SettingsCategory("Stream Format", "Choose your preferred format", HomeIconKind.List, colors.seriesAccent, onStreamFormat, "settings-stream-format"),
            SettingsCategory("Input Mode", "Mobile touch or TV remote controls", HomeIconKind.Provider, colors.liveTvAccent, onInputMode, "settings-input-mode"),
            SettingsCategory("Appearance", "Theme and visual customization", HomeIconKind.Settings, colors.moviesAccent, onAppearance, "settings-appearance"),
            SettingsCategory("Backup & Restore", "Export and restore application data", HomeIconKind.Provider, colors.seriesAccent, onBackup, "settings-backup-restore"),
            SettingsCategory("Check for Updates", "Check for a newer Watchio version", HomeIconKind.Announcement, colors.liveTvAccent, onUpdates, "settings-check-updates"),
        )
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    Box(Modifier.fillMaxSize().testTag("settings-root")) {
        WatchioHomeBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 34.dp, vertical = 24.dp),
        ) {
            SettingsHeader(title = "SETTINGS", onBack = onBack)
            Spacer(Modifier.height(spacing.md))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("settings-category-grid"),
                horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                items(categories) { category ->
                    SettingsCategoryCard(
                        category = category,
                        modifier = Modifier
                            .height(148.dp)
                            .then(if (category == categories.first()) Modifier.focusRequester(firstFocus) else Modifier),
                    )
                }
            }
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                color = colors.textMuted,
                modifier = Modifier.fillMaxWidth().padding(top = spacing.sm).testTag("settings-version"),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SettingsCategoryCard(category: SettingsCategory, modifier: Modifier = Modifier) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    WatchioCard(
        modifier = modifier.testTag(category.testTag),
        accent = category.accent,
        minWidth = 0.dp,
        minHeight = 0.dp,
        contentDescription = "${category.title}, ${category.subtitle}",
        onClick = category.onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(colors.surfaceCard.copy(alpha = 0.76f), colors.surfaceElevated.copy(alpha = 0.86f))))
                .padding(spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HomeVectorIcon(category.icon, category.accent, Modifier.size(38.dp))
            Spacer(Modifier.height(spacing.sm))
            Text(category.title, color = colors.textPrimary, style = type.cardTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(category.subtitle, color = colors.textSecondary, style = type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingsDetailScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalWatchioColors.current
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().testTag("settings-detail")) {
        WatchioHomeBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 34.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsHeader(title = title.uppercase(), onBack = onBack)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingsPlaceholderScreen(title: String, message: String, onBack: () -> Unit) {
    SettingsDetailScreen(title = title, onBack = onBack) {
        val colors = LocalWatchioColors.current
        val type = LocalWatchioTypography.current
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(title, color = colors.textPrimary, style = type.screenTitle, fontWeight = FontWeight.Bold)
            Text(message, color = colors.textSecondary, style = type.body)
        }
    }
}

@Composable
private fun AccountInformationContent(state: AccountInformationUiState) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("account-information-content"),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        WatchioCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("account-status-card"),
            accent = if (state.accountStatus == "Active") colors.liveTvAccent else colors.moviesAccent,
            minWidth = 0.dp,
            minHeight = 112.dp,
            contentDescription = "Account Status ${state.accountStatus}",
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(colors.surfaceCard.copy(alpha = 0.78f), colors.surfaceElevated.copy(alpha = 0.88f))))
                    .padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text("Account Status", color = colors.textSecondary, style = type.label)
                Text(state.accountStatus, color = colors.textPrimary, style = type.screenTitle, fontWeight = FontWeight.Bold)
                state.unavailableReason?.let {
                    Text(it, color = colors.textMuted, style = type.body)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val twoColumns = maxWidth >= 760.dp
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    AccountInfoSection("ACCOUNT", accountRows(state), Modifier.weight(1f))
                    AccountInfoSection("CONNECTIONS", connectionRows(state), Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    AccountInfoSection("ACCOUNT", accountRows(state), Modifier.fillMaxWidth())
                    AccountInfoSection("CONNECTIONS", connectionRows(state), Modifier.fillMaxWidth())
                }
            }
        }
        AccountInfoSection("WATCHIO", watchioRows(state), Modifier.fillMaxWidth())
    }
}

@Composable
private fun AccountInfoSection(title: String, rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    WatchioCard(
        modifier = modifier.testTag("account-section-${title.lowercase()}"),
        accent = colors.seriesAccent,
        minWidth = 0.dp,
        minHeight = 0.dp,
        contentDescription = title,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceCard.copy(alpha = 0.72f))
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(title, color = colors.liveTvAccent, style = type.label, fontWeight = FontWeight.Bold)
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = colors.textSecondary, style = type.body, modifier = Modifier.weight(0.45f))
                    Text(
                        value,
                        color = colors.textPrimary,
                        style = type.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.55f),
                    )
                }
            }
        }
    }
}

private fun accountRows(state: AccountInformationUiState): List<Pair<String, String>> = listOf(
    "Provider Name" to state.providerName,
    "Username" to state.username,
    "Account Status" to state.accountStatus,
    "Expiration Date" to formatAccountDate(state.expirationEpochMs),
    "Provider Type" to state.providerType,
)

private fun connectionRows(state: AccountInformationUiState): List<Pair<String, String>> = listOf(
    "Maximum Connections" to state.maximumConnections,
    "Active Connections" to state.activeConnections,
    "Output Formats" to state.outputFormats,
)

private fun watchioRows(state: AccountInformationUiState): List<Pair<String, String>> = listOf(
    "Added to Watchio" to formatAccountDateTime(state.addedAtEpochMs),
    "Last Provider Refresh" to formatAccountDateTime(state.providerRefreshAtEpochMs),
    "Last Live Refresh" to formatAccountDateTime(state.liveRefreshAtEpochMs),
    "Last Movies Refresh" to formatAccountDateTime(state.moviesRefreshAtEpochMs),
    "Last Series Refresh" to formatAccountDateTime(state.seriesRefreshAtEpochMs),
)

@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    WatchioPageHeader(title = title, onBack = onBack, testTagPrefix = "settings")
}

@Composable
private fun SettingsBackIconButton(onClick: () -> Unit) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        modifier = Modifier
            .size(48.dp)
            .testTag("settings-back-icon"),
        accent = colors.liveTvAccent,
        minWidth = 48.dp,
        minHeight = 48.dp,
        contentDescription = "Back",
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            HomeVectorIcon(HomeIconKind.Back, colors.textPrimary, Modifier.size(24.dp))
        }
    }
}

@Composable
private fun AppearanceSettingsContent(
    state: SettingsUiState,
    onTheme: (WatchioThemeState) -> Unit,
) {
    val colors = LocalWatchioColors.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(state.selectedTheme.id) { firstFocus.requestFocus() }
    Text("Theme: ${state.themeLabel}", color = colors.textMuted)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 720.dp) 2 else 4
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.themes.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { theme ->
                        val selected = theme.id == state.selectedTheme.id
                        WatchioFocusableCard(
                            title = if (selected) "Selected\n${theme.id.label}" else theme.id.label,
                            accent = if (selected) colors.focusGlow else theme.seriesAccent,
                            onClick = { onTheme(theme) },
                            modifier = Modifier
                                .weight(1f)
                                .then(if (theme == state.themes.first()) Modifier.focusRequester(firstFocus) else Modifier),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun InputModeSettingsContent(
    state: SettingsUiState,
    onInputMode: (InputMode) -> Unit,
) {
    val colors = LocalWatchioColors.current
    Text("Input mode", color = colors.textPrimary, fontWeight = FontWeight.Bold)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 720.dp) 2 else InputMode.entries.size
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InputMode.entries.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { mode ->
                        val selected = mode == state.inputMode
                        WatchioFocusableCard(
                            title = if (selected) "Selected\n${mode.label()}" else mode.label(),
                            accent = if (selected) colors.focusGlow else colors.seriesAccent,
                            onClick = { onInputMode(mode) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun StreamFormatSettingsContent(
    state: SettingsUiState,
    onStreamFormat: (StreamFormat) -> Unit,
) {
    val colors = LocalWatchioColors.current
    Text("Stream format", color = colors.textPrimary, fontWeight = FontWeight.Bold)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 720.dp) 2 else StreamFormat.entries.size
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StreamFormat.entries.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { format ->
                        val selected = format == state.streamFormat
                        WatchioFocusableCard(
                            title = if (selected) "Selected\n${format.label()}" else format.label(),
                            accent = if (selected) colors.focusGlow else colors.liveTvAccent,
                            onClick = { onStreamFormat(format) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PlayerSettingsContent(
    state: SettingsUiState,
    onAutoResume: (Boolean) -> Unit,
    onAutoPlayNextEpisode: (Boolean) -> Unit,
    onAutoPlayLive: (Boolean) -> Unit,
    onRememberLastLive: (Boolean) -> Unit,
    onShowControls: (Boolean) -> Unit,
    onAutoHideDelay: (ControlAutoHideDelay) -> Unit,
    onAutoRetry: (Boolean) -> Unit,
    onRetryAttempts: (Int) -> Unit,
    onVideoScaling: (VideoScalingMode) -> Unit,
) {
    val settings = state.playerSettings
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxWidth().testTag("player-settings-content"),
    ) {
        SettingsSection("PLAYBACK")
        BooleanSettingCard(
            title = "Auto Resume",
            description = "Use saved movie and episode progress for normal play.",
            checked = settings.autoResume,
            onClick = { onAutoResume(!settings.autoResume) },
        )
        BooleanSettingCard(
            title = "Auto Play Next Episode",
            description = "Automatically play the next episode when the current one ends.",
            checked = settings.autoPlayNextEpisode,
            onClick = { onAutoPlayNextEpisode(!settings.autoPlayNextEpisode) },
        )
        BooleanSettingCard(
            title = "Auto Play Live Channel",
            description = "Start remembered live channel when Live TV opens.",
            checked = settings.autoPlayLiveChannel,
            onClick = { onAutoPlayLive(!settings.autoPlayLiveChannel) },
        )
        BooleanSettingCard(
            title = "Remember Last Live Channel",
            description = "Save last played channel identity per provider.",
            checked = settings.rememberLastLiveChannel,
            onClick = { onRememberLastLive(!settings.rememberLastLiveChannel) },
        )

        SettingsSection("CONTROLS")
        BooleanSettingCard(
            title = "Show Player Controls",
            description = "Show playback controls when overlay opens.",
            checked = settings.showPlayerControls,
            onClick = { onShowControls(!settings.showPlayerControls) },
        )
        ChoiceSettingRow(
            title = "Control Auto-Hide Delay",
            tagPrefix = "player-autohide",
            choices = ControlAutoHideDelay.entries.toList(),
            selected = settings.controlAutoHideDelay,
            label = { it.label },
            tagValue = {
                when (it) {
                    ControlAutoHideDelay.ThreeSeconds -> "3"
                    ControlAutoHideDelay.FiveSeconds -> "5"
                    ControlAutoHideDelay.EightSeconds -> "8"
                    ControlAutoHideDelay.Never -> "never"
                }
            },
            onSelect = onAutoHideDelay,
        )

        SettingsSection("RECOVERY")
        BooleanSettingCard(
            title = "Auto Retry Streams",
            description = "Retry recoverable player errors with bounded attempts.",
            checked = settings.autoRetryStreams,
            onClick = { onAutoRetry(!settings.autoRetryStreams) },
        )
        ChoiceSettingRow(
            title = "Retry Attempts",
            tagPrefix = "player-retry",
            choices = listOf(1, 2, 3),
            selected = settings.retryAttempts,
            label = { it.toString() },
            tagValue = { it.toString() },
            onSelect = onRetryAttempts,
            enabled = settings.autoRetryStreams,
        )

        SettingsSection("VIDEO")
        ChoiceSettingRow(
            title = "Video Scaling",
            tagPrefix = "player-scaling",
            choices = VideoScalingMode.entries.toList(),
            selected = settings.videoScalingMode,
            label = { it.label },
            tagValue = { it.persisted },
            onSelect = onVideoScaling,
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(title, color = LocalWatchioColors.current.textPrimary, fontWeight = FontWeight.Bold)
}

@Composable
private fun BooleanSettingCard(title: String, description: String, checked: Boolean, onClick: () -> Unit) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        contentDescription = title,
        accent = if (checked) LocalWatchioColors.current.liveTvAccent else LocalWatchioColors.current.focusGlow,
        selected = checked,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("player-setting-${title.lowercase().replace(" ", "-")}"),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text("$title: ${if (checked) "ON" else "OFF"}", color = colors.textPrimary, fontWeight = FontWeight.Bold)
            Text(description, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun <T> ChoiceSettingRow(
    title: String,
    tagPrefix: String,
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    tagValue: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    val colors = LocalWatchioColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, color = if (enabled) colors.textPrimary else colors.textMuted, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            choices.forEach { choice ->
                WatchioChoiceCard(
                    label = label(choice),
                    selected = choice == selected,
                    enabled = enabled,
                    onClick = { onSelect(choice) },
                    modifier = Modifier.weight(1f).testTag("$tagPrefix-${tagValue(choice)}"),
                )
            }
        }
    }
}

@Composable
private fun WatchioChoiceCard(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    WatchioCard(
        contentDescription = "$label ${if (selected) "selected" else "not selected"}",
        accent = if (selected) colors.seriesAccent else colors.focusGlow,
        selected = selected,
        enabled = enabled,
        minWidth = 0.dp,
        minHeight = 54.dp,
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (enabled) colors.textPrimary else colors.textMuted,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EpgSettingsContent(
    state: SettingsUiState,
    onEpgAutoRefresh: (Boolean) -> Unit,
    onEpgRefreshInterval: (EpgRefreshInterval) -> Unit,
    onRefreshEpgNow: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    Text("TV Guide / EPG", color = colors.textPrimary, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        WatchioFocusableCard(
            title = if (state.epgAutoRefreshEnabled) "Selected\nAuto Refresh On" else "Auto Refresh On",
            accent = if (state.epgAutoRefreshEnabled) colors.focusGlow else colors.liveTvAccent,
            onClick = { onEpgAutoRefresh(true) },
            modifier = Modifier.weight(1f),
        )
        WatchioFocusableCard(
            title = if (!state.epgAutoRefreshEnabled) "Selected\nAuto Refresh Off" else "Auto Refresh Off",
            accent = if (!state.epgAutoRefreshEnabled) colors.focusGlow else colors.seriesAccent,
            onClick = { onEpgAutoRefresh(false) },
            modifier = Modifier.weight(1f),
        )
    }
    Text("Refresh interval", color = colors.textMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        EpgRefreshInterval.entries.forEach { interval ->
            val selected = interval == state.epgRefreshInterval
            WatchioFocusableCard(
                title = if (selected) "Selected\n${interval.label}" else interval.label,
                accent = if (selected) colors.focusGlow else colors.liveTvAccent,
                onClick = { onEpgRefreshInterval(interval) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Text("Last successful refresh: ${formatEpgRefreshTime(state.lastSuccessfulEpgRefreshEpochMs)}", color = colors.textSecondary)
    state.epgRefreshMessage?.let { Text(it, color = colors.textMuted) }
    WatchioFocusableCard(
        title = if (state.epgRefreshing) "Refreshing..." else "Refresh Now",
        accent = colors.liveTvAccent,
        onClick = onRefreshEpgNow,
    )
}

private data class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: HomeIconKind,
    val accent: Color,
    val onClick: () -> Unit,
    val testTag: String,
)

private fun formatEpgRefreshTime(epochMs: Long?): String {
    epochMs ?: return "Never"
    return DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
}

private fun InputMode.label(): String = when (this) {
    InputMode.Auto -> "Auto"
    InputMode.TvRemote -> "TV Remote"
    InputMode.Touch -> "Touch"
}

private fun StreamFormat.label(): String = when (this) {
    StreamFormat.Auto -> "AUTO"
    StreamFormat.Ts -> "TS"
    StreamFormat.Hls -> "HLS"
}

private fun providersFactory(container: AppContainer): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProviderManagementViewModel(
                providerRepository = container.providerRepository,
                settingsRepository = container.settingsRepository,
                xtreamRepository = container.xtreamRepository,
                m3uRepository = container.m3uRepository,
            ) as T
        }
    }

private fun settingsFactory(container: AppContainer): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(container.settingsRepository, container.epgRefreshCoordinator) as T
        }
    }

private fun liveTvFactory(container: AppContainer): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LiveTvViewModel(
                liveTvRepository = container.liveTvRepository,
                favoritesRepository = container.favoritesRepository,
                historyRepository = container.historyRepository,
                settingsRepository = container.settingsRepository,
                epgRefreshCoordinator = container.epgRefreshCoordinator,
                playerManager = container.playerManager,
                clock = SystemWatchioClock,
            ) as T
        }
    }

private fun formatEpochToTime(epochMs: Long?): String? {
    if (epochMs == null || epochMs <= 0L) return null
    return try {
        DateTimeFormatter.ofPattern("HH:mm")
            .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
    } catch (_: Exception) {
        null
    }
}
