package com.ultradownloader.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Luxury Obsidian Dark Palette
val ObsidianBackground = Color(0xFF090A0F)
val ObsidianSurface = Color(0xFF12141E)
val ObsidianCard = Color(0xFF191C2A)
val ObsidianBorder = Color(0xFF282D42)

// Daylight Clean Light Palette
val DaylightBackground = Color(0xFFF7F8FC)
val DaylightSurface = Color(0xFFFFFFFF)
val DaylightCard = Color(0xFFF0F3FA)
val DaylightBorder = Color(0xFFDCE2F0)

// Brand Signature Electric Purple-to-Blue Gradient
val ElectricPurple = Color(0xFF7C3AED)
val IndigoPulse = Color(0xFF4F46E5)
val ElectricBlue = Color(0xFF00D2FF)

// Status Semantic Colors
val EmeraldSuccess = Color(0xFF10B981)
val AmberWarning = Color(0xFFF59E0B)
val CrimsonError = Color(0xFFEF4444)

val UltraBrandGradient = Brush.linearGradient(
    colors = listOf(ElectricPurple, IndigoPulse, ElectricBlue)
)

val UltraSubtleCardGradientDark = Brush.verticalGradient(
    colors = listOf(Color(0xFF1B1F30), Color(0xFF131622))
)
