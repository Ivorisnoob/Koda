package com.ivor.ivormusic.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The chrome every queue row shares: the lift while it is being dragged, and
 * swipe-to-remove.
 *
 * The three queue views draw very different rows - the Editorial one is serif
 * and italic, Gesture has its own language - so this deliberately wraps a row
 * rather than drawing one. What is shared is the *behaviour*, which is the part
 * that was inconsistent: reordering existed in one view of the three, removal in
 * two, and neither had any feedback that the gesture had been recognised.
 *
 * @param removeEnabled false for the last song in the queue. The queue never
 * empties itself, so the swipe simply does not arm rather than snapping back
 * from a gesture that looked like it worked.
 */
@Composable
fun QueueRowContainer(
    isDragging: Boolean,
    dragOffset: Float,
    removeEnabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Springs, per the house convention for anything touch-driven. The lift is
    // small on purpose: the row has to read as picked up without covering the
    // rows it is being dragged past.
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "queueRowLift"
    )
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "queueRowScale"
    )

    // Read through, never captured. `rememberSwipeToDismissBoxState` holds the
    // lambda it was first given, and [onRemove] closes over the row's index -
    // which changes the moment anything is reordered above it. Captured, a
    // swipe after a reorder removes whichever song used to be at this position.
    val currentRemove by rememberUpdatedState(onRemove)
    val currentRemoveEnabled by rememberUpdatedState(removeEnabled)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val dismissed = value != SwipeToDismissBoxValue.Settled
            if (dismissed && currentRemoveEnabled) currentRemove()
            // Never let the box keep a dismissed row: the queue is the source of
            // truth and the item leaves the list on its own once it does. Saying
            // yes here as well would animate a row out and then leave a second,
            // stale one behind if the removal were refused.
            false
        }
    )

    Box(
        modifier = modifier
            // Above its neighbours only while it is actually lifted, so the
            // shadow falls on them rather than under them.
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffset
                scaleX = scale
                scaleY = scale
            }
    ) {
        // The swipe is disarmed while the row is being dragged. Both gestures
        // start from the same finger, and a reorder that ends in an accidental
        // removal is the worst outcome either of them can produce.
        val swipeArmed = removeEnabled && !isDragging
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = swipeArmed,
            enableDismissFromEndToStart = swipeArmed,
            backgroundContent = { RemoveSwipeBackground(dismissState.targetValue) }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation, RoundedCornerShape(20.dp), clip = false)
            ) {
                content()
            }
        }
    }
}

/**
 * What sits behind a row being swiped away. Tinted with the error container
 * only once the swipe has passed the threshold, so a hesitant drag does not
 * flash red at someone who has not decided yet.
 */
@Composable
private fun RemoveSwipeBackground(target: SwipeToDismissBoxValue) {
    val armed = target != SwipeToDismissBoxValue.Settled
    val alpha by animateFloatAsState(
        targetValue = if (armed) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "queueRemoveBackground"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (armed) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "queueRemoveIcon"
    )

    val alignment = when (target) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        else -> Alignment.CenterEnd
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = alpha))
            .padding(horizontal = 24.dp),
        contentAlignment = alignment
    ) {
        Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = alpha),
            modifier = Modifier
                .size(24.dp)
                .scale(iconScale)
        )
    }
}

/**
 * The grab target for reordering a queue row.
 *
 * A real 44dp touch target around a 20dp glyph, because this is the only
 * reliable way to start a drag - see [queueDragHandle] - and a handle people
 * miss is a feature they do not have. It carries no click of its own, so
 * nothing competes with the drag for the gesture.
 */
@Composable
fun QueueDragHandle(
    state: QueueReorderState,
    rowKey: Any,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .queueDragHandle(state, rowKey),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = "Drag to reorder",
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Undo for a song swiped out of the queue.
 *
 * Local to the queue screen rather than routed through the app-wide host in
 * `MainActivity`: that one exists because a dismissal can be taken from five
 * surfaces, two of them overlays. This can only be taken from one screen, and
 * that screen is itself an overlay above the NavHost, so its own host is both
 * simpler and the only one guaranteed to be on top.
 */
class QueueRemovalController internal constructor(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
    private val onUndo: () -> Unit
) {
    fun onRemoved(title: String) {
        scope.launch {
            // Replace rather than queue: swiping three rows in a row should
            // leave one snackbar about the last of them, not three in sequence.
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(
                message = "Removed $title",
                actionLabel = "Undo",
                withDismissAction = false,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }
}

@Composable
fun rememberQueueRemoval(onUndo: () -> Unit): QueueRemovalController {
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentUndo by rememberUpdatedState(onUndo)
    return remember(hostState, scope) {
        QueueRemovalController(hostState, scope) { currentUndo() }
    }
}
