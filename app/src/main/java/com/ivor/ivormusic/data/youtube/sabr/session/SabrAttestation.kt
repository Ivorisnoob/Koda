/*
 * Android owner of the attestation stack: binds the pure minter to the login
 * session, the Local-Only gate, the shared page runtime and an OkHttp edge.
 * Rotation policy lives here by construction - the minter re-inits on profile
 * or login-generation moves (never on cookie bytes), and every abandonment
 * advances attestationGeneration, so [currentIdentity] alone decides whether a
 * descriptor from an earlier mint is still usable. Mid-session attestation
 * failures therefore resolve by invalidating and re-resolving, not by swapping
 * token bytes under a live session.
 */
package com.ivor.ivormusic.data.youtube.sabr.session

import android.content.Context
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.youtube.sabr.model.SabrIdentity
import okhttp3.Call

/** Page UA shared by the bootstrap fetch and the runtime document. */
internal const val SABR_PAGE_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"

internal class SabrAttestation(context: Context, calls: Call.Factory) {
    private val appContext = context.applicationContext
    private val sessions = SessionManager(appContext)

    val minter = SabrTokenMinter(
        sessionNow = { sessions.captureSession() },
        localOnly = { ThemePreferences.isLocalOnly(appContext) },
        transport = SabrAttestationHttp(calls),
        dom = SabrWebViewRuntime.get(appContext),
    )

    /** Identity snapshot for descriptors minted from this point on. */
    fun currentIdentity(): SabrIdentity {
        val session = sessions.captureSession()
        return SabrIdentity(
            profileId = session?.profileId ?: sessions.activeProfileId(),
            loginGeneration = session?.generation ?: 0,
            attestationGeneration = minter.attestationGeneration,
        )
    }

    fun warmUp() = minter.warmUp()

    fun invalidate() = minter.invalidate()
}
