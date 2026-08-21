package com.vpnmonitor.tv

import androidx.activity.ComponentActivity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.vpnmonitor.tv.ui.screens.SettingsScreen
import com.vpnmonitor.tv.ui.theme.VpnColors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        const val REQUEST_OVERLAY = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = ComposeView(this).apply {
            setContent {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VpnColors.Background)
                        .padding(48.dp)
                ) {
                    SettingsScreen(
                        onToggleOverlay = { enable ->
                            if (enable) {
                                requestOverlayPermission()
                            } else {
                                stopOverlayService()
                            }
                        },
                        onTestConnection = { testConnection() },
                        onShowScreensaver = { startActivity(Intent(this@MainActivity, ScreensaverActivity::class.java)) }
                    )
                }
            }
        }
        setContentView(view)

        // Авто-старт оверлея при запуске приложения. Источник истины —
        // overlay_enabled в конфиге устройства на сервере: OverlayService
        // сам остановится, если сервер говорит «выключено». Здесь только
        // проверяем наличие разрешения на рисование поверх приложений.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            startOverlayService()
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, REQUEST_OVERLAY)
            } else {
                startOverlayService()
            }
        } else {
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Оверлей запущен", Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlayService() {
        stopService(Intent(this, OverlayService::class.java))
        Toast.makeText(this, "Оверлей остановлен", Toast.LENGTH_SHORT).show()
    }

    private fun testConnection() {
        val client = ApiClient(this)
        lifecycleScope.launch {
            val status = client.fetchStatus()
            if (status != null) {
                Toast.makeText(
                    this@MainActivity,
                    "✅ OK: ${status.servers.size} серверов",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "❌ Ошибка соединения",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                startOverlayService()
            } else {
                Toast.makeText(this, "Разрешение на оверлей не получено", Toast.LENGTH_LONG).show()
            }
        }
    }
}
