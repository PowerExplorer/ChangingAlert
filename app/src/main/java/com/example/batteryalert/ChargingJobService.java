package com.example.batteryalert;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Build;
import android.content.*;
import android.app.job.*;

public class ChargingJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        Logger.log("ChargingJobService", "onStartJob: Device is charging");
		if (!BatteryService.isRunning) {
			SharedPreferences prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
			prefs.edit().putBoolean("ChargingJobService.onStartJob", true).apply();

			Intent intent = new Intent(this, BatteryService.class);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				startForegroundService(intent);
			} else {
				startService(intent);
			}
		}
		
		return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Logger.log("ChargingJobService", "onStopJob: Job stopped");
        return false;
    }
}

