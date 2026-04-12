package com.tryaskafon.shake.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tryaskafon.shake.MainActivity
import com.tryaskafon.shake.R
import com.tryaskafon.shake.repository.ConfigRepository
import com.tryaskafon.shake.service.ShakeDetectorService

/**
 * ShakeWidgetProvider — виджет 2x1 на рабочий стол.
 * Показывает статус сервиса и кнопку быстрого включения/выключения.
 */
class ShakeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(ctx, manager, it) }
    }

    companion object {
        fun updateWidget(ctx: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_shake)

            // Кнопка открытия приложения
            val openIntent = Intent(ctx, MainActivity::class.java)
            val openPi = android.app.PendingIntent.getActivity(
                ctx, 0, openIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetBtnOpen, openPi)

            // Кнопка старт/стоп сервиса (через broadcast)
            val toggleIntent = Intent(ctx, WidgetToggleReceiver::class.java)
            val togglePi = android.app.PendingIntent.getBroadcast(
                ctx, 1, toggleIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetBtnToggle, togglePi)

            // Проверяем: сервис запущен?
            val running = ShakeDetectorService.isRunning
            views.setTextViewText(
                R.id.widgetTvStatus,
                if (running) "🔥 Активен" else "⚫ Выключен"
            )
            views.setTextViewText(
                R.id.widgetBtnToggle,
                if (running) "Стоп" else "Старт"
            )

            manager.updateAppWidget(widgetId, views)
        }
    }
}
