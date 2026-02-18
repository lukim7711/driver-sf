package com.driversfpoc.screenreader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class ScreenReaderApp : Application() {

    companion object {
        const val CHANNEL_ID = "screen_reader_channel"
        const val CHANNEL_NAME = "Screen Reader Service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi saat Screen Reader aktif membaca layar"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
