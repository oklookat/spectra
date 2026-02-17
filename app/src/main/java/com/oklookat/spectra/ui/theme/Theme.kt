package com.oklookat.spectra.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.oklookat.spectra.util.TvUtils

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8)
)

// Нативная тема Google TV с уточненными референсными цветами
private val TvDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8EAED), // Светло-серый Google для фокуса
    onPrimary = Color(0xFF0E0E0F),
    primaryContainer = Color(0xFF3C4043),
    onPrimaryContainer = Color(0xFFE8EAED),
    
    secondary = Color(0xFF8AB4F8), // Фирменный синий для акцентов
    onSecondary = Color(0xFF002F66),
    
    background = Color(0xFF0E0E0F), // Референсный темный фон
    surface = Color(0xFF0E0E0F),
    onBackground = Color(0xFFE8EAED),
    onSurface = Color(0xFFE8EAED),
    
    surfaceVariant = Color(0xFF181819), // Референсный серый чуть посветлее
    onSurfaceVariant = Color(0xFF9AA0A6),
    
    outline = Color(0xFF3C4043),
    
    inverseSurface = Color(0xFFFFFFFF), // Фокус (чисто белый для контраста)
    inverseOnSurface = Color(0xFF000000)
)

private val LightColorScheme = lightColorScheme()

@Composable
fun SpectraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isTv = TvUtils.isTv(context)
    val actualDarkTheme = if (isTv) true else darkTheme

    val colorScheme = when {
        isTv -> TvDarkColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (actualDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        actualDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !actualDarkTheme
            insetsController.isAppearanceLightNavigationBars = !actualDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
