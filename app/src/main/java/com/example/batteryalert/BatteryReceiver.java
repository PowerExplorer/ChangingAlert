package com.example.batteryalert;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.BatteryManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Locale;
import android.os.Environment;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.UtteranceProgressListener;
import java.util.HashMap;
import android.content.IntentFilter;
import android.os.*;
import android.content.*;
import android.app.job.*;

public class BatteryReceiver extends BroadcastReceiver {

    private static final String TAG = "BatteryReceiver";
    //private Context appContext;
    
	private LoudTTSQueue tts;
	private long lastUsed = 0;
	private long lastPercent = 0;
	boolean used1Time = false;
	long start = 0;

    public BatteryReceiver(Context context) {
        context = context.getApplicationContext();
		tts = new LoudTTSQueue(context);
	}

    public void onReceive(Context context, Intent intent) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
            String action = intent.getAction();
            
            boolean onStartJob  = prefs.getBoolean("ChargingJobService.onStartJob", false);
			//Logger.log(TAG, "onReceive Action: " + action.substring("android.intent.action.".length()) + ", onStartJob " + onStartJob);
			int level = -1;
			int scale = 100;
			
            if (Intent.ACTION_POWER_CONNECTED.equals(action)
				|| onStartJob) {
				prefs.edit().remove("ChargingJobService.onStartJob").apply();
                // Lấy mức pin thật từ BATTERY_CHANGED
				IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
				Intent batteryStatusIntent = context.registerReceiver(null, filter);

				if (batteryStatusIntent != null) {
					level = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
					scale = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
				}
				start = System.currentTimeMillis();
				int percent = level * 100 / scale;
				doAlert(context, "Bắt đầu sạc. Mức pin hiện tại: " + percent + "%", prefs, "alert_1");
				lastPercent = percent;
				lastUsed = SystemClock.elapsedRealtime();
				prefs.edit()
					.putInt("initial_level", percent)
					.putBoolean("alert_13", false)
					.putBoolean("alert_65", false)
					.putBoolean("used1Time", true)
					.apply();
                Logger.log(TAG, "Đã cắm sạc, ghi nhận pin ban đầu: " + percent + "%");
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                Intent intentService = new Intent(context, BatteryService.class);
				context.stopService(intentService);
				lastUsed = 0;
				lastPercent = 0;
				used1Time = false;
				prefs.edit().remove("initial_level")
					.remove("alert_13")
					.remove("alert_65")
					.remove("used1Time").apply();
                Logger.log(TAG, "Đã rút sạc");
				
				JobHelper.ensureJobScheduled(context.getApplicationContext());
				
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
				level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
				scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
				int percent = level * 100 / scale;

				final int initial = prefs.getInt("initial_level", -1);
				final boolean alerted13 = prefs.getBoolean("alert_13", false);
				final boolean alerted65 = prefs.getBoolean("alert_65", false);
				final boolean percentAlarm = prefs.getBoolean(MainActivity.KEY_USE_PERCENT, false);
				//Logger.log(TAG, "Mức pin hiện tại: " + percent + "%, ban đầu: " + initial + "%, alert_13=" + alerted13 + ", alert_65=" + alerted65);
				if (!alerted65 && percent >= 65) {
					doAlert(context, "Pin đã đạt " + percent + " phần trăm", prefs, "alert_65");
					lastPercent = percent;
				} else 
				if (!alerted13 && initial != -1 && percent >= initial + 13) {
					doAlert(context, "Pin đã tăng hơn 13%: hiện tại " + percent + "%, mất " + formatDuration(System.currentTimeMillis() - start), prefs, "alert_13");
					lastPercent = percent;
				} else if (percentAlarm) {
					used1Time = prefs.getBoolean("used1Time", false);
					//prefs.edit().putBoolean("used1Time", true).apply();
					long now = SystemClock.elapsedRealtime();
					//Logger.log(TAG, "percent=" + percent + ", used1Time=" + used1Time + ", lastPercent=" + lastPercent);
					if (!used1Time) {
						doAlert(context, "Mức pin hiện tại: " + percent + "%", prefs, "used1Time");
						lastPercent = percent;
						lastUsed = now;
					} else if (percent != lastPercent) {
						doAlert(context, "Mức pin hiện tại: " + percent + "%, được " + formatDuration(now-lastUsed), prefs, "used1Time");
						lastPercent = percent;
						lastUsed = now;
					}
				}
			}
        } catch (Exception e) {
            Logger.log(TAG, "Lỗi: " + e.getMessage());
        }
    }

    String formatDuration(long ms) {                 // hh:mm:ss
		long sec = ms / 1000;
		long min = sec / 60;
		//long hr  = min / 60;
		sec %= 60;
		return String.format("%01d phút:%01d giây", min, sec);
	}

    private void doAlert(Context context, final String msg, SharedPreferences prefs, String key) {
        try {
            Logger.log(TAG, "doAlert: " + msg);
            prefs.edit().putBoolean(key, true).apply();

            boolean useSound = prefs.getBoolean("use_sound", false);
            boolean useTts = prefs.getBoolean("use_tts", true);
            final int volume = prefs.getInt("volume", 80);
            String soundUri = prefs.getString("sound_uri", null);

            if (useSound && soundUri != null) {
                MediaPlayer player = MediaPlayer.create(context, Uri.parse(soundUri));
                if (player != null) {
                    player.setVolume(volume / 100f, volume / 100f);
                    player.start();
                } else {
                    Logger.log(TAG, "Không thể phát file âm báo");
                }
            }

            if (useTts) {
                tts.speakLoud(msg, volume, null,  null);
            }

        } catch (Exception e) {
            Logger.log(TAG, "Lỗi khi cảnh báo: " + e.getMessage());
        }
    }
}

