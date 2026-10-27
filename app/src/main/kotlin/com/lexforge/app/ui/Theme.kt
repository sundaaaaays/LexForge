package com.lexforge.app.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class ThemeMode(val label: String) {
    AUTO("跟随系统"),
    LIGHT("日间"),
    DARK("夜间");

    fun next(): ThemeMode = when (this) {
        AUTO -> LIGHT
        LIGHT -> DARK
        DARK -> AUTO
    }
}

object ThemeStore {
    private const val PREF = "lexforge_prefs"
    private const val KEY = "theme_mode"

    fun load(ctx: Context): ThemeMode = runCatching {
        val name = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
        ThemeMode.valueOf(name ?: ThemeMode.AUTO.name)
    }.getOrDefault(ThemeMode.AUTO)

    fun save(ctx: Context, mode: ThemeMode) {
        runCatching {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, mode.name).apply()
        }
    }
}

private val LexLight = lightColorScheme(
    primary = Color(0xFF1F6F4A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6F1EB),
    onPrimaryContainer = Color(0xFF14402B),
    secondary = Color(0xFF2B5C8A),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAF7),
    onBackground = Color(0xFF1A1A18),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A18),
    surfaceVariant = Color(0xFFF2F2EC),
    onSurfaceVariant = Color(0xFF6B6B66),
    outline = Color(0xFFE4E4DC),
    outlineVariant = Color(0xFFEFEFE8),
)

private val LexDark = darkColorScheme(
    primary = Color(0xFF6FD3A0),
    onPrimary = Color(0xFF06271A),
    primaryContainer = Color(0xFF1C3A2B),
    onPrimaryContainer = Color(0xFFA7E5C4),
    secondary = Color(0xFF8FB8E0),
    onSecondary = Color(0xFF0B273C),
    background = Color(0xFF121412),
    onBackground = Color(0xFFE7E9E3),
    surface = Color(0xFF1A1D1A),
    onSurface = Color(0xFFE7E9E3),
    surfaceVariant = Color(0xFF232722),
    onSurfaceVariant = Color(0xFF9CA39A),
    outline = Color(0xFF313730),
    outlineVariant = Color(0xFF262B25),
)

private val LexTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

/** 当前生效的明暗（AUTO 时等于系统设置），供组件按需读取 */
val LocalIsDark = staticCompositionLocalOf { false }

@Composable
fun LexTheme(mode: ThemeMode = ThemeMode.AUTO, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) LexDark else LexLight

    // 应用内强制夜间时，系统仍是浅色，状态栏/导航栏图标要手动改白
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    // 关键：Surface 会把 LocalContentColor 设成 onBackground，
    // 否则 Text 默认取 ContentColor 的黑，深色模式下就成了黑底黑字
    MaterialTheme(colorScheme = scheme, typography = LexTypography) {
        androidx.compose.runtime.CompositionLocalProvider(LocalIsDark provides dark) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = scheme.background,
                contentColor = scheme.onBackground,
            ) {
                content()
            }
        }
    }
}
