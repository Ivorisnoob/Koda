package com.ivor.ivormusic.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import com.ivor.ivormusic.data.LocalVideoAccess

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

/**
 * Read access to the device's video files, which on Android 14 is three states
 * rather than two.
 *
 * [LocalVideoAccess.PARTIAL] is the one worth carrying: the user picked some
 * files rather than granting the library, and everything works - the queries
 * simply return only what they picked. Treating that as a denial would show a
 * permission wall over a list of videos the user had just chosen to share,
 * while treating it as a full grant would leave someone staring at four videos
 * with no way to add more.
 */
@Stable
class VideoMediaAccessState internal constructor(
    access: LocalVideoAccess,
    shouldShowRationale: Boolean,
    private val requestAccess: () -> Unit,
) {
    var access: LocalVideoAccess = access
        internal set

    var shouldShowRationale: Boolean = shouldShowRationale
        internal set

    val isReadable: Boolean
        get() = access != LocalVideoAccess.DENIED

    /**
     * Shows the system dialog. Under a partial grant this reopens the photo
     * picker, which is how the selection is widened without a trip to settings.
     */
    fun launchRequest() = requestAccess()
}

/**
 * Remembers the current [VideoMediaAccessState].
 *
 * READ_MEDIA_VISUAL_USER_SELECTED is requested alongside READ_MEDIA_VIDEO on
 * Android 14 and above because the system only offers the "Select videos"
 * choice when the app declares it; asking for the broad permission alone gives
 * the user all-or-nothing.
 */
@Composable
fun rememberVideoMediaAccessState(): VideoMediaAccessState {
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity

    val requested = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO)

            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun held(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun checkAccess(): LocalVideoAccess = when {
        held(requested.first()) -> LocalVideoAccess.FULL
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            held(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> LocalVideoAccess.PARTIAL

        else -> LocalVideoAccess.DENIED
    }

    // Only the broad permission has a rationale worth showing. The selected-
    // access one is never permanently refused in the same way, since every
    // request of it reopens the picker.
    fun checkRationale() =
        activity?.shouldShowRequestPermissionRationale(requested.first()) ?: false

    var access by remember { mutableStateOf(checkAccess()) }
    var shouldShowRationale by remember { mutableStateOf(checkRationale()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Read the real state rather than trusting the result map: a partial
        // grant reports the broad permission as denied, which is not the same
        // thing as having no access.
        access = checkAccess()
        shouldShowRationale = checkRationale()
    }

    val state = remember {
        VideoMediaAccessState(
            access = access,
            shouldShowRationale = shouldShowRationale,
            requestAccess = { launcher.launch(requested) },
        )
    }
    state.access = access
    state.shouldShowRationale = shouldShowRationale

    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = checkAccess()
                shouldShowRationale = checkRationale()
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    return state
}
