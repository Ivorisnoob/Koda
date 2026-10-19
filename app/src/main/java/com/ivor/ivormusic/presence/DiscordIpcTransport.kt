package com.ivor.ivormusic.presence

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import com.discord.socialsdk.rpc.IDiscordRpcCallback
import com.discord.socialsdk.rpc.IDiscordRpcConnection
import com.discord.socialsdk.rpc.IDiscordRpcService
import com.ivor.ivormusic.util.KLog
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

/**
 * Discord transport over the Discord app's own RPC service
 * (`com.discord.socialsdk.rpc.IDiscordRpcService`), the route the Discord
 * Social SDK uses on Android for Rich Presence without account linking.
 *
 * Koda ships only the three binder interfaces, not the SDK: its AAR is a 30 MB
 * native voice library that also asks for the microphone, and none of that is
 * needed to hand the Discord app a `SET_ACTIVITY` frame.
 *
 * Verified October 2026 against `DiscordRpcClient` in Social SDK 1.10.19337
 * (`javap` on `libs/discord_partner_sdk.jar` inside the AAR): the interface
 * descriptors, method order and transaction codes of the stubs under
 * `com/discord/socialsdk/rpc/`, the bind action, the single `com.discord`
 * package, `BIND_AUTO_CREATE`, and the `connect(applicationId, "1", callback)`
 * handshake in which a null connection means Discord refused. This class
 * follows that client; re-check it against the SDK before changing the wire.
 *
 * No user token is involved; presence is published as Koda's application id.
 */
class DiscordIpcTransport private constructor(
    private val context: Context,
    private val serviceConnection: ServiceConnection,
    private val rpcConnection: IDiscordRpcConnection,
    /** Set from Discord's side too: its close callback and a lost service. */
    private val closed: AtomicBoolean,
) : DiscordTransport {

    private val released = AtomicBoolean(false)

    override val isOpen: Boolean
        get() = !closed.get() && runCatching { rpcConnection.asBinder().isBinderAlive }.getOrDefault(false)

    override fun setActivity(activity: JsonObject): Boolean {
        if (!isOpen) return false
        val frame = DiscordPresence.setActivityFrame(activity, Process.myPid(), nonce())
        return send(frame)
    }

    override fun clearActivity(): Boolean {
        if (!isOpen) return false
        val frame = DiscordPresence.clearActivityFrame(Process.myPid(), nonce())
        return send(frame)
    }

    override fun close() {
        // Its own flag: Discord closing the connection sets [closed], and the
        // binding still has to be given back exactly once after that.
        if (!released.compareAndSet(false, true)) return
        closed.set(true)
        runCatching { rpcConnection.disconnect() }
            .onFailure { KLog.d(TAG, "Discord RPC disconnect ignored: ${it.message}") }
        runCatching { context.unbindService(serviceConnection) }
            .onFailure { KLog.d(TAG, "Unbind ignored during close: ${it.message}") }
    }

    private fun send(frame: String): Boolean {
        return runCatching {
            rpcConnection.sendFrame(frame)
            true
        }.onFailure {
            KLog.w(TAG, "Discord RPC sendFrame failed; dropping connection: ${it.message}")
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "DiscordIpcTransport"
        private const val RPC_ACTION = "com.discord.socialsdk.rpc.IDiscordRpcService"
        private const val RPC_VERSION = "1"
        private const val DISCORD_PACKAGE = "com.discord"

        private val EVENT_FIELD = Regex("\"evt\"\\s*:\\s*\"([A-Z_]+)\"")
        private val COMMAND_FIELD = Regex("\"cmd\"\\s*:\\s*\"([A-Z_]+)\"")

        /**
         * Where an abandoned handshake finishes. The handshake is a blocking
         * binder call that no timeout can interrupt, so the wait is bounded and
         * the call is left to end here on its own.
         */
        private val handshakeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * The intent that reaches Discord's RPC service, or null when the
         * Discord app is not installed (or too old to have the service).
         * Resolved by the documented action only, the way the SDK does.
         */
        private fun resolveServiceIntent(context: Context): Intent? {
            val intent = Intent(RPC_ACTION).setPackage(DISCORD_PACKAGE)
            val pm = context.packageManager
            val resolved = if (Build.VERSION.SDK_INT >= 33) {
                pm.resolveService(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveService(intent, 0)
            }
            return intent.takeIf { resolved != null }
        }

        /**
         * Connects and handshakes with Discord's RPC service, or returns null.
         *
         * Both waits are bounded by [timeoutMs]: the bind, whose callbacks land
         * on the main thread and do no work there, and the handshake, which
         * runs off it. Every way out that does not hand a transport back gives
         * the binding back, so a retry never stacks a second one on a first.
         */
        suspend fun connectOrNull(context: Context, timeoutMs: Long = 3_000L): DiscordIpcTransport? {
            val appContext = context.applicationContext
            val intent = runCatching { resolveServiceIntent(appContext) }.getOrNull()
            if (intent == null) {
                KLog.d(TAG, "Discord RPC service not found (Discord missing or too old)")
                return null
            }

            val closed = AtomicBoolean(false)
            val binder = CompletableDeferred<IBinder?>()
            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, serviceBinder: IBinder?) {
                    binder.complete(serviceBinder)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    // Discord's process went away, and the connection with it.
                    KLog.d(TAG, "Discord RPC service disconnected")
                    closed.set(true)
                }

                override fun onBindingDied(name: ComponentName?) {
                    KLog.w(TAG, "Discord RPC binding died")
                    closed.set(true)
                    binder.complete(null)
                }

                override fun onNullBinding(name: ComponentName?) {
                    KLog.w(TAG, "Discord RPC null binding")
                    binder.complete(null)
                }
            }

            var handshake: Deferred<IDiscordRpcConnection?>? = null
            var handedOver = false
            try {
                val bound = runCatching {
                    appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                }.onFailure {
                    KLog.w(TAG, "Discord RPC bindService threw: ${it.message}")
                }.getOrDefault(false)
                if (!bound) {
                    KLog.w(TAG, "Discord RPC bindService refused")
                    return null
                }

                val serviceBinder = withTimeoutOrNull(timeoutMs) { binder.await() }
                if (serviceBinder == null) {
                    KLog.w(TAG, "Discord RPC bind gave no service within ${timeoutMs}ms")
                    return null
                }

                val rpcService = IDiscordRpcService.Stub.asInterface(serviceBinder)
                val callback = object : IDiscordRpcCallback.Stub() {
                    override fun onFrame(frame: String?) {
                        logFrame(frame)
                    }

                    override fun onClose(code: Int, message: String?) {
                        KLog.w(TAG, "Discord RPC closed: $code - $message")
                        closed.set(true)
                    }
                }
                val pending = handshakeScope.async {
                    runCatching {
                        rpcService.connect(DiscordPresence.APPLICATION_ID.toLong(), RPC_VERSION, callback)
                    }.onFailure {
                        KLog.w(TAG, "Discord RPC handshake failed: ${it.message}")
                    }.getOrNull()
                }
                handshake = pending
                val rpcConnection = withTimeoutOrNull(timeoutMs) { pending.await() }
                if (rpcConnection == null) {
                    KLog.w(TAG, "Discord RPC handshake rejected or timed out (signed out?)")
                    return null
                }

                KLog.d(TAG, "Discord RPC connected")
                handedOver = true
                return DiscordIpcTransport(appContext, serviceConnection, rpcConnection, closed)
            } finally {
                if (!handedOver) {
                    // A handshake that answers after it was given up on still
                    // opened a connection on Discord's side; close that one.
                    handshake?.let { late ->
                        late.invokeOnCompletion {
                            runCatching { late.getCompleted()?.disconnect() }
                        }
                    }
                    // Also after a refused bind: the system may have kept the
                    // connection registered.
                    runCatching { appContext.unbindService(serviceConnection) }
                        .onFailure { KLog.d(TAG, "Unbind ignored after failed connect: ${it.message}") }
                }
            }
        }

        /**
         * Discord's replies, by name only - a ready frame carries the user's
         * Discord identity, which has no business in a log. An error reply is
         * the one place a rejected activity shows up, so that one is kept.
         */
        private fun logFrame(frame: String?) {
            if (frame == null) return
            val event = EVENT_FIELD.find(frame)?.groupValues?.get(1)
            val command = COMMAND_FIELD.find(frame)?.groupValues?.get(1)
            if (event == "ERROR") {
                KLog.w(TAG, "Discord RPC error reply to $command: ${frame.take(300)}")
            } else {
                KLog.d(TAG, "Discord RPC frame: cmd=$command evt=$event")
            }
        }

        private fun nonce(): String = UUID.randomUUID().toString()
    }
}
