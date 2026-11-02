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
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The one and only feature: restore Xiaomi long_press_home_key after reboot.
 * No assistant switching, Accessibility, Shizuku, microphone or root.
 */
public final class MainActivity extends Activity {
  private static final long[] RETRY_MS = {15000L, 45000L, 85000L};
  private static final long MAX_WINDOW_MS = 90000L;
  private static final int BASE_JOB_ID = 7240;

  private TextView status;
  private Switch enabledSwitch;

  private static android.content.SharedPreferences schedulePrefs(Context c) {
    return c.getSharedPreferences("home_boot_checks", Context.MODE_PRIVATE);
  }

  private static void cancel(Context c) {
    JobScheduler scheduler = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    if (scheduler == null) return;
    for (int i = 0; i < RETRY_MS.length; i++) scheduler.cancel(BASE_JOB_ID + i);
  }

  private static void schedule(Context c) {
    cancel(c);
    if (!HomeRestore.enabled(c)) return;
    JobScheduler scheduler = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    if (scheduler == null) return;
    long token = SystemClock.elapsedRealtime();
    schedulePrefs(c).edit().putLong("started_elapsed", token).commit();
    ComponentName component = new ComponentName(c, HomeCheckService.class);
    for (int i = 0; i < RETRY_MS.length; i++) {
      PersistableBundle extras = new PersistableBundle();
      extras.putLong("token", token);
      JobInfo j = new JobInfo.Builder(BASE_JOB_ID + i, component)
          .setExtras(extras)
          .setMinimumLatency(RETRY_MS[i])
          .setOverrideDeadline(RETRY_MS[i] + 5000L)
          .build();
      scheduler.schedule(j);
    }
  }

  public static final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
      String action = intent.getAction();
      if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
          && !Intent.ACTION_USER_UNLOCKED.equals(action)
          && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
      if (!HomeRestore.enabled(c)) {
        cancel(c);
        return;
      }
      // Restore Home immediately; retry while the OEM is still booting.
      HomeRestore.restore(c);
      schedule(c);
    }
  }

  public static final class HomeCheckService extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
      // A single settings check is short and synchronous.
      long expected = params.getExtras().getLong("token", -1L);
      long started = schedulePrefs(this).getLong("started_elapsed", -1L);
      long elapsed = SystemClock.elapsedRealtime() - started;
      if (HomeRestore.enabled(this) && started >= 0 && expected == started
          && elapsed >= 0 && elapsed <= MAX_WINDOW_MS) {
        HomeRestore.restore(this);
      }
      return false;
    }
    @Override public boolean onStopJob(JobParameters params) { return false; }
  }

  private int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
  }
  private GradientDrawable panel(int color) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(14));
    return d;
  }
  private TextView label(String value, int size, int color) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setLineSpacing(dp(4), 1.05f);
    return t;
  }
  private void button(LinearLayout parent, String value, Runnable click) {
    Button b = new Button(this);
    b.setAllCaps(false);
    b.setText(value);
    b.setTextColor(Color.WHITE);
    b.setBackground(panel(0xff244f93));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
    lp.topMargin = dp(12);
    parent.addView(b, lp);
    b.setOnClickListener(v -> click.run());
  }
  private void refreshStatus() {
    if (status == null) return;
    String value = HomeRestore.current(this);
    status.setText("Tự khôi phục: " + (HomeRestore.enabled(this) ? "BẬT" : "TẮT")
        + "\nQuyền sửa cài đặt hệ thống: "
        + (HomeRestore.hasWritePermission(this) ? "Đã cấp" : "Chưa cấp")
        + "\nGiữ Home: " + (value == null ? "Chưa đọc được" : value)
        + "\n\n" + HomeRestore.lastResult(this));
    enabledSwitch.setChecked(HomeRestore.enabled(this));
  }
  private void setEnabled(boolean checked) {
    HomeRestore.setEnabled(this, checked);
    if (checked) {
      HomeRestore.restore(this);
      // Also check again after installation / enabling, no restart required.
      schedule(this);
      if (!HomeRestore.hasWritePermission(this) && !HomeRestore.isActive(this)) {
        new AlertDialog.Builder(this)
            .setTitle("Cấp quyền Sửa cài đặt hệ thống")
            .setMessage("APK chỉ cần quyền Sửa cài đặt hệ thống để thử giữ lối tắt "
                + "nhấn giữ Home → Gemini. Một số bản HyperOS vẫn chặn ứng dụng "
                + "thường ghi khóa riêng này; khi đó chỉ ADB có thể đặt được.")
            .setPositiveButton("Mở quyền", (d,w) -> HomeRestore.openPermissionScreen(this))
            .setNegativeButton("Để sau", null).show();
      }
    } else {
      cancel(this);
    }
    refreshStatus();
  }

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(0xff10182a);
    getWindow().setNavigationBarColor(0xff10182a);
    ScrollView scroll = new ScrollView(this);
    scroll.setBackgroundColor(0xff10182a);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(20), dp(30), dp(20), dp(25));
    scroll.addView(root);
    setContentView(scroll);

    TextView title = label("Home → Gemini", 28, Color.WHITE);
    title.setTypeface(null, Typeface.BOLD);
    root.addView(title);
    TextView description = label(
        "Chỉ khôi phục lệnh nhấn giữ Home sau khi khởi động lại. "
            + "Không bật/tắt Google, không đổi trợ lý mặc định và không cần Trợ năng.",
        15, 0xffb8c8e2);
    description.setPadding(0, dp(10), 0, dp(22));
    root.addView(description);

    enabledSwitch = new Switch(this);
    enabledSwitch.setText("Tự khôi phục Home khi khởi động");
    enabledSwitch.setTextColor(Color.WHITE);
    enabledSwitch.setTextSize(16);
    enabledSwitch.setBackground(panel(0xff223249));
    enabledSwitch.setPadding(dp(14), dp(17), dp(14), dp(17));
    enabledSwitch.setChecked(HomeRestore.enabled(this));
    root.addView(enabledSwitch);
    enabledSwitch.setOnCheckedChangeListener((v,checked) -> {
      if (HomeRestore.enabled(this) != checked) setEnabled(checked);
    });

    status = label("", 14, 0xffdce6f6);
    status.setPadding(dp(14), dp(16), dp(14), dp(16));
    status.setBackground(panel(0xff223249));
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
    sp.topMargin = dp(13);
    root.addView(status, sp);

    button(root, "Kiểm tra / Khôi phục Home ngay", () -> {
      boolean ok = HomeRestore.restore(this);
      refreshStatus();
      Toast.makeText(this, ok ? "Đã đúng giá trị Home."
          : "HyperOS chưa nhận thay đổi. Xem trạng thái.", Toast.LENGTH_LONG).show();
    });
    button(root, "Cấp quyền sửa cài đặt", () -> HomeRestore.openPermissionScreen(this));
    button(root, "Sao chép ADB dự phòng", () -> {
      ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
      cm.setPrimaryClip(ClipData.newPlainText("Home ADB", HomeRestore.adbCommand()));
      Toast.makeText(this, "Đã sao chép lệnh.", Toast.LENGTH_SHORT).show();
    });
    TextView foot = label(
        "Tự thử khôi phục ngay khi nhận sự kiện khởi động, sau đó kiểm tra "
        + "ở khoảng giây 15, 45, 85 (JobScheduler có thể trễ). "
        + "Cấp Tự khởi động và Không hạn chế pin trong HyperOS.\n\n"
        + "LƯU Ý: Đây là bản thử nghiệm targetSdk 22. Nếu HyperOS không cho APK "
        + "ghi khóa long_press_home_key, không thể hứa rằng chức năng Home "
        + "sẽ tự phục hồi sau reboot dù lệnh ADB thủ công vẫn hiệu quả.",
        13, 0xffb8c8e2);
    foot.setPadding(0, dp(20), 0, 0);
    root.addView(foot);
    refreshStatus();
  }

  @Override protected void onResume() {
    super.onResume();
    refreshStatus();
  }
}
