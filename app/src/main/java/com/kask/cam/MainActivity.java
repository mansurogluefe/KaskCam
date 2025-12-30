package com.kask.cam;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private boolean isRecording = false;
    private View blackOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Artık XML kullanıyoruz

        Button recordBtn = findViewById(R.id.recordButton);
        Button blackBtn = findViewById(R.id.btnBlackScreen);
        ImageButton settingsBtn = findViewById(R.id.settingsButton);
        blackOverlay = findViewById(R.id.blackOverlay);

        recordBtn.setOnClickListener(v -> {
            if (!isRecording) {
                startCamService();
                recordBtn.setText("DURDUR");
                isRecording = true;
            } else {
                stopService(new Intent(this, CamService.class));
                recordBtn.setText("KAYDI BAŞLAT");
                isRecording = false;
            }
        });

        blackBtn.setOnClickListener(v -> {
            blackOverlay.setVisibility(blackOverlay.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
        });

        settingsBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Ayar: 20GB Kota / Ultra Geniş Açı Aktif", Toast.LENGTH_LONG).show();
        });

        checkPermissions();
    }

    private void startCamService() {
        Intent intent = new Intent(this, CamService.class);
        ContextCompat.startForegroundService(this, intent);
    }

    private void checkPermissions() {
        String[] permissions = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION};
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, 101);
        }
    }
}
