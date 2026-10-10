package com.ivor.ivormusic.widget

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.unit.ColorProvider
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.R

/**
 * Vinyl: a record half out of its sleeve, turning while the music plays, with
 * a tonearm that comes down onto it and lifts off again. The sleeve is the
 * cover; play and next sit beside it. No card: it stands on the wallpaper.
 *
 * **The cover is the sleeve because the cover cannot turn.** A launcher will
 * rotate a drawable that ships in the app - an indeterminate `ProgressBar`
 * given a rotate drawable, which it animates on its own frame clock
 * ([R.layout.widget_vinyl_spin]) - but not a bitmap handed over at run time,
 * so album art on a spinning label was never available. Putting the art on the
 * sleeve is where it belongs on a real record anyway, and leaves the label
 * free to be something that can spin.
 *
 * **Only the label's print turns.** On a turntable the light on the grooves
 * stays where the lamp is; what moves is what is printed on the record. So the
 * sheen is a still layer and the turning one is the label's type and a scuff
 * on the grooves, both off-centre so the rotation reads. Paused, the same
 * drawable is drawn still.
 *
 * Every layer is an alpha mask tinted with a theme role, as in [PixelWidget]:
 * the record is drawn, not photographed, so it follows the palette. The
 * rotation's tint needs API 31; on 30 the record is drawn still rather than
 * turning in the wrong colour.
 */
class VinylWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(250.dp, 110.dp),
            DpSize(320.dp, 110.dp),
            DpSize(320.dp, 180.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PlayerWidgetHost.ensureSeeded(context)
        provideContent {
            val ui by PlayerWidgetHost.state.collectAsState()
            GlanceTheme(colors = KodaWidgetTheme.colors(context)) {
                VinylContent(ui)
            }
        }
    }
}

private val VINYL_PADDING = 8.dp

@Composable
private fun VinylContent(ui: PlayerWidgetUi) {
    if (!ui.snapshot.hasMedia) {
        WidgetSurface { WidgetEmptyState() }
        return
    }

    val size = LocalSize.current
    val playing = ui.snapshot.isPlaying
    // Measured against the bucket, like every layout that can clip: the sleeve
    // takes the cell's height, the record shows half its width past it, the
    // arm stands a quarter of a record further out, and the transport gets
    // what is left.
    // The stage is about 1.75 sleeves wide, so the width caps the sleeve too:
    // a tall bucket would otherwise push the buttons out of the cell.
    val widthForStage = size.width - VINYL_PADDING * 2 - 10.dp - 56.dp
    val sleeve = minOf(size.height - VINYL_PADDING * 2, (widthForStage + 4.dp) / 1.75f, 148.dp)
    val record = sleeve - 6.dp
    val armReach = record / 4
    val stage = sleeve + record / 2 + armReach
    val roomy = sleeve >= 120.dp
    val hero = if (roomy) 56.dp else 44.dp
    val side = if (roomy) 48.dp else 36.dp

    // No card, like Bloom: the sleeve, the record and the buttons are all
    // opaque and stand on the wallpaper by themselves. That is also why there
    // is no title here - theme-coloured text on a wallpaper nobody chose is
    // the one thing that could not supply its own contrast, and the cover on
    // the sleeve already says what is playing.
    WidgetSurface(background = null, openAppOnTap = false) {
        Row(
            modifier = GlanceModifier.padding(VINYL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = GlanceModifier.width(stage).height(sleeve)) {
                Box(
                    modifier = GlanceModifier.fillMaxSize().padding(end = armReach),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    VinylRecord(size = record, playing = playing)
                }
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())) {
                            WidgetArtwork(ui.artwork, size = sleeve, shape = ArtworkShape.ROUNDED)
                        }
                        // The sleeve's shadow on the record beside it.
                        Image(
                            provider = ImageProvider(R.drawable.vinyl_sleeve_shadow),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            colorFilter = ColorFilter.tint(recordColor()),
                            modifier = GlanceModifier.width(10.dp).height(record - 8.dp),
                        )
                    }
                }
                // Last, so the arm lies over the record: on the grooves while
                // it plays, parked beside the disc when it does not.
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    VinylArm(width = record + armReach, height = record, onRecord = playing)
                }
            }
            Spacer(modifier = GlanceModifier.width(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HeroPlayButton(isPlaying = playing, size = hero)
                Spacer(modifier = GlanceModifier.height(4.dp))
                WidgetSquareButton(
                    iconRes = R.drawable.ic_media_next,
                    description = widgetString(R.string.widget_action_next),
                    action = actionRunCallback<SkipNextAction>(),
                    size = side,
                )
            }
        }
    }
}

/**
 * The tonearm, in one of its two poses. Three masks: a soft shadow that also
 * gives it an edge on any wallpaper, the metal, and the headshell and pivot
 * cap in the accent colour.
 */
@Composable
private fun VinylArm(width: Dp, height: Dp, onRecord: Boolean) {
    val colors = GlanceTheme.colors
    val dark = KodaWidgetTheme.isDark(LocalContext.current)
    val layers = if (onRecord) {
        listOf(
            R.drawable.vinyl_arm_on_shadow to recordColor(),
            R.drawable.vinyl_arm_on_metal to (if (dark) colors.onSurface else colors.surface),
            R.drawable.vinyl_arm_on_accent to colors.primary,
        )
    } else {
        listOf(
            R.drawable.vinyl_arm_off_shadow to recordColor(),
            R.drawable.vinyl_arm_off_metal to (if (dark) colors.onSurface else colors.surface),
            R.drawable.vinyl_arm_off_accent to colors.primary,
        )
    }
    Box(modifier = GlanceModifier.width(width).height(height)) {
        layers.forEach { (mask, color) -> VinylLayer(mask, color, ContentScale.FillBounds) }
    }
}

/** A record is dark whichever way the theme faces. */
@Composable
private fun recordColor(): ColorProvider {
    val dark = KodaWidgetTheme.isDark(LocalContext.current)
    return if (dark) GlanceTheme.colors.background else GlanceTheme.colors.onSurface
}

@Composable
private fun VinylRecord(size: Dp, playing: Boolean) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    val dark = KodaWidgetTheme.isDark(context)
    // The light on the grooves is light in both themes, for the same reason.
    val sheen = if (dark) colors.onSurface else colors.surface
    val print = colors.onPrimaryContainer

    Box(
        modifier = GlanceModifier
            .size(size)
            .semantics {
                contentDescription = context.getString(
                    if (playing) R.string.widget_action_pause else R.string.widget_action_play
                )
            }
            .clickable(actionRunCallback<TogglePlaybackAction>()),
        contentAlignment = Alignment.Center,
    ) {
        VinylLayer(R.drawable.vinyl_disc, recordColor())
        VinylLayer(R.drawable.vinyl_grooves, sheen)
        VinylLayer(R.drawable.vinyl_label, colors.primaryContainer)
        if (playing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val tint = ColorStateList.valueOf(print.read().toArgb())
            val spin = RemoteViews(context.packageName, R.layout.widget_vinyl_spin).apply {
                setColorStateList(R.id.vinyl_spin, "setIndeterminateTintList", tint)
            }
            AndroidRemoteViews(remoteViews = spin, modifier = GlanceModifier.fillMaxSize())
        } else {
            VinylLayer(R.drawable.vinyl_label_marks, print)
        }
        VinylLayer(R.drawable.vinyl_spindle, recordColor())
    }
}

/** One layer of the record: a mask the full size of the disc, tinted. */
@Composable
private fun VinylLayer(
    @DrawableRes mask: Int,
    color: ColorProvider,
    scale: ContentScale = ContentScale.Fit,
) {
    Image(
        provider = ImageProvider(mask),
        contentDescription = null,
        contentScale = scale,
        colorFilter = ColorFilter.tint(color),
        modifier = GlanceModifier.fillMaxSize(),
    )
}

class VinylWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VinylWidget()
}
