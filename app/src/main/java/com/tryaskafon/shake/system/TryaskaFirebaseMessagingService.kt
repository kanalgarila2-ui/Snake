package com.tryaskafon.shake.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tryaskafon.shake.MainActivity
import com.tryaskafon.shake.R

/**
 * TryaskaFirebaseMessagingService — обработка FCM push-уведомлений.
 * Позволяет отправлять уведомления о новых функциях через Firebase Console.
 */
class TryaskaFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "TryaskaFCM"
    private val CHANNEL_ID = "tryaskafon_push"
    private val NOTIF_ID   = 2000

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Новый FCM токен: $token")
        // При необходимости: отправить токен на свой сервер
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM сообщение от: ${message.from}")

        val title = message.notification?.title ?: message.data["title"] ?: "ТряскаФон"
        val body  = message.notification?.body  ?: message.data["body"]  ?: "Новое сообщение"

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Создаём канал (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Уведомления ТряскаФон", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shake)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(NOTIF_ID, notification)
    }
}
