package au.edu.jcu.assessment.utilityapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color(0xFF101722),
    surface = Color(0xFF101722),
    surfaceContainerLow = Color(0xFF1B2432),
    primaryContainer = Color(0xFF203D69),
    onPrimaryContainer = Color(0xFFD9E7FF),
    onSurface = Color(0xFFE5EAF2),
    onSurfaceVariant = Color(0xFFB8C6D8),
    secondaryContainer = Color(0xFF203D69),
    onSecondaryContainer = Color(0xFFD9E7FF),
    outline = Color(0xFF8294AD),
    outlineVariant = Color(0xFF334258)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFF2F4F8),
    surface = Color(0xFFF2F4F8),
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFE8EDF5),
    primaryContainer = Color(0xFFDDEBFF),
    secondaryContainer = Color(0xFFDDEBFF),
    onSecondaryContainer = Color(0xFF12366A),
    onPrimaryContainer = Color(0xFF12366A),
    onSurface = Color(0xFF182536),
    onSurfaceVariant = Color(0xFF5C6B7E),
    outline = Color(0xFF7D8999),
    outlineVariant = Color(0xFFE4E8EE)

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun MobileTech_UtilityAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
