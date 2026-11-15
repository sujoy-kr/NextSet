package com.sujoy.nextset.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.sujoy.nextset.data.ThemeMode

private val Light =
    lightColorScheme(
        primary = Color(0xFF326748),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD8ECCC),
        onPrimaryContainer = Color(0xFF143B24),
        secondaryContainer = Color(0xFFE8ECD9),
        onSecondaryContainer = Color(0xFF35452F),
        background = Color(0xFFF6F7F3),
        onBackground = Color(0xFF19211C),
        surface = Color(0xFFF6F7F3),
        surfaceContainer = Color(0xFFEDF0E8),
        surfaceContainerLow = Color(0xFFF1F3EC),
        surfaceContainerHigh = Color(0xFFE7EBE1),
        surfaceContainerHighest = Color(0xFFE1E6DB),
        onSurface = Color(0xFF19211C),
        outline = Color(0xFF74806F),
        outlineVariant = Color(0xFFC8D0C2),
        surfaceVariant = Color(0xFFE6EAE1),
        onSurfaceVariant = Color(0xFF505A51),
    )
private val Dark =
    darkColorScheme(
        primary = Color(0xFFADD69D),
        onPrimary = Color(0xFF17391E),
        primaryContainer = Color(0xFF2E4F32),
        onPrimaryContainer = Color(0xFFDBEFD2),
        secondaryContainer = Color(0xFF303B2C),
        onSecondaryContainer = Color(0xFFD5E3C8),
        background = Color(0xFF111511),
        onBackground = Color(0xFFE5EAE0),
        surface = Color(0xFF111511),
        surfaceContainer = Color(0xFF1D241D),
        surfaceContainerLow = Color(0xFF171D17),
        surfaceContainerHigh = Color(0xFF283027),
        surfaceContainerHighest = Color(0xFF333C31),
        onSurface = Color(0xFFE5EAE0),
        outline = Color(0xFF89967F),
        outlineVariant = Color(0xFF424D3D),
        surfaceVariant = Color(0xFF283027),
        onSurfaceVariant = Color(0xFFB7C2B2),
    )

@Composable
fun NextSetTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography =
            Typography(
                headlineLarge =
                    TextStyle(
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                    ),
                headlineMedium =
                    TextStyle(
                        fontSize = 27.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                    ),
                titleLarge =
                    TextStyle(
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                titleMedium =
                    TextStyle(
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
                bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
                labelLarge =
                    TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
            ),
        content = content,
    )
}

fun restLabel(seconds: Int): String =
    when {
        seconds == 0 -> "No rest"
        seconds < 60 -> "${seconds}s"
        seconds % 60 == 0 -> "${seconds / 60} min"
        else -> "${seconds / 60}m ${seconds % 60}s"
    }
