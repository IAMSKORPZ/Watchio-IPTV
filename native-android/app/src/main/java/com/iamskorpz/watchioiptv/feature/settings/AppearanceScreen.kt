package com.iamskorpz.watchioiptv.feature.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.iamskorpz.watchioiptv.ui.theme.AnimationChoice
import com.iamskorpz.watchioiptv.ui.theme.BackgroundScale
import com.iamskorpz.watchioiptv.ui.theme.BackgroundAlignment
import com.iamskorpz.watchioiptv.ui.theme.CardSizeChoice
import com.iamskorpz.watchioiptv.ui.theme.DensityChoice
import com.iamskorpz.watchioiptv.ui.theme.FontWeightChoice
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioColors
import com.iamskorpz.watchioiptv.ui.theme.WatchioThemeDefinition
import com.iamskorpz.watchioiptv.ui.theme.WatchioBuiltInThemes
import com.iamskorpz.watchioiptv.ui.theme.hasLowContrast
import com.iamskorpz.watchioiptv.ui.theme.editorHeaderFocusPalette
import com.iamskorpz.watchioiptv.ui.theme.parseHexColor
import com.iamskorpz.watchioiptv.ui.theme.toComposeColor
import com.iamskorpz.watchioiptv.ui.theme.toHexColor

private val LocalPreviewTargetSetter = staticCompositionLocalOf<(AppearancePreviewTarget) -> Unit> { {} }

@Composable
fun AppearanceScreen(
    state: AppearanceEditorState,
    onBack: () -> Unit,
    onUpdate: ((WatchioThemeDefinition) -> WatchioThemeDefinition) -> Unit,
    onSelect: (String) -> Unit,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    onResetSection: (AppearanceSection) -> Unit,
    onResetAll: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(AppearanceSection.Presets) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var showReset by rememberSaveable { mutableStateOf(false) }
    var previewTarget by rememberSaveable { mutableStateOf(section.defaultPreviewTarget()) }
    val leave = { if (state.dirty) showDiscard = true else onBack() }
    BackHandler(onBack = leave)
    val appColors = LocalWatchioColors.current
    val controlsScroll = rememberScrollState()
    val backFocus = remember { FocusRequester() }
    val applyFocus = remember { FocusRequester() }
    LaunchedEffect(section) {
        controlsScroll.scrollTo(0)
        previewTarget = section.defaultPreviewTarget()
    }

    BoxWithConstraints(Modifier.fillMaxSize().testTag("appearance-screen")) {
        val tvLayout = maxWidth >= 900.dp
        val compactLandscape = !tvLayout && maxWidth > maxHeight && maxHeight < 600.dp
        CompositionLocalProvider(LocalContentColor provides appColors.textPrimary) {
          Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("APPEARANCE", color = appColors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppearanceHeaderAction(
                        label = "Back",
                        onClick = leave,
                        modifier = Modifier
                            .focusRequester(backFocus)
                            .focusProperties { right = applyFocus }
                            .testTag("appearance-back"),
                    )
                    AppearanceHeaderAction(
                        label = "Apply",
                        onClick = onApply,
                        enabled = state.dirty && !state.saving,
                        modifier = Modifier
                            .focusRequester(applyFocus)
                            .focusProperties { left = backFocus }
                            .testTag("appearance-apply"),
                    )
                }
            }
            if (state.draft.hasLowContrast()) {
                Text("Low contrast — some text or focus may be difficult to see.", color = Color(0xFFFFB74D), modifier = Modifier.testTag("appearance-contrast-warning"))
            }
            if (tvLayout) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LazyColumn(Modifier.width(220.dp).testTag("appearance-categories"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AppearanceSection.entries) { item ->
                            CategoryButton(item, section == item) { section = item }
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(controlsScroll).testTag("appearance-controls")) {
                        SectionContent(section, state, onUpdate, onSelect, onResetSection, { showReset = true }, onDuplicate, onRename, onDelete) { previewTarget = it }
                    }
                    AppearancePreview(state.draft, section.previewScene(), previewTarget, Modifier.width(320.dp).testTag("appearance-preview"))
                }
            } else if (compactLandscape) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LazyColumn(
                        modifier = Modifier.width(190.dp).testTag("appearance-categories"),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(AppearanceSection.entries) { item ->
                            CategoryButton(item, section == item) { section = item }
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(controlsScroll).testTag("appearance-controls")) {
                        SectionContent(section, state, onUpdate, onSelect, onResetSection, { showReset = true }, onDuplicate, onRename, onDelete) { previewTarget = it }
                        AppearancePreview(state.draft, section.previewScene(), previewTarget, Modifier.fillMaxWidth().height(190.dp).testTag("appearance-preview"))
                    }
                }
            } else {
                LazyRow(Modifier.fillMaxWidth().testTag("appearance-categories"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AppearanceSection.entries) { item -> CategoryButton(item, section == item) { section = item } }
                }
                AppearancePreview(state.draft, section.previewScene(), previewTarget, Modifier.fillMaxWidth().height(220.dp).testTag("appearance-preview"))
                Column(Modifier.weight(1f).verticalScroll(controlsScroll).testTag("appearance-controls")) {
                    SectionContent(section, state, onUpdate, onSelect, onResetSection, { showReset = true }, onDuplicate, onRename, onDelete) { previewTarget = it }
                }
            }
          }
        }
    }
    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your saved active theme will remain unchanged.") },
            confirmButton = { Button(onClick = { onDiscard(); showDiscard = false; onBack() }) { Text("Discard") } },
            dismissButton = { OutlinedButton(onClick = { showDiscard = false }) { Text("Keep editing") } },
        )
    }
    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset entire theme?") },
            text = { Text("Draft will return to immutable Watchio Default. Unrelated settings are not changed.") },
            confirmButton = { Button(onClick = { onResetAll(); showReset = false }) { Text("Reset") } },
            dismissButton = { OutlinedButton(onClick = { showReset = false }) { Text("Cancel") } },
        )
    }
}

/** Critical editor actions use fixed accessible focus colours so an unsafe draft cannot hide them. */
@Composable
private fun AppearanceHeaderAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val safeFocus = editorHeaderFocusPalette()
    val focusedBackground = safeFocus.background.toComposeColor()
    val focusedContent = safeFocus.content.toComposeColor()
    val focusedOutline = safeFocus.outline.toComposeColor()
    val contentColor = when {
        !enabled -> LocalWatchioColors.current.textMuted.copy(alpha = 0.55f)
        focused -> focusedContent
        else -> LocalWatchioColors.current.textPrimary
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .widthIn(min = 80.dp)
            .heightIn(min = 48.dp)
            .scale(if (focused && enabled) 1.04f else 1f)
            .shadow(
                elevation = if (focused && enabled) 8.dp else 0.dp,
                shape = shape,
                ambientColor = focusedOutline,
                spotColor = focusedOutline,
            )
            .background(if (focused && enabled) focusedBackground else Color.Transparent, shape)
            .border(
                width = if (focused && enabled) 4.dp else 1.dp,
                color = if (focused && enabled) focusedOutline else LocalWatchioColors.current.focusBorder,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
                stateDescription = when {
                    !enabled -> "Disabled"
                    focused -> "Focused"
                    else -> "Not focused"
                }
            }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = contentColor,
            fontWeight = if (focused && enabled) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun CategoryButton(section: AppearanceSection, selected: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().testTag("appearance-category-${section.name}")
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(section.label) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(section.label) }
}

@Composable
private fun SectionContent(
    section: AppearanceSection,
    state: AppearanceEditorState,
    onUpdate: ((WatchioThemeDefinition) -> WatchioThemeDefinition) -> Unit,
    onSelect: (String) -> Unit,
    onResetSection: (AppearanceSection) -> Unit,
    onResetAll: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onPreviewTarget: (AppearancePreviewTarget) -> Unit,
) {
    val theme = state.draft
    CompositionLocalProvider(LocalPreviewTargetSetter provides onPreviewTarget) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(section.label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        when (section) {
            AppearanceSection.Presets -> SavedThemes(state, onSelect, onDuplicate, onRename, onDelete)
            AppearanceSection.Colours -> ColourEditor(theme, onUpdate)
            AppearanceSection.Background -> BackgroundEditor(theme, onUpdate)
            AppearanceSection.Text -> {
                BoundedSlider("Title size", theme.typography.sectionSp, 18f..32f) { value -> onUpdate { it.copy(typography = it.typography.copy(sectionSp = value)) } }
                BoundedSlider("Body size", theme.typography.bodySp, 12f..22f) { value -> onUpdate { it.copy(typography = it.typography.copy(bodySp = value)) } }
                BoundedSlider("Metadata size", theme.typography.metadataSp, 10f..18f) { value -> onUpdate { it.copy(typography = it.typography.copy(metadataSp = value)) } }
                BoundedSlider("Button text size", theme.typography.buttonSp, 12f..22f) { value -> onUpdate { it.copy(typography = it.typography.copy(buttonSp = value)) } }
                BoundedSlider("Navigation text size", theme.typography.navigationSp, 10f..20f) { value -> onUpdate { it.copy(typography = it.typography.copy(navigationSp = value)) } }
                ChoiceRow("Title weight", FontWeightChoice.entries, theme.typography.sectionWeight) { value -> onUpdate { it.copy(typography = it.typography.copy(sectionWeight = value)) } }
                ChoiceRow("Body weight", FontWeightChoice.entries, theme.typography.bodyWeight) { value -> onUpdate { it.copy(typography = it.typography.copy(bodyWeight = value)) } }
                ChoiceRow("Button weight", FontWeightChoice.entries, theme.typography.buttonWeight) { value -> onUpdate { it.copy(typography = it.typography.copy(buttonWeight = value)) } }
            }
            AppearanceSection.Panels -> {
                BoundedSlider("Panel opacity", theme.surfaces.primaryOpacity, 0.2f..1f) { value -> onUpdate { it.copy(surfaces = it.surfaces.copy(primaryOpacity = value)) } }
                BoundedSlider("Secondary panel opacity", theme.surfaces.secondaryOpacity, 0.2f..1f) { value -> onUpdate { it.copy(surfaces = it.surfaces.copy(secondaryOpacity = value)) } }
                BoundedSlider("Dialog opacity", theme.surfaces.dialogOpacity, 0.4f..1f) { value -> onUpdate { it.copy(surfaces = it.surfaces.copy(dialogOpacity = value)) } }
                BoundedSlider("Header opacity", theme.surfaces.headerOpacity, 0.2f..1f) { value -> onUpdate { it.copy(surfaces = it.surfaces.copy(headerOpacity = value)) } }
                BoundedSlider("Navigation opacity", theme.surfaces.navigationOpacity, 0.2f..1f) { value -> onUpdate { it.copy(surfaces = it.surfaces.copy(navigationOpacity = value)) } }
                BoundedSlider("Panel corner radius", theme.surfaces.cornerRadiusDp, 0f..32f) { value -> onUpdate { it.copy(surfaces = it.surfaces.copy(cornerRadiusDp = value)) } }
                BoundedSlider("Panel outline", theme.surfaces.outlineWidthDp, 0f..6f) { value -> onUpdate { it.copy(surfaces = it.surfaces.copy(outlineWidthDp = value)) } }
            }
            AppearanceSection.Cards -> {
                BoundedSlider("Card opacity", theme.cards.opacity, 0.2f..1f) { value -> onUpdate { it.copy(cards = it.cards.copy(opacity = value)) } }
                BoundedSlider("Card corner radius", theme.cards.cornerRadiusDp, 0f..32f) { value -> onUpdate { it.copy(cards = it.cards.copy(cornerRadiusDp = value)) } }
                BoundedSlider("Card outline", theme.cards.outlineWidthDp, 0f..6f) { value -> onUpdate { it.copy(cards = it.cards.copy(outlineWidthDp = value)) } }
                BoundedSlider("Poster corner radius", theme.cards.posterCornerRadiusDp, 0f..32f) { value -> onUpdate { it.copy(cards = it.cards.copy(posterCornerRadiusDp = value)) } }
                BoundedSlider("Poster overlay opacity", theme.cards.posterOverlayOpacity, 0f..1f) { value -> onUpdate { it.copy(cards = it.cards.copy(posterOverlayOpacity = value)) } }
            }
            AppearanceSection.Controls -> {
                BoundedSlider("Control corner radius", theme.controls.cornerRadiusDp, 0f..32f) { value -> onUpdate { it.copy(controls = it.controls.copy(cornerRadiusDp = value)) } }
                BoundedSlider("Control outline", theme.controls.outlineWidthDp, 0f..6f) { value -> onUpdate { it.copy(controls = it.controls.copy(outlineWidthDp = value)) } }
                BoundedSlider("Disabled opacity", theme.controls.disabledOpacity, 0.2f..0.8f) { value -> onUpdate { it.copy(controls = it.controls.copy(disabledOpacity = value)) } }
            }
            AppearanceSection.Navigation -> ToggleChoice("Navigation labels", theme.navigation.showLabels, onUpdate) { definition, value -> definition.copy(navigation = definition.navigation.copy(showLabels = value)) }
            AppearanceSection.TvFocus -> {
                BoundedSlider("Focus outline", theme.focus.outlineWidthDp, 1f..8f) { value -> onUpdate { it.copy(focus = it.focus.copy(outlineWidthDp = value)) } }
                BoundedSlider("Glow intensity", theme.focus.glowIntensity, 0f..1f) { value -> onUpdate { it.copy(focus = it.focus.copy(glowIntensity = value)) } }
                BoundedSlider("Focus scale", theme.focus.scale, 1f..1.1f) { value -> onUpdate { it.copy(focus = it.focus.copy(scale = value)) } }
            }
            AppearanceSection.Layout -> {
                ChoiceRow("Density", DensityChoice.entries, theme.layout.density) { value -> onUpdate { it.copy(layout = it.layout.copy(density = value)) } }
                ChoiceRow("Card size", CardSizeChoice.entries, theme.layout.cardSize) { value -> onUpdate { it.copy(layout = it.layout.copy(cardSize = value)) } }
                BoundedSlider("Spacing", theme.layout.spacingScale, 0.8f..1.3f) { value -> onUpdate { it.copy(layout = it.layout.copy(spacingScale = value)) } }
                BoundedSlider("UI scale", theme.layout.uiScale, 0.9f..1.15f) { value -> onUpdate { it.copy(layout = it.layout.copy(uiScale = value)) } }
            }
            AppearanceSection.Effects -> {
                ChoiceRow("Animations", AnimationChoice.entries, theme.effects.animations) { value -> onUpdate { it.copy(effects = it.effects.copy(animations = value)) } }
                BoundedSlider("Elevation", theme.effects.elevationDp, 0f..16f) { value -> onUpdate { it.copy(effects = it.effects.copy(elevationDp = value)) } }
                BoundedSlider("Transition intensity", theme.effects.transitionIntensity, 0f..1f) { value -> onUpdate { it.copy(effects = it.effects.copy(transitionIntensity = value)) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onResetSection(section) }, modifier = Modifier.testTag("appearance-reset-section")) { Text("Reset section") }
            Button(onClick = onResetAll, modifier = Modifier.testTag("appearance-reset-all")) { Text("Reset all") }
        }
        Spacer(Modifier.height(24.dp))
      }
    }
}

@Composable
private fun SavedThemes(state: AppearanceEditorState, onSelect: (String) -> Unit, onDuplicate: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var name by remember(state.draft.id, state.draft.name) { mutableStateOf(state.draft.name) }
    val themes = WatchioBuiltInThemes.All + state.library.themes
    themes.forEach { theme ->
        val selected = theme.id == state.draft.id
        PresetCard(theme, selected) { onSelect(theme.id) }
    }
    OutlinedButton(onClick = onDuplicate, modifier = Modifier.testTag("appearance-duplicate")) { Text("Duplicate") }
    if (!state.draft.isBuiltIn) {
        OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Theme name") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onRename(name) }) { Text("Rename") }
            OutlinedButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun PresetCard(theme: WatchioThemeDefinition, selected: Boolean, onClick: () -> Unit) {
    val c = theme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().background(c.appBackground.toComposeColor(), shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) c.focusOutline.toComposeColor() else c.cardOutline.toComposeColor(), shape)
            .onFocusChanged { if (it.isFocused) onClick() }.clickable(onClick = onClick).focusable().padding(12.dp)
            .testTag("appearance-preset-${theme.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(theme.name, color = c.primaryText.toComposeColor(), fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(c.primaryPanel, c.secondaryPanel, c.accent, c.cardBackground, c.focusOutline).forEach { colour ->
                Box(Modifier.weight(1f).height(28.dp).background(colour.toComposeColor(), RoundedCornerShape(6.dp)))
            }
        }
        Text(if (theme.isBuiltIn) "Built-in preset" else "Custom theme", color = c.secondaryText.toComposeColor(), fontSize = 12.sp)
    }
}

private data class ColourField(val label: String, val get: (WatchioThemeDefinition) -> Long, val set: (WatchioThemeDefinition, Long) -> WatchioThemeDefinition)

@Composable
private fun ColourEditor(theme: WatchioThemeDefinition, onUpdate: ((WatchioThemeDefinition) -> WatchioThemeDefinition) -> Unit) {
    val fields = remember { colourFields() }
    Text("Every app colour is available below. Enter #RRGGBB or #AARRGGBB.")
    fields.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { field ->
                Box(Modifier.weight(1f)) { ColourSetting(field, theme, onUpdate) }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ColourSetting(
    field: ColourField,
    theme: WatchioThemeDefinition,
    onUpdate: ((WatchioThemeDefinition) -> WatchioThemeDefinition) -> Unit,
) {
    val setPreviewTarget = LocalPreviewTargetSetter.current
    var hex by remember(theme.id, field.label) { mutableStateOf(field.get(theme).toHexColor()) }
    var showChart by rememberSaveable(field.label) { mutableStateOf(false) }
    val parsed = parseHexColor(hex)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp)
                .background(field.get(theme).toComposeColor(), RoundedCornerShape(8.dp))
                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                .clickable { setPreviewTarget(previewTargetForSetting(field.label)); showChart = true }
                .focusable()
                .testTag("appearance-colour-chart-${field.label.replace(' ', '-')}")
        )
        OutlinedTextField(
            value = hex,
            onValueChange = { value ->
                hex = value.take(9)
                parseHexColor(hex)?.let { colour -> onUpdate { field.set(it, colour) } }
            },
            label = { Text(field.label) },
            supportingText = if (parsed == null) ({ Text("Invalid HEX") }) else null,
            isError = parsed == null,
            singleLine = true,
            modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) setPreviewTarget(previewTargetForSetting(field.label)) }.testTag(
                if (field.label == "Background") "appearance-hex-input"
                else "appearance-hex-${field.label.replace(' ', '-')}"
            )
        )
    }
    if (showChart) {
        ColourChartDialog(
            label = field.label,
            current = field.get(theme),
            onDismiss = { showChart = false },
            onSelect = { colour ->
                val preservedAlpha = field.get(theme) and 0xFF000000L
                val selected = preservedAlpha or (colour and 0x00FFFFFFL)
                hex = selected.toHexColor()
                onUpdate { field.set(it, selected) }
                showChart = false
            },
        )
    }
}

@Composable
private fun ColourChartDialog(label: String, current: Long, onDismiss: () -> Unit, onSelect: (Long) -> Unit) {
    val chart = remember {
        listOf(
            0xFFFFFFFFL, 0xFFD1D5DBL, 0xFF9CA3AFL, 0xFF6B7280L, 0xFF374151L, 0xFF000000L,
            0xFFFFCDD2L, 0xFFEF5350L, 0xFFC62828L, 0xFFFFE0B2L, 0xFFFF9800L, 0xFFEF6C00L,
            0xFFFFF9C4L, 0xFFFFEB3BL, 0xFFF9A825L, 0xFFC8E6C9L, 0xFF4CAF50L, 0xFF2E7D32L,
            0xFFB2EBF2L, 0xFF00BCD4L, 0xFF00838FL, 0xFFBBDEFBL, 0xFF2196F3L, 0xFF1565C0L,
            0xFFD1C4E9L, 0xFF7E57C2L, 0xFF4527A0L, 0xFFF8BBD0L, 0xFFEC407AL, 0xFFAD1457L,
            0xFFFF3D9AL, 0xFFA855F7L, 0xFF20D9D2L, 0xFF050712L, 0xFF0B1020L, 0xFF101426L,
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose $label") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Current: ${current.toHexColor()}")
                chart.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { colour ->
                            Box(
                                Modifier.size(44.dp)
                                    .background(colour.toComposeColor(), RoundedCornerShape(8.dp))
                                    .border(
                                        if ((current and 0x00FFFFFFL) == (colour and 0x00FFFFFFL)) 4.dp else 1.dp,
                                        Color.White,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable { onSelect(colour) }
                                    .focusable()
                                    .testTag("appearance-chart-${colour.toString(16)}"),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
        modifier = Modifier.testTag("appearance-colour-chart-dialog"),
    )
}

private fun colourFields(): List<ColourField> = listOf(
    ColourField("Background", { it.colors.appBackground }, { t, v -> t.copy(colors = t.colors.copy(appBackground = v)) }),
    ColourField("Accent", { it.colors.accent }, { t, v -> t.copy(colors = t.colors.copy(accent = v)) }),
    ColourField("Primary text", { it.colors.primaryText }, { t, v -> t.copy(colors = t.colors.copy(primaryText = v)) }),
    ColourField("Secondary text", { it.colors.secondaryText }, { t, v -> t.copy(colors = t.colors.copy(secondaryText = v)) }),
    ColourField("Muted text", { it.colors.mutedText }, { t, v -> t.copy(colors = t.colors.copy(mutedText = v)) }),
    ColourField("Primary panel", { it.colors.primaryPanel }, { t, v -> t.copy(colors = t.colors.copy(primaryPanel = v)) }),
    ColourField("Secondary panel", { it.colors.secondaryPanel }, { t, v -> t.copy(colors = t.colors.copy(secondaryPanel = v)) }),
    ColourField("Header", { it.colors.header }, { t, v -> t.copy(colors = t.colors.copy(header = v)) }),
    ColourField("Sidebar", { it.colors.sidebar }, { t, v -> t.copy(colors = t.colors.copy(sidebar = v)) }),
    ColourField("Dialog", { it.colors.dialog }, { t, v -> t.copy(colors = t.colors.copy(dialog = v)) }),
    ColourField("Popup", { it.colors.popup }, { t, v -> t.copy(colors = t.colors.copy(popup = v)) }),
    ColourField("Card", { it.colors.cardBackground }, { t, v -> t.copy(colors = t.colors.copy(cardBackground = v)) }),
    ColourField("Card outline", { it.colors.cardOutline }, { t, v -> t.copy(colors = t.colors.copy(cardOutline = v)) }),
    ColourField("Selected card", { it.colors.selectedCardBackground }, { t, v -> t.copy(colors = t.colors.copy(selectedCardBackground = v)) }),
    ColourField("Selected card outline", { it.colors.selectedCardOutline }, { t, v -> t.copy(colors = t.colors.copy(selectedCardOutline = v)) }),
    ColourField("Button", { it.colors.buttonBackground }, { t, v -> t.copy(colors = t.colors.copy(buttonBackground = v)) }),
    ColourField("Button text", { it.colors.buttonText }, { t, v -> t.copy(colors = t.colors.copy(buttonText = v)) }),
    ColourField("Button outline", { it.colors.buttonOutline }, { t, v -> t.copy(colors = t.colors.copy(buttonOutline = v)) }),
    ColourField("Selected button", { it.colors.selectedButton }, { t, v -> t.copy(colors = t.colors.copy(selectedButton = v)) }),
    ColourField("Selected button text", { it.colors.selectedButtonText }, { t, v -> t.copy(colors = t.colors.copy(selectedButtonText = v)) }),
    ColourField("Navigation", { it.colors.navigationBackground }, { t, v -> t.copy(colors = t.colors.copy(navigationBackground = v)) }),
    ColourField("Navigation icon", { it.colors.navigationIcon }, { t, v -> t.copy(colors = t.colors.copy(navigationIcon = v)) }),
    ColourField("Selected navigation icon", { it.colors.navigationSelectedIcon }, { t, v -> t.copy(colors = t.colors.copy(navigationSelectedIcon = v)) }),
    ColourField("Selected navigation", { it.colors.navigationSelectedBackground }, { t, v -> t.copy(colors = t.colors.copy(navigationSelectedBackground = v)) }),
    ColourField("Badge", { it.colors.badgeBackground }, { t, v -> t.copy(colors = t.colors.copy(badgeBackground = v)) }),
    ColourField("Badge text", { it.colors.badgeText }, { t, v -> t.copy(colors = t.colors.copy(badgeText = v)) }),
    ColourField("Focus outline", { it.colors.focusOutline }, { t, v -> t.copy(colors = t.colors.copy(focusOutline = v)) }),
    ColourField("Focus glow", { it.colors.focusGlow }, { t, v -> t.copy(colors = t.colors.copy(focusGlow = v)) }),
    ColourField("Focused background", { it.colors.focusedBackground }, { t, v -> t.copy(colors = t.colors.copy(focusedBackground = v)) }),
    ColourField("Focused text", { it.colors.focusedText }, { t, v -> t.copy(colors = t.colors.copy(focusedText = v)) }),
    ColourField("Player accent", { it.colors.playerAccent }, { t, v -> t.copy(colors = t.colors.copy(playerAccent = v)) }),
    ColourField("Player controls", { it.colors.playerControl }, { t, v -> t.copy(colors = t.colors.copy(playerControl = v)) }),
    ColourField("Player overlay", { it.colors.playerOverlay }, { t, v -> t.copy(colors = t.colors.copy(playerOverlay = v)) }),
    ColourField("Background overlay", { it.background.overlayColor }, { t, v -> t.copy(background = t.background.copy(overlayColor = v)) }),
)

@Composable
private fun BackgroundEditor(theme: WatchioThemeDefinition, onUpdate: ((WatchioThemeDefinition) -> WatchioThemeDefinition) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onUpdate { it.copy(background = it.background.copy(imageUri = uri.toString())) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.testTag("appearance-background-picker")) { Text("Choose image") }
        OutlinedButton(onClick = { onUpdate { it.copy(background = it.background.copy(imageUri = null)) } }) { Text("Remove image") }
    }
    BoundedSlider("Image opacity", theme.background.imageOpacity, 0f..1f) { value -> onUpdate { it.copy(background = it.background.copy(imageOpacity = value)) } }
    BoundedSlider("Overlay opacity", theme.background.overlayOpacity, 0f..1f) { value -> onUpdate { it.copy(background = it.background.copy(overlayOpacity = value)) } }
    BoundedSlider("Blur", theme.background.blurRadiusDp, 0f..24f) { value -> onUpdate { it.copy(background = it.background.copy(blurRadiusDp = value)) } }
    ChoiceRow("Image scaling", BackgroundScale.entries, theme.background.scale) { value -> onUpdate { it.copy(background = it.background.copy(scale = value)) } }
    ChoiceRow("Image alignment", BackgroundAlignment.entries, theme.background.alignment) { value -> onUpdate { it.copy(background = it.background.copy(alignment = value)) } }
}

@Composable
private fun AppearancePreview(theme: WatchioThemeDefinition, scene: AppearancePreviewScene, target: AppearancePreviewTarget, modifier: Modifier = Modifier) {
    val c = theme.colors
    val panelShape = RoundedCornerShape(theme.surfaces.cornerRadiusDp.dp)
    Box(modifier.background(c.appBackground.toComposeColor(), RoundedCornerShape(12.dp)).padding(10.dp)) {
        theme.background.imageUri?.let { uri ->
            AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize().alpha(theme.background.imageOpacity), contentScale = when (theme.background.scale) { BackgroundScale.Fit -> ContentScale.Fit; BackgroundScale.FillCrop -> ContentScale.Crop; BackgroundScale.Stretch -> ContentScale.FillBounds })
        }
        Column(Modifier.fillMaxSize().testTag("appearance-preview-theme-${theme.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("EDITING  ${target.label}", color = c.accent.toComposeColor(), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("appearance-preview-target"))
            when (scene) {
                AppearancePreviewScene.Text -> Column(Modifier.fillMaxWidth().background(c.primaryPanel.toComposeColor(), panelShape).padding(10.dp)) {
                    Text("Watchio", color = c.primaryText.toComposeColor(), fontSize = theme.typography.displaySp.sp, fontWeight = FontWeight.Bold)
                    Text("Featured Movies", color = c.primaryText.toComposeColor(), fontSize = theme.typography.sectionSp.sp, fontWeight = FontWeight.Bold)
                    Text("Example Movie Title", color = c.primaryText.toComposeColor(), fontSize = theme.typography.bodySp.sp)
                    Text("2026 • Action • 1h 52m", color = c.mutedText.toComposeColor(), fontSize = theme.typography.metadataSp.sp)
                    Text("Example description text…", color = c.secondaryText.toComposeColor(), fontSize = theme.typography.bodySp.sp)
                    Text("WATCH NOW", color = c.selectedButtonText.toComposeColor(), fontSize = theme.typography.buttonSp.sp, modifier = Modifier.background(c.selectedButton.toComposeColor(), RoundedCornerShape(theme.controls.cornerRadiusDp.dp)).padding(6.dp))
                }
                AppearancePreviewScene.Panels -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    MiniSurface("HEADER", c.header, target == AppearancePreviewTarget.Header, theme, opacity = theme.surfaces.headerOpacity)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        MiniSurface("NAVIGATION", c.navigationBackground, target == AppearancePreviewTarget.NavigationPanel, theme, Modifier.weight(0.35f), theme.surfaces.navigationOpacity)
                        Column(Modifier.weight(0.65f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            MiniSurface("PRIMARY PANEL", c.primaryPanel, target == AppearancePreviewTarget.PrimaryPanel, theme, opacity = theme.surfaces.primaryOpacity)
                            MiniSurface("SECONDARY PANEL", c.secondaryPanel, target == AppearancePreviewTarget.SecondaryPanel, theme, opacity = theme.surfaces.secondaryOpacity)
                        }
                    }
                    MiniSurface("DIALOG", c.dialog, target == AppearancePreviewTarget.Dialog, theme, opacity = theme.surfaces.dialogOpacity)
                }
                AppearancePreviewScene.Cards, AppearancePreviewScene.TvFocus, AppearancePreviewScene.Layout -> {
                    val gap = (8f * theme.layout.spacingScale).dp
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                        MiniCard("UNFOCUSED", false, target, theme, Modifier.weight(1f))
                        MiniCard("FOCUSED", true, target, theme, Modifier.weight(1f))
                    }
                }
                AppearancePreviewScene.Controls -> Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    MiniButton("WATCH NOW", true, theme, target == AppearancePreviewTarget.SelectedButton)
                    MiniButton("ADD TO LIST", false, theme, target == AppearancePreviewTarget.Button || target == AppearancePreviewTarget.ButtonText)
                    MiniButton("MORE INFO", false, theme, false, theme.controls.disabledOpacity)
                }
                AppearancePreviewScene.Effects -> Column(Modifier.fillMaxSize().background(c.primaryPanel.toComposeColor(), panelShape).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${theme.effects.animations.name} motion", color = c.primaryText.toComposeColor(), fontWeight = FontWeight.Bold)
                    Text("Elevation ${theme.effects.elevationDp.toInt()} dp", color = c.secondaryText.toComposeColor())
                    Row(horizontalArrangement = Arrangement.spacedBy((6f + theme.effects.transitionIntensity * 8f).dp)) {
                        MiniCard("START", false, target, theme, Modifier.weight(1f))
                        MiniCard("END", true, target, theme, Modifier.weight(1f))
                    }
                }
                AppearancePreviewScene.Navigation -> Row(Modifier.fillMaxSize().background(c.navigationBackground.toComposeColor(), panelShape).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Home", "Live TV", "Movies", "Series", "Search", "Settings").forEachIndexed { index, label ->
                        Column(Modifier.weight(1f).background(if (index == 2) c.navigationSelectedBackground.toComposeColor() else Color.Transparent, RoundedCornerShape(6.dp)).padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (index == 2) "●" else "○", color = if (index == 2) c.navigationSelectedIcon.toComposeColor() else c.navigationIcon.toComposeColor())
                            if (theme.navigation.showLabels) Text(label, color = if (index == 2) c.navigationSelectedIcon.toComposeColor() else c.navigationIcon.toComposeColor(), fontSize = 8.sp)
                            if (index == 4) Text("3", color = c.badgeText.toComposeColor(), fontSize = 8.sp, modifier = Modifier.background(c.badgeBackground.toComposeColor(), RoundedCornerShape(50)).padding(horizontal = 4.dp))
                        }
                    }
                }
                else -> Column(Modifier.fillMaxSize().background(c.primaryPanel.toComposeColor().copy(alpha = theme.surfaces.primaryOpacity), panelShape).then(if (target == AppearancePreviewTarget.PrimaryPanel) Modifier.border(3.dp, c.focusOutline.toComposeColor(), panelShape) else Modifier).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth().background(c.header.toComposeColor(), RoundedCornerShape(6.dp)).then(if (target == AppearancePreviewTarget.Header) Modifier.border(3.dp, c.focusOutline.toComposeColor(), RoundedCornerShape(6.dp)) else Modifier).padding(6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Watchio", color = c.primaryText.toComposeColor(), fontWeight = FontWeight.Bold)
                        Text("●  9+", color = c.badgeText.toComposeColor(), modifier = Modifier.background(c.badgeBackground.toComposeColor(), RoundedCornerShape(50)).then(if (target == AppearancePreviewTarget.Badge) Modifier.border(2.dp, c.focusOutline.toComposeColor(), RoundedCornerShape(50)) else Modifier).padding(horizontal = 5.dp))
                    }
                    Text("Featured", color = c.primaryText.toComposeColor(), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniCard("MOVIE", false, target, theme, Modifier.weight(1f))
                        MiniCard("SERIES", true, target, theme, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniButton("WATCH", true, theme, target == AppearancePreviewTarget.SelectedButton)
                        MiniButton("MORE", false, theme, target == AppearancePreviewTarget.Button || target == AppearancePreviewTarget.ButtonText)
                    }
                }
            }
        }
    }
}

@Composable private fun MiniSurface(label: String, colour: Long, active: Boolean, theme: WatchioThemeDefinition, modifier: Modifier = Modifier, opacity: Float = 1f) {
    Box(modifier.fillMaxWidth().height(32.dp).background(colour.toComposeColor().copy(alpha = opacity), RoundedCornerShape(theme.surfaces.cornerRadiusDp.dp)).border(if (active) 3.dp else theme.surfaces.outlineWidthDp.dp, if (active) theme.colors.focusOutline.toComposeColor() else theme.surfaces.outlineColor.toComposeColor(), RoundedCornerShape(theme.surfaces.cornerRadiusDp.dp)).padding(6.dp)) {
        Text(label, color = theme.colors.primaryText.toComposeColor(), fontSize = 9.sp)
    }
}

@Composable private fun MiniCard(label: String, focused: Boolean, target: AppearancePreviewTarget, theme: WatchioThemeDefinition, modifier: Modifier = Modifier) {
    val c = theme.colors
    val shape = RoundedCornerShape(theme.cards.cornerRadiusDp.dp)
    val active = if (focused) target in setOf(AppearancePreviewTarget.SelectedCard, AppearancePreviewTarget.FocusOutline, AppearancePreviewTarget.FocusGlow, AppearancePreviewTarget.FocusedBackground, AppearancePreviewTarget.FocusedText) else target in setOf(AppearancePreviewTarget.Card, AppearancePreviewTarget.CardOutline, AppearancePreviewTarget.Poster)
    val baseHeight = when (theme.layout.cardSize) { CardSizeChoice.Compact -> 68f; CardSizeChoice.Standard -> 82f; CardSizeChoice.Large -> 98f } * theme.layout.uiScale
    val glow = if (focused) (theme.effects.elevationDp + 10f * theme.focus.glowIntensity).dp else 0.dp
    Box(modifier.shadow(glow, shape, ambientColor = c.focusGlow.toComposeColor(), spotColor = c.focusGlow.toComposeColor()).height((if (focused) baseHeight * theme.focus.scale else baseHeight).dp).background(if (focused) c.focusedBackground.toComposeColor() else c.cardBackground.toComposeColor().copy(alpha = theme.cards.opacity), shape).border(if (focused) theme.focus.outlineWidthDp.dp else theme.cards.outlineWidthDp.dp, if (active) c.focusOutline.toComposeColor() else c.cardOutline.toComposeColor(), shape).padding(8.dp), contentAlignment = Alignment.BottomStart) {
        Text(label, color = if (focused) c.focusedText.toComposeColor() else c.primaryText.toComposeColor(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun MiniButton(label: String, selected: Boolean, theme: WatchioThemeDefinition, active: Boolean, opacity: Float = 1f) {
    val c = theme.colors
    Box(Modifier.alpha(opacity).background(if (selected) c.selectedButton.toComposeColor() else c.buttonBackground.toComposeColor(), RoundedCornerShape(theme.controls.cornerRadiusDp.dp)).border(if (active) 3.dp else theme.controls.outlineWidthDp.dp, if (active) c.focusOutline.toComposeColor() else c.buttonOutline.toComposeColor(), RoundedCornerShape(theme.controls.cornerRadiusDp.dp)).padding(horizontal = 10.dp, vertical = 7.dp)) {
        Text(label, color = if (selected) c.selectedButtonText.toComposeColor() else c.buttonText.toComposeColor(), fontSize = theme.typography.buttonSp.sp)
    }
}

@Composable
private fun BoundedSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    val setPreviewTarget = LocalPreviewTargetSetter.current
    Column {
        Text("$label: ${"%.2f".format(value)}")
        settingDescription(label)?.let { Text(it, fontSize = 11.sp, color = LocalWatchioColors.current.textSecondary) }
        Slider(value = value.coerceIn(range), onValueChange = { setPreviewTarget(previewTargetForSetting(label)); onValueChange(it) }, valueRange = range, modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) setPreviewTarget(previewTargetForSetting(label)) }.testTag("appearance-slider-${label.replace(' ', '-') }"))
    }
}

@Composable
private fun <T : Enum<T>> ChoiceRow(label: String, values: List<T>, selected: T, onSelected: (T) -> Unit) {
    val setPreviewTarget = LocalPreviewTargetSetter.current
    Text(label, fontWeight = FontWeight.SemiBold)
    settingDescription(label)?.let { Text(it, fontSize = 11.sp, color = LocalWatchioColors.current.textSecondary) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(values) { value ->
            val modifier = Modifier.onFocusChanged { if (it.isFocused) setPreviewTarget(previewTargetForSetting(label)) }
            if (value == selected) Button(onClick = { setPreviewTarget(previewTargetForSetting(label)); onSelected(value) }, modifier = modifier) { Text(value.name) }
            else OutlinedButton(onClick = { setPreviewTarget(previewTargetForSetting(label)); onSelected(value) }, modifier = modifier) { Text(value.name) }
        }
    }
}

private fun settingDescription(label: String): String? = when (label) {
    "Panel opacity" -> "Controls how much app background shows through main panel."
    "Panel corner radius" -> "Changes rounding of panel corners."
    "Panel outline" -> "Changes border width around panels."
    "Card outline" -> "Changes border around media cards."
    "Poster overlay opacity" -> "Controls darkness over poster artwork."
    "Focus scale" -> "Controls how much item grows with TV remote focus."
    "Glow intensity" -> "Controls glow around focused TV items."
    "Spacing" -> "Changes space between interface elements."
    "UI scale" -> "Changes overall interface scale."
    "Density" -> "Switches between compact, standard, and spacious layout."
    "Animations" -> "Controls interface motion level."
    else -> null
}

@Composable
private fun ToggleChoice(label: String, selected: Boolean, onUpdate: ((WatchioThemeDefinition) -> WatchioThemeDefinition) -> Unit, update: (WatchioThemeDefinition, Boolean) -> WatchioThemeDefinition) {
    val setPreviewTarget = LocalPreviewTargetSetter.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        OutlinedButton(onClick = { setPreviewTarget(previewTargetForSetting(label)); onUpdate { update(it, !selected) } }, modifier = Modifier.onFocusChanged { if (it.isFocused) setPreviewTarget(previewTargetForSetting(label)) }) { Text(if (selected) "On" else "Off") }
    }
}
