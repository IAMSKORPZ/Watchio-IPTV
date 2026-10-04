package com.iamskorpz.watchioiptv.feature.sports

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iamskorpz.watchioiptv.data.live.LiveTvChannel
import com.iamskorpz.watchioiptv.ui.components.WatchioButton
import com.iamskorpz.watchioiptv.ui.components.WatchioButtonVariant
import com.iamskorpz.watchioiptv.ui.components.WatchioCard
import com.iamskorpz.watchioiptv.ui.components.WatchioLoading
import com.iamskorpz.watchioiptv.ui.components.WatchioChip
import com.iamskorpz.watchioiptv.ui.components.WatchioPageHeader
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioColors
import com.iamskorpz.watchioiptv.ui.theme.watchioScreenBackgroundColor
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import com.iamskorpz.watchioiptv.feature.sports.v2.MatchChannelConfidence
import com.iamskorpz.watchioiptv.feature.sports.v2.SportsBroadcast

@Composable
fun SportsScreen(
    state: SportsUiState,
    onPreviousDay: () -> Unit,
    onToday: () -> Unit,
    onNextDay: () -> Unit,
    onRetry: () -> Unit,
    onWatch: (SportsFixture) -> Unit,
    onCloseCandidates: () -> Unit,
    onPlay: (LiveTvChannel) -> Unit,
    onConfigureApiKey: () -> Unit,
    onGetFreeApiKey: () -> Unit,
    onBack: () -> Unit,
    onSelectDate: (LocalDate) -> Unit = {},
    onToggleReminder: (SportsFixture) -> Unit = {},
    onDismissAlert: () -> Unit = {},
    onWatchAlert: () -> Unit = {},
) {
    val colors = LocalWatchioColors.current
    var selectedCompetition by remember(state.selectedDate) { mutableStateOf<String?>(null) }
    var calendarOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val requestReminder: (SportsFixture) -> Unit = { fixture ->
        onToggleReminder(fixture)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    BackHandler(onBack = when {
        calendarOpen -> ({ calendarOpen = false })
        state.selectedFixture != null -> onCloseCandidates
        state.matchAlert != null -> onDismissAlert
        else -> onBack
    })
    Box(Modifier.fillMaxSize().background(watchioScreenBackgroundColor()).testTag("sports-background")) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp).testTag("sports-screen")) {
        WatchioPageHeader(title = "SPORTS", onBack = onBack, testTagPrefix = "sports")
        Spacer(Modifier.height(8.dp))
        SportsDateNavigator(state.selectedDate, onPreviousDay, { calendarOpen = true }, onNextDay)
        if (state.refreshing) {
            Spacer(Modifier.height(4.dp))
            Text("Refreshing sports data…", color = colors.textSecondary, modifier = Modifier.testTag("sports-refreshing"))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().weight(1f).testTag("sports-content")) {
            when (val load = state.loadState) {
                SportsLoadState.Loading -> WatchioLoading("Loading fixtures…", Modifier.align(Alignment.Center).testTag("sports-loading"))
                SportsLoadState.SetupRequired -> FootballDataSetupState(
                    title = "Football Data setup required",
                    detail = "Football fixtures require a free football-data.org API key.",
                    onConfigureApiKey = onConfigureApiKey,
                    onGetFreeApiKey = onGetFreeApiKey,
                    modifier = Modifier.align(Alignment.Center),
                )
                SportsLoadState.CredentialNeedsAttention -> FootballDataSetupState(
                    title = "API key needs attention",
                    detail = "Update your football-data.org API key to continue loading fixtures.",
                    onConfigureApiKey = onConfigureApiKey,
                    onGetFreeApiKey = onGetFreeApiKey,
                    modifier = Modifier.align(Alignment.Center),
                )
                is SportsLoadState.Error -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(load.message, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    load.detail?.let { Spacer(Modifier.height(6.dp)); Text(it, color = colors.textSecondary) }
                    Spacer(Modifier.height(12.dp))
                    WatchioButton(if (load.retryEnabled) "Retry" else "Try again shortly", onRetry, enabled = load.retryEnabled, variant = WatchioButtonVariant.Secondary)
                }
                is SportsLoadState.Ready -> if (load.schedule.competitions.isEmpty()) {
                    Column(Modifier.align(Alignment.Center).testTag("sports-empty"), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NO MATCHES ON THIS DATE", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Try another date using the controls above.", color = colors.textSecondary)
                    }
                } else Column(Modifier.fillMaxSize()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().testTag("sports-competition-filters")) {
                        item { WatchioChip("ALL", selectedCompetition == null, { selectedCompetition = null }, Modifier.testTag("sports-filter-all")) }
                        items(load.schedule.competitions, key = { it.id }) { competition ->
                            WatchioChip(competition.name, selectedCompetition == competition.id, { selectedCompetition = competition.id }, Modifier.testTag("sports-filter-${competition.id}"))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize().testTag("sports-fixtures")) {
                    load.schedule.competitions.filter { selectedCompetition == null || it.id == selectedCompetition }.forEach { competition ->
                        item(key = "competition-${competition.id}") {
                            Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
                                Text(competition.name, color = colors.liveTvAccent, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                HorizontalDivider(color = colors.surfaceElevated)
                            }
                        }
                        items(competition.fixtures, key = { "fixture-${it.id}" }) { fixture ->
                            FixtureCard(
                                fixture,
                                load.broadcasts[fixture.id].orEmpty(),
                                fixture.id in load.broadcastUnavailable,
                                fixture.reminderKey() in state.reminderKeys,
                                onWatch,
                                requestReminder,
                            )
                        }
                    }
                    }
                }
            }
        }
    }
    }
    if (calendarOpen) SportsCalendarDialog(state.selectedDate, { calendarOpen = false }, { calendarOpen = false; onSelectDate(it) })
    if (state.selectedFixture != null) CandidateDialog(state, onCloseCandidates, onPlay)
    state.matchAlert?.let { MatchAlertDialog(it, state.alertCandidates, state.alertCandidatesLoading, onDismissAlert, onWatchAlert) }
}

@Composable
private fun FootballDataSetupState(
    title: String,
    detail: String,
    onConfigureApiKey: () -> Unit,
    onGetFreeApiKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWatchioColors.current
    Column(
        modifier = modifier.testTag("sports-api-setup"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold)
        Text(detail, color = colors.textSecondary)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WatchioButton("Get Free API Key", onGetFreeApiKey, variant = WatchioButtonVariant.Secondary)
            WatchioButton("Enter API Key", onConfigureApiKey)
        }
        Text("Data provided by football-data.org", color = colors.textSecondary)
    }
}

@Composable
private fun SportsDateNavigator(
    date: LocalDate,
    onPreviousDay: () -> Unit,
    onOpenCalendar: () -> Unit,
    onNextDay: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("sports-date-navigator")) {
        val compact = maxWidth < 600.dp
        if (compact) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                DateControls(onPreviousDay, onOpenCalendar, onNextDay, date)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                DateControls(onPreviousDay, onOpenCalendar, onNextDay, date)
            }
        }
    }
}

@Composable
private fun DateControls(
    onPreviousDay: () -> Unit,
    onOpenCalendar: () -> Unit,
    onNextDay: () -> Unit,
    date: LocalDate? = null,
) {
    val colors = LocalWatchioColors.current
    val previousFocus = remember { FocusRequester() }
    val todayFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current
    var initialFocusRequested by remember { mutableStateOf(false) }
    LaunchedEffect(inputModeManager.inputMode) {
        if (!initialFocusRequested && inputModeManager.inputMode == InputMode.Keyboard) {
            initialFocusRequested = todayFocus.requestFocus()
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        WatchioButton("‹", onPreviousDay, Modifier.width(52.dp).focusRequester(previousFocus).focusProperties { right = todayFocus }.testTag("sports-previous").semantics { contentDescription = "Previous day" }, WatchioButtonVariant.Ghost)
        val label = if (date == LocalDate.now()) "TODAY • ${date.format(DateTimeFormatter.ofPattern("d MMM"))}" else date?.format(DateTimeFormatter.ofPattern("EEE • d MMM")) ?: "CHOOSE DATE"
        WatchioButton(label, onOpenCalendar, Modifier.width(190.dp).focusRequester(todayFocus).focusProperties { left = previousFocus; right = nextFocus }.testTag("sports-today").semantics { contentDescription = "Choose sports date" }, WatchioButtonVariant.Secondary)
        WatchioButton("›", onNextDay, Modifier.width(52.dp).focusRequester(nextFocus).focusProperties { left = todayFocus }.testTag("sports-next").semantics { contentDescription = "Next day" }, WatchioButtonVariant.Ghost)
    }
}

@Composable private fun FixtureCard(
    fixture: SportsFixture,
    broadcasts: List<SportsBroadcast>,
    broadcastUnavailable: Boolean,
    reminderOn: Boolean,
    onWatch: (SportsFixture) -> Unit,
    onToggleReminder: (SportsFixture) -> Unit,
) {
    val colors = LocalWatchioColors.current
    val ukBroadcasts = broadcasts.filter { it.countryOrRegion == "GB" || it.countryOrRegion?.startsWith("GB-") == true }.take(3)
    WatchioCard(modifier = Modifier.fillMaxWidth().testTag("fixture-${fixture.id}"), minHeight = 88.dp, onClick = { onWatch(fixture) }, contentDescription = "Open ${fixture.homeTeam} versus ${fixture.awayTeam}") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TeamLogo(fixture.homeLogoUrl, fixture.homeTeam)
                Text(fixture.homeTeam, color = colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (fixture.status == SportsFixtureStatus.Live) "LIVE${fixture.minute?.let { " • $it'" }.orEmpty()}" else formatKickoff(fixture.kickoffUtc), color = colors.liveTvAccent, fontWeight = FontWeight.Bold)
                    Text(fixtureSummary(fixture), color = colors.textSecondary)
                }
                Text(fixture.awayTeam, color = colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                TeamLogo(fixture.awayLogoUrl, fixture.awayTeam)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        ukBroadcasts.isNotEmpty() -> ukBroadcasts.joinToString(" • ") { it.displayName }
                        broadcastUnavailable -> "Broadcast information unavailable"
                        else -> "Checking broadcast information…"
                    },
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
                if (fixture.status == SportsFixtureStatus.Scheduled) {
                    WatchioButton(
                        if (reminderOn) "NOTIFICATION ON" else "NOTIFY ME",
                        { onToggleReminder(fixture) },
                        modifier = Modifier.width(150.dp).testTag("fixture-reminder-${fixture.id}"),
                        variant = if (reminderOn) WatchioButtonVariant.Primary else WatchioButtonVariant.Secondary,
                    )
                }
                if (ukBroadcasts.isNotEmpty() && fixture.status != SportsFixtureStatus.Finished && fixture.status != SportsFixtureStatus.Cancelled && fixture.status != SportsFixtureStatus.Postponed) {
                    WatchioButton(if (fixture.status == SportsFixtureStatus.Live) "WATCH LIVE" else "FIND CHANNEL", { onWatch(fixture) }, modifier = Modifier.width(126.dp), variant = WatchioButtonVariant.CompactAction)
                }
            }
        }
    }
}

@Composable private fun TeamLogo(url: String?, name: String) {
    val colors = LocalWatchioColors.current
    if (url.isNullOrBlank()) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(colors.surfaceElevated), contentAlignment = Alignment.Center) {
            Text(name.take(1), color = colors.textPrimary, fontWeight = FontWeight.Bold)
        }
    } else AsyncImage(url, name, Modifier.size(34.dp).clip(CircleShape), contentScale = ContentScale.Fit)
}

@Composable
private fun SportsCalendarDialog(selectedDate: LocalDate, onDismiss: () -> Unit, onSelect: (LocalDate) -> Unit) {
    val colors = LocalWatchioColors.current
    var month by remember(selectedDate) { mutableStateOf(YearMonth.from(selectedDate)) }
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val days = List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    val cells = days + List((7 - days.size % 7) % 7) { null }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 720.dp).clip(RoundedCornerShape(24.dp)).background(colors.dialogSurface).padding(18.dp).testTag("sports-calendar"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                WatchioButton("‹", { month = month.minusMonths(1) }, Modifier.width(52.dp).testTag("sports-calendar-previous-month"), WatchioButtonVariant.Ghost)
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("sports-calendar-month"))
                WatchioButton("›", { month = month.plusMonths(1) }, Modifier.width(52.dp).testTag("sports-calendar-next-month"), WatchioButtonVariant.Ghost)
            }
            Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, color = colors.textSecondary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        if (day == null) Spacer(Modifier.weight(1f).height(42.dp)) else CalendarDayCell(day, day == selectedDate, { onSelect(day) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(day: LocalDate, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalWatchioColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.height(42.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(if (focused) 3.dp else 0.dp, if (focused) colors.focusBorder else Color.Transparent, shape)
            .padding(if (focused) 4.dp else 0.dp)
            .clip(shape)
            .background(if (selected) colors.liveTvAccent else colors.surfaceElevated)
            .clickable(onClick = onClick)
            .testTag("sports-calendar-day-$day"),
        contentAlignment = Alignment.Center,
    ) {
        Text(day.dayOfMonth.toString(), color = if (selected) colors.surfaceBase else colors.textPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MatchAlertDialog(reminder: MatchReminder, candidates: List<SportsChannelCandidate>, loading: Boolean, onDismiss: () -> Unit, onWatch: () -> Unit) {
    val colors = LocalWatchioColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("sports-match-alert"),
        title = { Text("MATCH STARTING SOON", color = colors.liveTvAccent, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(reminder.homeTeam, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                Text("vs", color = colors.textSecondary)
                Text(reminder.awayTeam, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                Text("Kick-off • ${formatKickoff(Instant.ofEpochMilli(reminder.kickoffEpochMs))}", color = colors.textSecondary)
                Text(reminder.competitionName, color = colors.textSecondary)
                when {
                    loading -> Text("Checking your provider…", color = colors.textSecondary)
                    candidates.isEmpty() -> Text("No matching channel currently available", color = colors.textSecondary)
                    else -> Text(candidates.first().channel.name, color = colors.textPrimary)
                }
            }
        },
        confirmButton = {
            if (candidates.isNotEmpty()) WatchioButton(
                if (candidates.first().v2Confidence == MatchChannelConfidence.POSSIBLE) "CHOOSE CHANNEL" else "WATCH NOW",
                onWatch,
                modifier = Modifier.testTag("sports-alert-watch"),
            )
        },
        dismissButton = { WatchioButton("DISMISS", onDismiss, modifier = Modifier.testTag("sports-alert-dismiss"), variant = WatchioButtonVariant.Ghost) },
    )
}

@Composable private fun CandidateDialog(state: SportsUiState, onClose: () -> Unit, onPlay: (LiveTvChannel) -> Unit) {
    val colors = LocalWatchioColors.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Available channels") },
        text = {
            when {
                state.candidatesLoading -> CircularProgressIndicator()
                state.candidateError != null -> Text(state.candidateError)
                state.candidates.isEmpty() -> Text("No matching channel found for this provider.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("sports-candidates")) {
                    item { Text("Watch on", fontWeight = FontWeight.Bold) }
                    items(state.candidates, key = { "candidate-${it.channel.providerId.value}-${it.channel.id}" }) { CandidateRow(it, onPlay) }
                }
            }
        },
        confirmButton = { WatchioButton("Close", onClose, variant = WatchioButtonVariant.Ghost) },
    )
}

@Composable private fun CandidateRow(candidate: SportsChannelCandidate, onPlay: (LiveTvChannel) -> Unit) {
    val colors = LocalWatchioColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(candidate.channel.name, color = colors.textPrimary, fontWeight = FontWeight.Bold)
            Text("${candidate.v2Confidence?.name ?: candidate.confidence.name} • ${candidate.broadcasterName.orEmpty()}", color = colors.textSecondary)
            candidate.matchedProgrammeTitle?.let { Text(it, color = colors.textSecondary, maxLines = 1) }
        }
        WatchioButton(if (candidate.v2Confidence == MatchChannelConfidence.POSSIBLE) "TRY" else "WATCH LIVE", { onPlay(candidate.channel) }, modifier = Modifier.width(116.dp), variant = WatchioButtonVariant.CompactAction)
    }
}

private fun formatKickoff(instant: Instant) = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(instant)
private fun fixtureSummary(fixture: SportsFixture) = when (fixture.status) {
    SportsFixtureStatus.Live -> "LIVE  ${fixture.homeScore ?: 0}–${fixture.awayScore ?: 0}"
    SportsFixtureStatus.Finished -> "${fixture.homeScore ?: 0}–${fixture.awayScore ?: 0}  Finished"
    SportsFixtureStatus.Postponed -> "Postponed"
    SportsFixtureStatus.Cancelled -> "Cancelled"
    SportsFixtureStatus.Scheduled -> "vs"
}
