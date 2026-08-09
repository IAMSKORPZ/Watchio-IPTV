import 'package:flutter/material.dart';

class BingieThemeExtension extends ThemeExtension<BingieThemeExtension> {
  final LinearGradient primaryGradient;
  final LinearGradient secondaryGradient;
  final LinearGradient panelGradient;
  final Color glassColor;
  final Color glassBorder;
  final Color highlightColor;
  final Color glowColor;
  final Color panelColor;

  const BingieThemeExtension({
    required this.primaryGradient,
    required this.secondaryGradient,
    required this.panelGradient,
    required this.glassColor,
    required this.glassBorder,
    required this.highlightColor,
    required this.glowColor,
    required this.panelColor,
  });

  @override
  ThemeExtension<BingieThemeExtension> copyWith({
    LinearGradient? primaryGradient,
    LinearGradient? secondaryGradient,
    LinearGradient? panelGradient,
    Color? glassColor,
    Color? glassBorder,
    Color? highlightColor,
    Color? glowColor,
    Color? panelColor,
  }) {
    return BingieThemeExtension(
      primaryGradient: primaryGradient ?? this.primaryGradient,
      secondaryGradient: secondaryGradient ?? this.secondaryGradient,
      panelGradient: panelGradient ?? this.panelGradient,
      glassColor: glassColor ?? this.glassColor,
      glassBorder: glassBorder ?? this.glassBorder,
      highlightColor: highlightColor ?? this.highlightColor,
      glowColor: glowColor ?? this.glowColor,
      panelColor: panelColor ?? this.panelColor,
    );
  }

  @override
  ThemeExtension<BingieThemeExtension> lerp(
    ThemeExtension<BingieThemeExtension>? other,
    double t,
  ) {
    if (other is! BingieThemeExtension) {
      return this;
    }
    return BingieThemeExtension(
      primaryGradient: LinearGradient.lerp(
        primaryGradient,
        other.primaryGradient,
        t,
      )!,
      secondaryGradient: LinearGradient.lerp(
        secondaryGradient,
        other.secondaryGradient,
        t,
      )!,
      panelGradient: LinearGradient.lerp(
        panelGradient,
        other.panelGradient,
        t,
      )!,
      glassColor: Color.lerp(glassColor, other.glassColor, t)!,
      glassBorder: Color.lerp(glassBorder, other.glassBorder, t)!,
      highlightColor: Color.lerp(highlightColor, other.highlightColor, t)!,
      glowColor: Color.lerp(glowColor, other.glowColor, t)!,
      panelColor: Color.lerp(panelColor, other.panelColor, t)!,
    );
  }

  static BingieThemeExtension of(BuildContext context) {
    return Theme.of(context).extension<BingieThemeExtension>() ?? defaults;
  }

  static const defaults = BingieThemeExtension(
    primaryGradient: LinearGradient(
      colors: [Color(0xFFFF3D9A), Color(0xFFA855F7)],
    ),
    secondaryGradient: LinearGradient(
      colors: [Color(0xFFA855F7), Color(0xFF20D9D2)],
    ),
    panelGradient: LinearGradient(
      colors: [Color(0xCC0B1020), Color(0xCC101426)],
    ),
    glassColor: Color(0xB3101426),
    glassBorder: Color(0x9E30354D),
    highlightColor: Color(0xFFFF3D9A),
    glowColor: Color(0x6620D9D2),
    panelColor: Color(0xFF0B1020),
  );
}
