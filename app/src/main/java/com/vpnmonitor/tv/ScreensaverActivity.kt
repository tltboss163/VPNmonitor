package com.vpnmonitor.tv

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView

/**
 * Production-заставка: полноэкранная Activity поверх любого приложения.
 * Запускается watchdog-механизмом (IdleWatchdogService) после бездействия
 * или вручную из настроек.
 *
 * Конфиг заставки (режим, картинки, плейлист) загружается с сервера
 * внутри [ScreensaverRoot] — при недоступности роутера используются дефолты.
 *
 * Любая клавиша пульта закрывает заставку (finish()).
 */
class ScreensaverActivity : ComponentActivity() {
    companion object {
        const val TAG = "VPNScreensaverActivity"

        /** Признак того, что заставка сейчас на экране (для watchdog). */
        @Volatile
        var isShowing = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isShowing = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val view = ComposeView(this).apply {
            setContent {
                Box(modifier = Modifier.fillMaxSize()) {
                    ScreensaverRoot(apiClient = ApiClient(this@ScreensaverActivity))
                }
            }
        }
        setContentView(view)

        Log.d(TAG, "Started")
    }

    override fun onStop() {
        super.onStop()
        // HOME (или сон ТВ) переводит активность в STOPPED, НЕ уничтожая её.
        // Без этого isShowing навсегда остался бы true и watchdog никогда бы
        // не запустил заставку снова.
        isShowing = false
        // Сбрасываем таймер watchdog, чтобы заставка не выскочила мгновенно.
        IdleWatchdogService.lastUserActivityMs = System.currentTimeMillis()
        Log.d(TAG, "Stopped")
    }

    override fun onDestroy() {
        isShowing = false
        // Клавиша закрыла заставку — сбрасываем таймер watchdog, чтобы заставка
        // не выскочила снова мгновенно (onKeyEvent не гарантирован без гранта
        // filter-key-events для accessibility-сервиса).
        IdleWatchdogService.lastUserActivityMs = System.currentTimeMillis()
        Log.d(TAG, "Destroyed")
        super.onDestroy()
    }

    // Любая клавиша пульта закрывает заставку.
    // Обрабатываем и ACTION_DOWN, и ACTION_UP, чтобы поймать все варианты пультов.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "Key down $keyCode -> finish")
        finish()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "Key up $keyCode -> finish")
        finish()
        return true
    }
}
