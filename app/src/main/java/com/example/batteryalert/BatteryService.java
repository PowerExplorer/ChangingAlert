package com.example.batteryalert;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.content.SharedPreferences;
import android.app.*;
import android.content.*;
import android.os.*;

public class BatteryService extends Service {
	
    private BatteryReceiver receiver;
    private static final String CHANNEL_ID = "battery_alert_channel";
	static boolean isRunning = false;
	
	private static final String TAG = "BatteryService";

    @Override
    public void onCreate() {
        BatteryService.isRunning = true;
		super.onCreate();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
				Logger.log("BatteryService", "Ứng dụng đang bị tối ưu pin! Nên yêu cầu người dùng tắt.");
			}
		}
		
        receiver = new BatteryReceiver(getApplicationContext());//BatteryService.this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
		filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(receiver, filter);
		
		createNotificationChannel();
        Notification notification = buildNotification();
        startForeground(1, notification);
		int pid = android.os.Process.myPid();
        Logger.log(TAG, "Service CREATED - PID: " + pid);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (receiver != null) {
            unregisterReceiver(receiver);
			receiver = null;
        }
		stopForeground(true); // Xóa notification
		BatteryService.isRunning = false;
		Logger.log(TAG, "Service DESTROYED");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.log(TAG, "Service onStartCommand");
        
        return START_NOT_STICKY;//START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Battery Alert Channel",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Thông báo theo dõi pin");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
// 1. Tạo Intent mở Activity bạn muốn
		Context applicationContext = getApplicationContext();
		Intent intent = new Intent(applicationContext, MainActivity.class);
// Dùng flags để xử lý lịch sử Activity sao cho đúng
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

// 2. Tạo PendingIntent từ Intent
		PendingIntent pendingIntent = PendingIntent.getActivity(
			applicationContext,
			0, // requestCode
			intent,
			PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
		);

// 3. Xây dựng Notification với contentIntent là PendingIntent vừa tạo
		
// 4. Hiển thị notification
//		NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
//		notificationManager.notify(12345, builder.build());
		
        builder.setContentTitle("Battery Alert đang chạy")
			.setContentText("Đang theo dõi trạng thái sạc pin")
			.setSmallIcon(android.R.drawable.ic_lock_idle_charging)  // dùng icon hệ thống
			.setPriority(Notification.PRIORITY_DEFAULT)
			.setContentIntent(pendingIntent)  // Gán intent mở Activity khi bấm
			.setOngoing(true)
			.setAutoCancel(false);

        return builder.build();
    }
}

