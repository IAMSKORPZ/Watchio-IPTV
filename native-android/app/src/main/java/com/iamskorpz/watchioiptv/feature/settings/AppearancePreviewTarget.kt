package com.iamskorpz.watchioiptv.feature.settings

enum class AppearancePreviewScene { Presets, Background, Colours, Text, Panels, Cards, Controls, Navigation, TvFocus, Layout, Effects }

enum class AppearancePreviewTarget(val label: String) {
    Preset("Theme preset"), Background("App background"), Accent("Accent"), PrimaryText("Primary text"),
    SecondaryText("Secondary text"), MutedText("Metadata text"), PrimaryPanel("Primary panel"),
    SecondaryPanel("Secondary panel"), Header("Header"), Dialog("Dialog"), NavigationPanel("Navigation panel"),
    Card("Card"), CardOutline("Card outline"), SelectedCard("Selected card"), Poster("Poster"),
    Button("Button"), ButtonText("Button text"), SelectedButton("Selected button"), NavigationIcon("Navigation icon"),
    NavigationSelection("Navigation selection"), Badge("Notification badge"), FocusOutline("Focus outline"),
    FocusGlow("Focus glow"), FocusedBackground("Focused background"), FocusedText("Focused text"),
    TitleTypography("Title typography"), BodyTypography("Body typography"), MetadataTypography("Metadata typography"),
    ButtonTypography("Button typography"), LayoutDensity("Layout density"), CardSize("Card size"),
    Spacing("Spacing"), UiScale("UI scale"), Effects("Effects"), Animation("Animation")
}

fun AppearanceSection.previewScene(): AppearancePreviewScene = when (this) {
    AppearanceSection.Presets -> AppearancePreviewScene.Presets
    AppearanceSection.Background -> AppearancePreviewScene.Background
    AppearanceSection.Colours -> AppearancePreviewScene.Colours
    AppearanceSection.Text -> AppearancePreviewScene.Text
    AppearanceSection.Panels -> AppearancePreviewScene.Panels
    AppearanceSection.Cards -> AppearancePreviewScene.Cards
    AppearanceSection.Controls -> AppearancePreviewScene.Controls
    AppearanceSection.Navigation -> AppearancePreviewScene.Navigation
    AppearanceSection.TvFocus -> AppearancePreviewScene.TvFocus
    AppearanceSection.Layout -> AppearancePreviewScene.Layout
    AppearanceSection.Effects -> AppearancePreviewScene.Effects
}

fun AppearanceSection.defaultPreviewTarget(): AppearancePreviewTarget = when (this) {
    AppearanceSection.Presets -> AppearancePreviewTarget.Preset
    AppearanceSection.Background -> AppearancePreviewTarget.Background
    AppearanceSection.Colours -> AppearancePreviewTarget.Accent
    AppearanceSection.Text -> AppearancePreviewTarget.TitleTypography
    AppearanceSection.Panels -> AppearancePreviewTarget.PrimaryPanel
    AppearanceSection.Cards -> AppearancePreviewTarget.Card
    AppearanceSection.Controls -> AppearancePreviewTarget.Button
    AppearanceSection.Navigation -> AppearancePreviewTarget.NavigationPanel
    AppearanceSection.TvFocus -> AppearancePreviewTarget.FocusOutline
    AppearanceSection.Layout -> AppearancePreviewTarget.LayoutDensity
    AppearanceSection.Effects -> AppearancePreviewTarget.Effects
}

fun previewTargetForSetting(label: String): AppearancePreviewTarget = when (label) {
    "Background", "Image opacity", "Overlay opacity", "Blur", "Image scaling", "Image alignment" -> AppearancePreviewTarget.Background
    "Accent" -> AppearancePreviewTarget.Accent
    "Primary text" -> AppearancePreviewTarget.PrimaryText
    "Secondary text" -> AppearancePreviewTarget.SecondaryText
    "Muted text" -> AppearancePreviewTarget.MutedText
    "Primary panel", "Panel opacity", "Panel corner radius", "Panel outline" -> AppearancePreviewTarget.PrimaryPanel
    "Secondary panel", "Secondary panel opacity" -> AppearancePreviewTarget.SecondaryPanel
    "Header", "Header opacity" -> AppearancePreviewTarget.Header
    "Dialog", "Dialog opacity" -> AppearancePreviewTarget.Dialog
    "Sidebar", "Navigation", "Navigation opacity" -> AppearancePreviewTarget.NavigationPanel
    "Card", "Card opacity", "Card corner radius" -> AppearancePreviewTarget.Card
    "Card outline" -> AppearancePreviewTarget.CardOutline
    "Selected card", "Selected card outline" -> AppearancePreviewTarget.SelectedCard
    "Poster corner radius", "Poster overlay opacity" -> AppearancePreviewTarget.Poster
    "Button", "Control corner radius", "Control outline", "Disabled opacity" -> AppearancePreviewTarget.Button
    "Button text" -> AppearancePreviewTarget.ButtonText
    "Selected button", "Selected button text" -> AppearancePreviewTarget.SelectedButton
    "Navigation icon", "Selected navigation icon" -> AppearancePreviewTarget.NavigationIcon
    "Selected navigation" -> AppearancePreviewTarget.NavigationSelection
    "Badge", "Badge text" -> AppearancePreviewTarget.Badge
    "Focus outline" -> AppearancePreviewTarget.FocusOutline
    "Focus glow", "Glow intensity" -> AppearancePreviewTarget.FocusGlow
    "Focused background", "Focus scale" -> AppearancePreviewTarget.FocusedBackground
    "Focused text" -> AppearancePreviewTarget.FocusedText
    "Title size", "Title weight" -> AppearancePreviewTarget.TitleTypography
    "Body size", "Body weight" -> AppearancePreviewTarget.BodyTypography
    "Metadata size" -> AppearancePreviewTarget.MetadataTypography
    "Button text size", "Button weight" -> AppearancePreviewTarget.ButtonTypography
    "Density" -> AppearancePreviewTarget.LayoutDensity
    "Card size" -> AppearancePreviewTarget.CardSize
    "Spacing" -> AppearancePreviewTarget.Spacing
    "UI scale" -> AppearancePreviewTarget.UiScale
    "Animations" -> AppearancePreviewTarget.Animation
    else -> AppearancePreviewTarget.Effects
}
