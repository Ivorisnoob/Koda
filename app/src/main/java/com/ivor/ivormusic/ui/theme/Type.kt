package com.ivor.ivormusic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// FALLBACK: Use Default font because 'roboto_flex.ttf' is missing in res/font/.
// To use Expressive Typography with Variable Fonts:
// 1. Download Roboto Flex (Variable) .ttf
// 2. Place it in app/src/main/res/font/roboto_flex.ttf
// 3. Uncomment the code below and usage.

/*
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import com.ivor.ivormusic.R

val RobotoFlex = FontFamily(
    Font(
        resId = R.font.roboto_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.width(100f),
            FontVariation.weight(400)
        )
    )
)

val RobotoFlexWide = FontFamily(
    Font(
        resId = R.font.roboto_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.width(115f),
            FontVariation.weight(700)
        )
    )
)

val RobotoFlexMediumWidth = FontFamily(
    Font(
        resId = R.font.roboto_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.width(110f),
            FontVariation.weight(600)
        )
    )
)
*/

// Using Default for now to ensure compile
val RobotoFlex = FontFamily.Default
val RobotoFlexWide = FontFamily.Default
val RobotoFlexMediumWidth = FontFamily.Default

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = RobotoFlexWide,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = RobotoFlexMediumWidth,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)