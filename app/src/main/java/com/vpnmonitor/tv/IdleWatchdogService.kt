package com.vpnmonitor.tv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.runBlocking

/**
 * Watchdog-механизм Activity-заставки.
 *
 * Следит за глобальной активностью пользователя (нажатия клавиш на пульте
 * и любые accessibility-события). Если активность отсутствует дольше
 * таймаута и автозапуск включён в конфиге устройства на сервере —
 * запускает ScreensaverActivity поверх текущего приложения.
 *
 * Работает как AccessibilityService: пользователь включает его один раз в
 * Настройки ТВ → Спец. возможности → VPN Monitor Watchdog.
 */
class IdleWatchdogService : AccessibilityService() {
    companion object {
        const val TAG = "VPNWatchdog"
        private const val CHECK_INTERVAL_MS = 5000L

        /** Метка последней пользовательской активности (wall-clock ms). */
        @Volatile
        var lastUserActivityMs = System.currentTimeMillis()
    }

    private val handler = Handler(Looper.getMainLooper())

    private val checkRunnable = object : Runnable {
        override fun run() {
            maybeRefreshConfig()
            checkIdle()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    private fun checkIdle() {
        if (ScreensaverActivity.isShowing) return
        // Источник истины — конфиг с сервера (DeviceConfigCache). Оффлайн —
        // дефолты: автозапуск выключен.
        val config = DeviceConfigCache.config
        val auto = config?.screensaver?.auto ?: false
        if (!auto) return

        val timeoutMs = config?.screensaver?.idleTimeoutMs
            ?: VpnMonitorApp.DEFAULT_SCREENSAVER_IDLE_TIMEOUT_MS
        val idleMs = System.currentTimeMillis() - lastUserActivityMs
        if (idleMs >= timeoutMs) {
            lastUserActivityMs = System.currentTimeMillis() // не запускать повторно сразу
            Log.d(TAG, "Idle $idleMs ms >= $timeoutMs ms -> launching screensaver")
            startActivity(
                Intent(this, ScreensaverActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Периодически обновляем конфиг с сервера, чтобы watchdog жил без оверлея. */
    private var lastConfigRefreshMs = 0L
    private var configRefreshing = false

    private fun maybeRefreshConfig() {
        if (configRefreshing) return
        val now = System.currentTimeMillis()
        if (now - lastConfigRefreshMs < 60000L) return
        lastConfigRefreshMs = now
        configRefreshing = true
        Thread {
            try {
                runBlocking {
                    ApiClient(this@IdleWatchdogService).fetchDeviceConfig()
                }
            } catch (e: Exception) {
                // оффлайн — остаёмся на дефолтах
            } finally {
                configRefreshing = false
            }
        }.start()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastUserActivityMs = System.currentTimeMillis()
        maybeRefreshConfig()
        handler.post(checkRunnable)
        Log.d(TAG, "Watchdog connected")
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event != null && event.action == KeyEvent.ACTION_DOWN) {
            lastUserActivityMs = System.currentTimeMillis()
        }
        return false // не перехватываем клавиши
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Считаем активностью только события, вызванные реальным пользователем.
        // TYPE_WINDOW_CONTENT_CHANGED и прочие фоновые события генерируются
        // постоянно самими приложениями — они не должны сбрасывать таймер.
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                lastUserActivityMs = System.currentTimeMillis()
            }
        }
    }

    override fun onInterrupt() {
        // no-op
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        Log.d(TAG, "Watchdog destroyed")
        super.onDestroy()
    }
}
