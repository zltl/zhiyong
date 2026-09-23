package app.zhencao.qianwen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Paper = Color(0xFFF3EDE2)
val PaperDeep = Color(0xFFE9E1D3)
val PaperShade = Color(0xFFE2D8C6)
val Ink = Color(0xFF2B2622)
val Cinnabar = Color(0xFF9A4B3E)
val Moss = Color(0xFF5E7361)
val Mist = Color(0xFF8C8276)
val Hairline = Color(0xFFDCD2C1)

// Every container Material would tint lavender is set to a paper tone.
private val Colors = lightColorScheme(
    primary = Cinnabar,
    onPrimary = Color(0xFFFBF7F0),
    primaryContainer = PaperShade,
    onPrimaryContainer = Ink,
    secondary = Moss,
    onSecondary = Color(0xFFFBF7F0),
    secondaryContainer = PaperShade,
    onSecondaryContainer = Ink,
    tertiary = Moss,
    tertiaryContainer = PaperShade,
    onTertiaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = Mist,
    surfaceTint = Color.Transparent,
    surfaceBright = Paper,
    surfaceDim = PaperDeep,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = Color(0xFFEEE7DA),
    surfaceContainerHigh = PaperDeep,
    surfaceContainerHighest = PaperShade,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    outline = Hairline,
    outlineVariant = Hairline,
)

private val Serif = FontFamily.Serif

private val Type = Typography().run {
    copy(
        headlineMedium = TextStyle(
            fontFamily = Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            letterSpacing = 4.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            letterSpacing = 2.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            letterSpacing = 1.sp,
        ),
        bodyLarge = bodyLarge.copy(lineHeight = 26.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 22.sp, letterSpacing = 0.3.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Normal, letterSpacing = 1.sp),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Normal, letterSpacing = 1.sp),
    )
}

@Composable
fun QianwenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = Type,
        content = content,
    )
}
