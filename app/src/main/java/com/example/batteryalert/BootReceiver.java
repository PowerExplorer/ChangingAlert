package com.example.batteryalert;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.content.*;
import android.app.job.*;

public class BootReceiver extends BroadcastReceiver {

	private static final String TAG = "BootReceiver";
	
    @Override
    public void onReceive(Context context, Intent intent) {
        int pid = android.os.Process.myPid();
        String action = (intent != null) ? intent.getAction() : "null";
		Logger.log(TAG, action + " received - PID: " + pid);
        Logger.log(TAG, "BatteryService.isRunning = " + BatteryService.isRunning);
		if (intent == null) {// || BatteryService.isRunning
            Logger.log(TAG, "Intent " +  intent);// + " hoặc BatteryService đã chạy rồi → không khởi động lại");
            return;
        }
		if (Intent.ACTION_BOOT_COMPLETED.equals(action)
            || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {

            JobHelper.ensureJobScheduled(context.getApplicationContext());
			
            Logger.log(TAG, "JobScheduler registered for charging event");
//        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
//			|| Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())) {
//            Intent serviceIntent = new Intent(context, BatteryService.class);
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                context.startForegroundService(serviceIntent);
//            } else {
//                context.startService(serviceIntent);
//            }
//			Logger.log(TAG, "BatteryService has just started");
		} else {
			Logger.log(TAG, "Không khớp action cần xử lý");
		}
    }
}
