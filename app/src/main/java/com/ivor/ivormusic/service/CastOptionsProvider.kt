package com.ivor.ivormusic.service

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.CastMediaControlIntent

/**
 * Declares Koda's cast receiver to Google Play services.
 *
 * Registered through the manifest meta-data
 * `com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME`, which is
 * how the Cast framework discovers its configuration when Koda lazily starts
 * it. The class must exist and answer without touching anything else in the
 * app; normal local playback never needs to initialize the framework.
 *
 * The [CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID] Default
 * Media Receiver is deliberate: it plays progressive MP4, DASH and HLS - every
 * source video mode can resolve - needs no Cast Developer Console registration,
 * and renders WebVTT text tracks sent with the load. A styled custom receiver
 * would be a paid registration plus a hosted web app for no playback gain.
 *
 * [setStopReceiverApplicationWhenEndingSession] makes "disconnect" from the
 * device sheet mean what it says: the TV goes back to its idle screen instead
 * of sitting on a paused poster for hours, which matters because the receiver
 * keeps playing audio to the room until told otherwise.
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(
                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            )
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
