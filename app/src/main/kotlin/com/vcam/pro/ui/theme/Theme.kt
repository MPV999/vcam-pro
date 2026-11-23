package com.vcam.pro.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val Violet = Color(0xFF7C5CFF)
private val VioletDim = Color(0xFF3A2E63)
private val Mint = Color(0xFF3DDCB4)
private val Amber = Color(0xFFFFC466)
private val Rose = Color(0xFFFF5C7A)

private val Ink = Color(0xFF08080C)
private val Surface = Color(0xFF12121A)
private val SurfaceHigh = Color(0xFF1B1B26)
private val Outline = Color(0xFF2E2E3D)
private val TextPrimary = Color(0xFFF2F2F7)
private val TextSecondary = Color(0xFF9A9AAE)

private val DarkColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = VioletDim,
    onPrimaryContainer = Color(0xFFE7E0FF),
    secondary = Mint,
    onSecondary = Color(0xFF04140F),
    tertiary = Amber,
    onTertiary = Color(0xFF1F1400),
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = Outline,
    outlineVariant = Color(0xFF23232F),
    error = Rose,
    onError = Color(0xFF1B0007),
    errorContainer = Color(0xFF3A111C),
    onErrorContainer = Color(0xFFFFD9E0),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B3BE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E0FF),
    onPrimaryContainer = Color(0xFF1B0F45),
    secondary = Color(0xFF0F8F76),
    onSecondary = Color.White,
    tertiary = Color(0xFF9A6200),
    background = Color(0xFFF7F6FB),
    onBackground = Color(0xFF12121A),
    surface = Color.White,
    onSurface = Color(0xFF12121A),
    surfaceVariant = Color(0xFFEFEDF6),
    onSurfaceVariant = Color(0xFF565568),
    outline = Color(0xFFD4D2E0),
    error = Color(0xFFB3261E),
)

private val VcamTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
    ),
)

@Composable
fun VcamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = VcamTypography,
        content = content,
    )
}
