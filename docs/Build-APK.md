# Сборка APK

## Требования

| Компонент | Версия | Ссылка |
|-----------|--------|--------|
| JDK | **21** | [Adoptium Temurin 21](https://adoptium.net/temurin/releases/?version=21) |
| Android SDK | 34 | через Android Studio или cmdline-tools |
| Gradle | 8.14.5 | встроенный wrapper (автозагрузка) |

> ⚠️ **JDK 25 несовместим** с Kotlin 1.9.22. Обязательно используйте JDK 21.

## Сборка из командной строки

### Windows (PowerShell)

```powershell
# 1. Клонировать
git clone https://github.com/tltboss163/VPNmonitor.git
cd VPNmonitor

# 2. Указать JDK 21
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\jdk-21.0.12+8"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 3. Собрать
.\gradlew.bat clean assembleDebug

# Результат: app\build\outputs\apk\debug\app-debug.apk
```

### Linux / macOS

```bash
git clone https://github.com/tltboss163/VPNmonitor.git
cd VPNmonitor

export JAVA_HOME=/path/to/jdk-21
export PATH=$JAVA_HOME/bin:$PATH

./gradlew clean assembleDebug
# Результат: app/build/outputs/apk/debug/app-debug.apk
```

## Сборка через Android Studio

1. **File → Open** → выбрать папку `VPNmonitor`
2. **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**
   - **Gradle JDK** → выбрать JDK 21
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
4. APK появится в `app/build/outputs/apk/debug/`

## Ошибки сборки

### "Unsupported class file major version"
→ Установлен JDK 25+. Переключитесь на JDK 21.

### "Could not resolve kotlin-stdlib"
→ Проверьте интернет-соединение. Gradle скачивает зависимости при первой сборке.

### "Compose compiler requires Kotlin X.Y"
→ Версия Kotlin фиксирована в `build.gradle` (1.9.22). Не обновляйте.

## Подписка APK (для release)

Для установки без отладки:

```bash
.\gradlew.bat assembleRelease
# APK: app/build/outputs/apk/release/app-release-unsigned.apk
```

Для подписи создайте keystore:

```bash
keytool -genkey -v -keystore vpn-monitor.jks -alias vpn-monitor \
  -keyalg RSA -keysize 2048 -validity 10000

# Затем добавьте в app/build.gradle:
# signingConfigs {
#     release {
#         storeFile file('vpn-monitor.jks')
#         storePassword 'your-password'
#         keyAlias 'vpn-monitor'
#         keyPassword 'your-password'
#     }
# }
# buildTypes { release { signingConfig signingConfigs.release } }
```
