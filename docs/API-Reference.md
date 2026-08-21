# API Reference

Полный справочник REST API VPN Monitor.

**Base URL:** `http://<router-ip>:17463`

---

## Серверы

### GET /api/status
Статус всех серверов + настройки.

**Ответ:**
```json
{
  "servers": [
    {
      "id": 1,
      "name": "Netherlands",
      "host": "node-nl.example.com",
      "port": 443,
      "status": "up",
      "vless_ok": 1,
      "ping_ms": 45.2,
      "location": "Нидерланды",
      "checked_at": "2026-08-21T10:00:00",
      "down_count": 0
    }
  ],
  "settings": { ... },
  "checked_at": "2026-08-21T10:00:00"
}
```

### GET /api/servers
Список всех серверов (включая отключённые).

### POST /api/servers
Добавить сервер.

**Тело:**
```json
{
  "name": "Netherlands",
  "host": "node-nl.example.com",
  "port": 443,
  "protocol": "vless",
  "location": "Нидерланды",
  "vless_uuid": "uuid-here",
  "network": "tcp",
  "security": "reality",
  "sni": "example.com",
  "fingerprint": "chrome",
  "subscription_url": "https://..."
}
```

### PUT /api/servers/{id}
Обновить сервер.

### DELETE /api/servers/{id}
Удалить сервер.

---

## Парсинг URI

### POST /api/parse-uri
Парсинг URI (vless/vmess/trojan/https).

**Тело:**
```json
{ "uri": "vless://uuid@host:443#name?type=tcp&..." }
```

**Ответ (один сервер):**
```json
{
  "protocol": "vless",
  "vless_uuid": "...",
  "host": "...",
  "port": 443,
  "name": "...",
  ...
}
```

**Ответ (подписка):**
```json
{
  "servers": [ { ... }, { ... } ],
  "count": 15,
  "subscription_url": "https://..."
}
```

---

## Подписки

### GET /api/subscriptions
Список подписок.

**Ответ:**
```json
[
  {
    "id": 1,
    "url": "https://example.com/sub/vless",
    "last_sync": "2026-08-21T10:00:00",
    "last_count": 15,
    "enabled": 1
  }
]
```

### POST /api/subscriptions
Добавить подписку.

**Тело:** `{ "url": "https://example.com/sub/vless" }`

### DELETE /api/subscriptions/{id}
Удалить подписку.

---

## Устройства

### GET /api/devices
Список ТВ-устройств.

### GET /api/device/config?device_id=X&name=Y
Получить конфиг устройства (auto-register).

### POST /api/device/config
Сохранить конфиг устройства.

**Тело:**
```json
{
  "device_id": "uuid",
  "screensaver": {
    "mode": "status",
    "playlist": [],
    "image_urls": []
  },
  "video_sound": false,
  "overlay": {
    "enabled": true,
    "position": "bottom-right",
    "size": "small",
    "opacity": 0.2
  }
}
```

### DELETE /api/devices/{id}
Удалить устройство.

---

## Настройки

### GET /api/settings
Глобальные настройки оверлея.

**Ответ:**
```json
{
  "position": "bottom-right",
  "size": "small",
  "opacity": 0.2,
  "show_location": 1,
  "show_ping": 1,
  "alert_sound": 1,
  "alert_threshold": 4
}
```

### POST /api/settings
Сохранить настройки.

---

## Медиа

### Плейлисты

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/playlists` | Список плейлистов |
| POST | `/api/playlists` | Создать |
| PUT | `/api/playlists/{id}` | Обновить |
| DELETE | `/api/playlists/{id}` | Удалить |

### Медиа-источники

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/media-sources` | Список источников |
| POST | `/api/media-sources` | Добавить |
| POST | `/api/media-sources/{id}/sync` | Синхронизировать |
| POST | `/api/media-sources/sync-all` | Синхронизировать все |
| DELETE | `/api/media-sources/{id}` | Удалить |

### Транскодинг

| Метод | Путь | Описание |
|-------|------|----------|
| POST | `/api/media/prepare` | Подготовить видео |
| GET | `/api/media/status?url=X` | Статус транскодинга |

---

## Проверки

| Метод | Путь | Описание |
|-------|------|----------|
| POST | `/api/check` | Ручная проверка |
| POST | `/api/force-check` | Принудительная проверка |
| GET | `/api/history/{id}` | История проверок сервера |
| GET | `/api/logs` | Системные логи |

---

## Сохранённый контент

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/saved-content` | Получить |
| POST | `/api/saved-content` | Сохранить |

---

## Веб-дашборд

### GET /
Веб-интерфейс (HTML). Возвращает полный HTML-документ с JavaScript.

Функции:
- Карточки серверов в реальном времени
- Управление серверами (добавление/удаление)
- Подписки (парсинг URL)
- Настройки оверлея и заставки
- Медиа-плейлисты и источники
- Устройства и история
