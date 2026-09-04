package com.ivor.ivormusic.ui.video

import android.content.Context

/** Whether the device has a connected display capable of presenting HDR. */
internal fun hasHdrDisplay(context: Context): Boolean =
    context.resources.configuration.isScreenHdr
