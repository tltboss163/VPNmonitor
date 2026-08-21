# Установка на роутер

## Требования

- OpenWrt роутер с Python 3 и pip
- `plink.exe` / `pscp.exe` (PuTTY) на компьютере (для Windows)
- SSH-доступ к роутеру

## Автоматическая установка

### Через pscp + plink (Windows)

```powershell
$plink = "C:\Program Files\PuTTY\plink.exe"
$pscp  = "C:\Program Files\PuTTY\pscp.exe"
ROUTER="root@192.168.2.1"
PASS="password"

# Загрузить файлы
& $pscp -batch -pw $PASS api_server.py ${ROUTER}:/etc/vpn-monitor/
& $pscp -batch -pw $PASS vpn_monitor.py ${ROUTER}:/etc/vpn-monitor/
& $pscp -batch -pw $PASS checker.py ${ROUTER}:/etc/vpn-monitor/
& $pscp -batch -pw $PASS database.py ${ROUTER}:/etc/vpn-monitor/
& $pscp -batch -pw $PASS config.py ${ROUTER}:/etc/vpn-monitor/
& $pscp -batch -pw $PASS transcoder.py ${ROUTER}:/etc/vpn-monitor/
& $pscp -batch -pw $PASS media_sources.py ${ROUTER}:/etc/vpn-monitor/
& $pscp -batch -pw $PASS index.html ${ROUTER}:/etc/vpn-monitor/

# Запустить
& $plink -batch -pw $PASS $ROUTER "cd /etc/vpn-monitor && nohup python3 vpn_monitor.py > /tmp/vpn_monitor.log 2>&1 &"
```

### Через SCP (Linux/macOS)

```bash
ROUTER="root@192.168.2.1"

scp api_server.py vpn_monitor.py checker.py database.py \
    config.py transcoder.py media_sources.py index.html \
    ${ROUTER}:/etc/vpn-monitor/

ssh $ROUTER "cd /etc/vpn-monitor && nohup python3 vpn_monitor.py > /tmp/vpn_monitor.log 2>&1 &"
```

## Установка зависимостей

```bash
ssh root@192.168.2.1
pip3 install flask
```

## Проверка

```bash
# Проверить что Flask отвечает
curl -s -o /dev/null -w '%{http_code}' http://192.168.2.1:17463/
# Должно вернуть: 200

# Проверить API
curl -s http://192.168.2.1:17463/api/status | python3 -m json.tool

# Проверить логи
tail -20 /tmp/vpn_monitor.log
```

## Структура файлов

```
/etc/vpn-monitor/
├── vpn_monitor.py       # Главный процесс
├── api_server.py        # Flask API (577 строк)
├── checker.py           # Проверка серверов
├── database.py          # SQLite
├── config.py            # Конфигурация
├── transcoder.py        # Транскодинг видео
├── media_sources.py     # Парсинг медиа
├── index.html           # Веб-дашборд
└── vpn.db               # База данных (создаётся автоматически)
```

## Автозапуск

Для автозапуска при загрузке роутера добавьте в `/etc/rc.local`:

```bash
# VPN Monitor
cd /etc/vpn-monitor && nohup python3 vpn_monitor.py > /tmp/vpn_monitor.log 2>&1 &
```
