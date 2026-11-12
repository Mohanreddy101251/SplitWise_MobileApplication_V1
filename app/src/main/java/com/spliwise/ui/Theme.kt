package com.spliwise.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Bright, colourful light palette (no dark theme by design). */
object P {
    val Bg = Color(0xFFF6F3FF)
    val Card = Color.White
    val Ink = Color(0xFF1E1B3A)
    val Sub = Color(0xFF706C93)
    val Line = Color(0xFFECE8FA)
    val Violet = Color(0xFF6A5AF9)
    val Magenta = Color(0xFFC04BFF)
    val Pink = Color(0xFFFF5FA2)
    val Orange = Color(0xFFFFA94D)
    val Yellow = Color(0xFFFFD166)
    val Teal = Color(0xFF1FC8A9)
    val Sky = Color(0xFF3FA9FF)
    val Red = Color(0xFFFF4D6D)
    val Green = Color(0xFF16B47A)

    val Hero = Brush.linearGradient(listOf(Violet, Magenta, Pink))
    val Sunset = Brush.linearGradient(listOf(Pink, Orange))
    val Ocean = Brush.linearGradient(listOf(Sky, Teal))
    val Lime = Brush.linearGradient(listOf(Teal, Color(0xFF8BE36B)))
    val Grape = Brush.linearGradient(listOf(Color(0xFF8E7DFF), Violet))
    val Bars = Brush.verticalGradient(listOf(Pink, Violet))
}

private val type = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun SpliTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(
        primary = P.Violet, onPrimary = Color.White, secondary = P.Pink, tertiary = P.Teal,
        background = P.Bg, surface = P.Card, onBackground = P.Ink, onSurface = P.Ink,
        surfaceVariant = P.Line, onSurfaceVariant = P.Sub, error = P.Red,
    ),
    typography = type,
    content = content,
)
