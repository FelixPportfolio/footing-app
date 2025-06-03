package com.example.footingtrainmap2;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private RunDatabase db;
    private ListView runList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("HistoryActivity", "onCreate called");
        setContentView(R.layout.activity_history);

        db = Room.databaseBuilder(getApplicationContext(), RunDatabase.class, "run-database")
                .allowMainThreadQueries()
                .build();
        Log.d("HistoryActivity", "Database initialized");

        runList = findViewById(R.id.run_list);
        Log.d("HistoryActivity", "runList initialized: " + runList);

        List<Run> runs = db.runDao().getAllRuns();
        Log.d("HistoryActivity", "Runs retrieved: " + runs.size());

        String[] runItems = new String[runs.size()];
        for (int i = 0; i < runs.size(); i++) {
            Run run = runs.get(i);
            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(run.timestamp));
            runItems[i] = String.format("%s - %.2f km - %02d:%02d",
                    date, run.distance / 1000, run.duration / 1000 / 60, (run.duration / 1000) % 60);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, runItems);
        runList.setAdapter(adapter);
        Log.d("HistoryActivity", "Adapter set with " + runItems.length + " items");

        runList.setOnItemClickListener((parent, view, position, id) -> {
            Run selectedRun = runs.get(position);
            Log.d("HistoryActivity", "Run clicked: ID=" + selectedRun.id);
            Intent intent = new Intent(HistoryActivity.this, StatsActivity.class);
            intent.putExtra("runId", selectedRun.id);
            startActivity(intent);
        });
    }
}