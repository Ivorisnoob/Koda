package com.ivor.ivormusic.service

internal enum class HeadsetButtonAction {
    TOGGLE_PLAY_PAUSE,
    NEXT,
    PREVIOUS,
}

/**
 * Resolves presses of a single wired-headset button without changing Media3's
 * handling of explicit play, pause, next or previous media keys.
 */
internal class HeadsetButtonSequence(
    private val timeoutMs: Long,
) {
    data class TapResult(
        val completedAction: HeadsetButtonAction?,
        val awaitingMore: Boolean,
    )

    private var tapCount = 0
    private var lastTapTimeMs = Long.MIN_VALUE

    fun onTap(eventTimeMs: Long): TapResult {
        val completedBeforeTap = if (
            tapCount > 0 &&
            (eventTimeMs < lastTapTimeMs || eventTimeMs - lastTapTimeMs > timeoutMs)
        ) {
            consumePending()
        } else {
            null
        }

        tapCount += 1
        lastTapTimeMs = eventTimeMs

        if (tapCount == 3) {
            clear()
            return TapResult(
                completedAction = HeadsetButtonAction.PREVIOUS,
                awaitingMore = false,
            )
        }

        return TapResult(
            completedAction = completedBeforeTap,
            awaitingMore = true,
        )
    }

    fun consumePending(): HeadsetButtonAction? {
        val action = when (tapCount) {
            1 -> HeadsetButtonAction.TOGGLE_PLAY_PAUSE
            2 -> HeadsetButtonAction.NEXT
            3 -> HeadsetButtonAction.PREVIOUS
            else -> null
        }
        clear()
        return action
    }

    fun clear() {
        tapCount = 0
        lastTapTimeMs = Long.MIN_VALUE
    }
}
