package com.cclilshy.tayc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

val TaycBlue = Color(0xFF0B57D0)
val TaycSky = Color(0xFF1598EF)
val TaycSuccess = Color(0xFF137333)
val TaycSuccessContainer = Color(0xFFD4F8DC)
val TaycWarning = Color(0xFF7B5800)
val TaycWarningContainer = Color(0xFFFFEFB8)

private val LightColors = lightColorScheme(
    primary = TaycBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF475D92),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E2FF),
    onSecondaryContainer = Color(0xFF001A41),
    tertiary = Color(0xFF00658A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC4E7FF),
    onTertiaryContainer = Color(0xFF001E2C),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F9FF),
    onBackground = Color(0xFF172033),
    surface = Color(0xFFF7F9FF),
    onSurface = Color(0xFF172033),
    surfaceVariant = Color(0xFFE7EBF3),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
    surfaceDim = Color(0xFFD8DCE5),
    surfaceBright = Color(0xFFF7F9FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4FA),
    surfaceContainer = Color(0xFFEBEFF6),
    surfaceContainerHigh = Color(0xFFE5E9F1),
    surfaceContainerHighest = Color(0xFFDDE2EB),
    inverseSurface = Color(0xFF2C3038),
    inverseOnSurface = Color(0xFFF1F0F8),
    inversePrimary = Color(0xFFA9C7FF),
    surfaceTint = TaycBlue,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00468A),
    onPrimaryContainer = Color(0xFFD7E3FF),
    secondary = Color(0xFFB2C5FF),
    onSecondary = Color(0xFF172E60),
    secondaryContainer = Color(0xFF2F4578),
    onSecondaryContainer = Color(0xFFD9E2FF),
    tertiary = Color(0xFF7CD0FF),
    onTertiary = Color(0xFF00344A),
    tertiaryContainer = Color(0xFF004C69),
    onTertiaryContainer = Color(0xFFC4E7FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF10131A),
    onBackground = Color(0xFFE4E9F5),
    surface = Color(0xFF10131A),
    onSurface = Color(0xFFE4E9F5),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    surfaceDim = Color(0xFF10131A),
    surfaceBright = Color(0xFF363A43),
    surfaceContainerLowest = Color(0xFF0B0E14),
    surfaceContainerLow = Color(0xFF181B22),
    surfaceContainer = Color(0xFF1C2028),
    surfaceContainerHigh = Color(0xFF272B34),
    surfaceContainerHighest = Color(0xFF32363F),
    inverseSurface = Color(0xFFE4E9F5),
    inverseOnSurface = Color(0xFF2C3038),
    inversePrimary = TaycBlue,
    surfaceTint = Color(0xFFA9C7FF),
)

private val TaycTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)

private val TaycShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TaycTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = TaycTypography,
        shapes = TaycShapes,
        content = content,
    )
}
