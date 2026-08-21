package com.vpnmonitor.tv

import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.media.MediaCodec
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vpnmonitor.tv.ui.components.StatusIndicator
import com.vpnmonitor.tv.ui.theme.VpnColors
import com.vpnmonitor.tv.ui.theme.VpnTypography
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val UPDATE_INTERVAL_MS = 10000L
private const val IMAGE_SLIDESHOW_INTERVAL_MS = 8000L

/**
 * Android (12+) отбирает MediaCodec у фоновых приложений (DreamService
 * заставки — в фоне, когда поверх launcher). media3 1.3.1 не имеет
 * отдельного кода ошибки для этого случая (ERROR_CODE_RECLAIMED появился
 * позже): reclaim приходит как ERROR_CODE_DECODING_FAILED, а признак —
 * MediaCodec.CodecException с errorCode == ERROR_RECLAIMED в цепочке причин.
 */
private fun isCodecReclaimed(error: PlaybackException): Boolean {
    var cause: Throwable? = error
    while (cause != null) {
        if (cause is MediaCodec.CodecException && cause.errorCode == MediaCodec.CodecException.ERROR_RECLAIMED) {
            return true
        }
        cause = cause.cause
    }
    return false
}

class ScreensaverService : DreamService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    companion object {
        const val TAG = "VPNScreensaver"
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val internalViewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = internalViewModelStore

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var apiClient: ApiClient

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        isInteractive = false
        isFullscreen = true
        window?.decorView?.setBackgroundColor(AndroidColor.BLACK)

        apiClient = ApiClient(this)

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ScreensaverService)
            setViewTreeSavedStateRegistryOwner(this@ScreensaverService)
            setViewTreeViewModelStoreOwner(this@ScreensaverService)
            setContent {
                ScreensaverRoot(apiClient = apiClient)
            }
        }

        setContentView(view)
        Log.d(TAG, "Screensaver started")
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        Log.d(TAG, "Dreaming started")
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        Log.d(TAG, "Screensaver stopped")
    }
}

// ============ Корень заставки ============

/**
 * Корень заставки. Конфиг (режим фона, картинки, плейлист) загружается
 * с сервера через [apiClient]; при недоступности роутера — дефолты.
 * Для видео-режима плейлист транскодируется на роутере в H.264 MP4,
 * проигрываются served_url'ы (локальные ссылки роутера).
 *
 * [overrideMode]/[overrideVideoUrls]/[overrideImageUrls] — только для
 * debug-экрана, чтобы переключать режимы кнопками пульта.
 */
@Composable
fun ScreensaverRoot(
    apiClient: ApiClient,
    overrideMode: String? = null,
    overrideVideoUrls: List<String>? = null,
    overrideImageUrls: List<String>? = null
) {
    var statusData by remember { mutableStateOf<StatusResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var backgroundMode by remember { mutableStateOf(overrideMode ?: "status") }
    var videoUrls by remember { mutableStateOf<List<String>>(overrideVideoUrls ?: emptyList()) }
    var customImageUrls by remember { mutableStateOf<List<String>>(overrideImageUrls ?: emptyList()) }
    var videoSound by remember { mutableStateOf(false) }

    // Загрузка конфига с сервера + подготовка видео-плейлиста (транскодинг на роутере)
    LaunchedEffect(apiClient, overrideMode, overrideVideoUrls, overrideImageUrls) {
        val cfg = apiClient.fetchDeviceConfig()
        val sc = cfg?.screensaver
        backgroundMode = overrideMode ?: sc?.mode ?: "status"
        customImageUrls = overrideImageUrls ?: (sc?.imageUrls ?: emptyList())
        videoSound = cfg?.videoSound ?: false
        videoUrls = overrideVideoUrls ?: if (backgroundMode == "video") {
            resolveVideoPlaylist(apiClient, sc?.playlist ?: emptyList())
        } else {
            emptyList()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            statusData = apiClient.fetchStatus()
            isLoading = false
            delay(UPDATE_INTERVAL_MS)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Фоновый слой по выбранному режиму
        when (backgroundMode) {
            "images" -> ImageSlideshowBackground(customImageUrls)
            "video" -> VideoBackground(videoUrls, soundEnabled = videoSound)
            else -> AnimatedGradientBackground()
        }

        // Затемнение для читаемости статусов поверх картинок/видео
        if (backgroundMode == "images" || backgroundMode == "video") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    )
            )
        }

        // Статусы VPN поверх фона
        StatusOverlay(statusData = statusData, isLoading = isLoading)
    }
}

/**
 * Готовит видео-плейлист: для каждого URL из плейлиста просит роутер
 * перекодировать (prepare + poll), возвращает served_url'ы локального
 * транскодинга. Если плейлист пуст — встроенные аниме-ролики напрямую.
 *
 * Если транскодинг не успел за отведённое время (большие файлы) —
 * возвращаем ИСХОДНЫЕ URL: онлайновые mp4 (hentaicloud/archive.org) H.264
 * ExoPlayer играет напрямую, а транскодинг доделается в фоне.
 */
private suspend fun resolveVideoPlaylist(apiClient: ApiClient, playlist: List<String>): List<String> {
    if (playlist.isEmpty()) return DEFAULT_ANIME_VIDEO_URLS
    // Запускаем транскодинг всех роликов параллельно
    playlist.forEach { apiClient.prepareMedia(it) }
    // Ждём готовности хотя бы одного (остальные доиграют к следующему показу)
    val deadline = System.currentTimeMillis() + 90_000L
    val ready = LinkedHashSet<String>()
    while (System.currentTimeMillis() < deadline) {
        for (url in playlist) {
            val st = apiClient.mediaStatus(url) ?: continue
            if (st.status == "ready" && st.servedUrl.isNotBlank()) ready.add(st.servedUrl)
        }
        if (ready.isNotEmpty()) return ready.toList()
        delay(2000)
    }
    // Транскодинг не успел — играем оригиналы напрямую (H.264 mp4 онлайновые
    // источники ExoPlayer тянет сам). Транскодинг при этом продолжает идти в фоне.
    return if (ready.isNotEmpty()) ready.toList() else playlist
}

// ============ Фоновые режимы ============

@Composable
fun AnimatedGradientBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bgOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            VpnColors.Primary.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(offset, size.height * 0.3f),
                        radius = size.width * 0.5f
                    ),
                    radius = size.width * 0.5f,
                    center = Offset(offset, size.height * 0.3f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            VpnColors.GradientStart.copy(alpha = 0.07f),
                            Color.Transparent
                        ),
                        center = Offset(size.width - offset * 0.5f, size.height * 0.7f),
                        radius = size.width * 0.4f
                    ),
                    radius = size.width * 0.4f,
                    center = Offset(size.width - offset * 0.5f, size.height * 0.7f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            VpnColors.GradientEnd.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.2f, size.height * 0.8f),
                        radius = size.width * 0.35f
                    ),
                    radius = size.width * 0.35f,
                    center = Offset(size.width * 0.2f, size.height * 0.8f)
                )
            }
    )
}

/**
 * Слайд-шоу аниме-картинок. Если свой список URL не задан — тянет случайные
 * картинки с waifu.pics API.
 */
@Composable
fun ImageSlideshowBackground(customImageUrls: List<String>) {
    var images by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }

    LaunchedEffect(customImageUrls) {
        val urls = if (customImageUrls.isNotEmpty()) customImageUrls else fetchAnimeImageUrls(6)
        val loaded = mutableListOf<ImageBitmap>()
        for (url in urls) {
            loadImageBitmap(url)?.let { loaded.add(it) }
            if (loaded.size >= 8) break
        }
        images = loaded
    }

    LaunchedEffect(images.size) {
        if (images.size > 1) {
            while (true) {
                delay(IMAGE_SLIDESHOW_INTERVAL_MS)
                currentIndex = (currentIndex + 1) % images.size
            }
        }
    }

    val current = images.getOrNull(currentIndex)
    if (current == null) {
        AnimatedGradientBackground()
    } else {
        AnimatedContent(
            targetState = current,
            transitionSpec = { fadeIn(tween(1000)) togetherWith fadeOut(tween(1000)) },
            label = "slideshow"
        ) { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/**
 * Встроенный плейлист аниме-видео (проверенные прямые mp4, без ключей):
 * DesktopHut HD-обои, Tenor короткие циклы, archive.org тизер.
 * Используется, когда плейлист на сервере пуст или роутер недоступен.
 */
private val DEFAULT_ANIME_VIDEO_URLS = listOf(
    // Сначала лёгкие ролики — играют на любом декодере (включая Amlogic без 4K):
    // Tenor — короткие аниме-циклы (2-5s, почти бесшовные, 640x360 и ниже)
    "https://media.tenor.com/_8oadF3hZwIAAAPo/kiss.mp4",
    "https://media.tenor.com/cQzRWAWrN6kAAAPo/ichigo-hiro.mp4",
    "https://media.tenor.com/ZDqsYLDQzIUAAAPo/shirayuki-zen-kiss-anime.mp4",
    "https://media.tenor.com/LrKmxrDxJN0AAAPo/love-cheek.mp4",
    "https://media.tenor.com/lyuW54_wDU0AAAPo/kiss-anime.mp4",
    // archive.org — MF Ghost тизер (302 → CDN, ExoPlayer следует редиректу, 854x480)
    "https://archive.org/download/youtube-2fSXarfeg3A/2fSXarfeg3A.mp4",
    // Затем 4K DesktopHut-обои — играют на TV с 4K-декодером (Sony). На слабых
    // декодерах дорожка отклоняется молча (без onPlayerError) — их отсеивает
    // PlaylistWatchdog по таймауту без кадров.
    "https://www.desktophut.com/files/zcg5EVr9AD-ghibli-style-water-basin-live-wallpaper.mp4",
    "https://www.desktophut.com/files/FjcLNEVKRk-chainsaw-man-reze-summer-poolside-live-wallpaper.mp4",
    "https://www.desktophut.com/files/N3whV52EDj-makima-ocean-halo-live-wallpaper.mp4",
    "https://www.desktophut.com/files/1776077648.mp4",
    "https://www.desktophut.com/files/1774976305.mp4",
    "https://www.desktophut.com/files/1774885353.mp4"
)

/**
 * Watchdog для видео-плейлиста: если после перехода на новый ролик за
 * [WATCHDOG_TIMEOUT_MS] не появились кадры (декодер молча отклонил дорожку,
 * напр. 4K на слабом SoC — без onPlayerError), принудительно переходим дальше.
 */
private class PlaylistWatchdog(
    private val player: Player
) : Player.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private val skip = object : Runnable {
        override fun run() {
            // Кадры рендерятся => позиция растёт. Если декодер молча отклонил
            // ролик (4K на слабом SoC, MTK surface-баг) — позиция застревает на
            // ~0, и мы переходим дальше. НЕ полагаемся на videoSize: он не
            // сбрасывается между роликами (после первого успешного ролика старый
            // watchdog навсегда «выключался» и 4K-ролики висли молча).
            // Не скипаем, пока идёт буферизация (данные прибывают, первый кадр
            // ещё не декодирован) — только когда ролик мёртв (нет данных) или
            // READY без движения позиции (декодер отклонил дорожку).
            val stalled = player.currentPosition <= 200 && (
                player.bufferedPosition <= 200 ||
                    player.playbackState == Player.STATE_READY
                )
            if (stalled) {
                Log.w(
                    ScreensaverService.TAG,
                    "Video watchdog: no frames in $WATCHDOG_TIMEOUT_MS ms, skipping to next"
                )
                player.seekToNextMediaItem()
                player.prepare()
                player.playWhenReady = true
            }
        }
    }

    init {
        player.addListener(this)
        // Страховка на случай, если onMediaItemTransition для первого ролика
        // уже отработал до регистрации слушателя.
        handler.postDelayed(skip, WATCHDOG_TIMEOUT_MS)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        handler.removeCallbacks(skip)
        handler.postDelayed(skip, WATCHDOG_TIMEOUT_MS)
    }

    fun dispose() {
        handler.removeCallbacks(skip)
        player.removeListener(this)
    }

    private companion object {
        const val WATCHDOG_TIMEOUT_MS = 4000L
    }
}

/**
 * Стриминг видео-фона (ExoPlayer, loop). Звук включается только при
 * [soundEnabled] = true (настройка «Звук видео» на устройстве/в вебе).
 * [videoUrls] — served_url'ы транскодинга с роутера (или встроенный плейлист).
 */
@Composable
fun VideoBackground(videoUrls: List<String>, soundEnabled: Boolean) {
    val urls = remember(videoUrls) { videoUrls.ifEmpty { DEFAULT_ANIME_VIDEO_URLS } }

    val context = LocalContext.current
    val player = remember(urls, soundEnabled) {
        // MTK AVC-декодеры (OMX.MTK.VIDEO.DECODER.AVC.kick) на Android 10/12 молча
        // не стартуют в surface-режиме: ACodec не может переключить выходной порт
        // в DynamicANWBuffer, кодек конфигурируется, но ни один сэмпл не
        // потребляется и кадры не выдаются (watchdog скипает каждые 4с).
        // Исключаем MTK-компоненты из выбора — остаётся Google SW-декодер,
        // который стартует всегда. На устройствах с рабочим HW-декодером
        // (Amlogic и т.п.) фильтр ни на что не влияет: имена декодеров MTK не содержат.
        // ТЕСТ: TextureView починил surface-путь (ACodec fallback заработал),
        // поэтому пробуем MTK HW-декодер — для 4K-роликов desktophut он нужен.
        // Если HW+TextureView не даст кадров — вернуть MediaCodecSelector
        // с фильтром MTK-компонентов (остаётся OMX.google.h264.decoder).
        // (Аудио-клок AudioTrack оставляем включённым: "device stall time
        // corrected" — безобидный warning на MTK, воспроизведение не ломает.)
        ExoPlayer.Builder(context, DefaultRenderersFactory(context)).build().apply {
            volume = if (soundEnabled) 1f else 0f
            repeatMode = Player.REPEAT_MODE_ALL
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ false
            )
            setMediaItems(urls.map { MediaItem.fromUri(it) })
            prepare()
            playWhenReady = true
            val mainHandler = Handler(Looper.getMainLooper())
            // Retry того же ролика после reclaim кодека. Отдельный Runnable + общий
            // Handler, чтобы повторные reclaim не наслаивали отложенные задачи.
            val retryReclaimed = Runnable {
                // Плеер мог быть освобождён (DisposableEffect onDispose),
                // пока отложенный retry ждал — prepare() на released плеере
                // бросает IllegalStateException.
                try {
                    prepare()
                    playWhenReady = true
                } catch (e: IllegalStateException) {
                    Log.w(ScreensaverService.TAG, "Player released while retrying reclaim, skip retry")
                }
            }
            addListener(object : Player.Listener {
                // Диагностика поверхностной MTK/surface-проблемы — оставляем,
                // пока не подтвердим кадры на обоих ТВ.
                override fun onPlaybackStateChanged(playbackState: Int) {
                    Log.d(
                        ScreensaverService.TAG,
                        "VideoBackground: state=$playbackState (1idle 2buil 3ready 4end), items=${urls.size}, pos=${currentPosition}ms/${duration}ms"
                    )
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    Log.d(
                        ScreensaverService.TAG,
                        "VideoBackground: item=${mediaItem?.localConfiguration?.uri}, pos=${currentPosition}ms/${duration}ms"
                    )
                }
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    Log.d(
                        ScreensaverService.TAG,
                        "VideoBackground: posDisc r=$reason old=${oldPosition.positionMs}(c=${oldPosition.contentPositionMs}) new=${newPosition.positionMs}(c=${newPosition.contentPositionMs}), cur=${currentPosition}ms/${duration}ms"
                    )
                }
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    Log.d(
                        ScreensaverService.TAG,
                        "VideoBackground: videoSize=${videoSize.width}x${videoSize.height}"
                    )
                }
                override fun onPlayerError(error: PlaybackException) {
                    // Android 12+ отбирает MediaCodec у фоновых приложений
                    // (DreamService в фоне — launcher наверху): кодек «отозван»
                    // (ERROR_CODE_RECLAIMED). Скип на следующий ролик не помогает:
                    // REPEAT_MODE_ALL вернёт тот же ролик, и reclaim повторится —
                    // получаем watchdog-петлю каждые 4с. Вместо скипа ждём 2с и
                    // пробуем тот же ролик заново (когда заставка снова станет
                    // видимой, систему не нужно будет отбирать кодек).
                    if (isCodecReclaimed(error)) {
                        Log.w(ScreensaverService.TAG, "Codec reclaimed by system, retrying same item in 2s")
                        mainHandler.removeCallbacks(retryReclaimed)
                        mainHandler.postDelayed(retryReclaimed, 2000)
                        return
                    }
                    // Ролик не воспроизводится (напр. 4K@60 на слабом декодере) —
                    // пропускаем его и переходим к следующему в плейлисте.
                    Log.w(ScreensaverService.TAG, "Video error, skipping to next: ${error.errorCodeName}")
                    seekToNextMediaItem()
                    prepare()
                    playWhenReady = true
                }
            })
        }
    }

    // Watchdog: декодер может молча отклонить дорожку (4K на слабом SoC) без
    // onPlayerError — тогда пропускаем ролик по таймауту отсутствия кадров.
    val watchdog = remember(urls) { PlaylistWatchdog(player) }

    // Ключ по player: при смене URL старый плеер (и его watchdog) освобождаются
    // сразу, а не утекают (раньше DisposableEffect(Unit) держал первый плеер).
    DisposableEffect(player) {
        onDispose {
            watchdog.dispose()
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            // SurfaceView-путь на MTK-ТВ (Android 10/12) не даёт кадров даже для
            // Google SW-декодера: ACodec не может переключить выходной порт в
            // DynamicANWBuffer и кодек молчит (0 сэмплов, 0 кадров). TextureView
            // (res/layout/view_player_texture.xml, surface_type="texture_view")
            // использует SurfaceTexture — обходит сломанный surface-путь.
            android.view.LayoutInflater.from(ctx)
                .inflate(R.layout.view_player_texture, null) as PlayerView
        },
        // update-блок (а не factory) переподключает PlayerView к новому плееру при
        // смене URL — раньше factory навсегда захватывал первый плеер.
        update = { view ->
            if (view.player !== player) view.player = player
        },
        modifier = Modifier.fillMaxSize()
    )
}

// ============ Статусы VPN поверх фона ============

@Composable
fun StatusOverlay(statusData: StatusResponse?, isLoading: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 36.dp)
    ) {
        // Заголовок + часы
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "🔒 VPN Monitor",
                    style = VpnTypography.DisplayLarge.copy(
                        brush = Brush.horizontalGradient(
                            colors = listOf(VpnColors.GradientStart, VpnColors.GradientEnd)
                        )
                    )
                )
                Text(
                    text = "Обновлено: ${statusData?.checkedAt ?: "—"}",
                    style = VpnTypography.BodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Clock()
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (isLoading) {
            Text(
                text = "Загрузка...",
                style = VpnTypography.BodyLarge,
                modifier = Modifier.alpha(0.5f)
            )
        } else if (statusData?.servers.isNullOrEmpty()) {
            Text(
                text = "Нет настроенных серверов",
                style = VpnTypography.BodyLarge
            )
        } else {
            // Список серверов — адаптивная сетка
            val servers = statusData!!.servers
            val columnCount = when {
                servers.size <= 2 -> 1
                servers.size <= 4 -> 2
                servers.size <= 8 -> 3
                else -> 4
            }
            val rows = servers.chunked(columnCount)
            val verticalSpacing = if (columnCount == 1) 14.dp else 10.dp
            val horizontalSpacing = if (columnCount >= 3) 10.dp else 14.dp

            Column(
                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                rows.forEachIndexed { rowIndex, row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEachIndexed { colIndex, server ->
                            val index = rowIndex * columnCount + colIndex
                            val weight = if (columnCount == 1) 1f else 1f / columnCount
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(animationSpec = tween(400, delayMillis = index * 80)) +
                                    slideInVertically(animationSpec = tween(400, delayMillis = index * 80)) { it / 3 },
                                exit = fadeOut(),
                                modifier = Modifier.weight(weight)
                            ) {
                                if (columnCount == 1) {
                                    ServerRow(server = server)
                                } else {
                                    ServerRowCompact(server = server)
                                }
                            }
                        }
                        // Заполнение пустых ячеек в неполной строке
                        if (row.size < columnCount) {
                            repeat(columnCount - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Сводка
            val upCount = statusData!!.servers.count { it.status == "up" }
            val downCount = statusData!!.servers.count { it.status == "down" }
            val degradedCount = statusData!!.servers.count { it.status == "degraded" }

            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier
                    .background(
                        VpnColors.Surface.copy(alpha = 0.55f),
                        RoundedCornerShape(18.dp)
                    )
                    .border(1.dp, VpnColors.CardBorder.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 40.dp, vertical = 20.dp)
            ) {
                SummaryItem("VLESS OK", upCount, VpnColors.Success)
                SummaryItem("Degraded", degradedCount, VpnColors.Warning)
                SummaryItem("Down", downCount, VpnColors.Danger)
            }
        }
    }
}

@Composable
fun Clock() {
    val time by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Text(
        text = formatter.format(Date(time)),
        style = VpnTypography.DisplayMedium.copy(
            color = VpnColors.TextSecondary,
            fontWeight = FontWeight.Light
        )
    )
}

@Composable
fun ServerRow(server: ServerStatus) {
    val statusColor = when (server.status) {
        "up" -> VpnColors.Success
        "down" -> VpnColors.Danger
        "degraded" -> VpnColors.Warning
        else -> VpnColors.TextMuted
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                VpnColors.Surface.copy(alpha = 0.6f),
                RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = statusColor.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(start = 0.dp, end = 24.dp, top = 14.dp, bottom = 14.dp)
    ) {
        // Цветная полоска статуса слева
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(48.dp)
                .background(statusColor, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
        )
        Spacer(modifier = Modifier.width(18.dp))
        StatusIndicator(status = server.status ?: "unknown")
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(server.name)
                    if (!server.location.isNullOrEmpty()) {
                        append(" · ")
                        append(server.location)
                    }
                },
                style = VpnTypography.TitleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                // Бейдж VLESS
                Text(
                    text = if (server.vlessOk == 1) "✓ VLESS" else "✗ VLESS",
                    style = VpnTypography.Label.copy(
                        color = if (server.vlessOk == 1) VpnColors.Success else VpnColors.Danger,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier
                        .background(
                            (if (server.vlessOk == 1) VpnColors.Success else VpnColors.Danger).copy(alpha = 0.12f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
                // Бейдж пинга
                if (server.pingMs != null && server.pingMs > 0) {
                    Text(
                        text = "${server.pingMs.toInt()}ms",
                        style = VpnTypography.Label.copy(
                            color = VpnColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier
                            .background(VpnColors.Background.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (!server.errorMsg.isNullOrEmpty()) {
            Text(
                text = server.errorMsg,
                style = VpnTypography.Label.copy(color = VpnColors.Danger),
                textAlign = TextAlign.End
            )
        }
    }
}

/** Компактная строка сервера для multi-column layouts (>=3 колонок) */
@Composable
fun ServerRowCompact(server: ServerStatus) {
    val statusColor = when (server.status) {
        "up" -> VpnColors.Success
        "down" -> VpnColors.Danger
        "degraded" -> VpnColors.Warning
        else -> VpnColors.TextMuted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                VpnColors.Surface.copy(alpha = 0.6f),
                RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = statusColor.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIndicator(status = server.status ?: "unknown")
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = VpnTypography.TitleMedium.copy(fontSize = 14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!server.location.isNullOrEmpty()) {
                    Text(
                        text = server.location,
                        style = VpnTypography.Label.copy(fontSize = 11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (server.vlessOk == 1) "✓ VLESS" else "✗ VLESS",
                style = VpnTypography.Label.copy(
                    fontSize = 11.sp,
                    color = if (server.vlessOk == 1) VpnColors.Success else VpnColors.Danger,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier
                    .background(
                        (if (server.vlessOk == 1) VpnColors.Success else VpnColors.Danger).copy(alpha = 0.12f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
            if (server.pingMs != null && server.pingMs > 0) {
                Text(
                    text = "${server.pingMs.toInt()}ms",
                    style = VpnTypography.Label.copy(
                        fontSize = 11.sp,
                        color = VpnColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier
                        .background(VpnColors.Background.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
        if (!server.errorMsg.isNullOrEmpty()) {
            Text(
                text = server.errorMsg,
                style = VpnTypography.Label.copy(fontSize = 10.sp, color = VpnColors.Danger),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun SummaryItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut()
            },
            label = "summary"
        ) { target ->
            Text(
                text = target.toString(),
                style = VpnTypography.DisplayMedium.copy(color = color)
            )
        }
        Text(text = label, style = VpnTypography.Label)
    }
}

// ============ Загрузка изображений ============

private val imageHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

private suspend fun fetchAnimeImageUrls(count: Int = 6): List<String> {
    return withContext(Dispatchers.IO) {
        val urls = mutableListOf<String>()
        repeat(count) {
            try {
                val request = Request.Builder()
                    .url(VpnMonitorApp.ANIME_IMAGE_API)
                    .build()
                imageHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val url = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                        if (url != null && !urls.contains(url)) urls.add(url)
                    }
                }
            } catch (e: Exception) {
                // пропускаем неудачный запрос
            }
        }
        urls
    }
}

private suspend fun loadImageBitmap(url: String): ImageBitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            imageHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
