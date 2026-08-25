package com.ivor.ivormusic.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ivor.ivormusic.data.ThemePreferences

/**
 * How much the app vibrates back. Off silences everything; the three levels
 * are a dial on intensity, not on coverage - Subtle keeps the events that mark
 * commitments (a skip landing, a sheet opening) and drops the chatter
 * (detents, thresholds), so quieter never means confusing.
 */
enum class HapticsLevel {
    OFF, SUBTLE, BALANCED, EXPRESSIVE;

    companion object {
        const val DEFAULT = "balanced"

        fun fromPref(value: String?): HapticsLevel = when (value) {
            "off" -> OFF
            "subtle" -> SUBTLE
            "expressive" -> EXPRESSIVE
            else -> BALANCED
        }

        fun toPref(level: HapticsLevel): String = when (level) {
            OFF -> "off"
            SUBTLE -> "subtle"
            EXPRESSIVE -> "expressive"
            BALANCED -> "balanced"
        }
    }
}

/**
 * One semantic vocabulary for every touch response in the app.
 *
 * Call sites speak intent - [confirm], [tick], [threshold], [longPress],
 * [toggle], [reject] - and this class alone decides which platform vibration
 * pattern that means at the user's chosen intensity. Routing every call site
 * through here is what makes a setting possible at all: before it existed,
 * fifteen files each picked their own type by hand and none of them could
 * hear the switch.
 *
 * The intensity dial works by dropping and stepping events, not by scaling
 * amplitude (the Compose API exposes patterns, not strength):
 * - **Subtle** keeps only commitments, using lighter patterns for them.
 *   Detents and thresholds go silent - they are texture, not information.
 * - **Balanced** is the default full vocabulary.
 * - **Expressive** steps every event one pattern heavier, for people who
 *   read the phone through their hand.
 */
class KodaHaptics(
    private val feedback: HapticFeedback,
    private val level: () -> HapticsLevel,
) {
    private fun perform(type: HapticFeedbackType) {
        if (level() != HapticsLevel.OFF) {
            feedback.performHapticFeedback(type)
        }
    }

    private fun stepped(heavy: HapticFeedbackType, medium: HapticFeedbackType, light: HapticFeedbackType) {
        when (level()) {
            HapticsLevel.EXPRESSIVE -> perform(heavy)
            HapticsLevel.BALANCED -> perform(medium)
            HapticsLevel.SUBTLE -> perform(light)
            HapticsLevel.OFF -> {}
        }
    }

    private fun atLeast(minimum: HapticsLevel, type: HapticFeedbackType) {
        if (level() >= minimum) perform(type)
    }

    /** Frequent detents: style-wheel steps, reorder crossings, slider slats. */
    fun tick() = stepped(HapticFeedbackType.Confirm, HapticFeedbackType.ContextClick, HapticFeedbackType.TextHandleMove)

    /** A drag crossed an edge that matters: 2x boost armed, volume/brightness rails. */
    fun threshold() = atLeast(HapticsLevel.BALANCED, HapticFeedbackType.GestureThresholdActivate)

    /** A long press landed: a sheet opened or a row was grabbed for dragging. */
    fun longPress() = stepped(HapticFeedbackType.LongPress, HapticFeedbackType.LongPress, HapticFeedbackType.ContextClick)

    /** An action committed: skip landed, item queued, download started. */
    fun confirm() = stepped(HapticFeedbackType.Confirm, HapticFeedbackType.Confirm, HapticFeedbackType.ContextClick)

    /** Something the user asked for cannot happen. */
    fun reject() = stepped(HapticFeedbackType.Reject, HapticFeedbackType.Reject, HapticFeedbackType.LongPress)

    /** A binary control flipped, with [on] as the state being switched to. */
    fun toggle(on: Boolean) = stepped(
        if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
        if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
        HapticFeedbackType.ContextClick,
    )

    /** Light navigation taps: tab switches, mode toggle. */
    fun subtle() = stepped(HapticFeedbackType.ContextClick, HapticFeedbackType.ContextClick, HapticFeedbackType.TextHandleMove)

    /**
     * Drop-in replacement for the platform call, so existing call sites keep
     * their shape while their intent gets routed through the level dial.
     * Maps raw types onto the semantic vocabulary above.
     */
    fun performHapticFeedback(type: HapticFeedbackType) {
        when (type) {
            HapticFeedbackType.Confirm -> confirm()
            HapticFeedbackType.Reject -> reject()
            HapticFeedbackType.LongPress -> longPress()
            HapticFeedbackType.TextHandleMove -> tick()
            HapticFeedbackType.SegmentFrequentTick -> tick()
            HapticFeedbackType.ContextClick -> subtle()
            HapticFeedbackType.GestureThresholdActivate -> threshold()
            HapticFeedbackType.ToggleOn -> toggle(true)
            HapticFeedbackType.ToggleOff -> toggle(false)
            else -> perform(type)
        }
    }
}

/**
 * The one factory every surface uses. Reads the setting through the flow so a
 * change in Settings lands mid-session without recomposition gymnastics - the
 * wrapper is rebuilt when the level changes, and every call site already holds
 * it in a remember.
 */
@Composable
fun rememberKodaHaptics(): KodaHaptics {
    val context = LocalContext.current
    val themePreferences = remember(context) { ThemePreferences(context) }
    val levelPref by themePreferences.hapticsLevel.collectAsState()
    val feedback = LocalHapticFeedback.current
    // Read through a state holder rather than capturing the value: some
    // holders (QueueReorderState) remember this wrapper across level changes,
    // and a captured value would leave them on the old intensity forever.
    val currentLevel by androidx.compose.runtime.rememberUpdatedState(levelPref)
    return remember(feedback) {
        KodaHaptics(feedback) { HapticsLevel.fromPref(currentLevel) }
    }
}
