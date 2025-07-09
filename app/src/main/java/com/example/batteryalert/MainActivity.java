package com.example.batteryalert;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.*;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.util.*;
import java.io.*;
import android.app.NotificationManager;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import android.app.*;
import android.content.*;

public class MainActivity extends Activity {

    static final String PREF_NAME = "prefs";
    private static final String KEY_USE_SOUND = "use_sound";
    private static final String KEY_USE_TTS = "use_tts";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_SOUND_URI = "sound_uri";
    static final String KEY_USE_PERCENT = "percent";
    
	private static final int REQUEST_CODE_PICK_AUDIO = 1001;
	private static final int REQUEST_PERMISSIONS = 100;
    private static final int REQUEST_MANAGE_STORAGE = 101;
	private static final int REQUEST_CODE_OPEN_DIRECTORY = 103;
	private static final int REQUEST_CODE_OPEN_FILE = 200;
	private static final int REQUEST_CODE_OPEN_TEXT_FILE = 201;
	public static final int REQUEST_NOTIFICATION = 1003;
	
    private Button btnStart;
	private SharedPreferences prefs;
	
    private BroadcastReceiver batteryInfoReceiver;
    //private PermissionManager permissionManager;
	
	private static final String TAG = "MainActivity";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
				private Thread.UncaughtExceptionHandler defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
				@Override
				public void uncaughtException(final Thread t, final Throwable e) {
					Logger.log(TAG, e);
					defaultUEH.uncaughtException(t, e);
				}
			});
//		permissionManager = new PermissionManager(this);
//		permissionManager.start(); // bắt đầu chuỗi xin quyền
		
		if (Build.VERSION.SDK_INT >= 30) {
            // Android 11+ cần quyền đặc biệt: MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.parse("package:" + getPackageName());
                intent.setData(uri);
                startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                Logger.log(TAG, "Đang yêu cầu quyền MANAGE_EXTERNAL_STORAGE...");
            }
        } else {
            // Android < 11 cần quyền WRITE_EXTERNAL_STORAGE và READ_EXTERNAL_STORAGE
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(new String[] {
									   Manifest.permission.WRITE_EXTERNAL_STORAGE,
									   Manifest.permission.READ_EXTERNAL_STORAGE
								   }, REQUEST_PERMISSIONS);
                Logger.log(TAG, "Đang yêu cầu quyền đọc/ghi bộ nhớ ngoài...");
            }
		}
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
		prefs.edit().remove("initial_level")
			.remove("alert_13")
			.remove("alert_65")
			.remove("used1Time").apply();
		
        RadioButton cbSound = (RadioButton) findViewById(R.id.checkbox_sound);
        RadioButton cbTts = (RadioButton) findViewById(R.id.checkbox_tts);
        Button btnChooseFile = (Button) findViewById(R.id.btn_choose_file);
        SeekBar volumeBar = (SeekBar) findViewById(R.id.volume_seekbar);
		btnStart = (Button) findViewById(R.id.btnStart);
		
		CheckBox btn1Percent = (CheckBox) findViewById(R.id.btn1Percent);
		btn1Percent.setChecked(prefs.getBoolean(KEY_USE_PERCENT, false));
        
		cbSound.setChecked(prefs.getBoolean(KEY_USE_SOUND, false));
        cbTts.setChecked(prefs.getBoolean(KEY_USE_TTS, true));
        volumeBar.setProgress(prefs.getInt(KEY_VOLUME, 80));

        cbSound.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					prefs.edit().putBoolean(KEY_USE_SOUND, isChecked).apply();
				}
			});

        cbTts.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					prefs.edit().putBoolean(KEY_USE_TTS, isChecked).apply();
				}
			});

        btn1Percent.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					prefs.edit().putBoolean(KEY_USE_PERCENT, isChecked).apply();
				}
			});

		volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					prefs.edit().putInt(KEY_VOLUME, progress).apply();
				}

				public void onStartTrackingTouch(SeekBar seekBar) {}
				public void onStopTrackingTouch(SeekBar seekBar) {}
			});

        btnChooseFile.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
					intent.setType("audio/*");
					intent.addCategory(Intent.CATEGORY_OPENABLE);
					startActivityForResult(Intent.createChooser(intent, "Chọn file âm báo"), REQUEST_CODE_PICK_AUDIO);
				}
			});
		
		btnStart.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
						if (!BatteryUtil.isIgnoringBatteryOptimizations(MainActivity.this)) {
							new AlertDialog.Builder(MainActivity.this)
								.setTitle("Tắt tối ưu hóa pin")
								.setMessage("Ứng dụng cần được loại khỏi tối ưu hóa pin để chạy nền ổn định và cảnh báo khi sạc.")
								.setPositiveButton("Cho phép", new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) {
										BatteryUtil.requestDisableBatteryOptimization(MainActivity.this);
									}
								})
								.setNegativeButton("Bỏ qua", null)
								.show();
						}
					}
					if (Build.VERSION.SDK_INT >= 33
						&& getApplicationInfo().targetSdkVersion >= 33
						&& checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
						requestPermissions(new String[] {
											   "android.permission.POST_NOTIFICATIONS"
										   }, REQUEST_NOTIFICATION);
						return;
					} else {
						// target < 33 → không xin được POST_NOTIFICATIONS → kiểm tra thủ công
						NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
						if (nm != null && !nm.areNotificationsEnabled()) {
							Toast.makeText(MainActivity.this,
										   "Ứng dụng chưa được bật thông báo. Vui lòng bật trong phần Cài đặt.",
										   Toast.LENGTH_LONG).show();
							Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
							intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
							startActivity(intent);
							return;
						}
					}
					startStopService(btnStart);
				}
			});
			
		JobHelper.ensureJobScheduled(getApplicationContext());
		Logger.log(TAG, "JobScheduler registered for charging event");
		
        final TextView tvCapacity = (TextView) findViewById(R.id.tv_battery_capacity);
        final TextView tvTemp = (TextView) findViewById(R.id.tv_battery_temp);
		final TextView tv_health = (TextView) findViewById(R.id.tv_health);
        final TextView tv_plugged = (TextView) findViewById(R.id.tv_plugged);
		final TextView tv_status = (TextView) findViewById(R.id.tv_status);
        final TextView tv_technology = (TextView) findViewById(R.id.tv_technology);
		final TextView tv_voltage = (TextView) findViewById(R.id.tv_voltage);
        final TextView tv_property = (TextView) findViewById(R.id.tv_property);
        batteryInfoReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
				if (BatteryService.isRunning) {
					btnStart.setText("Ngừng giám sát pin");
				} else {
					btnStart.setText("Bắt đầu giám sát pin");
				}
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                float percentage = level * 100f / scale;

                tvCapacity.setText("Dung lượng pin: " + percentage + "%");
                tvTemp.setText("Nhiệt độ: " + (temp / 10.0f) + " °C");
				tv_health.setText("Health: " + findConstantNameByValue(BatteryManager.class, "BATTERY_HEALTH_", intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)).substring("BATTERY_HEALTH_".length()));
				tv_plugged.setText("Plugged: " + findConstantNameByValue(BatteryManager.class, "BATTERY_PLUGGED_", intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)));
				tv_status.setText("Status: " + findConstantNameByValue(BatteryManager.class, "BATTERY_STATUS_", intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)).substring("BATTERY_STATUS_".length()));
				tv_technology.setText("Technology: " + intent.getCharSequenceExtra(BatteryManager.EXTRA_TECHNOLOGY));
				tv_voltage.setText("Voltage: " + intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));
				tv_property.setText(BatteryPropertyInspector.inspectBatteryProperties(MainActivity.this));
			}
        };
    }
	
	public String findConstantNameByValue(Class<?> clazz, String prefix, int targetValue) {
		Field[] fields = clazz.getDeclaredFields();
		for (int i = 0; i < fields.length; i++) {
			Field field = fields[i];

			// Kiểm tra nếu là static final int và tên bắt đầu với prefix
			String prefixLowerCase = prefix.toLowerCase();
			if (Modifier.isStatic(field.getModifiers()) &&
				Modifier.isFinal(field.getModifiers()) &&
				field.getType() == int.class &&
				field.getName().toLowerCase().startsWith(prefixLowerCase)) {
				try {
					int value = field.getInt(null); // null vì là static
					if (value == targetValue) {
						return field.getName();
					}
				} catch (IllegalAccessException e) {
					Logger.log(TAG, "Bỏ qua không truy cập được");
				}
			}
		}
		return null; // Không tìm thấy
	}
	
	private void startStopService(Button btnStart) {
		//Logger.log(TAG, "BatteryService.isRunning before start/stop: " + BatteryService.isRunning);

		Intent intent = new Intent(MainActivity.this, BatteryService.class);
		if (!BatteryService.isRunning) {
			prefs.edit().putBoolean("used1Time", false).apply();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				startForegroundService(intent);
			} else {
				startService(intent);
			}
			btnStart.setText("Ngừng giám sát pin");
		} else {
			stopService(intent);
			prefs.edit().remove("initial_level")
				.remove("alert_13")
				.remove("alert_65")
				.remove("used1Time").apply();
			btnStart.setText("Bắt đầu giám sát pin");
		}
	}
	
	void openDirectoryPicker() {
        // Chỉ hỗ trợ SAF từ API 21 trở lên
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY);
        } else {
            Logger.log(TAG, "Thiết bị không hỗ trợ Storage Access Framework.");
        }
    }
	
	// 📁 A. Chọn file SAF, lọc theo MIME
    void openTextFilePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");

            // Lọc theo MIME types: text/plain, application/json, application/pdf
            String[] mimeTypes = {"text/plain", "application/json", "application/pdf"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

            startActivityForResult(intent, REQUEST_CODE_OPEN_TEXT_FILE);
        } else {
            Logger.log(TAG, "Thiết bị không hỗ trợ SAF.");
        }
    }

	// Mở giao diện chọn file SAF
	void openFilePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*"); // Tất cả loại file, có thể giới hạn nếu muốn
            startActivityForResult(intent, REQUEST_CODE_OPEN_FILE);
        } else {
            Logger.log(TAG, "Thiết bị không hỗ trợ SAF.");
        }
    }
	
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_AUDIO
			&& resultCode == RESULT_OK
			&& data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                prefs.edit().putString(KEY_SOUND_URI, uri.toString()).apply();
            }
        } else if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
                Logger.log(TAG, "Đã cấp quyền MANAGE_EXTERNAL_STORAGE.");
            } else {
                Logger.log(TAG, "Bạn chưa cấp quyền quản lý bộ nhớ.");
            }
        } else if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == Activity.RESULT_OK) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                // Ghi nhớ quyền truy cập thư mục
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    getContentResolver()
						.takePersistableUriPermission(treeUri,
													  Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                }
                //Logger.writeFileToUri(this, treeUri);
            } else {
                Logger.log(TAG, "Không chọn thư mục.");
            }
        } else if (requestCode == REQUEST_CODE_OPEN_FILE && resultCode == RESULT_OK) {
            Uri fileUri = data.getData();
            if (fileUri != null) {
                //Logger.readFileAndSaveToInternal(fileUri);
            } else {
                Logger.log(TAG, "Không chọn file.");
            }
        } else if (requestCode == REQUEST_CODE_OPEN_TEXT_FILE && resultCode == RESULT_OK) {
            Uri fileUri = data.getData();
            if (fileUri != null) {
                //Logger.readFileAndSaveToInternal(fileUri);
            } else {
                Logger.log(TAG, "Không chọn file.");
            }
        }
    }
	
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
//		if (permissionManager != null) {
//			permissionManager.onRequestPermissionsResult(requestCode, grantResults);
//		}
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean granted = true;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
				Logger.log(TAG, "Quyền WRITE_EXTERNAL_STORAGE đã được cấp.");
            } else {
                Logger.log(TAG, "Bạn chưa cấp đủ quyền.");
            }
        } else if (requestCode == REQUEST_NOTIFICATION) {
			
		}
    }

	@Override
	protected void onResume() {
		super.onResume();
//		if (permissionManager != null) {
//			permissionManager.onResume(); // tiếp tục sau khi quay về từ Settings
//		}
		registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		if (BatteryService.isRunning) {
			btnStart.setText("Ngừng giám sát pin");
		} else {
			btnStart.setText("Bắt đầu giám sát pin");
		}
	}
	
    protected void onPause() {
        super.onDestroy();
        if (batteryInfoReceiver != null)
			unregisterReceiver(batteryInfoReceiver);
    }
}
