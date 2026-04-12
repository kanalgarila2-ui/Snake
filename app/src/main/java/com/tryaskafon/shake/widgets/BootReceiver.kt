package com.tryaskafon.shake.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tryaskafon.shake.repository.ConfigRepository
import com.tryaskafon.shake.service.ShakeDetectorService

/**
 * WidgetToggleReceiver — обрабатывает нажатие кнопки виджета.
 * Запускает или останавливает ShakeDetectorService.
 */
class WidgetToggleReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (ShakeDetectorService.isRunning) {
            ShakeDetectorService.stop(ctx)
        } else {
            val repo = ConfigRepository(ctx)
            val path = repo.loadFilePath()
            if (path.isNotEmpty()) {
                ShakeDetectorService.start(ctx, path, repo.loadSensitivity(), repo.loadVibrateEnabled())
            }
        }
        // Обновляем виджет
        val manager = AppWidgetManager.getInstance(ctx)
        val ids = manager.getAppWidgetIds(ComponentName(ctx, ShakeWidgetProvider::class.java))
        ids.forEach { ShakeWidgetProvider.updateWidget(ctx, manager, it) }
    }
}

/**
 * BootReceiver — восстанавливает виджет после перезагрузки устройства.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Устройство перезагружено, обновляем виджет")
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, ShakeWidgetProvider::class.java))
            ids.forEach { ShakeWidgetProvider.updateWidget(ctx, manager, it) }
        }
    }
}
