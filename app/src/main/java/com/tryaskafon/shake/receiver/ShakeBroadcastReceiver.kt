package com.tryaskafon.shake.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ShakeBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("ShakeBroadcastReceiver", "action=${intent?.action}")
        // Основная логика обработки — в фрагментах через динамические receivers
    }
}
