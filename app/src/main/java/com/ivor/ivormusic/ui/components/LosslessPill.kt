package com.ivor.ivormusic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R

/**
 * The one mark Koda uses for lossless audio: on a downloaded row, on the
 * download sheet's Lossless card and in every player while a lossless file or
 * stream is what is being decoded.
 *
 * "Hi-Res" where the file is more than CD quality (above 16-bit or 48 kHz),
 * "Lossless" otherwise, and [detail] ("24-bit · 96 kHz") after it where there
 * is room for it. Tertiary, so it reads as a property of the song rather than
 * a control, in the app's scheme or a player's album scheme alike.
 */
@Composable
fun LosslessPill(
    modifier: Modifier = Modifier,
    hiRes: Boolean = false,
    detail: String? = null,
    compact: Boolean = false,
) {
    val label = stringResource(if (hiRes) R.string.lossless_pill_hires else R.string.lossless_pill)
    val spoken = listOfNotNull(label, detail).joinToString(", ")
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken }
    ) {
        Row(
            modifier = Modifier.padding(
                start = if (compact) 6.dp else 8.dp,
                end = if (compact) 7.dp else 10.dp,
                top = if (compact) 2.dp else 3.dp,
                bottom = if (compact) 2.dp else 3.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 11.dp else 13.dp)
            )
            Text(
                text = label.uppercase(),
                fontSize = if (compact) 9.sp else 10.sp,
                lineHeight = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                maxLines = 1,
            )
            if (detail != null && !compact) {
                Text(
                    text = detail,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}
