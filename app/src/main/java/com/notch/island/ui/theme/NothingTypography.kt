package com.notch.island.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.notch.island.R

/**
 * Nothing OS typography pairing:
 * - Ndot: The iconic retro dot-matrix typeface for headings, timestamps, battery %, and status codes.
 * - Geist: Clean geometric modern sans-serif for readable body copy and subtitles.
 */
val NdotFontFamily = FontFamily(
    Font(R.font.ndot, FontWeight.Normal)
)

val GeistFontFamily = FontFamily(
    Font(R.font.geist, FontWeight.Normal),
    Font(R.font.geist, FontWeight.Medium),
    Font(R.font.geist, FontWeight.Bold)
)
