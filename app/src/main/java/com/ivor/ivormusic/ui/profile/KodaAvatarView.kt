package com.ivor.ivormusic.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ivor.ivormusic.data.KodaAvatar
import com.ivor.ivormusic.data.KodaAvatarColors
import com.ivor.ivormusic.data.KodaProfileCard
import com.ivor.ivormusic.data.KodaProfileStore
import com.ivor.ivormusic.data.Profile
import com.ivor.ivormusic.data.ProfileManager
import com.ivor.ivormusic.ui.player.EditorialPolygonShape

/**
 * Opens the account switcher from wherever a profile picture is drawn. Provided
 * by the Home shell, which owns the sheet; null where there is no shell, in
 * which case the long-press that calls it simply is not offered.
 */
val LocalOpenAccountSwitcher = staticCompositionLocalOf<(() -> Unit)?> { null }

/** The outline an avatar is cut to, by its stored index. Wraps, so an unknown index still draws. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberAvatarShape(index: Int): Shape = remember(index) {
    when (Math.floorMod(index, KodaAvatar.SHAPES)) {
        0 -> CircleShape
        1 -> EditorialPolygonShape(MaterialShapes.Cookie9Sided)
        2 -> EditorialPolygonShape(MaterialShapes.Flower)
        3 -> EditorialPolygonShape(MaterialShapes.Cookie6Sided)
        4 -> EditorialPolygonShape(MaterialShapes.Clover4Leaf)
        5 -> EditorialPolygonShape(MaterialShapes.Cookie12Sided)
        6 -> EditorialPolygonShape(MaterialShapes.SoftBurst)
        7 -> EditorialPolygonShape(MaterialShapes.Cookie4Sided)
        else -> EditorialPolygonShape(MaterialShapes.Sunny)
    }
}

/**
 * One of Koda's drawn avatars: a coloured shape with a face on it.
 *
 * Everything is drawn as a fraction of the avatar's own size, so the same
 * avatar is right at 28dp in a row and at 140dp on the profile screen. The
 * face sits in the middle half of the shape, clear of every outline's edge,
 * which is what lets the nine shapes share one face.
 *
 * Its colours are the avatar's own (see `KodaAvatarColors`), not the theme's.
 */
@Composable
fun KodaAvatarView(avatar: KodaAvatar, modifier: Modifier = Modifier) {
    val colors = KodaAvatarColors[Math.floorMod(avatar.color, KodaAvatarColors.size)]
    val fill = Color(colors.fill)
    val ink = Color(colors.ink)
    val blush = Color(colors.blush)
    Box(
        modifier = modifier
            .clip(rememberAvatarShape(avatar.shape))
            .background(fill)
            .drawBehind {
                drawRect(fill)
                if (avatar.blush) drawBlush(blush)
                drawExtraBehind(avatar.extra, ink, blush)
                drawEyes(avatar.eyes, ink, fill)
                drawMouth(avatar.mouth, ink, blush)
                drawExtraFront(avatar.extra, ink, blush)
            }
    )
}

private fun DrawScope.at(x: Float, y: Float) = Offset(size.width * x, size.height * y)
private fun DrawScope.unit(value: Float) = size.minDimension * value
private fun DrawScope.line(width: Float = 0.035f) =
    Stroke(width = unit(width), cap = StrokeCap.Round)

private const val EYE_Y = 0.45f
private const val EYE_LEFT = 0.37f
private const val EYE_RIGHT = 0.63f
private const val MOUTH_Y = 0.60f

private fun DrawScope.drawBlush(blush: Color) {
    drawCircle(blush.copy(alpha = 0.45f), radius = unit(0.065f), center = at(0.28f, 0.57f))
    drawCircle(blush.copy(alpha = 0.45f), radius = unit(0.065f), center = at(0.72f, 0.57f))
}

private fun DrawScope.drawEyes(kind: Int, ink: Color, fill: Color) {
    fun dot(x: Float, radius: Float = 0.042f) = drawCircle(ink, radius = unit(radius), center = at(x, EYE_Y))
    // An arc opening downward: a closed, smiling eye.
    fun smile(x: Float) = drawArc(
        color = ink,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = at(x - 0.055f, EYE_Y - 0.035f),
        size = Size(unit(0.11f), unit(0.09f)),
        style = line()
    )
    when (Math.floorMod(kind, KodaAvatar.EYES)) {
        0 -> { dot(EYE_LEFT); dot(EYE_RIGHT) }
        1 -> { smile(EYE_LEFT); smile(EYE_RIGHT) }
        2 -> { dot(EYE_LEFT); smile(EYE_RIGHT) }
        3 -> listOf(EYE_LEFT, EYE_RIGHT).forEach { x ->
            // Big and shiny: a highlight cut out of the pupil in the fill colour.
            drawCircle(ink, radius = unit(0.062f), center = at(x, EYE_Y))
            drawCircle(fill, radius = unit(0.022f), center = at(x + 0.02f, EYE_Y - 0.022f))
        }
        4 -> listOf(EYE_LEFT, EYE_RIGHT).forEach { x ->
            // Sleepy: a short flat line.
            drawLine(ink, at(x - 0.045f, EYE_Y), at(x + 0.045f, EYE_Y), strokeWidth = unit(0.035f), cap = StrokeCap.Round)
        }
        else -> listOf(EYE_LEFT, EYE_RIGHT).forEach { x ->
            // Tall ovals.
            drawOval(ink, topLeft = at(x - 0.032f, EYE_Y - 0.06f), size = Size(unit(0.064f), unit(0.12f)))
        }
    }
}

private fun DrawScope.drawMouth(kind: Int, ink: Color, blush: Color) {
    when (Math.floorMod(kind, KodaAvatar.MOUTHS)) {
        0 -> drawArc(
            ink, startAngle = 20f, sweepAngle = 140f, useCenter = false,
            topLeft = at(0.42f, MOUTH_Y - 0.06f), size = Size(unit(0.16f), unit(0.11f)), style = line()
        )
        1 -> drawArc(
            // Open and happy: a filled half.
            ink, startAngle = 0f, sweepAngle = 180f, useCenter = true,
            topLeft = at(0.41f, MOUTH_Y - 0.075f), size = Size(unit(0.18f), unit(0.15f))
        )
        2 -> {
            // The cat's ":3": two small bowls meeting in the middle.
            drawArc(
                ink, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = at(0.42f, MOUTH_Y - 0.035f), size = Size(unit(0.08f), unit(0.07f)), style = line()
            )
            drawArc(
                ink, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = at(0.50f, MOUTH_Y - 0.035f), size = Size(unit(0.08f), unit(0.07f)), style = line()
            )
        }
        3 -> drawLine(
            ink, at(0.45f, MOUTH_Y + 0.01f), at(0.55f, MOUTH_Y + 0.01f),
            strokeWidth = unit(0.035f), cap = StrokeCap.Round
        )
        4 -> drawCircle(ink, radius = unit(0.035f), center = at(0.5f, MOUTH_Y + 0.01f), style = line(0.03f))
        else -> {
            // A grin with the tongue out.
            drawArc(
                ink, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = at(0.42f, MOUTH_Y - 0.06f), size = Size(unit(0.16f), unit(0.11f)), style = line()
            )
            drawCircle(blush, radius = unit(0.032f), center = at(0.53f, MOUTH_Y + 0.055f))
        }
    }
}

/** Extras that sit behind the face. */
private fun DrawScope.drawExtraBehind(kind: Int, ink: Color, blush: Color) {
    when (Math.floorMod(kind, KodaAvatar.EXTRAS)) {
        1 -> {
            // Headphones: a band over the top and a cup at each ear.
            drawArc(
                ink, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = at(0.22f, 0.20f), size = Size(unit(0.56f), unit(0.52f)), style = line(0.045f)
            )
            drawRoundRect(
                blush, topLeft = at(0.16f, 0.40f), size = Size(unit(0.10f), unit(0.17f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit(0.04f))
            )
            drawRoundRect(
                blush, topLeft = at(0.74f, 0.40f), size = Size(unit(0.10f), unit(0.17f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit(0.04f))
            )
        }
        3 -> {
            // A sprout: a stem and two leaves.
            drawLine(ink, at(0.5f, 0.30f), at(0.5f, 0.20f), strokeWidth = unit(0.03f), cap = StrokeCap.Round)
            drawOval(blush, topLeft = at(0.41f, 0.155f), size = Size(unit(0.09f), unit(0.055f)))
            drawOval(blush, topLeft = at(0.50f, 0.155f), size = Size(unit(0.09f), unit(0.055f)))
        }
        4 -> {
            // A bow: two wings and a knot.
            val bow = Path().apply {
                moveTo(size.width * 0.5f, size.height * 0.235f)
                lineTo(size.width * 0.38f, size.height * 0.175f)
                lineTo(size.width * 0.38f, size.height * 0.295f)
                close()
                moveTo(size.width * 0.5f, size.height * 0.235f)
                lineTo(size.width * 0.62f, size.height * 0.175f)
                lineTo(size.width * 0.62f, size.height * 0.295f)
                close()
            }
            drawPath(bow, blush)
            drawCircle(ink, radius = unit(0.025f), center = at(0.5f, 0.235f))
        }
        else -> Unit
    }
}

/** Extras that sit over the face. */
private fun DrawScope.drawExtraFront(kind: Int, ink: Color, blush: Color) {
    when (Math.floorMod(kind, KodaAvatar.EXTRAS)) {
        2 -> {
            // A sparkle: a four-point star up and to the side.
            val cx = size.width * 0.74f
            val cy = size.height * 0.27f
            val long = unit(0.075f)
            val short = unit(0.022f)
            val star = Path().apply {
                moveTo(cx, cy - long)
                lineTo(cx + short, cy - short)
                lineTo(cx + long, cy)
                lineTo(cx + short, cy + short)
                lineTo(cx, cy + long)
                lineTo(cx - short, cy + short)
                lineTo(cx - long, cy)
                lineTo(cx - short, cy - short)
                close()
            }
            drawPath(star, blush)
        }
        5 -> {
            // Round glasses.
            drawCircle(ink, radius = unit(0.085f), center = at(EYE_LEFT, EYE_Y), style = line(0.025f))
            drawCircle(ink, radius = unit(0.085f), center = at(EYE_RIGHT, EYE_Y), style = line(0.025f))
            drawLine(ink, at(EYE_LEFT + 0.085f, EYE_Y), at(EYE_RIGHT - 0.085f, EYE_Y), strokeWidth = unit(0.025f))
        }
        else -> Unit
    }
}

/**
 * A profile's picture, whatever it is made of: the photo it was given, else
 * its Koda avatar, else (an account with neither chosen) the account's own
 * picture. A device-only profile always has a Koda avatar - the one it chose,
 * or the one its id works out to - so it is never the grey person icon.
 */
@Composable
fun ProfileAvatar(
    profile: Profile,
    card: KodaProfileCard,
    modifier: Modifier = Modifier,
    /** The account's picture as the session knows it, when that is fresher than the roster's. */
    accountAvatarUrl: String? = profile.avatarUrl
) {
    val photo = card.photoPath
    val chosen = card.avatar
    when {
        photo != null -> AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(java.io.File(photo))
                // The path is reused for every new photo; the version is what
                // tells the image cache it changed.
                .memoryCacheKey("$photo#${card.photoVersion}")
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(rememberAvatarShape(chosen?.shape ?: 0))
        )
        chosen != null -> KodaAvatarView(chosen, modifier)
        !profile.isLocal && !accountAvatarUrl.isNullOrBlank() -> AsyncImage(
            model = accountAvatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
        )
        else -> KodaAvatarView(KodaAvatar.forSeed(profile.id), modifier)
    }
}

/** [ProfileAvatar] for whichever profile is active, following a switch and an edit. */
@Composable
fun ActiveProfileAvatar(modifier: Modifier = Modifier, accountAvatarUrl: String? = null) {
    val context = LocalContext.current
    val profileManager = remember(context) { ProfileManager(context) }
    val store = remember(context) { KodaProfileStore(context) }
    val activeId by profileManager.activeProfileId.collectAsState()
    val profiles by profileManager.profiles.collectAsState()
    val cards by store.cards.collectAsState()
    val profile = profiles.firstOrNull { it.id == activeId } ?: return
    Box(modifier = modifier) {
        ProfileAvatar(
            profile = profile,
            card = cards[profile.id] ?: KodaProfileCard(),
            modifier = Modifier.fillMaxSize(),
            accountAvatarUrl = accountAvatarUrl ?: profile.avatarUrl
        )
    }
}
