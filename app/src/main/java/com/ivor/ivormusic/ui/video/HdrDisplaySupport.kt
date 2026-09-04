package com.ivor.ivormusic.ui.video

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display

/**
 * Whether HDR is worth requesting for this device's display.
 *
 * [scar] HDR shipped gated on the `prefer_hdr_video` preference alone and
 * worked. A later change added a display check on top of it, reading
 * `Configuration.isScreenHdr` - and both callers are ViewModels holding the
 * **Application** context, whose configuration is not display-adjusted. The bit
 * is populated for a context associated with a display, so it read false on
 * phones with an HDR panel, `includeHdr` came back false, the visionOS
 * augmentation never ran, and HDR disappeared from the quality sheet for
 * everyone who had been using it. Nothing failed and nothing logged. The unit
 * tests could not see it either: they build quality lists directly and never
 * touch a Context.
 *
 * So this asks the display itself, and **fails open**. A positive answer from
 * the display is trusted in both directions; anything else honours the
 * preference, because that preference is explicit opt-in, off by default, and
 * withholding what someone deliberately switched on is a worse failure than
 * fetching a ladder that turns out not to help. Only a display we can
 * positively confirm has no HDR mode skips the extra request.
 */
internal fun hasHdrDisplay(context: Context): Boolean {
    val supportedHdrTypes = supportedHdrTypesOrNull(context)
        // No trustworthy signal - do what the build before the display check
        // did, and take the preference at its word.
        ?: return true
    return supportedHdrTypes.isNotEmpty()
}

private fun supportedHdrTypesOrNull(context: Context): IntArray? = runCatching {
    val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    val display = manager?.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // getHdrCapabilities is deprecated at 34. The mode reports what the
        // display can present as currently configured, which is the question
        // being asked here.
        display.mode.supportedHdrTypes
    } else {
        @Suppress("DEPRECATION")
        display.hdrCapabilities?.supportedHdrTypes
    }
}.getOrNull()
