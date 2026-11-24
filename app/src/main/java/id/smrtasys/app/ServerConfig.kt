package id.smrtasys.app

import android.net.Uri
import java.util.Locale

object ServerConfig {
    const val PRIMARY_URL: String = "https://smrtasys.web.id/"
    const val FALLBACK_URL: String = "https://143.20.199.236/"

    const val PRIMARY_HOST: String = "smrtasys.web.id"
    const val FALLBACK_HOST: String = "143.20.199.236"

    val ALLOWED_HOSTS: Set<String> = setOf(
        PRIMARY_HOST,
        FALLBACK_HOST
    )

    val ALLOWED_MIME_TYPES: Array<String> = arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel",
        "application/pdf",
        "image/*"
    )

    const val CONNECT_TIMEOUT_MS: Long = 12_000L
    const val PAGE_LOAD_TIMEOUT_MS: Long = 20_000L
    const val MAX_FALLBACK_ATTEMPTS: Int = 1
    const val MAX_AUTO_RETRIES: Int = 2
    const val SPLASH_MIN_DURATION_MS: Long = 650L
    const val SPLASH_MAX_DURATION_MS: Long = 1400L

    fun isAllowedInternalUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme != "https") return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return host == PRIMARY_HOST || host.endsWith(".$PRIMARY_HOST") || host == FALLBACK_HOST
    }

    fun switchHostPreservingPath(currentUrl: String?, destinationBaseUrl: String): String {
        val fallbackDefault = if (destinationBaseUrl.endsWith("/")) destinationBaseUrl else "$destinationBaseUrl/"
        if (currentUrl.isNullOrBlank()) return fallbackDefault
        val uri = runCatching { Uri.parse(currentUrl) }.getOrNull() ?: return fallbackDefault
        val path = uri.encodedPath?.takeIf { it.isNotEmpty() } ?: "/"
        val query = uri.encodedQuery?.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""
        val cleanBase = if (destinationBaseUrl.endsWith("/")) destinationBaseUrl.dropLast(1) else destinationBaseUrl
        return "$cleanBase$path$query"
    }
}
