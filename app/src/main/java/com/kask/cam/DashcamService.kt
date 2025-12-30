package com.kask.cam

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat

class DashcamService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "dash_ch"
        val channel = NotificationChannel(channelId, "Kask Kaydı", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Kask Kamerası Aktif")
            .setContentText("Ekran kapalıyken kayıt devam ediyor...")
            .setSmallIcon(android.R.drawable.ic_menu_camera).build()
        
        startForeground(101, notification)
        startSegmentTimer()
        return START_STICKY
    }

    private fun startSegmentTimer() {
        handler.postDelayed({
            sendBroadcast(Intent("ACTION_NEXT_SEGMENT"))
            startSegmentTimer()
        }, 60000) // Her 1 dakikada bir tetikler
    }

    override fun onBind(intent: Intent?) = null
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
