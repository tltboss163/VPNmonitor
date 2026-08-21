# Структура проекта

## Android TV приложение

```
VPNmonitor/
├── app/
│   ├── build.gradle                    # Зависимости (Compose, ExoPlayer, OkHttp)
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml         # Разрешения, сервисы,.activity
│       ├── java/com/vpnmonitor/tv/
│       │   ├── VpnMonitorApp.kt        # Application (Koin DI, SharedPreferences)
│       │   ├── MainActivity.kt         # Главный экран настроек
│       │   ├── ApiClient.kt            # REST-клиент (OkHttp + Gson)
│       │   │                           #   auto-discover, retry, fetchStatus
│       │   │                           #   fetchDeviceConfig, saveDeviceConfig
│       │   │                           #   fetchPlaylists, fetchMediaSources
│       │   │                           #   prepareMedia, mediaStatus
│       │   ├── ScreensaverService.kt   # DreamService заставка (960 строк)
│       │   │                           #   ScreensaverRoot, StatusOverlay
│       │   │                           #   Adaptive grid (1-4 columns)
│       │   │                           #   AnimatedGradientBackground
│       │   │                           #   ImageSlideshowBackground
│       │   │                           #   VideoBackground (ExoPlayer)
│       │   │                           #   ServerRow, ServerRowCompact
│       │   ├── ScreensaverActivity.kt  # Production wrapper для ScreensaverService
│       │   ├── ScreensaverDebugActivity.kt  # Debug: ручное переключение
│       │   ├── OverlayService.kt       # SYSTEM_ALERT_WINDOW оверлей
│       │   │                           #   Foreground Service + Handler loop
│       │   ├── IdleWatchdogService.kt  # Accessibility Service
│       │   │                           #   Мониторинг бездействия → запуск заставки
│       │   ├── BootReceiver.kt         # BOOT_COMPLETED → автозапуск оверлея
│       │   ├── RouterDiscovery.kt      # Автопоиск роутера через DHCP gateway
│       │   └── ui/
│       │       ├── screens/
│       │       │   └── SettingsScreen.kt   # Настройки (Compose)
│       │       │                           #   URL роутера, API Key
│       │       │                           #   Оверлей: позиция, размер, прозрачность
│       │       │                           #   Заставка: режим, плейлист, фон
│       │       │                           #   Медиа: плейлисты, источники
│       │       │                           #   TvSlider, TvSwitch, кнопки позиции
│       │       ├── components/
│       │       │   └── Components.kt       # TvSlider (step + valueSuffix)
│       │       │                           #   TvSwitch, TvCard,焦点-анимации
│       │       └── theme/
│       │           └── Theme.kt            # VpnColors (Primary, Success/Danger/Warning)
│       │                                   #   VpnTypography (DisplayLarge → Label)
│       └── res/
│           ├── drawable/               # banner.xml, overlay_bg.xml, кнопки
│           ├── layout/
│           │   └── view_player_texture.xml  # TextureView для ExoPlayer
│           ├── mipmap/                 # Иконки приложения
│           ├── raw/
│           │   └── alert_sound.wav     # Звук алерта
│           ├── values/
│           │   ├── colors.xml
│           │   ├── strings.xml         # app_name = "VPN Monitor"
│           │   └── styles.xml
│           └── xml/
│               └── accessibility_watchdog.xml  # Конфиг Accessibility Service
├── build.gradle                        # Kotlin 1.9.22, AGP
├── gradle.properties
├── gradle/wrapper/                     # Gradle 8.14.5
├── gradlew / gradlew.bat
├── settings.gradle
├── DESIGN.md                           # Дизайн-система (Neo-Industrial)
└── README.md                           # Документация
```

## Бэкенд (роутер)

```
/etc/vpn-monitor/
├── vpn_monitor.py       # Главный процесс
│                       #   checker loop (300s)
│                       #   refresh_subscriptions()
│                       #   check_all_servers()
│                       #   Запуск API server
├── api_server.py        # Flask REST API (577 строк)
│                       #   Web UI (index.html)
│                       #   /api/status, /api/servers
│                       #   /api/subscriptions, /api/devices
│                       #   /api/playlists, /api/media-sources
│                       #   /api/media/prepare, /api/media/status
│                       #   /api/device/config (auto-register)
│                       #   /api/settings, /api/saved-content
│                       #   /api/check, /api/force-check
│                       #   /api/history, /api/logs
├── checker.py          # Проверка серверов
│                       #   refresh_subscriptions()
│                       #   check_all_servers(force=False)
│                       #   SSH через plink → ping + curl
├── database.py         # SQLite операции
│                       #   servers, devices, playlists
│                       #   media_sources, subscriptions
│                       #   display_settings, device_settings
│                       #   saved_content, logs, history
├── config.py           # DB_PATH, API_HOST, API_PORT
│                       #   CHECK_INTERVAL, ALERT_THRESHOLD
├── transcoder.py       # Транскодинг видео (ffmpeg)
│                       #   prepare(url), status(url)
├── media_sources.py    # Парсинг медиа-сайтов
│                       #   sync_source(src)
│                       #   hentaicloud, archive.org
├── index.html          # Веб-дашборд (Neo-Industrial)
│                       #   Карточки серверов (CSS Grid)
│                       #   Подписки (модалка парсинга)
│                       #   Настройки (модалка общих настроек)
│                       #   Устройства, медиа, логи
└── vpn.db              # SQLite база данных
```

## Модули и зависимости

| Модуль | Назначение |
|--------|-----------|
| `VpnMonitorApp` | Application context, Koin DI, SharedPreferences |
| `ApiClient` | HTTP REST → роутер с auto-discover + retry |
| `ScreensaverService` | DreamService: заставка с адаптивной сеткой |
| `OverlayService` | SYSTEM_ALERT_WINDOW: оверлей поверх приложений |
| `IdleWatchdogService` | Accessibility: мониторинг бездействия |
| `RouterDiscovery` | DHCP gateway → autodiscover роутер |
| `SettingsScreen` | Compose UI: все настройки приложения |
| `Components` | Compose: TvSlider, TvSwitch, TvCard |
| `Theme` | VpnColors, VpnTypography |
