package com.ivor.ivormusic.ui.components

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * A single runtime permission, observed as Compose state.
 *
 * The app's replacement for Accompanist Permissions, which is in maintenance
 * upstream now that the platform covers the same ground. Deliberately narrow:
 * the two things call sites actually need are whether the permission is held
 * and a way to ask for it.
 *
 * Anything that wants a rationale (a second refusal means the system will not
 * show the dialog again, so the app has to send the user to system settings)
 * should read [shouldShowRationale] rather than tracking denials itself.
 */
@Stable
class PermissionState internal constructor(
    val permission: String,
    isGranted: Boolean,
    shouldShowRationale: Boolean,
    private val requestPermission: () -> Unit
) {
    /** Whether the permission is held right now. Recomposes when it changes. */
    var isGranted: Boolean = isGranted
        internal set

    /**
     * True once the user has denied at least once but has not permanently
     * blocked the permission, which is the window where explaining why it is
     * needed still leads somewhere.
     */
    var shouldShowRationale: Boolean = shouldShowRationale
        internal set

    /** Shows the system permission dialog. A no-op if already granted. */
    fun launchPermissionRequest() = requestPermission()
}

/**
 * Remembers a [PermissionState] for [permission].
 *
 * The status is re-read on every `ON_RESUME` as well as from the request
 * result, because a permission can also be granted or revoked from system
 * settings while the app is in the background, and nothing calls back for that.
 */
@Composable
fun rememberPermissionState(permission: String): PermissionState {
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity

    fun checkGranted() = ContextCompat.checkSelfPermission(context, permission) ==
        PackageManager.PERMISSION_GRANTED

    fun checkRationale() = activity?.shouldShowRequestPermissionRationale(permission) ?: false

    var isGranted by remember(permission) { mutableStateOf(checkGranted()) }
    var shouldShowRationale by remember(permission) { mutableStateOf(checkRationale()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isGranted = granted
        // A denial here is what flips rationale on, and a second denial is what
        // flips it back off again while leaving the permission ungranted: that
        // is the "don't ask again" state, and the only remaining route is
        // system settings.
        shouldShowRationale = checkRationale()
    }

    val state = remember(permission) {
        PermissionState(
            permission = permission,
            isGranted = isGranted,
            shouldShowRationale = shouldShowRationale,
            requestPermission = { launcher.launch(permission) }
        )
    }
    state.isGranted = isGranted
    state.shouldShowRationale = shouldShowRationale

    DisposableEffect(activity, permission) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isGranted = checkGranted()
                shouldShowRationale = checkRationale()
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    return state
}
