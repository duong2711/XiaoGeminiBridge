package com.ldd.geminirestore;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** One purpose: restore Xiaomi long-press Home -> Gemini, if HyperOS permits it. */
public class MainActivity extends Activity {
  private static final long[] CHECK_DELAYS_MS = {15000L, 45000L, 85000L};
  private static final long MAX_BOOT_WINDOW_MS = 90000L;
  private static final int JOB_BASE = 6240;

  private TextView status;
  private Switch autoSwitch;
  private LinearLayout content;

  private static android.content.SharedPreferences schedulePrefs(Context context) {
    return context.getSharedPreferences("home_schedule", Context.MODE_PRIVATE);
  }

  private static void cancelChecks(Context context) {
    JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    if (scheduler == null) return;
    for (int i = 0; i < CHECK_DELAYS_MS.length; i++) scheduler.cancel(JOB_BASE + i);
  }

  private static void scheduleChecks(Context context) {
    cancelChecks(context);
    if (!HomeRestore.enabled(context)) return;
    JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    if (scheduler == null) return;
    schedulePrefs(context).edit().putLong("boot_elapsed", SystemClock.elapsedRealtime()).apply();
    ComponentName service = new ComponentName(context, RestoreService.class);
    for (int i = 0; i < CHECK_DELAYS_MS.length; i++) {
      long delay = CHECK_DELAYS_MS[i];
      JobInfo job = new JobInfo.Builder(JOB_BASE + i, service)
          .setMinimumLatency(delay)
          .setOverrideDeadline(delay + 5000L)
          .build();
      scheduler.schedule(job);
    }
  }

  public static final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (Intent.ACTION_BOOT_COMPLETED.equals(action)
          || Intent.ACTION_USER_UNLOCKED.equals(action)
          || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
        if (HomeRestore.enabled(context)) {
          HomeRestore.restore(context);
          scheduleChecks(context);
        } else {
          cancelChecks(context);
        }
      }
    }
  }

  public static final class RestoreService extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
      new Thread(() -> {
        try {
          long start = schedulePrefs(this).getLong("boot_elapsed", -1L);
          long elapsed = SystemClock.elapsedRealtime() - start;
          if (HomeRestore.enabled(this) && start >= 0L
              && elapsed >= 0L && elapsed <= MAX_BOOT_WINDOW_MS) {
            HomeRestore.restore(this);
          }
        } finally {
          jobFinished(params, false);
        }
      }, "GeminiHomeCheck").start();
      return true;
    }
    @Override public boolean onStopJob(JobParameters params) { return false; }
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
  }

  private GradientDrawable background(int color) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(color);
    drawable.setCornerRadius(dp(14));
    return drawable;
  }

  private TextView text(String value, int size, int color) {
    TextView result = new TextView(this);
    result.setText(value);
    result.setTextColor(color);
    result.setTextSize(size);
    result.setLineSpacing(dp(4), 1.0f);
    return result;
  }

  private void addButton(String value, int color, Runnable click) {
    Button button = new Button(this);
    button.setText(value);
    button.setTextColor(Color.WHITE);
    button.setAllCaps(false);
    button.setBackground(background(color));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
    params.topMargin = dp(12);
    content.addView(button, params);
    button.setOnClickListener(view -> click.run());
  }

  private void updateStatus() {
    String current = HomeRestore.current(this);
    String currentLabel = current == null ? "Chưa đọc được" : current;
    status.setText(
        "Tự khôi phục: " + (HomeRestore.enabled(this) ? "BẬT" : "TẮT")
        + "\nQuyền sửa cài đặt: " + (HomeRestore.hasWritePermission(this) ? "Đã cấp" : "Chưa cấp")
        + "\nGiá trị Home: " + currentLabel
        + "\n\n" + HomeRestore.lastResult(this));
    autoSwitch.setChecked(HomeRestore.enabled(this));
  }

  private void enable(boolean enabled) {
    HomeRestore.setEnabled(this, enabled);
    if (!enabled) {
      cancelChecks(this);
      Toast.makeText(this, "Đã tắt tự khôi phục Home.", Toast.LENGTH_SHORT).show();
    } else if (!HomeRestore.hasWritePermission(this) && !HomeRestore.isActive(this)) {
      new AlertDialog.Builder(this)
          .setTitle("Cấp quyền sửa cài đặt hệ thống")
          .setMessage("Ứng dụng cần quyền Sửa cài đặt hệ thống để thử khôi phục Home."
              + "\n\nNếu HyperOS chặn thay đổi, hãy dùng lệnh ADB dự phòng.")
          .setPositiveButton("Mở cài đặt", (dialog, which) -> HomeRestore.openPermissionScreen(this))
          .setNegativeButton("Để sau", null).show();
    } else {
      HomeRestore.restore(this);
    }
    updateStatus();
  }

  private void checkNow() {
    if (!HomeRestore.enabled(this)) {
      Toast.makeText(this, "Hãy bật tự khôi phục trước.", Toast.LENGTH_SHORT).show();
      return;
    }
    boolean ok = HomeRestore.restore(this);
    updateStatus();
    Toast.makeText(this, ok ? "Giá trị Home đã đúng." : "Chưa khôi phục được. Xem trạng thái.", Toast.LENGTH_LONG).show();
  }

  private void copyAdb() {
    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("Gemini Home ADB", HomeRestore.adbCommand()));
    Toast.makeText(this, "Đã sao chép lệnh ADB.", Toast.LENGTH_SHORT).show();
  }

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(0xff10182a);
    getWindow().setNavigationBarColor(0xff10182a);
    ScrollView scroll = new ScrollView(this);
    scroll.setBackgroundColor(0xff10182a);
    content = new LinearLayout(this);
    content.setPadding(dp(20), dp(30), dp(20), dp(28));
    content.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(content);
    setContentView(scroll);

    TextView title = text("Home → Gemini · Legacy", 29, Color.WHITE);
    title.setTypeface(null, Typeface.BOLD);
    content.addView(title);
    TextView subtitle = text("Bản thử nghiệm riêng cho HyperOS 3. Tự khôi phục Home sau khởi động hoặc mở khóa.", 15, 0xffacbdd5);
    subtitle.setPadding(0, dp(8), 0, dp(22));
    content.addView(subtitle);

    autoSwitch = new Switch(this);
    autoSwitch.setText("Tự khôi phục Home → Gemini");
    autoSwitch.setTextSize(16);
    autoSwitch.setTextColor(Color.WHITE);
    autoSwitch.setPadding(dp(14), dp(14), dp(14), dp(14));
    autoSwitch.setBackground(background(0xff223249));
    autoSwitch.setChecked(HomeRestore.enabled(this));
    content.addView(autoSwitch);
    autoSwitch.setOnCheckedChangeListener((view, checked) -> {
      if (HomeRestore.enabled(this) != checked) enable(checked);
    });

    status = text("", 14, 0xffdce6f6);
    status.setPadding(dp(14), dp(17), dp(14), dp(17));
    LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
    statusParams.topMargin = dp(14);
    status.setBackground(background(0xff223249));
    content.addView(status, statusParams);

    addButton("Kiểm tra & khôi phục ngay", 0xff2563c6, this::checkNow);
    addButton("Cấp quyền sửa cài đặt", 0xff32546b, () -> HomeRestore.openPermissionScreen(this));
    addButton("Sao chép lệnh ADB dự phòng", 0xff324157, this::copyAdb);

    TextView note = text(
        "BẢN THỬ NGHIỆM: chỉ cài bằng ADB; sử dụng targetSdk 22 nên giảm lớp bảo vệ tương thích/bảo mật của Android.\n\n"
        + "Ứng dụng chỉ xử lý thao tác nhấn giữ Home, không chỉnh các cài đặt trợ lý khác.\n\n"
        + "Sau khởi động, ứng dụng thử khôi phục ngay và kiểm tra lại tối đa 90 giây. "
        + "Hãy bật Tự khởi động và chọn Không hạn chế pin trong HyperOS.\n\n"
        + "Nếu xuất hiện IllegalArgumentException hoặc SecurityException, HyperOS có thể "
        + "không cho APK thường ghi cài đặt này, dù ADB thủ công vẫn làm được.",
        13, 0xffacbdd5);
    note.setPadding(0, dp(20), 0, 0);
    content.addView(note);
    updateStatus();
  }

  @Override protected void onResume() {
    super.onResume();
    if (status != null) {
      if (HomeRestore.enabled(this)) HomeRestore.restore(this);
      updateStatus();
    }
  }
}
