package com.example.batteryalert;

import android.app.Activity;
import android.os.*;
import android.content.*;
import android.provider.*;
import android.net.*;
import android.content.pm.*;
import android.*;
import android.app.*;
import android.widget.*;

public class PermissionManager {

    public static final int STEP_NONE = 0;
    public static final int STEP_MANAGE_STORAGE = 1;
    public static final int STEP_STORAGE_PERMISSION = 2;
    public static final int STEP_IGNORE_BATTERY = 3;
    public static final int STEP_POST_NOTIFICATION = 4;

    public static final int REQUEST_MANAGE_STORAGE = 1001;
    public static final int REQUEST_STORAGE_PERMISSION = 1002;
    public static final int REQUEST_NOTIFICATION = 1003;

    private Activity activity;
    private int currentStep = STEP_NONE;

	private static final String TAG = "PermissionManager";

    public PermissionManager(Activity activity) {
        this.activity = activity;
    }

    public void start() {
        currentStep = STEP_MANAGE_STORAGE;
        runCurrentStep();
    }

    public void onResume() {
        runCurrentStep(); // tiếp tục sau khi người dùng quay lại từ Settings
    }

    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode == REQUEST_STORAGE_PERMISSION || requestCode == REQUEST_NOTIFICATION) {
            currentStep++;
            runCurrentStep();
        }
    }

    public static boolean isNotificationEnabled(Context context) {
		NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		return nm != null && nm.areNotificationsEnabled();
	}

	private void runCurrentStep() {
        switch (currentStep) {
            case STEP_MANAGE_STORAGE:
                if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                    return;
                }
                currentStep++;
				runCurrentStep();
                break;
            case STEP_STORAGE_PERMISSION:
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M && Build.VERSION.SDK_INT < 30) {
                    if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                        activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        activity.requestPermissions(new String[] {
														Manifest.permission.WRITE_EXTERNAL_STORAGE,
														Manifest.permission.READ_EXTERNAL_STORAGE
													}, REQUEST_STORAGE_PERMISSION);
                        return;
                    }
                }
                currentStep++;
                runCurrentStep();
                break;

            case STEP_IGNORE_BATTERY:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
                    if (!pm.isIgnoringBatteryOptimizations(activity.getPackageName())) {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(intent);
                        return;
                    }
                }
                currentStep++;
                runCurrentStep();
                break;

            case STEP_POST_NOTIFICATION:
                if (Build.VERSION.SDK_INT >= 33
					&& activity.getApplicationInfo().targetSdkVersion >= 33
					&& activity.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(new String[] {
													"android.permission.POST_NOTIFICATIONS"
												}, REQUEST_NOTIFICATION);
                    return;
                } else {
					// target < 33 → không xin được POST_NOTIFICATIONS → kiểm tra thủ công
					if (!isNotificationEnabled(activity)) {
						Toast.makeText(activity, "Thông báo đang bị tắt. Vui lòng bật trong phần Cài đặt.", Toast.LENGTH_LONG).show();
						Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
						intent.putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
						activity.startActivity(intent);
						return;
					}
				}
                currentStep++;
                Logger.log(TAG, "✅ Tất cả quyền đã được cấp");
                break;
        }
    }
}

