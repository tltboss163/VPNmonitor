@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.vpnmonitor.tv.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.vpnmonitor.tv.ApiClient
import com.vpnmonitor.tv.DeviceConfig
import com.vpnmonitor.tv.DeviceConfigCache
import com.vpnmonitor.tv.OverlayConfig
import com.vpnmonitor.tv.Playlist
import com.vpnmonitor.tv.ScreensaverConfig
import com.vpnmonitor.tv.VpnMonitorApp
import com.vpnmonitor.tv.ui.components.GradientTitle
import com.vpnmonitor.tv.ui.components.GlowButton
import com.vpnmonitor.tv.ui.components.PositionButton
import com.vpnmonitor.tv.ui.components.ToggleRow
import com.vpnmonitor.tv.ui.components.TvSlider
import com.vpnmonitor.tv.ui.theme.VpnColors
import com.vpnmonitor.tv.ui.theme.VpnTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Экран настроек: всё состояние (оверлей, заставка, плейлист) хранится
 * на сервере (роутер) в per-device конфиге. Источник истины — сервер.
 *
 * Никакого ручного ввода URL/API key: роутер ищется автоматически
 * (шлюз Wi-Fi → кешированный URL → дефолт).
 */
@Composable
fun SettingsScreen(
    onToggleOverlay: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onShowScreensaver: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // --- Состояние конфига устройства (с сервера) ---
    var deviceConfig by remember { mutableStateOf(DeviceConfigCache.config) }
    var connectionStatus by remember { mutableStateOf("connecting") } // connecting | ok | error

    // Локальные правки (не перезаписываются фоновым refresh)
    var overlayEnabled by remember { mutableStateOf(true) }
    var overlaySize by remember { mutableStateOf(1.0f) }
    var overlayOpacity by remember { mutableStateOf(0.9f) }
    var overlayPosition by remember { mutableStateOf("top-right") }
    var overlayStyle by remember { mutableStateOf("card") }
    var showLocation by remember { mutableStateOf(true) }
    var showPing by remember { mutableStateOf(true) }
    var showVless by remember { mutableStateOf(true) }

    var screensaverMode by remember { mutableStateOf("status") }
    var screensaverAuto by remember { mutableStateOf(false) }
    var screensaverIdleTimeoutMs by remember { mutableStateOf(300_000L) }
    var adultEnabled by remember { mutableStateOf(false) }
    var videoSound by remember { mutableStateOf(false) }
    var settingsScope by remember { mutableStateOf("device") } // device | web
    var selectedPlaylistId by remember { mutableStateOf<Int?>(null) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var playlistsLoaded by remember { mutableStateOf(false) }

    var showSaved by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf("✅ Настройки сохранены") }

    // Загрузка конфига с сервера при старте
    LaunchedEffect(Unit) {
        connectionStatus = "connecting"
        val client = ApiClient(context)
        val config = client.fetchDeviceConfig()
        if (config != null) {
            deviceConfig = config
            overlayEnabled = config.overlayEnabled
            overlaySize = config.overlay.size
            overlayOpacity = config.overlay.opacity
            overlayPosition = config.overlay.position
            overlayStyle = config.overlay.style
            showLocation = config.overlay.showLocation
            showPing = config.overlay.showPing
            showVless = config.overlay.showVless
            screensaverMode = config.screensaver.mode
            screensaverAuto = config.screensaver.auto
            screensaverIdleTimeoutMs = config.screensaver.idleTimeoutMs
            adultEnabled = config.adultEnabled
            videoSound = config.videoSound
            settingsScope = config.settingsScope
            selectedPlaylistId = config.playlistId
            connectionStatus = "ok"
        } else {
            connectionStatus = "error"
        }
        playlists = client.fetchPlaylists()
        playlistsLoaded = true
    }

    // Фоновый refresh конфига, чтобы оверлей/заставка жили без перезапуска
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val config = ApiClient(context).fetchDeviceConfig()
            if (config != null) {
                deviceConfig = config
                connectionStatus = "ok"
            }
        }
    }

    fun collectConfig(): DeviceConfig = DeviceConfig(
        deviceId = VpnMonitorApp.prefs.getString(VpnMonitorApp.KEY_DEVICE_ID, "") ?: "",
        name = VpnMonitorApp.prefs.getString(VpnMonitorApp.KEY_DEVICE_NAME, "") ?: "",
        overlayEnabled = overlayEnabled,
        overlay = OverlayConfig(
            position = overlayPosition,
            style = overlayStyle,
            size = overlaySize,
            opacity = overlayOpacity,
            showLocation = showLocation,
            showPing = showPing,
            showVless = showVless
        ),
        screensaver = ScreensaverConfig(
            mode = screensaverMode,
            auto = screensaverAuto,
            idleTimeoutMs = screensaverIdleTimeoutMs
        ),
        adultEnabled = adultEnabled,
        videoSound = videoSound,
        settingsScope = settingsScope,
        playlistId = selectedPlaylistId
    )

    fun saveToServer() {
        scope.launch {
            val ok = ApiClient(context).saveDeviceConfig(collectConfig())
            saveMessage = if (ok) "✅ Настройки сохранены" else "❌ Не удалось сохранить: роутер недоступен"
            showSaved = true
            delay(2500)
            showSaved = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        GradientTitle("🔒 VPN Monitor")

        // «Только через веб»: настройки на ТВ заблокированы админом с роутера
        if (settingsScope == "web") {
            SettingsSection(title = "🔒 Настройки заблокированы") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Администратор отключил изменение настроек на этом устройстве. Все настройки изменяются только через веб-интерфейс роутера.",
                        style = VpnTypography.BodyMedium
                    )
                    GlowButton(
                        text = "▶ Показать заставку",
                        onClick = onShowScreensaver
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            return@Column
        }

        // Статус подключения и идентификация устройства
        SettingsSection(title = "🛜 Устройство") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Имя: ${VpnMonitorApp.prefs.getString(VpnMonitorApp.KEY_DEVICE_NAME, "") ?: ""}",
                    style = VpnTypography.BodyMedium
                )
                Text(
                    text = "ID: ${VpnMonitorApp.prefs.getString(VpnMonitorApp.KEY_DEVICE_ID, "") ?: ""}",
                    style = VpnTypography.BodyMedium.copy(color = VpnColors.TextMuted)
                )
                Text(
                    text = when (connectionStatus) {
                        "connecting" -> "⏳ Подключение к роутеру…"
                        "ok" -> "✅ Соединение с роутером установлено (URL найден автоматически)"
                        else -> "❌ Роутер недоступен — показываются дефолтные настройки. Проверьте, что приложение работает на роутере и ТВ в одной сети."
                    },
                    style = VpnTypography.BodyMedium.copy(
                        color = when (connectionStatus) {
                            "ok" -> VpnColors.Success
                            "error" -> VpnColors.Danger
                            else -> VpnColors.Warning
                        }
                    )
                )
            }
        }

        SettingsSection(title = "📺 Оверлей") {
            ToggleRow(
                text = "Показывать оверлей на экране",
                checked = overlayEnabled,
                onCheckedChange = {
                    overlayEnabled = it
                    onToggleOverlay(it)
                }
            )

            TvSlider(
                label = "Размер окна",
                value = overlaySize,
                valueRange = 0.1f..2.0f,
                onValueChange = { overlaySize = it },
                onValueChangeFinished = { saveToServer() }
            )

            TvSlider(
                label = "Прозрачность",
                value = overlayOpacity,
                valueRange = 0.1f..1.0f,
                onValueChange = { overlayOpacity = it },
                onValueChangeFinished = { saveToServer() }
            )

            Text(
                text = "Положение окна",
                style = VpnTypography.BodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            val isLineStyle = overlayStyle == "line" || overlayStyle == "marquee"
            if (isLineStyle) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PositionButton(
                        text = "⬆ Верх",
                        selected = overlayPosition == "top",
                        onClick = { overlayPosition = "top"; saveToServer() },
                        modifier = Modifier.weight(1f)
                    )
                    PositionButton(
                        text = "⬇ Низ",
                        selected = overlayPosition == "bottom",
                        onClick = { overlayPosition = "bottom"; saveToServer() },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "Строка занимает всю ширину экрана: при положении «верх» — вверху, при «низ» — внизу.",
                    style = VpnTypography.BodyMedium.copy(color = VpnColors.TextMuted)
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PositionButton(
                        text = "↖ Верх. слева",
                        selected = overlayPosition == "top-left",
                        onClick = { overlayPosition = "top-left"; saveToServer() },
                        modifier = Modifier.weight(1f)
                    )
                    PositionButton(
                        text = "↗ Верх. справа",
                        selected = overlayPosition == "top-right",
                        onClick = { overlayPosition = "top-right"; saveToServer() },
                        modifier = Modifier.weight(1f)
                    )
                    PositionButton(
                        text = "↙ Низ. слева",
                        selected = overlayPosition == "bottom-left",
                        onClick = { overlayPosition = "bottom-left"; saveToServer() },
                        modifier = Modifier.weight(1f)
                    )
                    PositionButton(
                        text = "↘ Низ. справа",
                        selected = overlayPosition == "bottom-right",
                        onClick = { overlayPosition = "bottom-right"; saveToServer() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text(
                text = "Стиль отображения",
                style = VpnTypography.BodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PositionButton(
                    text = "🗂 Карточка",
                    selected = overlayStyle == "card",
                    onClick = {
                        overlayStyle = "card"
                        // Switch corner positions if coming from line style
                        if (overlayPosition == "top" || overlayPosition == "bottom") {
                            overlayPosition = "top-right"
                        }
                        saveToServer()
                    },
                    modifier = Modifier.weight(1f)
                )
                PositionButton(
                    text = "📏 Строка",
                    selected = overlayStyle == "line",
                    onClick = {
                        overlayStyle = "line"
                        saveToServer()
                    },
                    modifier = Modifier.weight(1f)
                )
                PositionButton(
                    text = "🎞 Бегущая строка",
                    selected = overlayStyle == "marquee",
                    onClick = {
                        overlayStyle = "marquee"
                        saveToServer()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "Что показывать",
                style = VpnTypography.BodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            ToggleRow(
                text = "Локация сервера",
                checked = showLocation,
                onCheckedChange = { showLocation = it; saveToServer() }
            )
            ToggleRow(
                text = "Пинг",
                checked = showPing,
                onCheckedChange = { showPing = it; saveToServer() }
            )
            ToggleRow(
                text = "Метка VLESS (✓/✗)",
                checked = showVless,
                onCheckedChange = { showVless = it; saveToServer() }
            )
        }

        SettingsSection(title = "🖥 Заставка") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Режим фона заставки",
                    style = VpnTypography.BodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PositionButton(
                        text = "📊 Статусы",
                        selected = screensaverMode == "status",
                        onClick = { screensaverMode = "status"; saveToServer() },
                        modifier = Modifier.weight(1f)
                    )
                    PositionButton(
                        text = "🖼 Картинки",
                        selected = screensaverMode == "images",
                        onClick = { screensaverMode = "images"; saveToServer() },
                        modifier = Modifier.weight(1f)
                    )
                    PositionButton(
                        text = "🎬 Видео",
                        selected = screensaverMode == "video",
                        onClick = { screensaverMode = "video"; saveToServer() },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "Контент заставки (плейлисты общие для всех ТВ, настраиваются на роутере во вкладке «Медиа»)",
                    style = VpnTypography.BodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (!playlistsLoaded) {
                    Text(
                        text = "⏳ Загрузка плейлистов…",
                        style = VpnTypography.BodyMedium.copy(color = VpnColors.TextMuted)
                    )
                } else {
                    PositionButton(
                        text = "📦 Встроенный список",
                        selected = selectedPlaylistId == null,
                        onClick = { selectedPlaylistId = null; saveToServer() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 18+ плейлисты видны только при включённом режиме 18+
                    val visiblePlaylists = playlists.filter { p ->
                        p.category != "adult" || adultEnabled
                    }
                    visiblePlaylists.forEach { p ->
                        PositionButton(
                            text = (if (p.category == "adult") "🔞 " else "🎞 ") + p.name,
                            selected = selectedPlaylistId == p.id,
                            onClick = { selectedPlaylistId = p.id; saveToServer() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (visiblePlaylists.isEmpty()) {
                        Text(
                            text = "Плейлистов пока нет — используйте встроенный список или создайте плейлист на роутере.",
                            style = VpnTypography.BodyMedium.copy(color = VpnColors.TextMuted)
                        )
                    }
                }

                Text(
                    text = "Режим 18+",
                    style = VpnTypography.BodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                ToggleRow(
                    text = "Разрешить 18+ контент на этом устройстве",
                    checked = adultEnabled,
                    onCheckedChange = {
                        adultEnabled = it
                        saveToServer()
                    }
                )
                Text(
                    text = "При выключенном режиме 18+ плейлисты категории 18+ игнорируются сервером.",
                    style = VpnTypography.BodyMedium.copy(color = VpnColors.TextMuted)
                )

                Text(
                    text = "Звук",
                    style = VpnTypography.BodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                ToggleRow(
                    text = "🔊 Звук видео в заставке",
                    checked = videoSound,
                    onCheckedChange = {
                        videoSound = it
                        saveToServer()
                    }
                )

                Text(
                    text = "Автозапуск при бездействии",
                    style = VpnTypography.BodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                ToggleRow(
                    text = "Показывать заставку автоматически",
                    checked = screensaverAuto,
                    onCheckedChange = {
                        screensaverAuto = it
                        saveToServer()
                    }
                )

                Text(
                    text = "Таймаут бездействия",
                    style = VpnTypography.BodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                val timeoutMinutes = (screensaverIdleTimeoutMs / 60_000f).coerceIn(1f, 1440f)
                TvSlider(
                    label = "Бездействие (мин)",
                    value = timeoutMinutes,
                    valueRange = 1f..1440f,
                    onValueChange = { screensaverIdleTimeoutMs = (it * 60_000f).toLong() },
                    onValueChangeFinished = { saveToServer() },
                    step = 1f,
                    valueSuffix = " мин"
                )

                Text(
                    text = "Автозапуск требует включения службы доступности: Настройки ТВ → Спец. возможности → VPN Monitor Watchdog. Любая клавиша пульта закрывает заставку.",
                    style = VpnTypography.BodyMedium.copy(color = VpnColors.TextMuted)
                )
                GlowButton(
                    text = "▶ Показать заставку",
                    onClick = onShowScreensaver
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlowButton(
                text = "💾 Сохранить",
                onClick = { saveToServer() }
            )
            GlowButton(
                text = "🧪 Проверить",
                onClick = onTestConnection
            )
        }

        AnimatedVisibility(visible = showSaved) {
            Text(
                text = saveMessage,
                style = VpnTypography.BodyLarge.copy(
                    color = if (saveMessage.startsWith("✅")) VpnColors.Success else VpnColors.Danger
                ),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Настройки устройства хранятся на роутере и применяются ко всем экранам (оверлей, заставка, автозапуск). Роутер ищется автоматически по Wi-Fi сети.\nАвтостарт оверлея: при включении ТВ оверлей запускается автоматически, если он включён в настройках.",
            style = VpnTypography.BodyMedium
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VpnColors.Surface, RoundedCornerShape(20.dp))
            .border(1.dp, VpnColors.CardBorder, RoundedCornerShape(20.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = VpnTypography.TitleMedium.copy(color = VpnColors.PrimaryLight)
        )
        content()
    }
}

@Composable
fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1f, label = "fieldScale")

    Column {
        Text(text = label, style = VpnTypography.BodyMedium, modifier = Modifier.padding(bottom = 8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .onFocusChanged { isFocused = it.isFocused }
                .background(VpnColors.Background, RoundedCornerShape(12.dp))
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) VpnColors.Primary else VpnColors.CardBorder,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp),
            textStyle = VpnTypography.BodyLarge.copy(color = VpnColors.TextPrimary),
            cursorBrush = SolidColor(VpnColors.Primary),
            visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = VpnTypography.BodyLarge.copy(color = VpnColors.TextMuted)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}
