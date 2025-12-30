package com.kask.cam;

import android.app.*;
import android.content.Intent;
import android.os.*;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

public class CamService extends Service {
    private Timer segmentTimer;
    private long STORAGE_LIMIT = 20L * 1024 * 1024 * 1024; // 20GB

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, "kask_cam")
                .setContentTitle("Kask Kamerası Kayıtta")
                .setContentText("Ekran kapalıyken bile verileriniz güvende.")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();

        startForeground(1, notification);
        startSegmentLoop();
        
        return START_STICKY;
    }

    private void startSegmentLoop() {
        segmentTimer = new Timer();
        segmentTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // Burada her 1 dakikada bir VideoCapture.stop() ve start() tetiklenir
                // Ayrıca checkStorageQuota() fonksiyonu ile eski dosyalar temizlenir
            }
        }, 60000, 60000); 
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel("kask_cam", "Kask Servisi", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (segmentTimer != null) segmentTimer.cancel();
        super.onDestroy();
    }
}
