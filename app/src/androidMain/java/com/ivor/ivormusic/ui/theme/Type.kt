@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.ivor.ivormusic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R

// Material 3 Expressive Typography using Roboto Flex Variable Font
// Using FontVariation.width() for wider/condensed text styles

// Standard width (100f) - for body text and regular content
val RobotoFlex = FontFamily(
    androidx.compose.ui.text.font.Font(
        resId = R.font.roboto_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.width(100f),
            FontVariation.weight(400)
        )
    )
)

// Google Font Provider
val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val MontserratFont = GoogleFont("Montserrat")

val MontserratFamily = FontFamily(
    Font(googleFont = MontserratFont, fontProvider = provider, weight = FontWeight.Black),
    Font(googleFont = MontserratFont, fontProvider = provider, weight = FontWeight.ExtraBold),
    Font(googleFont = MontserratFont, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = MontserratFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = MontserratFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = MontserratFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = MontserratFont, fontProvider = provider, weight = FontWeight.Light),
)

// Medium width (110f) - for section headers
val RobotoFlexMediumWidth = FontFamily(
    androidx.compose.ui.text.font.Font(
        resId = R.font.roboto_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.width(110f),
            FontVariation.weight(600)
        )
    )
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 68.sp,
        textGeometricTransform = TextGeometricTransform(scaleX = 1.5f), // Stretch horizontally
        letterSpacing = (-0.05).em,
        lineHeight = 0.8.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
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