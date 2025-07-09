package com.example.batteryalert;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;

public class TTSSettingsActivity extends Activity {
    private SeekBar seekVolume, seekSpeed, seekPitch;
    private TextView txtVolume, txtSpeed, txtPitch;
    private SharedPreferences prefs;

    private static final float MIN_SPEED = 0.3f;
    private static final float MAX_SPEED = 2.0f;
    private static final float MIN_PITCH = 0.5f;
    private static final float MAX_PITCH = 2.0f;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tts_settings);

        prefs = getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE);

        seekVolume = findViewById(R.id.seek_volume);
        seekSpeed = findViewById(R.id.seek_speed);
        seekPitch = findViewById(R.id.seek_pitch);
        txtVolume = findViewById(R.id.txt_volume);
        txtSpeed = findViewById(R.id.txt_speed);
        txtPitch = findViewById(R.id.txt_pitch);

        int vol = prefs.getInt("volume", 100);
        float spd = prefs.getFloat("speed", 1.0f);
        float pit = prefs.getFloat("pitch", 1.0f);

        seekVolume.setProgress(vol);
        seekSpeed.setMax(100);
        seekPitch.setMax(100);

        int speedProgress = (int)((spd - MIN_SPEED) / (MAX_SPEED - MIN_SPEED) * 100);
        int pitchProgress = (int)((pit - MIN_PITCH) / (MAX_PITCH - MIN_PITCH) * 100);

        seekSpeed.setProgress(speedProgress);
        seekPitch.setProgress(pitchProgress);

        txtVolume.setText("Volume: " + vol + "%");
        txtSpeed.setText("Speed: " + spd);
        txtPitch.setText("Pitch: " + pit);

        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
					txtVolume.setText("Volume: " + progress + "%");
					prefs.edit().putInt("volume", progress).apply();
				}
				public void onStartTrackingTouch(SeekBar s) {}
				public void onStopTrackingTouch(SeekBar s) {}
			});

        seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
					float val = MIN_SPEED + progress * (MAX_SPEED - MIN_SPEED) / 100f;
					txtSpeed.setText("Speed: " + String.format("%.2f", val));
					prefs.edit().putFloat("speed", val).apply();
				}
				public void onStartTrackingTouch(SeekBar s) {}
				public void onStopTrackingTouch(SeekBar s) {}
			});

        seekPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
					float val = MIN_PITCH + progress * (MAX_PITCH - MIN_PITCH) / 100f;
					txtPitch.setText("Pitch: " + String.format("%.2f", val));
					prefs.edit().putFloat("pitch", val).apply();
				}
				public void onStartTrackingTouch(SeekBar s) {}
				public void onStopTrackingTouch(SeekBar s) {}
			});
    }
}

