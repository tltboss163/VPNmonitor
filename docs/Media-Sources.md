# Медиа-источники

Медиа-источники позволяют добавлять контент (видео, картинки) для заставки и плейлистов.

## Встроенные источники

### Hentaicloud

Парсит популярные видео с hentaicloud.com:
- Каталог: Popular, Latest, Random
- Формат: HD MP4
- Автоматическое обновление

### Archive.org

Парсит видео с archive.org:
- Каталог: Popular, Community
- Формат: MP4, AVI (транскодируется при необходимости)
- Поддержка длинных серий

## Добавление источника

### Через веб-дашборд

1. Откройте `http://192.168.2.1:17463`
2. **Медиа → Источники → Добавить**
3. Выберите тип: hentaicloud / archiveorg
4. Нажмите **"Сохранить"**

### Через API

```bash
curl -X POST http://192.168.2.1:17463/api/media-sources \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-source",
    "site": "hentaicloud",
    "section": "popular",
    "category": "normal",
    "enabled": 1
  }'
```

## Синхронизация

### Ручная синхронизация

```bash
# Один источник
curl -X POST http://192.168.2.1:17463/api/media-sources/11/sync

# Все источники
curl -X POST http://192.168.2.1:17463/api/media-sources/sync-all
```

### Автоматическая синхронизация

Источники синхронизируются:
- При старте vpn_monitor.py
- При каждой проверке серверов (каждые 300 секунд)
- При ручном запросе через API

## Плейлисты

### Структура

```json
{
  "id": 5,
  "name": "hentaicloud-18+",
  "category": "adult",
  "kind": "manual",
  "enabled": 1,
  "items": [
    "https://hentaicloud.com/media/videos/hd/3910.mp4",
    "https://hentaicloud.com/media/videos/hd/3909.mp4"
  ],
  "image_urls": []
}
```

### Управление

```bash
# Список плейлистов
curl -s http://192.168.2.1:17463/api/playlists

# Добавить плейлист
curl -X POST http://192.168.2.1:17463/api/playlists \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Мой плейлист",
    "category": "normal",
    "items": ["https://example.com/video1.mp4"]
  }'

# Удалить плейлист
curl -X DELETE http://192.168.2.1:17463/api/playlists/5
```

## Сохранённый контент

Сохранённый контент используется для заставки:

```bash
# Получить
curl -s http://192.168.2.1:17463/api/saved-content

# Сохранить
curl -X POST http://192.168.2.1:17463/api/saved-content \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": true,
    "image_urls": ["https://example.com/bg.jpg"],
    "video_urls": ["https://example.com/video.mp4"]
  }'
```
