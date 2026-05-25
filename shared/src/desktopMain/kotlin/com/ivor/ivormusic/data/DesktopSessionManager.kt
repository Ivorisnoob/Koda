package com.ivor.ivormusic.data

import java.io.File
import java.util.Properties

class DesktopSessionManager : SessionManager {

    private val configDir = File(System.getProperty("user.home"), ".config/koda").also { it.mkdirs() }
    private val sessionFile = File(configDir, "session.properties")

    private fun load(): Properties = Properties().also { p ->
        if (sessionFile.exists()) sessionFile.inputStream().use { p.load(it) }
    }

    private fun save(props: Properties) {
        sessionFile.outputStream().use { props.store(it, null) }
    }

    override fun saveCookies(cookies: String) = mutate { setProperty("cookies", cookies) }
    override fun getCookies(): String? = load().getProperty("cookies")?.ifEmpty { null }
    override fun clearSession() { sessionFile.delete() }
    override fun isLoggedIn(): Boolean = !getCookies().isNullOrBlank()
    override fun getVisitorData(): String? = load().getProperty("visitor_data")?.ifEmpty { null }
    override fun saveVisitorData(data: String) = mutate { setProperty("visitor_data", data) }
    override fun saveUserAvatar(url: String) = mutate { setProperty("user_avatar", url) }
    override fun getUserAvatar(): String? = load().getProperty("user_avatar")?.ifEmpty { null }
    override fun saveUserName(name: String) = mutate { setProperty("user_name", name) }
    override fun getUserName(): String? = load().getProperty("user_name")?.ifEmpty { null }

    private fun mutate(block: Properties.() -> Unit) {
        val props = load()
        props.block()
        save(props)
    }
}
