package com.ivor.ivormusic.platform

import android.content.Context

/**
 * Application context holder, initialized in Application.onCreate().
 * Used by platform implementations that need a Context (Coil, Palette, etc.)
 */
lateinit var applicationContext: Context
    internal set

fun initKodaPlatform(context: Context) {
    applicationContext = context.applicationContext
}
