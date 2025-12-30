package com.kask.cam;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.util.Log;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class CamService extends LifecycleService {

    private VideoCapture<Recorder> videoCapture;
    private Recording currentRecording;
    private FusedLocationProviderClient fusedLocationClient;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    
    // AYARLAR
    private static final long SEGMENT_DURATION_MS = 60000; // 1 Dakika
    private static final long STORAGE_QUOTA_BYTES = 20L * 1024 * 1024 * 1024; // 20 GB
    private int partCounter = 1;
    private String sessionID;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, createNotification());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        initializeCamera();
    }

    private Notification createNotification() {
        String channelId = "kask_cam_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Kask Kayıt Servisi", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Kask Kamerası Aktif")
                .setContentText("Kayıt devam ediyor...")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();
    }

    private void initializeCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                
                // Geniş açı önceliği için kamera seçimi
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, videoCapture);

                startNewRecordingSegment();

            } catch (ExecutionException | InterruptedException e) {
                Log.e("KaskCam", "Kamera Hatası: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("MissingPermission")
    private void startNewRecordingSegment() {
        if (videoCapture == null) return;

        if (currentRecording != null) {
            currentRecording.stop();
            currentRecording = null;
        }

        checkAndCleanQuota(); // Kayda başlamadan önce kotayı temizle

        if (sessionID == null) sessionID = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis());
        String fileName = "KASK_" + sessionID + "_P" + partCounter;

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/KaskCam");

        MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(
                getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build();

        // ÇÖKMEYİ ENGELLEYEN SES KONTROLÜ
        var recordingPrepare = videoCapture.getOutput().prepareRecording(this, options);
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                recordingPrepare = recordingPrepare.withAudioEnabled();
            } catch (Exception e) {
                Log.e("KaskCam", "Ses kaynağı hatası, sessiz devam ediliyor: " + e.getMessage());
            }
        }

        currentRecording = recordingPrepare.start(ContextCompat.getMainExecutor(this), event -> {
            if (event instanceof VideoRecordEvent.Start) {
                sendBroadcast(new Intent("UPDATE_UI").putExtra("status", "KAYITTA (P" + partCounter + ")"));
                startTimerForNextSegment();
            }
        });
    }

    private void startTimerForNextSegment() {
        new CountDownTimer(SEGMENT_DURATION_MS, 1000) {
            public void onTick(long millisUntilFinished) {}
            public void onFinish() {
                partCounter++;
                startNewRecordingSegment();
            }
        }.start();
    }

    // 20GB KOTA YÖNETİMİ (MediaStore Üzerinden Eski Dosyaları Silme)
    private void checkAndCleanQuota() {
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Video.Media._ID, MediaStore.Video.Media.SIZE};
        String selection = MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?";
        String[] selectionArgs = new String[]{"%Movies/KaskCam%"};
        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " ASC";

        try (Cursor cursor = getContentResolver().query(collection, projection, selection, selectionArgs, sortOrder)) {
            if (cursor == null) return;

            long totalSize = 0;
            while (cursor.moveToNext()) {
                totalSize += cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE));
            }

            // Eğer kota aşılmışsa en eski dosyadan silmeye başla
            cursor.moveToFirst();
            while (totalSize > STORAGE_QUOTA_BYTES && !cursor.isAfterLast()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID));
                long fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE));
                Uri deleteUri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                
                getContentResolver().delete(deleteUri, null, null);
                totalSize -= fileSize;
                cursor.moveToNext();
                Log.d("KaskCam", "Kota doldu, eski video silindi.");
            }
        } catch (Exception e) {
            Log.e("KaskCam", "Kota temizleme hatası: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        if (currentRecording != null) {
            currentRecording.stop();
            currentRecording = null;
        }
        super.onDestroy();
    }
}
