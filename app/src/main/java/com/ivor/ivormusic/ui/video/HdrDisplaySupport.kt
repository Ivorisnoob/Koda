package com.ivor.ivormusic.ui.video

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display

/**
 * Whether the device has a connected display capable of presenting HDR.
 *
 * [scar] This used to read `Configuration.isScreenHdr` alone, and both callers
 * are ViewModels holding the **Application** context. That configuration is not
 * display-adjusted: the HDR bit is filled in for a context that has been
 * associated with a display, and an application context reports no HDR on
 * phones that plainly have an HDR panel. The effect was silent and total -
 * `includeHdr` came back false, so the visionOS augmentation never ran, the
 * ladder never carried an HDR entry, and the quality sheet never showed its
 * HDR/Standard tabs no matter what the setting said.
 *
 * The display's own capabilities are the authority. The configuration flag
 * stays as a fallback, since it is the one signal that survives a display this
 * process cannot enumerate.
 */
internal fun hasHdrDisplay(context: Context): Boolean {
    val display = runCatching {
        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        manager?.getDisplay(Display.DEFAULT_DISPLAY)
    }.getOrNull()

    if (display != null) {
        val supportedHdrTypes = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // getHdrCapabilities is deprecated at 34 and reports the types
                // the display can decode; the mode reports what it can actually
                // present in its current configuration, which is the question.
                display.mode.supportedHdrTypes
            } else {
                @Suppress("DEPRECATION")
                display.hdrCapabilities?.supportedHdrTypes
            }
        }.getOrNull()
        if (supportedHdrTypes != null && supportedHdrTypes.isNotEmpty()) return true
    }

    return context.resources.configuration.isScreenHdr
}
