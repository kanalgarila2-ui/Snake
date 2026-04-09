# 📳 ТряскаФон (TryaskaFon)

[![Build APK](https://github.com/YOUR_USERNAME/TryaskaFon/actions/workflows/build.yml/badge.svg)](https://github.com/YOUR_USERNAME/TryaskaFon/actions/workflows/build.yml)

**ТряскаФон** — Android-приложение, которое слушает акселерометр и воспроизводит MP3 файл при обнаружении тряски телефона. Работает в фоне через Foreground Service — даже когда экран выключен.

---

## 📱 Что умеет

- Обнаруживает тряску через акселерометр (настраиваемый порог 5–30 м/с²)
- Воспроизводит выбранный MP3 файл при каждой тряске
- Работает **в фоне** — не останавливается при сворачивании приложения
- Вибрация при тряске (опционально)
- Счётчик трясок + время последней
- Сохраняет путь к файлу каждый раз при изменении (да, каждый символ — синхронно)
- Кнопка **Stop** прямо в уведомлении

---

## 📥 Установка

### Вариант 1: скачать готовый APK
1. Перейди в [Releases](https://github.com/YOUR_USERNAME/TryaskaFon/releases)
2. Скачай последний `TryaskaFon-vX.X.apk`
3. На телефоне: **Настройки → Безопасность → Установка из неизвестных источников** → включить
4. Открыть скачанный APK и установить

### Вариант 2: собрать самому
```bash
git clone https://github.com/YOUR_USERNAME/TryaskaFon.git
cd TryaskaFon
./gradlew assembleDebug
# APK будет в: app/build/outputs/apk/debug/app-debug.apk
```

---

## 🎵 Как получить MP3 файл

Тебе нужен файл формата `.mp3` в памяти телефона.

**Способы:**
1. **Скачать с телефона** — любой трек из браузера или мессенджера
2. **Онлайн-конвертер** — например [convertio.co](https://convertio.co) или [cloudconvert.com](https://cloudconvert.com)
3. **Скопировать с компьютера** через USB или Wi-Fi

**Примеры путей:**
```
/storage/emulated/0/Download/shake.mp3
/storage/emulated/0/Music/alarm.mp3
/storage/emulated/0/Ringtones/beep.mp3
/sdcard/Download/sound.mp3
```

---

## 🚀 Как использовать

1. Открыть приложение
2. Ввести путь к MP3 файлу **или** нажать **"Выбрать файл"**
3. Настроить чувствительность (SeekBar):
   - `5` = реагирует на любое движение
   - `15` = обычная тряска (по умолчанию)
   - `30` = только сильная тряска
4. При желании включить **"Вибрировать при тряске"**
5. Включить переключатель **"Активировать детектор тряски"**
6. Всё! Теперь тряхни телефон — услышишь звук

---

## 🛑 Как отключить

**Способ 1:** Выключить переключатель в приложении  
**Способ 2:** Нажать кнопку **"Stop"** в уведомлении в шторке

---

## 🏗️ Архитектура

```
MainActivity (MVVM)
    ↕ LiveData
ShakeViewModel
    ↕
ConfigRepository (SharedPreferences + файл)

ShakeDetectorService (ForegroundService)
    ├── SensorManager / Accelerometer
    ├── AudioPlayer (MediaPlayer)
    ├── Vibrator
    └── BroadcastReceiver → MainActivity
```

**Стек:**
- Kotlin 100%
- Jetpack ViewModel + LiveData
- ViewBinding
- MediaPlayer (AudioPlayer обёртка)
- ForegroundService + WakeLock
- SensorManager (TYPE_ACCELEROMETER)

---

## ⚠️ Важные предупреждения

### 🐢 Производительность
Приложение сохраняет путь к файлу **синхронно** при каждом изменении текста в поле ввода — это означает `SharedPreferences.commit()` + запись в файл на каждый введённый символ. Это намеренно (ТЗ). При быстром вводе текста телефон может **слегка** тормозить — это нормально.

Лог покажет:
```
V/ConfigRepository: SharedPreferences.commit() → true, path=/storage/...
V/ConfigRepository: Файл записан: /sdcard/TryaskaFon/last_path.txt
```

### 🔋 Батарея
Foreground Service с WakeLock держит CPU активным. При длительном использовании батарея расходуется быстрее обычного.

### 📂 Доступ к файлам
На Android 11+ (API 30+) прямой доступ к `/sdcard/` ограничен. Используй кнопку **"Выбрать файл"** для надёжного выбора через системный файловый менеджер.

---

## 🛠️ Требования

| Параметр | Значение |
|----------|----------|
| Минимальный Android | 7.0 (API 24) |
| Target Android | 14 (API 34) |
| Язык | Kotlin |
| Акселерометр | Обязателен |

---

## 📋 Разрешения

| Разрешение | Зачем |
|-----------|-------|
| `FOREGROUND_SERVICE` | Фоновый сервис |
| `WAKE_LOCK` | CPU не засыпает |
| `VIBRATE` | Вибрация при тряске |
| `READ_MEDIA_AUDIO` | Чтение MP3 (Android 13+) |
| `READ_EXTERNAL_STORAGE` | Чтение MP3 (Android < 13) |
| `POST_NOTIFICATIONS` | Уведомление сервиса (Android 13+) |

---

## 🤖 GitHub Actions

Каждый push в `main` автоматически собирает APK.  
При создании тега `v1.0`, `v1.1` и т.д. — создаётся GitHub Release с APK.

```bash
# Создать релиз:
git tag v1.0
git push origin v1.0
```

---

*Сделано с любовью к тряске* 📳
