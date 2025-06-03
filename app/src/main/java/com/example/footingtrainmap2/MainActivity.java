package com.example.footingtrainmap2;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (isServiceRunning(LocationService.class)) {
            Log.d("MainActivity", "Service running, redirecting to RunActivity");
            Intent intent = new Intent(this, RunActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        Button startRunButton = findViewById(R.id.start_run_button);
        Button historyButton = findViewById(R.id.history_button);
        Log.d("MainActivity", "Buttons initialized: startRunButton=" + startRunButton + ", historyButton=" + historyButton);

        startRunButton.setOnClickListener(v -> {
            Log.d("MainActivity", "Start Run button clicked");
            Intent intent = new Intent(MainActivity.this, RunActivity.class);
            startActivity(intent);
        });

        historyButton.setOnClickListener(v -> {
            Log.d("MainActivity", "History button clicked");
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}