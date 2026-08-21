package com.vpnmonitor.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.vpnmonitor.tv.ui.components.StatusIndicator
import com.vpnmonitor.tv.ui.theme.VpnColors
import com.vpnmonitor.tv.ui.theme.VpnTypography
import kotlinx.coroutines.*
import kotlin.math.roundToInt

/**
 * Оверлей поверх приложений. Источник истины — конфиг устройства на сервере:
 * при overlay_enabled=false сервис сам останавливается; параметры окна
 * (позиция/стиль/размер/прозрачность/поля) применяются из серверного конфига.
 */
class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    companion object {
        const val TAG = "VPNOverlay"
        const val CHANNEL_ID = "vpn_monitor_overlay"
        const val NOTIFICATION_ID = 1001
        const val UPDATE_INTERVAL_MS = 10000L
        const val CONFIG_REFRESH_INTERVAL_MS = 30000L
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val internalViewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = internalViewModelStore

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private lateinit var apiClient: ApiClient
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var soundPool: SoundPool? = null
    private var alertSoundId: Int = 0
    private var lastAlertedServerIds = mutableSetOf<Int>()

    // Контент оверлея живёт в compose-состоянии: обновление данных НЕ пересоздаёт
    // окно (иначе бегущая строка сбрасывалась бы каждые 10 сек).
    private var contentServers by mutableStateOf<List<ServerStatus>>(emptyList())
    private var contentAlertThreshold by mutableStateOf(3)
    private var contentSizeFactor by mutableStateOf(1f)
    private var contentOpacity by mutableStateOf(1f)
    private var contentShowLocation by mutableStateOf(true)
    private var contentShowPing by mutableStateOf(true)
    private var contentShowVless by mutableStateOf(true)
    private var contentStyle by mutableStateOf("card")

    // Параметры, влияющие на LayoutParams окна (style/position/size меняют геометрию)
    private var lastWindowSignature: String? = null

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateOverlay()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        Log.d(TAG, "OverlayService created")

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        apiClient = ApiClient(this)

        initSoundPool()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            refreshDeviceConfig() // сразу применяем серверный конфиг (может остановить сервис)
        }
        handler.post(updateRunnable)
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()

        alertSoundId = soundPool?.load(this, R.raw.alert_sound, 1) ?: 0
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Monitor Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновый мониторинг VLESS/XHTTP"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VPN Monitor")
            .setContentText("Мониторинг VLESS/XHTTP активен")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }

    /**
     * Каждый тик обновляем статусы; конфиг устройства перечитываем реже
     * (каждые CONFIG_REFRESH_INTERVAL_MS), чтобы подхватывать изменения
     * с Web UI. Если overlay_enabled=false — сервис останавливается сам.
     */
    private fun updateOverlay() {
        serviceScope.launch {
            refreshDeviceConfig()
            val data = apiClient.fetchStatus()
            data?.let { showOverlay(it) }
        }
    }

    private suspend fun refreshDeviceConfig() {
        val config = apiClient.fetchDeviceConfig() ?: return
        if (!config.overlayEnabled) {
            Log.d(TAG, "overlay_enabled=false на сервере — останавливаюсь")
            stopSelf()
            return
        }
        val ov = config.overlay
        contentSizeFactor = ov.size
        contentOpacity = ov.opacity
        contentShowLocation = ov.showLocation
        contentShowPing = ov.showPing
        contentShowVless = ov.showVless
        contentStyle = ov.style
        // Геометрия (style/position/size) меняется — окно будет пересоздано
        lastWindowSignature = null
    }

    private fun showOverlay(data: StatusResponse) {
        val settings = data.settings

        contentServers = data.servers
        contentAlertThreshold = settings.alertThreshold

        // Окно пересоздаём только при изменении геометрии (style/position/size).
        val position = DeviceConfigCache.config?.overlay?.position ?: "top-right"
        val windowSignature = "$contentStyle::$position::$contentSizeFactor"
        if (windowSignature == lastWindowSignature && composeView != null) return
        lastWindowSignature = windowSignature

        // Удаляем старый view
        composeView?.let { windowManager.removeView(it) }

        // Строки (line/marquee) занимают всю ширину экрана и крепятся к верху/низу
        val isLineStyle = contentStyle == "line" || contentStyle == "marquee"
        val gravity = if (isLineStyle) {
            when (position) {
                "bottom-left", "bottom-right" -> Gravity.BOTTOM
                else -> Gravity.TOP
            }
        } else {
            when (position) {
                "top-left" -> Gravity.TOP or Gravity.START
                "top-right" -> Gravity.TOP or Gravity.END
                "bottom-left" -> Gravity.BOTTOM or Gravity.START
                "bottom-right" -> Gravity.BOTTOM or Gravity.END
                else -> Gravity.TOP or Gravity.END
            }
        }

        val params = WindowManager.LayoutParams(
            if (isLineStyle) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            x = 24
            y = 24
        }

        // Проверяем алерты
        var hasNewDown = false
        for (server in data.servers) {
            if (server.status == "down" && server.downCount >= settings.alertThreshold) {
                if (!lastAlertedServerIds.contains(server.id)) {
                    hasNewDown = true
                    lastAlertedServerIds.add(server.id)
                }
            } else if (server.status != "down") {
                lastAlertedServerIds.remove(server.id)
            }
        }

        if (hasNewDown && settings.alertSound == 1) {
            soundPool?.play(alertSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setContent {
                OverlayContent(
                    servers = contentServers,
                    sizeFactor = contentSizeFactor,
                    opacity = contentOpacity,
                    showLocation = contentShowLocation,
                    showPing = contentShowPing,
                    showVless = contentShowVless,
                    style = contentStyle
                )
            }
        }

        composeView = view
        windowManager.addView(view, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ComposeView в сервисе использует lifecycle-aware WindowRecomposer:
        // цикл рекомпозиции запускается ТОЛЬКО после ON_START. Без этого
        // анимации замирают (frame clock не работает) и запись состояния
        // не вызывает перерисовку — проверено на Sony.
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        handler.removeCallbacks(updateRunnable)
        serviceScope.cancel()
        composeView?.let { windowManager.removeView(it) }
        soundPool?.release()
        Log.d(TAG, "OverlayService destroyed")
    }
}

@Composable
fun OverlayContent(
    servers: List<ServerStatus>,
    sizeFactor: Float = 1f,
    opacity: Float = 1f,
    showLocation: Boolean = true,
    showPing: Boolean = true,
    showVless: Boolean = true,
    style: String = "card",
    modifier: Modifier = Modifier
) {
    val textSize = VpnTypography.BodyLarge

    // Строки (line/marquee): все серверы одной строкой, цвет = статус сервера
    if (style == "line" || style == "marquee") {
        val lineText = remember(servers, showLocation, showPing, showVless) {
            buildServerStatusLine(servers, showLocation, showPing, showVless)
        }
        if (style == "marquee") {
            MarqueeOverlay(text = lineText, textSize = textSize, opacity = opacity)
        } else {
            LineOverlay(text = lineText, textSize = textSize, opacity = opacity)
        }
        return
    }

    Box(
        modifier = modifier
            .wrapContentSize()
            .scale(sizeFactor)
            .alpha(opacity)
            .background(
                color = VpnColors.Surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(1.dp, VpnColors.CardBorder.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(VpnColors.Primary, RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "VPN",
                    style = VpnTypography.Label.copy(
                        color = VpnColors.PrimaryLight,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                )
            }

            // Servers
            for (server in servers) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusIndicator(status = server.status ?: "unknown")

                    Text(
                        text = buildString {
                            append(server.name)
                            if (showLocation && !server.location.isNullOrEmpty()) {
                                append(" ")
                                append(server.location)
                            }
                            if (showVless) {
                                append(" ")
                                append(if (server.vlessOk == 1) "✓" else "✗")
                            }
                            if (showPing && server.pingMs != null && server.pingMs > 0) {
                                append(" ${server.pingMs.toInt()}ms")
                            }
                        },
                        style = textSize.copy(
                            color = when (server.status) {
                                "up" -> VpnColors.SuccessGlow
                                "down" -> VpnColors.DangerGlow
                                "degraded" -> VpnColors.WarningGlow
                                else -> VpnColors.TextSecondary
                            }
                        )
                    )
                }
            }
        }
    }
}

/** Собирает AnnotatedString «● Сервер Локация ✓ 12ms | ● …» с цветом по статусу. */
private fun buildServerStatusLine(
    servers: List<ServerStatus>,
    showLocation: Boolean,
    showPing: Boolean,
    showVless: Boolean
): AnnotatedString {
    return buildAnnotatedString {
        servers.forEachIndexed { index, server ->
            if (index > 0) append("   |   ")
            val color = when (server.status) {
                "up" -> VpnColors.SuccessGlow
                "down" -> VpnColors.DangerGlow
                "degraded" -> VpnColors.WarningGlow
                else -> VpnColors.TextSecondary
            }
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) {
                append("● ")
            }
            withStyle(SpanStyle(color = color)) {
                append(server.name)
                if (showLocation && !server.location.isNullOrEmpty()) {
                    append(" ")
                    append(server.location)
                }
                if (showVless) {
                    append(" ")
                    append(if (server.vlessOk == 1) "✓" else "✗")
                }
                if (showPing && server.pingMs != null && server.pingMs > 0) {
                    append(" ${server.pingMs.toInt()}ms")
                }
            }
        }
    }
}

/** Статичная строка во всю ширину экрана (сверху/снизу). */
@Composable
fun LineOverlay(
    text: AnnotatedString,
    textSize: TextStyle,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(opacity)
            .background(
                color = VpnColors.Surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(0.dp)
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = textSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Бегущая строка: текст едет справа налево по кругу. */
@Composable
fun MarqueeOverlay(
    text: AnnotatedString,
    textSize: TextStyle,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    var textWidth by remember { mutableIntStateOf(0) }
    var containerWidth by remember { mutableIntStateOf(0) }

    // Ручной скролл по таймеру: LaunchedEffect + delay() работает через главный
    // Looper и НЕ зависит от Choreographer/frame clock (который в overlay-окне
    // сервиса на ТВ не тикает — проверено: rememberInfiniteTransition замирает).
    val distancePx = (containerWidth + textWidth).coerceAtLeast(1)
    val durationMs = (distancePx / 100f * 1000).toInt().coerceAtLeast(3000)
    var manualOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(containerWidth, textWidth) {
        val start = SystemClock.elapsedRealtime()
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - start
            val progress = (elapsed % durationMs).toFloat() / durationMs
            manualOffset = -(progress * distancePx)  // 0 → -distancePx (полный уход влево)
            delay(16)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(opacity)
            .background(
                color = VpnColors.Surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(0.dp)
            )
            .clipToBounds()
            .onSizeChanged { containerWidth = it.width }
    ) {
        Text(
            text = text,
            style = textSize,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { textWidth = it.size.width },
            // Старт: текст прижат к правому краю (translationX = containerWidth),
            // дальше едет влево на (containerWidth + textWidth) — полностью уходит.
            modifier = Modifier.offset {
                IntOffset((containerWidth + manualOffset).roundToInt(), 0)
            }
        )
    }
}
