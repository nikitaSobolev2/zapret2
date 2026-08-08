package zapret.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Geometric sans for everything, Menlo for the raw tpws strategy text. */
@OptIn(ExperimentalTextApi::class)
object Fonts {
    val display: FontFamily = FontFamily("Avenir Next")
    val mono: FontFamily = FontFamily("Menlo")
}

val ZapretTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Fonts.display, fontWeight = FontWeight.Medium),
        headlineLarge = base.headlineLarge.copy(fontFamily = Fonts.display, fontWeight = FontWeight.Medium),
        headlineSmall = base.headlineSmall.copy(fontFamily = Fonts.display, fontWeight = FontWeight.Medium),
        titleLarge = base.titleLarge.copy(fontFamily = Fonts.display, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = Fonts.display, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = Fonts.display),
        bodyMedium = base.bodyMedium.copy(fontFamily = Fonts.display),
        labelLarge = base.labelLarge.copy(fontFamily = Fonts.display, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = Fonts.display),
    )
}

val BrandStyle = TextStyle(
    fontFamily = Fonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    letterSpacing = 10.sp,
)

val TimerStyle = TextStyle(
    fontFamily = Fonts.display,
    fontWeight = FontWeight.Light,
    fontSize = 44.sp,
    letterSpacing = 2.sp,
)

val MonoStyle = TextStyle(
    fontFamily = Fonts.mono,
    fontSize = 12.sp,
    lineHeight = 18.sp,
)
