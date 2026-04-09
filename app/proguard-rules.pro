# ProGuard правила для ТряскаФон
# Сохраняем все классы приложения
-keep class com.tryaskafon.shake.** { *; }

# MediaPlayer не трогаем
-keep class android.media.** { *; }

# Sensor не трогаем
-keep class android.hardware.** { *; }
