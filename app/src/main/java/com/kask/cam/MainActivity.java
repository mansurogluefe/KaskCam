package com.kask.cam;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

public class MainActivity extends AppCompatActivity {

    private boolean isRecording = false;
    private View blackOverlay;
    private TextView statusText, speedText;
    private Button recordButton;

    // Gerekli İzinler Listesi
    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // Ekran açık kalsın

        // UI Tanımları
        PreviewView viewFinder = findViewById(R.id.viewFinder);
        blackOverlay = findViewById(R.id.blackOverlay);
        statusText = findViewById(R.id.statusText);
        speedText = findViewById(R.id.speedText);
        recordButton = findViewById(R.id.recordButton);
        Button btnBlackScreen = findViewById(R.id.btnBlackScreen);
        ImageButton btnSettings = findViewById(R.id.btnSettings);

        // İzin Kontrolü
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 101);
        } else {
            startCameraPreview(viewFinder); // Sadece önizleme, kayıt değil
        }

        // Kayıt Butonu
        recordButton.setOnClickListener(v -> {
            if (!isRecording) {
                Intent intent = new Intent(this, CamService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
                recordButton.setText("DURDURULUYOR...");
                recordButton.setBackgroundColor(0xFF555555);
                isRecording = true;
            } else {
                stopService(new Intent(this, CamService.class));
                recordButton.setText("KAYIT BAŞLAT");
                recordButton.setBackgroundColor(0xFFCC0000);
                statusText.setText("Durduruldu");
                isRecording = false;
            }
        });

        // Siyah Ekran Butonu
        btnBlackScreen.setOnClickListener(v -> {
            if (blackOverlay.getVisibility() == View.GONE) {
                blackOverlay.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Ekrana dokunarak açabilirsiniz", Toast.LENGTH_SHORT).show();
            }
        });

        // Siyah Ekrana Dokununca Aç
        blackOverlay.setOnClickListener(v -> blackOverlay.setVisibility(View.GONE));

        // Ayarlar Butonu
        btnSettings.setOnClickListener(v -> Toast.makeText(this, "Kota: 20GB | Kayıt: 1dk Parçalı", Toast.LENGTH_LONG).show());

        // Servisten gelen durum bilgisini dinle
        registerReceiver(uiReceiver, new IntentFilter("UPDATE_UI"), Context.RECEIVER_NOT_EXPORTED);
    }

    private void startCameraPreview(PreviewView viewFinder) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview);
            } catch (Exception e) {
                statusText.setText("Kamera Hatası");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private final BroadcastReceiver uiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra("status");
            if (status != null) statusText.setText(status);
        }
    };

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
