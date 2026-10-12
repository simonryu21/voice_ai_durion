package com.lgcns.codeengine.translation.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Pipecat Voice UI design tokens — direct mirror of `app.jsx#themes` from the
 * Claude Design handoff. Dark is the primary palette. Mirrors the
 * `--pc-*` CSS variables defined in voice-ui-kit.
 */
object Pipecat {
    // Backgrounds
    val BgDark           = Color(0xFF0E0B0A) // warm black
    val SurfaceDark      = Color(0xFF1A1413)
    val SurfaceElevDark  = Color(0xFF241B19)
    val InputBgDark      = Color(0xFF1F1816)

    val BgLight          = Color(0xFFFAF6F2) // cream
    val SurfaceLight     = Color(0xFFFFFFFF)
    val InputBgLight     = Color(0xFFFFFFFF)

    // Foreground
    val TextDark         = Color(0xFFF7F1ED)
    val TextDimDark      = Color(0x99F7F1ED)   // alpha 0.60
    val TextFaintDark    = Color(0x5CF7F1ED)   // alpha 0.36

    val TextLight        = Color(0xFF0E0B0A)
    val TextDimLight     = Color(0x990E0B0A)
    val TextFaintLight   = Color(0x5C0E0B0A)

    // Borders / chips
    val BorderDark       = Color(0x14FFFFFF)   // alpha 0.08
    val ChipBgDark       = Color(0x0AFFFFFF)   // alpha 0.04
    val ChipBorderDark   = Color(0x14FFFFFF)   // alpha 0.08

    val BorderLight      = Color(0x140E0B0A)
    val ChipBgLight      = Color(0xFFFFFFFF)
    val ChipBorderLight  = Color(0x1A0E0B0A)   // alpha 0.10

    // Device shell (used by Storybook / Idle preview)
    val DeviceBody       = Color(0xFF0E0B0A)
    val DeviceBezel      = Color(0xFF15110F)
    val CanvasDark       = Color(0xFF1B1614)
    val CanvasLight      = Color(0xFFE8E2DC)

    // Accent — deep vermilion (default + 3 alternates)
    val Vermilion        = Color(0xFFC73E1D)
    val VermilionAlt1    = Color(0xFFD94A2B)
    val VermilionAlt2    = Color(0xFFE55934)
    val VermilionAlt3    = Color(0xFFB33A1A)

    // Semantic dot — idle/ready only. Everything else uses Vermilion.
    val ReadyGreen       = Color(0xFF4ADE80)

    // Motion (millis)
    const val MOTION_FAST_MS = 180
    const val MOTION_BASE_MS = 240
    const val MOTION_SLOW_MS = 520
}

/**
 * Resolved theme palette. Builds light/dark variants from [Pipecat] tokens.
 */
data class PipecatPalette(
    val bgBase: Color,
    val surface: Color,
    val surfaceElev: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val border: Color,
    val chipBg: Color,
    val chipBorder: Color,
    val inputBg: Color,
) {
    companion object {
        fun forTheme(isDark: Boolean): PipecatPalette =
            if (isDark) {
                PipecatPalette(
                    bgBase = Pipecat.BgDark,
                    surface = Pipecat.SurfaceDark,
                    surfaceElev = Pipecat.SurfaceElevDark,
                    text = Pipecat.TextDark,
                    textDim = Pipecat.TextDimDark,
                    textFaint = Pipecat.TextFaintDark,
                    border = Pipecat.BorderDark,
                    chipBg = Pipecat.ChipBgDark,
                    chipBorder = Pipecat.ChipBorderDark,
                    inputBg = Pipecat.InputBgDark,
                )
            } else {
                PipecatPalette(
                    bgBase = Pipecat.BgLight,
                    surface = Pipecat.SurfaceLight,
                    surfaceElev = Pipecat.SurfaceLight,
                    text = Pipecat.TextLight,
                    textDim = Pipecat.TextDimLight,
                    textFaint = Pipecat.TextFaintLight,
                    border = Pipecat.BorderLight,
                    chipBg = Pipecat.ChipBgLight,
                    chipBorder = Pipecat.ChipBorderLight,
                    inputBg = Pipecat.InputBgLight,
                )
            }
    }
}
