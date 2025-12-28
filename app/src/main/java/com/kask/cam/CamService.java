package com.kask.cam;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

public class CamService extends Service {
    private MediaRecorder recorder;
    private Handler handler = new Handler();
    private boolean isRecording = false;
    private static final int MAX_STORAGE_FILES = 20; // Kaç video saklansın? (Örn: 20 tane 1dk'lık video)

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("KASK_CHANNEL", "Kask Cam", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new Notification.Builder(this, "KASK_CHANNEL")
            .setContentTitle("Kask Kamerası Kayıtta")
            .setContentText("Video ve Konum kaydediliyor...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build();
        
        startForeground(1, notification);
        
        if (!isRecording) {
            startRecordingLoop();
        }
        
        return START_STICKY;
    }

    private void startRecordingLoop() {
        isRecording = true;
        recordNextChunk();
    }

    private void recordNextChunk() {
        if (!isRecording) return;

        checkAndCleanStorage(); // Eski videoları sil
        setupRecorder();

        try {
            recorder.prepare();
            recorder.start();
            // 60 saniye (60000 ms) sonra durdur ve yenisini başlat
            handler.postDelayed(this::stopAndRestart, 60000);
        } catch (IOException e) {
            Log.e("KaskCam", "Kayıt başlatılamadı", e);
        }
    }

    private void stopAndRestart() {
        if (recorder != null) {
            recorder.stop();
            recorder.reset();
            recorder.release();
            recorder = null;
        }
        recordNextChunk();
    }

    private void setupRecorder() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        
        String fileName = getExternalFilesDir(null) + "/vid_" + System.currentTimeMillis() + ".mp4";
        recorder.setOutputFile(fileName);
        
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setVideoSize(1920, 1080); // Full HD
        recorder.setVideoFrameRate(30);
    }

    private void checkAndCleanStorage() {
        File dir = getExternalFilesDir(null);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".mp4"));
        if (files != null && files.length >= MAX_STORAGE_FILES) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            files[0].delete(); // En eski dosyayı sil
        }
    }

    @Override
    public void onDestroy() {
        isRecording = false;
        handler.removeCallbacksAndMessages(null);
        if (recorder != null) {
            recorder.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
