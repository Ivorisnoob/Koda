package com.ivor.ivormusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.ivor.ivormusic.data.CrashReporter
import com.ivor.ivormusic.data.LocalVideoThumbnail
import com.ivor.ivormusic.data.LocalVideoThumbnailFetcher
import com.ivor.ivormusic.data.youtube.sabr.bridge.selectAudio
import com.ivor.ivormusic.data.youtube.sabr.bridge.selectVideo
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.runBlocking

class IvorMusicApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Installed before anything else can crash, so the bug reporter has a
        // file to offer on the next launch. It wraps - never replaces - the
        // platform handler.
        CrashReporter.install(this)
        // Reconcile the persisted periodic job with the user's current opt-in.
        // The setting handler does the same immediately when the value changes.
        com.ivor.ivormusic.work.UploadCheckWorker.sync(this)
        com.ivor.ivormusic.work.ScheduledBackupWorker.sync(this)
        // TEMPORARY 5b-live validation probe (revert before merge): DEBUG-only,
        // once per process start, on a background thread. Exercises the real
        // BotGuard + GenerateIT mint, a token-bound resolve and a session
        // preparation through the app WebView, logging only counts and verdicts
        // - never tokens, cookies, visitor ids or URLs.
        if (BuildConfig.DEBUG) {
            Thread({
                runBlocking {
                try {
                    val attestation =
                        com.ivor.ivormusic.data.youtube.sabr.session.SabrAttestation.get(
                            this@IvorMusicApplication,
                        )
                    attestation.warmUp()
                    val token = attestation.minter.mint("dQw4w9WgXcQ")
                    KLog.i(
                        "SabrMintProbe",
                        "mint ok clientVersion=${token.clientVersion} " +
                            "generation=${attestation.minter.attestationGeneration}",
                    )
                    val repository =
                        com.ivor.ivormusic.data.YouTubeRepository(this@IvorMusicApplication)
                    val descriptor =
                        com.ivor.ivormusic.data.youtube.sabr.SabrResolver(repository, attestation)
                            .resolve("dQw4w9WgXcQ")
                    val audio = descriptor.selectAudio()
                    val hours =
                        (descriptor.expiresAtMs - System.currentTimeMillis()) / 3600000
                    KLog.i(
                        "SabrMintProbe",
                        "resolve ok formats=${descriptor.formats.size} " +
                            "expiresH=$hours " +
                            "mwebUrl=${descriptor.serverAbrStreamingUrl.contains("c=MWEB")} " +
                            "ustreamer=${descriptor.ustreamerConfig.isNotEmpty()}",
                    )
                    if (audio == null) {
                        KLog.w("SabrMintProbe", "resolve ok but no audio rendition")
                    } else {
                        val spoolDir = java.io.File(cacheDir, "sabr-probe-${System.nanoTime()}")
                        val spool =
                            com.ivor.ivormusic.data.youtube.sabr.media.SabrSpool(spoolDir)
                        // Decorator that captures the first bytes of a non-200
                        // streaming answer for diagnosis, then lets the session
                        // handle the status exactly as production does.
                        val diagnosing = object :
                            com.ivor.ivormusic.data.youtube.sabr.session.SabrTransport {
                            private val inner =
                                com.ivor.ivormusic.data.youtube.sabr.session.OkHttpSabrTransport(
                                    attestation.http,
                                )

                            override fun newCall(
                                url: String,
                                headers: Map<String, String>,
                                body: ByteArray,
                            ): com.ivor.ivormusic.data.youtube.sabr.session.SabrCall {
                                val call = inner.newCall(url, headers, body)
                                return object : com.ivor.ivormusic.data.youtube.sabr.session.SabrCall by call {
                                    override fun execute():
                                        com.ivor.ivormusic.data.youtube.sabr.session.SabrHttpResponse {
                                        val response = call.execute()
                                        if (response.status != 200) {
                                            val snippet = try {
                                                val buf = ByteArray(200)
                                                val read = response.body.read(buf)
                                                String(buf, 0, maxOf(0, read), Charsets.UTF_8)
                                                    .replace(Regex("\\s+"), " ")
                                            } catch (_: Exception) {
                                                "<unreadable>"
                                            } finally {
                                                runCatching { response.close() }
                                            }
                                            KLog.w(
                                                "SabrMintProbe",
                                                "streaming http=${response.status} " +
                                                    "type=${response.contentType} body=$snippet",
                                            )
                                        }
                                        return response
                                    }
                                }
                            }
                        }
                        val session = com.ivor.ivormusic.data.youtube.sabr.session.SabrSession(
                            descriptor,
                            diagnosing,
                            spool,
                            { attestation.currentIdentity() },
                        )
                        try {
                            val spec = com.ivor.ivormusic.data.youtube.sabr.bridge.SabrPlaybackSpec(
                                descriptor.videoId, audio,
                            )
                            val bridge = com.ivor.ivormusic.data.youtube.sabr.bridge.SabrBridge(
                                session, spec,
                            )
                            try {
                                bridge.prepareTimelines(0)
                            } catch (e: Exception) {
                                KLog.w(
                                    "SabrMintProbe",
                                    "prepareAudioOnly failed",
                                    e,
                                )
                            }
                            KLog.i(
                                "SabrMintProbe",
                                "prepareAudioOnly timelines=${bridge.hasTimelines()}",
                            )
                            // Discriminator: upstream always prepares audio AND
                            // video. If audio+video succeeds where audio-only
                            // 403s, the refusal is the preparation shape, not
                            // the IP.
                            val video = descriptor.selectVideo(720)
                            if (video != null) {
                                val specAv = com.ivor.ivormusic.data.youtube.sabr.bridge.SabrPlaybackSpec(
                                    descriptor.videoId, audio, video,
                                )
                                val bridgeAv = com.ivor.ivormusic.data.youtube.sabr.bridge.SabrBridge(
                                    session, specAv,
                                )
                                try {
                                    bridgeAv.prepareTimelines(0)
                                } catch (e: Exception) {
                                    KLog.w(
                                        "SabrMintProbe",
                                        "prepareAudioVideo failed",
                                        e,
                                    )
                                }
                                KLog.i(
                                    "SabrMintProbe",
                                    "prepareAudioVideo timelines=${bridgeAv.hasTimelines()} " +
                                        "segments=${if (bridgeAv.hasTimelines()) bridgeAv.timeline(audio).endSequence else -1}",
                                )
                                bridgeAv.stop()
                            }
                            bridge.stop()
                        } finally {
                            runCatching { session.close() }
                            runCatching { spool.close() }
                            runCatching { spoolDir.deleteRecursively() }
                        }
                    }
                } catch (e: Exception) {
                    KLog.w("SabrMintProbe", "sabr probe failed", e)
                }
                }
            }, "SabrMintProbe").apply { isDaemon = true }.start()
        }
    }

    /**
     * The one Coil loader for the whole process. AsyncImage and every direct
     * ImageRequest (artwork color extraction, notification artwork, downloads)
     * resolve to this instance through Context.imageLoader, so they share one
     * memory cache, one disk cache and one connection pool instead of each
     * call site building its own loader.
     *
     * The only component added is the device-video frame fetcher, which is
     * keyed on its own [LocalVideoThumbnail] model and so cannot affect any
     * other image: every existing request still resolves exactly as it did.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(LocalVideoThumbnailFetcher.Factory(this@IvorMusicApplication))
                add(LocalVideoThumbnailFetcher.ThumbnailKeyer())
            }
            .build()
}
