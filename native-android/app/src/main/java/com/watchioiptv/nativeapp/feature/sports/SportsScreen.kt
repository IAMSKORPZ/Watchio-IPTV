package com.watchioiptv.nativeapp.feature.sports

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
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
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.watchioiptv.nativeapp.data.live.LiveTvChannel
import com.watchioiptv.nativeapp.ui.components.WatchioButton
import com.watchioiptv.nativeapp.ui.components.WatchioButtonVariant
import com.watchioiptv.nativeapp.ui.components.WatchioCard
import com.watchioiptv.nativeapp.ui.components.WatchioLoading
import com.watchioiptv.nativeapp.ui.components.WatchioPageHeader
import com.watchioiptv.nativeapp.ui.theme.LocalWatchioColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
) {
    val colors = LocalWatchioColors.current
    BackHandler(onBack = if (state.selectedFixture != null) onCloseCandidates else onBack)
    Box(Modifier.fillMaxSize().background(colors.surfaceBase).testTag("sports-background")) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp).testTag("sports-screen")) {
        WatchioPageHeader(title = "SPORTS", onBack = onBack, testTagPrefix = "sports")
        Spacer(Modifier.height(8.dp))
        SportsDateNavigator(state.selectedDate, onPreviousDay, onToday, onNextDay)
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
                        Text(if (state.selectedDate == LocalDate.now()) "No football fixtures today" else "No football fixtures on this date", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Try another date using the controls above.", color = colors.textSecondary)
                    }
                } else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize().testTag("sports-fixtures")) {
                    load.schedule.competitions.forEach { competition ->
                        item(key = "competition-${competition.id}") {
                            Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
                                Text(competition.name, color = colors.liveTvAccent, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                HorizontalDivider(color = colors.surfaceElevated)
                            }
                        }
                        items(competition.fixtures, key = { "fixture-${it.id}" }) { fixture -> FixtureRow(fixture, onWatch) }
                    }
                }
            }
        }
    }
    }
    if (state.selectedFixture != null) CandidateDialog(state, onCloseCandidates, onPlay)
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
    onToday: () -> Unit,
    onNextDay: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("sports-date-navigator")) {
        val compact = maxWidth < 600.dp
        if (compact) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")), color = colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("sports-date"))
                Spacer(Modifier.height(6.dp))
                DateControls(onPreviousDay, onToday, onNextDay)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                DateControls(onPreviousDay, onToday, onNextDay, date)
            }
        }
    }
}

@Composable
private fun DateControls(
    onPreviousDay: () -> Unit,
    onToday: () -> Unit,
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
        if (date != null) Text(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")), color = colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 220.dp).testTag("sports-date"), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        WatchioButton("TODAY", onToday, Modifier.width(96.dp).focusRequester(todayFocus).focusProperties { left = previousFocus; right = nextFocus }.testTag("sports-today"), WatchioButtonVariant.Secondary)
        WatchioButton("›", onNextDay, Modifier.width(52.dp).focusRequester(nextFocus).focusProperties { left = todayFocus }.testTag("sports-next").semantics { contentDescription = "Next day" }, WatchioButtonVariant.Ghost)
    }
}

@Composable private fun FixtureRow(fixture: SportsFixture, onWatch: (SportsFixture) -> Unit) {
    val colors = LocalWatchioColors.current
    WatchioCard(modifier = Modifier.fillMaxWidth().testTag("fixture-${fixture.id}"), minHeight = 56.dp, onClick = { onWatch(fixture) }, contentDescription = "Watch ${fixture.homeTeam} versus ${fixture.awayTeam}") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(formatKickoff(fixture.kickoffUtc), color = colors.textSecondary, modifier = Modifier.widthIn(min = 52.dp))
            Text(fixture.homeTeam, color = colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            Text(fixtureSummary(fixture), color = colors.textSecondary, modifier = Modifier.widthIn(min = 58.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(fixture.awayTeam, color = colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            WatchioButton("WATCH", { onWatch(fixture) }, modifier = Modifier.width(96.dp), variant = WatchioButtonVariant.CompactAction)
        }
    }
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
                state.candidates.isEmpty() -> Text("No matching TV guide entries were found. No sports channels are available in this provider.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("sports-candidates")) {
                    val likely = state.candidates.filter { it.confidence != SportsMatchConfidence.Low }
                    val other = state.candidates.filter { it.confidence == SportsMatchConfidence.Low }
                    if (likely.isNotEmpty()) item { Text("Likely channels", fontWeight = FontWeight.Bold) }
                    items(likely, key = { "likely-${it.channel.providerId.value}-${it.channel.id}" }) { CandidateRow(it, onPlay) }
                    if (other.isNotEmpty()) item { Text("Other sports channels", color = colors.textSecondary, fontWeight = FontWeight.Bold) }
                    items(other, key = { "other-${it.channel.providerId.value}-${it.channel.id}" }) { CandidateRow(it, onPlay) }
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
            candidate.matchedProgrammeTitle?.let { Text(it, color = colors.textSecondary) }
        }
        WatchioButton("PLAY", { onPlay(candidate.channel) }, modifier = Modifier.width(88.dp), variant = WatchioButtonVariant.CompactAction)
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
