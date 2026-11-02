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
import android.os.PersistableBundle;
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
  private static final Object RESTORE_LOCK = new Object();

  private TextView status;
  private Switch autoSwitch;
  private Switch assistantSwitch;
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
    // Each schedule carries a session token; stale jobs cannot repeat an old
    // Home/Google cycle after a new boot/unlock cycle has been scheduled.
    long session = SystemClock.elapsedRealtime();
    schedulePrefs(context).edit()
        .putLong("boot_elapsed", session)
        .commit();
    ComponentName service = new ComponentName(context, RestoreService.class);
    for (int i = 0; i < CHECK_DELAYS_MS.length; i++) {
      long delay = CHECK_DELAYS_MS[i];
      PersistableBundle extras = new PersistableBundle();
      extras.putLong("session", session);
      extras.putInt("stage", i);
      JobInfo job = new JobInfo.Builder(JOB_BASE + i, service)
          .setExtras(extras)
          .setMinimumLatency(delay)
          .setOverrideDeadline(delay + 5000L)
          .build();
      scheduler.schedule(job);
    }
  }

  /**
   * A single restore pass is always ordered: first Home, then a Google
   * None -> Google refresh immediately afterward (if user opted in).
   * If Home cannot be restored, do not touch the assistant setting.
   */
  private static boolean restoreHomeThenGoogle(Context context) {
    synchronized (RESTORE_LOCK) {
      if (!HomeRestore.enabled(context)) return false;
      boolean homeRestored = HomeRestore.restore(context);
      if (homeRestored && AssistantRefresh.enabled(context)) {
        AssistantRefresh.refresh(context);
      }
      return homeRestored;
    }
  }

  public static final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
          && !Intent.ACTION_USER_UNLOCKED.equals(action)
          && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;

      // Do not block the broadcast thread while Google is being rebound.
      final PendingResult pending = goAsync();
      new Thread(() -> {
        try {
          synchronized (RESTORE_LOCK) {
            if (HomeRestore.enabled(context)) {
              restoreHomeThenGoogle(context);
              scheduleChecks(context);
            } else {
              cancelChecks(context);
            }
          }
        } finally {
          pending.finish();
        }
      }, "GeminiRestoreBoot").start();
    }
  }

  public static final class RestoreService extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
      new Thread(() -> {
        try {
          android.content.SharedPreferences prefs = schedulePrefs(this);
          PersistableBundle extras = params.getExtras();
          long requestedSession = extras.getLong("session", -1L);
          int stage = extras.getInt("stage", -1);

          synchronized (RESTORE_LOCK) {
            long start = prefs.getLong("boot_elapsed", -1L);
            long elapsed = SystemClock.elapsedRealtime() - start;
            if (start < 0L || requestedSession != start
                || stage < 0 || stage >= CHECK_DELAYS_MS.length
                || elapsed < 0L || elapsed > MAX_BOOT_WINDOW_MS) {
              return; // Stale or late job. Ignore it.
            }
            // At 15s, 45s AND 85s: Home first, Google immediately after.
            restoreHomeThenGoogle(this);
          }
        } finally {
          jobFinished(params, false);
        }
      }, "GeminiRestorePair").start();
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
        + "\nGoogle sau khởi động: " + (AssistantRefresh.enabled(this) ? "BẬT" : "TẮT")
        + "\nQuyền WRITE_SECURE_SETTINGS: "
        + (AssistantRefresh.permitted(this) ? "Đã cấp ADB" : "Chưa cấp")
        + "\n" + AssistantRefresh.lastResult(this)
        + "\n\n" + HomeRestore.lastResult(this));
    autoSwitch.setChecked(HomeRestore.enabled(this));
    if (assistantSwitch != null) assistantSwitch.setChecked(AssistantRefresh.enabled(this));
  }

  private void enable(boolean enabled) {
    HomeRestore.setEnabled(this, enabled);
    if (!enabled) {
      if (!AssistantRefresh.enabled(this)) cancelChecks(this);
      Toast.makeText(this, "Đã tắt tự khôi phục Home.", Toast.LENGTH_SHORT).show();
    } else if (!HomeRestore.hasWritePermission(this) && !HomeRestore.isActive(this)) {
      new AlertDialog.Builder(this)
          .setTitle("Cấp quyền sửa cài đặt hệ thống")
          .setMessage("Ứng dụng cần quyền Sửa cài đặt hệ thống để thử khôi phục Home."
              + "\n\nNếu HyperOS chặn thay đổi, hãy dùng lệnh ADB dự phòng.")
          .setPositiveButton("Mở cài đặt", (dialog, which) -> HomeRestore.openPermissionScreen(this))
          .setNegativeButton("Để sau", null).show();
    } else {
      new Thread(() -> {
        restoreHomeThenGoogle(this);
        runOnUiThread(this::updateStatus);
      }, "GeminiRestoreManual").start();
    }
    updateStatus();
  }

  private void checkNow() {
    if (!HomeRestore.enabled(this)) {
      Toast.makeText(this, "Hãy bật tự khôi phục trước.", Toast.LENGTH_SHORT).show();
      return;
    }
    new Thread(() -> {
      boolean ok = restoreHomeThenGoogle(this);
      runOnUiThread(() -> {
        updateStatus();
        Toast.makeText(this, ok
            ? "Đã kiểm tra Home và thực hiện bước làm mới Google nếu được bật."
            : "Chưa khôi phục được Home. Xem trạng thái.",
            Toast.LENGTH_LONG).show();
      });
    }, "GeminiRestoreManual").start();
  }

  private void enableAssistant(boolean enabled) {
    AssistantRefresh.setEnabled(this, enabled);
    if (enabled && !AssistantRefresh.permitted(this)) {
      new AlertDialog.Builder(this)
          .setTitle("Cấp quyền ADB một lần, không cần Trợ năng")
          .setMessage("Ứng dụng dùng WRITE_SECURE_SETTINGS như Gemini Restore Auto v3 "
              + "để ngắt dịch vụ Google 1,5 giây, rồi kích hoạt lại. "
              + "Không yêu cầu Accessibility. Hãy sao chép và chạy lệnh ADB một lần.")
          .setPositiveButton("Sao chép lệnh", (d,w) -> copyGrant())
          .setNegativeButton("Để sau", null).show();
    }
    if (!enabled && !HomeRestore.enabled(this)) cancelChecks(this);
    updateStatus();
  }

  private void refreshAssistantNow() {
    if (!AssistantRefresh.enabled(this)) {
      Toast.makeText(this, "Hãy bật làm mới Google trước.", Toast.LENGTH_LONG).show();
      return;
    }
    new Thread(() -> {
      boolean ok = AssistantRefresh.refresh(this);
      runOnUiThread(() -> {
        updateStatus();
        Toast.makeText(this,
            ok ? "Đã ghi lại cấu hình Google; hãy thử giữ Home."
                : AssistantRefresh.lastResult(this),
            Toast.LENGTH_LONG).show();
      });
    }, "GeminiGoogleV3Test").start();
  }

  private void copyGrant() {
    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText(
        "Grant assistant refresh permission", AssistantRefresh.grantCommand(this)));
    Toast.makeText(this, "Đã sao chép lệnh ADB cấp quyền.", Toast.LENGTH_SHORT).show();
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

    assistantSwitch = new Switch(this);
    assistantSwitch.setText("Tự ngắt → bật Google sau mỗi lượt Home (không Trợ năng)");
    assistantSwitch.setTextSize(16);
    assistantSwitch.setTextColor(Color.WHITE);
    assistantSwitch.setPadding(dp(14), dp(14), dp(14), dp(14));
    assistantSwitch.setBackground(background(0xff223249));
    assistantSwitch.setChecked(AssistantRefresh.enabled(this));
    LinearLayout.LayoutParams assistantParams = new LinearLayout.LayoutParams(-1, -2);
    assistantParams.topMargin = dp(12);
    content.addView(assistantSwitch, assistantParams);
    assistantSwitch.setOnCheckedChangeListener((view, checked) -> {
      if (AssistantRefresh.enabled(this) != checked) enableAssistant(checked);
    });

    status = text("", 14, 0xffdce6f6);
    status.setPadding(dp(14), dp(17), dp(14), dp(17));
    LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
    statusParams.topMargin = dp(14);
    status.setBackground(background(0xff223249));
    content.addView(status, statusParams);

    addButton("Khôi phục Home → làm mới Google ngay", 0xff2563c6, this::checkNow);
    addButton("Thử ngắt → bật Google theo cách v3", 0xff2563c6, this::refreshAssistantNow);
    addButton("Sao chép lệnh cấp WRITE_SECURE_SETTINGS", 0xff32546b, this::copyGrant);
    addButton("Cấp quyền sửa cài đặt", 0xff32546b, () -> HomeRestore.openPermissionScreen(this));
    addButton("Sao chép lệnh ADB dự phòng", 0xff324157, this::copyAdb);

    TextView note = text(
        "BẢN THỬ NGHIỆM: dùng targetSdk 22, chỉ cài qua ADB. "
        + "Đây là cách chạy không root và không cần Accessibility giống APK v3 bạn đã gửi.\\n\\n"
        + "Sau mỗi lượt khôi phục Home thành công: xóa tạm 2 giá trị trợ lý Google, "
        + "chờ 1,5 giây, ghi lại Google Voice Interaction Service, rồi chờ 1 giây và kiểm tra. "
        + "Chỉ dùng khi bạn bật công tắc và cấp WRITE_SECURE_SETTINGS một lần qua ADB. "
        + "Nếu đang chọn MiCTS, app không ghi đè.\\n\\n"
        + "Các lượt kiểm tra Home chạy ngay và khoảng giây thứ 15, 45, 85 sau boot "
        + "(JobScheduler/HyperOS có thể trì hoãn). "
        + "Thao tác ghi giá trị thành công chưa đảm bảo tương đương chọn None → Google "
        + "bằng giao diện. Hãy bật Tự khởi động và không hạn chế pin.",
        13, 0xffacbdd5);
    note.setPadding(0, dp(20), 0, 0);
    content.addView(note);
    updateStatus();
  }

  @Override protected void onResume() {
    super.onResume();
    if (status != null) {
      // Opening this screen only displays status; it does not run extra cycles.
      updateStatus();
    }
  }
}
