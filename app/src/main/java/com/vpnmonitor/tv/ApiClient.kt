package com.vpnmonitor.tv

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ServerStatus(
    val id: Int,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: String,
    val location: String?,
    val network: String?,
    val security: String?,
    val pingMs: Float?,
    @SerializedName("tcp_ok") val tcpOk: Int,
    @SerializedName("proxy_ok") val proxyOk: Int,
    @SerializedName("vless_ok") val vlessOk: Int,
    val status: String?,
    @SerializedName("error_msg") val errorMsg: String?,
    @SerializedName("checked_at") val checkedAt: String?,
    @SerializedName("down_count") val downCount: Int = 0
)

data class DisplaySettings(
    val position: String = "top-right",
    val size: String = "medium",
    val opacity: Float = 0.85f,
    @SerializedName("show_ping") val showPing: Int = 1,
    @SerializedName("show_location") val showLocation: Int = 1,
    @SerializedName("alert_sound") val alertSound: Int = 1,
    @SerializedName("alert_threshold") val alertThreshold: Int = 3
)

data class StatusResponse(
    val servers: List<ServerStatus>,
    val settings: DisplaySettings,
    @SerializedName("checked_at") val checkedAt: String?
)

// ============ Per-device конфиг (источник истины — сервер на роутере) ============

data class OverlayConfig(
    val position: String = "top-right",
    val style: String = "card",
    val size: Float = 1.0f,
    val opacity: Float = 0.9f,
    @SerializedName("show_location") val showLocation: Boolean = true,
    @SerializedName("show_ping") val showPing: Boolean = true,
    @SerializedName("show_vless") val showVless: Boolean = true
)

data class ScreensaverConfig(
    val mode: String = "status",
    val auto: Boolean = false,
    @SerializedName("idle_timeout_ms") val idleTimeoutMs: Long = 300_000L,
    @SerializedName("image_urls") val imageUrls: List<String> = emptyList(),
    val playlist: List<String> = emptyList()
)

data class DeviceConfig(
    @SerializedName("device_id") val deviceId: String = "",
    val name: String = "",
    @SerializedName("overlay_enabled") val overlayEnabled: Boolean = true,
    val overlay: OverlayConfig = OverlayConfig(),
    val screensaver: ScreensaverConfig = ScreensaverConfig(),
    @SerializedName("adult_enabled") val adultEnabled: Boolean = false,
    @SerializedName("video_sound") val videoSound: Boolean = false,
    @SerializedName("settings_scope") val settingsScope: String = "device", // device | web
    @SerializedName("playlist_id") val playlistId: Int? = null
)

/** Общий плейлист из библиотеки сервера (вкладка «Медиа»). */
data class Playlist(
    val id: Int = 0,
    val name: String = "",
    val category: String = "normal", // normal | adult
    val kind: String = "manual",     // manual | source
    val items: List<String> = emptyList(),
    @SerializedName("image_urls") val imageUrls: List<String> = emptyList(),
    @SerializedName("source_id") val sourceId: Int? = null,
    val enabled: Boolean = true
)

/** Источник видео на сервере (xvru/hentaicloud — 18+, archiveorg — обычное). */
data class MediaSource(
    val id: Int = 0,
    val name: String = "",
    val site: String = "",
    val section: String = "popular",
    val category: String = "adult", // normal | adult
    val items: List<String> = emptyList(),
    @SerializedName("last_sync") val lastSync: String? = null,
    val error: String? = null,
    val enabled: Boolean = true
)

data class MediaPrepareResponse(
    val hash: String = "",
    val status: String = "idle", // idle | preparing | ready | error
    @SerializedName("served_url") val servedUrl: String = "",
    val error: String? = null
)

/**
 * Кеш последнего полученного конфига устройства: используется оверлеем,
 * заставкой и watchdog'ом, чтобы не дёргать сеть на каждый чих.
 */
object DeviceConfigCache {
    @Volatile
    var config: DeviceConfig? = null
}

/**
 * API-клиент без ключа (только LAN). Base URL определяется автоматически:
 * 1) кешированный URL из prefs (быстрый старт),
 * 2) шлюз Wi-Fi (роутер) — основной способ,
 * 3) дефолтный http://192.168.2.1:17463 как крайний fallback.
 *
 * Рабочий URL кешируется в памяти и в prefs; при сбое запроса клиент
 * заново перебирает кандидатов и обновляет кеш.
 */
class ApiClient(private val context: Context) {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var workingBaseUrl: String? = null

    // ============ Авто-поиск base URL ============

    /** Перебирает кандидатов (кеш → шлюз Wi-Fi → дефолт) и кеширует первый живой. */
    suspend fun discoverBaseUrl(): String? = withContext(Dispatchers.IO) {
        val prefs = VpnMonitorApp.prefs
        val candidates = buildList {
            prefs.getString(VpnMonitorApp.KEY_API_URL, null)
                ?.takeIf { it.startsWith("http") }
                ?.let { add(it.trimEnd('/')) }
            RouterDiscovery.discover(context)?.let { add(it) }
            add(VpnMonitorApp.DEFAULT_API_URL)
        }.distinct()

        for (candidate in candidates) {
            if (ping(candidate)) {
                workingBaseUrl = candidate
                if (prefs.getString(VpnMonitorApp.KEY_API_URL, null) != candidate) {
                    prefs.edit().putString(VpnMonitorApp.KEY_API_URL, candidate).apply()
                }
                return@withContext candidate
            }
        }
        null
    }

    private fun ping(baseUrl: String): Boolean = try {
        val req = Request.Builder().url("$baseUrl/api/status").get().build()
        client.newCall(req).execute().use { it.isSuccessful }
    } catch (e: Exception) {
        false
    }

    /** Быстрый доступ к текущему base URL (без сети). */
    private fun getBaseUrl(): String {
        workingBaseUrl?.let { return it }
        return VpnMonitorApp.prefs.getString(VpnMonitorApp.KEY_API_URL, null)
            ?.takeIf { it.startsWith("http") }
            ?.trimEnd('/')
            ?: RouterDiscovery.discover(context)
            ?: VpnMonitorApp.DEFAULT_API_URL
    }

    private fun runRequest(url: String, method: String = "GET", body: String? = null): String? {
        return try {
            val builder = Request.Builder().url(url)
            when (method) {
                "POST" -> builder.post(
                    body?.toRequestBody(jsonMediaType) ?: "{}".toRequestBody(jsonMediaType)
                )
                "DELETE" -> builder.delete()
                else -> builder.get()
            }
            client.newCall(builder.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Выполняет запрос, при сбое — заново находит роутер и повторяет один раз. */
    private suspend fun requestWithRetry(
        method: String = "GET",
        pathAndQuery: String,
        body: String? = null
    ): String? {
        var result = runRequest("${getBaseUrl()}/$pathAndQuery", method, body)
        if (result == null) {
            val fresh = discoverBaseUrl()
            if (fresh != null) {
                result = runRequest("$fresh/$pathAndQuery", method, body)
            }
        }
        return result
    }

    // ============ /api/status ============

    suspend fun fetchStatus(): StatusResponse? = withContext(Dispatchers.IO) {
        try {
            requestWithRetry(pathAndQuery = "api/status")
                ?.let { gson.fromJson(it, StatusResponse::class.java) }
        } catch (e: Exception) {
            null
        }
    }

    // ============ /api/device/config (авто-регистрация + per-device) ============

    /** GET — авто-регистрирует устройство и возвращает конфиг с сервера. */
    suspend fun fetchDeviceConfig(): DeviceConfig? = withContext(Dispatchers.IO) {
        val deviceId = VpnMonitorApp.prefs.getString(VpnMonitorApp.KEY_DEVICE_ID, "") ?: return@withContext null
        val name = VpnMonitorApp.prefs.getString(VpnMonitorApp.KEY_DEVICE_NAME, "") ?: return@withContext null
        try {
            val path = "api/device/config?device_id=${URLEncoder.encode(deviceId, "UTF-8")}&name=${URLEncoder.encode(name, "UTF-8")}"
            val config = requestWithRetry(pathAndQuery = path)?.let { gson.fromJson(it, DeviceConfig::class.java) }
            if (config != null) {
                DeviceConfigCache.config = config
            }
            config
        } catch (e: Exception) {
            null
        }
    }

    /** POST — сохраняет конфиг устройства на сервер. */
    suspend fun saveDeviceConfig(config: DeviceConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            requestWithRetry(method = "POST", pathAndQuery = "api/device/config", body = gson.toJson(config)) != null
        } catch (e: Exception) {
            false
        }
    }

    // ============ /api/playlists + /api/media-sources (общие, вкладка «Медиа») ============

    /** GET /api/playlists — библиотека общих плейлистов. */
    suspend fun fetchPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        try {
            requestWithRetry(pathAndQuery = "api/playlists")
                ?.let { gson.fromJson(it, Array<Playlist>::class.java)?.toList() }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** GET /api/media-sources — подключённые источники 18+. */
    suspend fun fetchMediaSources(): List<MediaSource> = withContext(Dispatchers.IO) {
        try {
            requestWithRetry(pathAndQuery = "api/media-sources")
                ?.let { gson.fromJson(it, Array<MediaSource>::class.java)?.toList() }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** POST /api/media-sources/<id>/sync — запустить синхронизацию источника. */
    suspend fun syncMediaSource(id: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            requestWithRetry(method = "POST", pathAndQuery = "api/media-sources/$id/sync") != null
        } catch (e: Exception) {
            false
        }
    }

    // ============ /api/media (транскодинг на роутере) ============

    /** POST /api/media/prepare — просит роутер скачать и перекодировать URL. */
    suspend fun prepareMedia(url: String): MediaPrepareResponse? = withContext(Dispatchers.IO) {
        try {
            requestWithRetry(method = "POST", pathAndQuery = "api/media/prepare", body = gson.toJson(mapOf("url" to url)))
                ?.let { gson.fromJson(it, MediaPrepareResponse::class.java) }
        } catch (e: Exception) {
            null
        }
    }

    /** GET /api/media/status — статус транскодинга (ready → можно играть served_url). */
    suspend fun mediaStatus(url: String): MediaPrepareResponse? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(url, "UTF-8")
            requestWithRetry(pathAndQuery = "api/media/status?url=$encoded")
                ?.let { gson.fromJson(it, MediaPrepareResponse::class.java) }
        } catch (e: Exception) {
            null
        }
    }

    /** Ждёт готовности транскодинга до [maxWaitMs], возвращает served_url или null. */
    suspend fun awaitServedUrl(url: String, maxWaitMs: Long = 120_000L): String? = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            val status = mediaStatus(url) ?: return@withContext null
            when (status.status) {
                "ready" -> return@withContext status.servedUrl.takeIf { it.isNotBlank() }
                "error" -> return@withContext null
            }
            delay(2000)
        }
        null
    }
}
