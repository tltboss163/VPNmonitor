# VPN Monitor for Android TV

Мониторинг VLESS/XTR подключений на Android TV с красивой заставкой, оверлеем и веб-дашбордом.

![Platform](https://img.shields.io/badge/platform-Android%20TV-3DDC84?logo=android)
![Kotlin](https://img.shields.io/badge/kotlin-1.9.22-7F52FF?logo=kotlin)
![Compose](https://img.shields.io/badge/jetpack%20compose-1.5-blueviolet)

---

## Возможности

- **Серверы** — VLESS, VMess, Trojan с проверкой доступности, пингом и VLESS-статусом
- **Подписки** — вставка URL подписки, авто-парсинг серверов, периодическая синхронизация
- **Заставка** — статусы серверов поверх анимированного фона (градиент / картинки / видео), адаптивная сетка 1-4 колонки для любого количества серверов
- **Оверлей** — компактный виджет поверх любого приложения, настраиваемый по позиции, размеру, прозрачности
- **Веб-дашборд** — панель управления на `http://роутер:17463` с карточками серверов, медиа, настройками
- **Устройства** — автоматическая регистрация ТВ, персональные настройки заставки и оверлея
- **Медиа** — плейлисты, источники (hentaicloud, archive.org), транскодинг на роутере

---

## Архитектура

```
┌─────────────────────────────────────────────┐
│  OpenWrt Router (192.168.2.1)               │
│  ├── vpn_monitor.py   — фоновый checker     │
│  ├── api_server.py    — Flask REST API      │
│  ├── checker.py       — проверка серверов   │
│  ├── transcoder.py    — транскодинг видео   │
│  ├── media_sources.py — парсинг медиа       │
│  ├── database.py      — SQLite              │
│  └── index.html       — веб-дашборд         │
└────────────┬────────────────────────────────┘
             │ HTTP :17463
┌────────────┴────────────────────────────────┐
│  Android TV App (com.vpnmonitor.tv)          │
│  ├── ApiClient      — REST → роутер         │
│  ├── MainActivity   — настройки + управление │
│  ├── ScreensaverService — DreamService      │
│  ├── OverlayService — SYSTEM_ALERT_WINDOW   │
│  └── IdleWatchdogService — AccessibilitySvc │
└─────────────────────────────────────────────┘
```

---

## Требования

| Компонент | Минимальная версия |
|-----------|-------------------|
| Android TV | 5.0 (API 21) |
| OpenWrt роутер | Любой с Python 3 + Flask |
| JDK | **21** (JDK 25 несовместим с Kotlin 1.9.22) |
| Gradle | 8.14.5 (встроенный wrapper) |
| Android SDK | 34 |

---

## Сборка APK

### Windows (PowerShell)

```powershell
# 1. Клонировать репозиторий
git clone https://github.com/tltboss163/VPNmonitor.git
cd VPNmonitor

# 2. Установить JDK 21 (если не установлен)
# Скачать: https://adoptium.net/temurin/releases/?version=21
# Распаковать, например в %USERPROFILE%\.jdks\jdk-21.0.12+8

# 3. Указать путь к JDK
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\jdk-21.0.12+8"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 4. Собрать APK
.\gradlew.bat assembleDebug

# APK будет в: app\build\outputs\apk\debug\app-debug.apk
```

### Linux / macOS

```bash
git clone https://github.com/tltboss163/VPNmonitor.git
cd VPNmonitor

# Установить JDK 21
export JAVA_HOME=/path/to/jdk-21
export PATH=$JAVA_HOME/bin:$PATH

./gradlew assembleDebug
```

### Android Studio

1. Открыть папку `VPNmonitor` в Android Studio
2. **File → Settings → Build → Gradle → Gradle JDK** → выбрать JDK 21
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**

---

## Установка на Android TV

### Через ADB (по Wi-Fi)

```bash
# 1. Включить ADB на TV
#    Sony BRAVIA: Настройки → Система → О разработчике → ADB-отладка → Вкл
#    MiTV: Настройки → О телевизоре → 5× "Версия" → Разработчик → ADB-отладка → Вкл

# 2. Подключиться к TV
adb connect 192.168.2.132:5555    # MiTV
adb connect 192.168.2.193:5555    # Sony BRAVIA

# 3. Установить APK
adb install app\build\outputs\apk\debug\app-debug.apk

# 4. Запустить
adb shell am start -n com.vpnmonitor.tv/.MainActivity
```

### Через USB

1. Подключить TV к компьютеру по USB
2. Включить "Отладка по USB" в настройках разработчика
3. `adb install app-debug.apk`

---

## Настройка роутера

### Структура файлов на роутере

```
/etc/vpn-monitor/
├── vpn_monitor.py       # Главный процесс (checker loop + API)
├── api_server.py        # Flask REST API + веб-интерфейс
├── checker.py           # Проверка серверов (SSH → plink)
├── database.py          # SQLite операции
├── config.py            # Конфигурация (порты, интервалы)
├── transcoder.py        # Транскодинг видео на роутере
├── media_sources.py     # Парсинг медиа-сайтов
├── index.html           # Веб-дашборд (Neo-Industrial дизайн)
└── vpn.db               # База данных (SQLite)
```

### Запуск

```bash
cd /etc/vpn-monitor
nohup python3 vpn_monitor.py > /tmp/vpn_monitor.log 2>&1 &
```

### Остановка

```bash
kill $(pidof python3)
```

### Проверка логов

```bash
tail -f /tmp/vpn_monitor.log
```

---

## API Reference

### Серверы

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/` | Веб-дашборд (HTML) |
| GET | `/api/status` | Статус всех серверов + настройки |
| GET | `/api/servers` | Список всех серверов |
| POST | `/api/servers` | Добавить сервер |
| PUT | `/api/servers/<id>` | Обновить сервер |
| DELETE | `/api/servers/<id>` | Удалить сервер |
| POST | `/api/parse-uri` | Парсинг URI (`{"uri": "vless://..."}`) |
| POST | `/api/check` | Ручная проверка |
| POST | `/api/force-check` | Принудительная проверка |

### Подписки

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/subscriptions` | Список подписок |
| POST | `/api/subscriptions` | Добавить подписку (`{"url": "https://..."}`) |
| DELETE | `/api/subscriptions/<id>` | Удалить подписку |

### Устройства

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/devices` | Список ТВ-устройств |
| GET | `/api/device/config?device_id=X&name=Y` | Конфиг устройства (auto-register) |
| POST | `/api/device/config` | Сохранить конфиг устройства |
| DELETE | `/api/devices/<id>` | Удалить устройство |

### Медиа

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/playlists` | Список плейлистов |
| POST | `/api/playlists` | Создать плейлист |
| PUT | `/api/playlists/<id>` | Обновить плейлист |
| DELETE | `/api/playlists/<id>` | Удалить плейлист |
| GET | `/api/media-sources` | Список медиа-источников |
| POST | `/api/media-sources` | Добавить источник |
| POST | `/api/media-sources/<id>/sync` | Синхронизировать источник |
| POST | `/api/media-sources/sync-all` | Синхронизировать все |
| POST | `/api/media/prepare` | Подготовить видео (транскодинг) |
| GET | `/api/media/status?url=X` | Статус транскодинга |

### Прочее

| Метод | Путь | Описание |
|-------|------|----------|
| GET/POST | `/api/settings` | Глобальные настройки оверлея |
| GET | `/api/history/<id>` | История проверок сервера |
| GET | `/api/logs` | Системные логи |
| GET/POST | `/api/saved-content` | Сохранённый контент |

---

## Поддерживаемые форматы URI

### VLESS

```
vless://UUID@host:port#name?type=tcp&security=reality&sni=domain&fp=chrome&pbk=KEY&sid=ID&spx=/&flow=xtls-rprx-vision
```

### VMess

```
vmess://BASE64_JSON
```

Где JSON:
```json
{"id":"uuid","add":"host","port":"443","ps":"name","net":"tcp","tls":"reality","sni":"domain","fp":"chrome"}
```

### Trojan

```
trojan://password@host:port#name?type=tcp&security=tls&sni=domain
```

### Подписка (URL)

```
https://example.com/sub/vless
```

HTTP(S)-ссылки автоматически пробуются как подписка. Поддерживаются base64-кодированные списки с `vless://`, `vmess://`, `trojan://` URI.

---

## Структура проекта

```
VPNmonitor/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/vpnmonitor/tv/
│       │   ├── VpnMonitorApp.kt           # Application (Koin DI, SharedPreferences)
│       │   ├── MainActivity.kt             # Главный экран (настройки URL, API Key)
│       │   ├── ApiClient.kt                # HTTP-клиент (OkHttp → роутер)
│       │   ├── ScreensaverService.kt       # DreamService заставка + адаптивная сетка
│       │   ├── ScreensaverActivity.kt      # Production-заставка (Activity wrapper)
│       │   ├── ScreensaverDebugActivity.kt # Debug-заставка (переключение кнопками)
│       │   ├── OverlayService.kt           # Оверлей поверх приложений
│       │   ├── IdleWatchdogService.kt      # Accessibility Service (мониторинг бездействия)
│       │   ├── BootReceiver.kt             # Автозапуск при включении TV
│       │   ├── RouterDiscovery.kt          # Автопоиск роутера в Wi-Fi сети
│       │   └── ui/
│       │       ├── screens/
│       │       │   └── SettingsScreen.kt   # Экран настроек (слайдеры, переключатели)
│       │       ├── components/
│       │       │   └── Components.kt       # TvSlider, TvSwitch, TvCard и др.
│       │       └── theme/
│       │           └── Theme.kt            # VpnColors, VpnTypography
│       └── res/
│           ├── drawable/                   # Визуальные ресурсы
│           ├── raw/alert_sound.wav         # Звук алерта
│           ├── values/                     # Строки, цвета, стили
│           └── xml/accessibility_watchdog.xml
├── DESIGN.md                               # Дизайн-система (Neo-Industrial)
├── README.md                               # Этот файл
├── build.gradle                            # Корневой Gradle
├── app/build.gradle                        # Зависимости приложения
└── gradle/wrapper/                         # Gradle wrapper
```

---

## Технологии

| Компонент | Технология |
|-----------|-----------|
| Язык | Kotlin 1.9.22 |
| UI | Jetpack Compose for TV (`tv-foundation`, `tv-material`) |
| Видео | ExoPlayer / Media3 1.3.1 |
| Сеть | OkHttp 4.12 + Gson 2.10 |
| БД (роутер) | SQLite через `database.py` |
| API (роутер) | Flask (Python 3) |
| Навигация | D-Pad (Compose TV) |
| Оверлей | `SYSTEM_ALERT_WINDOW` + `FOREGROUND_SERVICE` |
| Заставка | `DreamService` + `AccessibilityService` (IdleWatchdog) |

---

## Тroubleshooting

### APK не компилируется

Убедитесь что установлен **JDK 21**, не 25. JDK 25 несовместим с Kotlin 1.9.22:

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\jdk-21.0.12+8"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

### Sony BRAVIA: при нажатии OK вылезает меню

Это поведение лаунчера Sony, не баг приложения. Включите ADB вручную:
**Настройки → Система → О разработчике → ADB-отладка → Вкл**

### Заставка показывает "offline"

1. Проверьте доступность роутера: `http://192.168.2.1:17463/api/status`
2. Убедитесь что `vpn_monitor.py` запущен
3. Проверьте логи: `tail -20 /tmp/vpn_monitor.log`

### Веб-дашборд не открывается (404)

```bash
# Перезапустить Flask
kill $(pidof python3)
cd /etc/vpn-monitor
nohup python3 vpn_monitor.py > /tmp/vpn_monitor.log 2>&1 &
```

### Серверы не добавляются из подписки

- Проверьте URL подписки: `curl -s https://your-sub-url`
- Убедитесь что ответ содержит `vless://`, `vmess://` или `trojan://` строки
- Поддерживается base64-кодировка (автоматически декодируется)

---

## Лицензия

MIT
