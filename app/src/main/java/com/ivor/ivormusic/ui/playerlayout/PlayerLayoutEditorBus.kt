package com.ivor.ivormusic.ui.playerlayout

import com.ivor.ivormusic.data.playerlayout.LayoutPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the layout editor was opened to do. */
sealed interface LayoutEditorRequest {
    /** Change a saved layout. */
    data class Edit(val layoutId: String) : LayoutEditorRequest

    /**
     * Start a new layout from [preset]. [choose] opens the "start from" picker
     * over the editor, with [preset] marked as the one in use.
     */
    data class New(val preset: LayoutPreset, val choose: Boolean = true) : LayoutEditorRequest
}

/**
 * Opens the layout editor from wherever the way in is: the player's menu or
 * the Custom player page in Settings. The editor itself is hosted once, in
 * `MainActivity` above both players, where the player's ViewModel is.
 *
 * A transient UI bus like `VisualizerBus`, not repository state: it holds only
 * whether the editor is open and on what.
 */
object PlayerLayoutEditorBus {
    private val _request = MutableStateFlow<LayoutEditorRequest?>(null)
    val request: StateFlow<LayoutEditorRequest?> = _request.asStateFlow()

    fun open(request: LayoutEditorRequest) {
        _request.value = request
    }

    fun close() {
        _request.value = null
    }
}
