package com.vpnmonitor.tv

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.UUID

class VpnMonitorApp : Application() {
    companion object {
        lateinit var prefs: SharedPreferences
            private set

        const val PREFS_NAME = "vpn_monitor_prefs"

        // Идентификация устройства (авто-регистрация на роутере)
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_NAME = "device_name"

        // Последний известный URL роутера (кеш для быстрого старта, перепроверяется)
        const val KEY_API_URL = "api_url"

        // Локальный флаг оверлея — только fallback на случай недоступности роутера.
        // Источник истины — overlay_enabled в конфиге устройства на сервере.
        const val KEY_OVERLAY_ENABLED = "overlay_enabled"

        // Режим заставки: "status" | "images" | "video"
        const val KEY_SCREENSAVER_MODE = "screensaver_mode"
        const val DEFAULT_SCREENSAVER_MODE = "status"

        // Activity-заставка: автозапуск по бездействию (watchdog через AccessibilityService)
        const val KEY_SCREENSAVER_AUTO = "screensaver_auto"                    // Boolean
        const val KEY_SCREENSAVER_IDLE_TIMEOUT_MS = "screensaver_idle_timeout_ms" // Long, мс
        const val DEFAULT_SCREENSAVER_IDLE_TIMEOUT_MS = 300000L                // 5 минут

        // Публичный API аниме-картинок (используется если свой список URL не задан)
        const val ANIME_IMAGE_API = "https://api.waifu.pics/sfw/waifu"

        const val DEFAULT_API_URL = "http://192.168.2.1:17463"
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Генерируем стабильный device_id при первом запуске
        if (!prefs.contains(KEY_DEVICE_ID)) {
            prefs.edit().putString(KEY_DEVICE_ID, UUID.randomUUID().toString()).apply()
        }
        // Имя устройства = модель ТВ
        if (!prefs.contains(KEY_DEVICE_NAME)) {
            prefs.edit().putString(KEY_DEVICE_NAME, Build.MODEL).apply()
        }
    }
}
