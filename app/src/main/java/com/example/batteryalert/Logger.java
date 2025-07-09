package com.example.batteryalert;

import android.os.Environment;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.*;
import android.util.*;
import android.net.*;
import android.provider.*;
import android.content.*;
//import android.support.v4.content.FileProvider;
import java.util.*;

public class Logger {
	private static final String LOG_DIR = "BatteryAlert";
	private static final String TAG = "Battery Alert";
	private static final long MAX_LOG_SIZE = 128 * 1024; // 128kB

	private static Writer writer;
	private static File logFile;
	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
	private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static String currentDay = "";

	public static synchronized void log(String tag, String message) {
		Log.d(TAG, message);

		String time = simpleDateFormat.format(new Date());
		String msg = time + " - " + tag + ": " + message + "\n";

		try {
			setupWriter();
			writer.append(msg);
			writer.flush();
		} catch (IOException e) {
			Log.e(TAG, "Lỗi ghi log", e);
			closeWriter();
		}
	}

	public static synchronized void log(String tag, Throwable t) {
		String time = simpleDateFormat.format(new Date());
		Log.e(TAG, time + " - " + tag, t);

		try {
			setupWriter();
			writer.append(time + " - " + tag + ": ");
			PrintWriter printWriter = new PrintWriter(writer);
			t.printStackTrace(printWriter);
			printWriter.flush();
		} catch (IOException e) {
			Log.e(TAG, "Lỗi ghi log (Throwable)", e);
			closeWriter();
		}
	}

	private static void setupWriter() throws IOException {
		String today = sdf.format(new Date());
		boolean needNewFile = false;

		if (writer == null || logFile == null || !logFile.exists()) {
			needNewFile = true;
		} else if (!today.equals(currentDay)) {
			// Ngày đã thay đổi
			needNewFile = true;
			closeWriter();
		} else if (logFile.length() >= MAX_LOG_SIZE) {
			// File quá lớn → xoay
			needNewFile = true;
			closeWriter();
		}

		if (needNewFile) {
			currentDay = today;

			File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
			File logDir = new File(downloadDir, LOG_DIR);
			if (!logDir.exists())
				logDir.mkdirs();

			int index = 0;
			File candidate;
			do {
				String name = "battery_" + today + (index == 0 ? "" : "_" + index) + ".log";
				candidate = new File(logDir, name);
				index++;
			} while (candidate.exists() && candidate.length() >= MAX_LOG_SIZE);

			logFile = candidate;
			writer = new BufferedWriter(new FileWriter(logFile, true));
		}
	}

	public static void closeWriter() {
		try {
			if (writer != null)
				writer.close();
		} catch (IOException e) {
			Log.e(TAG, "Đóng writer lỗi", e);
		}
		writer = null;
		logFile = null;
		currentDay = "";
	}
	
	public static void cleanupOldLogs(int maxFiles) {
		File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		File logDir = new File(downloadDir, LOG_DIR);
		if (!logDir.exists() || !logDir.isDirectory()) return;

		File[] files = logDir.listFiles();
		if (files == null || files.length <= maxFiles) return;

		// Sắp xếp file theo ngày sửa đổi tăng dần (cũ nhất trước)
		Arrays.sort(files, new Comparator<File>() {
				public int compare(File f1, File f2) {
					return Long.compare(f1.lastModified(), f2.lastModified());
				}
			});

		int filesToDelete = files.length - maxFiles;
		for (int i = 0; i < filesToDelete; i++) {
			files[i].delete();
		}
	}
	
	public static void exportLogToSAF(Context ctx, Uri treeUri) {
		OutputStream os = null;
		InputStream fis = null;
		try {
			if (logFile == null || !logFile.exists()) {
				log("Logger", "Không tìm thấy file log hiện tại để export.");
				return;
			}

			String filename = logFile.getName();
			Uri docUri = DocumentsContract.createDocument(
				ctx.getContentResolver(),
				treeUri,
				"text/plain",
				filename
			);

			if (docUri == null) {
				log("Logger", "Không thể tạo file SAF");
				return;
			}

			os = new BufferedOutputStream(ctx.getContentResolver().openOutputStream(docUri));
			fis = new BufferedInputStream(new FileInputStream(logFile));
			byte[] buffer = new byte[4096];
			int len;
			while ((len = fis.read(buffer)) != -1) {
				os.write(buffer, 0, len);
			}
			log("Logger", "Export file log thành công: " + filename);

		} catch (Exception e) {
			log(TAG, e);
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {}
			}
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {}
			}
		}
	}

//	public void sendLogFileEmail(Context context) {
//		if (Logger.logFile == null || !Logger.logFile.exists()) {
//			Logger.log("Logger", "Không có file log để gửi email.");
//			return;
//		}
//
//		Uri fileUri = FileProvider.getUriForFile(context,
//												 context.getPackageName() + ".fileprovider",
//												 Logger.logFile);
//
//		Intent emailIntent = new Intent(Intent.ACTION_SEND);
//		emailIntent.setType("message/rfc822");
//		emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Log file Battery Alert");
//		emailIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
//		emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//
//		try {
//			context.startActivity(Intent.createChooser(emailIntent, "Gửi log qua email"));
//		} catch (android.content.ActivityNotFoundException ex) {
//			Logger.log("Logger", "Không có app email nào cài trên thiết bị.");
//		}
//	}
	
	// Đọc file SAF và lưu vào Internal Storage
    public static InputStream readFileAndSaveToInternal(Context ctx, Uri uri) throws FileNotFoundException {
        try {
            InputStream is = ctx.getContentResolver().openInputStream(uri);
            return is;
		} catch (FileNotFoundException e) {
            log(TAG, "Lỗi khi đọc file SAF: " + e.getMessage());
			throw e;
        }
    }
}


