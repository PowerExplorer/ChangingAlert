package com.example.batteryalert;
import android.app.job.*;
import android.content.*;
import android.util.*;
import java.util.*;

class JobIds {
    public static final int CHARGING_JOB = 1001;
    public static final int LOG_UPLOAD_JOB = 1002;
    public static final int SYNC_JOB = 1003;
}

public class JobHelper {
	private static final String TAG = "JobHelper";
	
    public static void ensureJobScheduled(Context context) {
        //Logger.log(TAG, "ensureJobScheduled");
		JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        // Kiểm tra xem job đã tồn tại chưa
//        List<JobInfo> jobs = scheduler.getAllPendingJobs();
//        for (JobInfo job : jobs) {
//            if (job.getId() == JobIds.CHARGING_JOB) {
//                Logger.log(TAG, "Đã có, không cần đăng ký lại");
//				return;
//            }
//        }
		if (scheduler.getPendingJob(JobIds.CHARGING_JOB) != null) {
			Logger.log(TAG, "Job pending cancelled");
			// Hủy job cũ nếu có để đảm bảo chạy lại được
			scheduler.cancel(JobIds.CHARGING_JOB);
			//return;
		}
        // Chưa có job, tiến hành đăng ký mới
        ComponentName serviceName = new ComponentName(context, ChargingJobService.class);
        JobInfo jobInfo = new JobInfo.Builder(JobIds.CHARGING_JOB, serviceName)
            .setRequiresCharging(true)
            .setPersisted(true) // Giữ sau reboot
            .build();

        int result = scheduler.schedule(jobInfo);
        if (result == JobScheduler.RESULT_SUCCESS) {
            Logger.log(TAG, "ChargingJobService vừa được đăng ký thành công.");
        } else {
            Logger.log(TAG, "Đăng ký ChargingJobService thất bại.");
        }
    }
}

