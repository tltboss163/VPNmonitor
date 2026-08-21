package com.vpnmonitor.tv

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpnmonitor.tv.ui.theme.VpnColors

/**
 * Debug-активити для визуальной проверки заставки на экране ТВ.
 * Обходит неработающий на Sony DreamManager: просто показывает
 * ScreensaverRoot в полноэкранной Activity.
 *
 * Управление с пульта:
 *   D-Pad Left/Right — переключение режима фона (status / images / video)
 *   OK / Back        — выход
 */
class ScreensaverDebugActivity : ComponentActivity() {
    companion object {
        const val TAG = "ScreensaverDebug"
        private val MODES = listOf("status", "images", "video")
    }

    private val mode = mutableStateOf("status")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = ComposeView(this).apply {
            setContent {
                Box(modifier = Modifier.fillMaxSize()) {
                    ScreensaverRoot(
                        apiClient = ApiClient(this@ScreensaverDebugActivity),
                        overrideMode = mode.value
                    )

                    // Бейдж текущего режима внизу — для отладки
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .background(VpnColors.Background.copy(alpha = 0.7f))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "DEBUG mode=${mode.value}",
                            color = VpnColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        setContentView(view)

        Log.d(TAG, "Started, mode=${mode.value}")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val idx = MODES.indexOf(mode.value)
                val next = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    (idx + 1) % MODES.size
                } else {
                    (idx - 1 + MODES.size) % MODES.size
                }
                mode.value = MODES[next]
                Log.d(TAG, "Mode -> ${mode.value}")
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
