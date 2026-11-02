package com.ldd.mictshomebridge;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import android.os.SystemClock;

/** Three extra best-effort Home value checks after reboot; no continuous task. */
public final class BootCheckService extends JobService {
  private static final long[] DELAYS = {15000L, 45000L, 85000L};
  private static final int BASE = 8080;
  private static final long WINDOW = 90000L;

  private static android.content.SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("boot_checks", Context.MODE_PRIVATE);
  }

  public static void cancel(Context c) {
    JobScheduler jobs = (JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    if (jobs == null) return;
    for (int i = 0; i < DELAYS.length; i++) jobs.cancel(BASE + i);
  }

  public static void schedule(Context c) {
    cancel(c);
    if (!HomeBinding.enabled(c)) return;
    JobScheduler jobs = (JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    if (jobs == null) return;
    long token = SystemClock.elapsedRealtime();
    prefs(c).edit().putLong("started", token).commit();
    for (int i = 0; i < DELAYS.length; i++) {
      PersistableBundle extras = new PersistableBundle();
      extras.putLong("session", token);
      JobInfo info = new JobInfo.Builder(BASE + i, new ComponentName(c, BootCheckService.class))
          .setExtras(extras)
          .setMinimumLatency(DELAYS[i])
          .setOverrideDeadline(DELAYS[i] + 5000L)
          .build();
      jobs.schedule(info);
    }
  }

  @Override public boolean onStartJob(JobParameters p) {
    long expected = p.getExtras().getLong("session", -1L);
    long start = prefs(this).getLong("started", -1L);
    long elapsed = SystemClock.elapsedRealtime() - start;
    if (expected == start && start >= 0 && elapsed >= 0 && elapsed <= WINDOW) {
      HomeBinding.restore(this);
    }
    return false;
  }
  @Override public boolean onStopJob(JobParameters params) { return false; }
}
