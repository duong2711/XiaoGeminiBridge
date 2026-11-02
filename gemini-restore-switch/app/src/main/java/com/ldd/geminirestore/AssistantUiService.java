package com.ldd.geminirestore;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;

/**
 * Opt-in experiment for Xiaomi/HyperOS. Works through the actual Settings UI,
 * which triggers the assistant role/controller hooks that writing Secure settings
 * alone does not. Runs ONLY when the user enabled it and Accessibility.
 * No global coordinate taps, root, screenshot or data transfer.
 *
 * This is best effort. Settings navigation strings differ among MIUI/HyperOS ROMs.
 */
public final class AssistantUiService extends AccessibilityService {
  private static final String PREF = "assistant_ui";
  private static final long MAX_MS = 40000L;
  private static final String GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox";
  private static volatile AssistantUiService connected;
  private final Handler handler = new Handler(Looper.getMainLooper());

  private boolean running = false;
  private boolean enteringSettings = false;
  private int state = 0; // 1 find picker, 2 pick None, 3 pick Google, 4 finalize
  private long startedAt = 0L;
  private int actions = 0;
  private String before = "";

  public static boolean connected() { return connected != null; }
  public static boolean enabled(Context c) { return AssistantRefresh.enabled(c); }
  private static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
  }
  private static void report(Context c, String s) {
    prefs(c).edit().putString("result", s)
        .putLong("last_checked", System.currentTimeMillis()).apply();
  }
  public static String lastResult(Context c) {
    return prefs(c).getString("result", "Chưa thực hiện thao tác giao diện.");
  }
  private static boolean locked(Context c) {
    KeyguardManager kg = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
    return kg != null && kg.isKeyguardLocked();
  }

  public static void request(Context c) {
    if (!enabled(c)) return;
    // Only refresh an already-selected Google assistant. Do not change MiCTS.
    String assistant = Settings.Secure.getString(c.getContentResolver(), "assistant");
    String voice = Settings.Secure.getString(c.getContentResolver(), "voice_interaction_service");
    boolean google = (assistant != null && assistant.startsWith(GOOGLE_PACKAGE + "/"))
        || (voice != null && voice.startsWith(GOOGLE_PACKAGE + "/"));
    if (!google) {
      report(c, "Không thấy Google trong cấu hình trợ lý. Bỏ qua để bảo vệ MiCTS/None.");
      return;
    }
    if (locked(c)) {
      prefs(c).edit().putBoolean("pending_unlock", true).apply();
      report(c, "Đang chờ bạn mở khóa để thao tác Digital assistant app.");
      return;
    }
    AssistantUiService s = connected;
    if (s == null) {
      report(c, "Accessibility chưa hoạt động. Bật Gemini Home Restore trong Trợ năng.");
      return;
    }
    s.handler.post(s::begin);
  }

  public static void afterUnlock(Context c) {
    if (!enabled(c) || locked(c)) return;
    if (!prefs(c).getBoolean("pending_unlock", false)) return;
    prefs(c).edit().putBoolean("pending_unlock", false).apply();
    request(c);
  }

  public static void openPermission(Context c) {
    Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    c.startActivity(i);
  }

  @Override protected void onServiceConnected() {
    connected = this;
    report(this, "Accessibility đã kết nối; chờ lượt khôi phục Home.");
    afterUnlock(this);
  }

  @Override public void onDestroy() {
    if (connected == this) connected = null;
    super.onDestroy();
  }

  @Override public void onInterrupt() {
    if (running) stop("Accessibility bị ngắt trước khi hoàn tất. Kiểm tra trợ lý mặc định.");
  }

  private void begin() {
    if (!enabled(this) || locked(this)) {
      report(this, "Chưa sẵn sàng: máy bị khóa hoặc chế độ tự động đã tắt.");
      return;
    }
    if (running) {
      report(this, "Đang thực hiện một lượt None → Google, không chạy trùng.");
      return;
    }
    running = true;
    enteringSettings = true;
    state = 1;
    startedAt = SystemClock.uptimeMillis();
    actions = 0;
    before = "";
    try {
      AccessibilityNodeInfo root = getRootInActiveWindow();
      if (root != null && root.getPackageName() != null) {
        before = root.getPackageName().toString();
      }
      Intent i = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(i);
      report(this, "Đã mở Default apps; đang tìm Digital assistant app.");
      handler.postDelayed(this::step, 800L);
      handler.postDelayed(() -> {
        if (running && SystemClock.uptimeMillis() - startedAt >= MAX_MS) {
          stop("Hết thời gian thao tác; kiểm tra Google trong Digital assistant app.");
        }
      }, MAX_MS + 200L);
    } catch (Exception ex) {
      stop("HyperOS chặn mở Default apps: " + ex.getClass().getSimpleName());
    }
  }

  @Override public void onAccessibilityEvent(AccessibilityEvent event) {
    if (!running) {
      if (prefs(this).getBoolean("pending_unlock", false) && !locked(this)) {
        afterUnlock(this);
      }
      return;
    }
    if (event == null) return;
    int type = event.getEventType();
    if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
      handler.removeCallbacks(nextStep);
      handler.postDelayed(nextStep, 300L);
    }
  }

  private final Runnable nextStep = this::step;
  private static boolean isSettings(String pkg) {
    return pkg != null && (pkg.equals("com.android.settings")
        || pkg.equals("com.miui.securitycenter")
        || pkg.contains("permissioncontroller"));
  }

  private void step() {
    if (!running) return;
    if (++actions > 70 || SystemClock.uptimeMillis() - startedAt > MAX_MS) {
      stop("Không xác định được giao diện mặc định của HyperOS. Không thao tác tiếp.");
      return;
    }
    AccessibilityNodeInfo root = getRootInActiveWindow();
    if (root == null || root.getPackageName() == null) return;
    if (!isSettings(root.getPackageName().toString())) return;
    enteringSettings = false;

    if (state == 1 || state == 2) {
      // Do not select None on an arbitrary screen. Require Google and None
      // to be present at the same time in the picker UI.
      AccessibilityNodeInfo none = find(root, "none", "không có",
          "không chọn", "无", "无默认应用", "不使用");
      AccessibilityNodeInfo google = find(root, "google");
      if (none != null && google != null && looksLikePicker(root)) {
        state = 3;
        if (click(none)) {
          report(this, "Đã chọn None bằng giao diện, đang chọn lại Google.");
          handler.postDelayed(this::step, 750L);
        } else {
          stop("Không thể nhấn None trong màn hình trợ lý.");
        }
        return;
      }
      AccessibilityNodeInfo menu = find(root, "default digital assistant app",
          "digital assistant app", "assistant app", "ứng dụng trợ lý kỹ thuật số",
          "trợ lý kỹ thuật số", "ứng dụng trợ lý", "trợ lý mặc định",
          "默认数字助理应用", "数字助理应用", "数字助手", "assistant");
      if (menu != null && click(menu)) {
        state = 2;
        handler.postDelayed(this::step, 750L);
      }
      return;
    }

    if (state == 3) {
      // Some firmware shows a confirmation dialog for changing the default.
      // Handle only a recognized confirmation screen, never random Settings.
      if (looksLikeConfirm(root)) {
        AccessibilityNodeInfo ok = find(root, "ok", "confirm", "change",
            "đồng ý", "xác nhận", "thay đổi", "确定", "确认");
        if (ok != null && click(ok)) {
          handler.postDelayed(this::step, 700L);
          return;
        }
      }
      AccessibilityNodeInfo google = find(root, "google");
      if (google != null && looksLikePicker(root)) {
        if (click(google)) {
          state = 4;
          report(this, "Đã chọn lại Google trong Default digital assistant app.");
          handler.postDelayed(this::step, 850L);
        } else {
          stop("Không thể nhấn lại Google. Hãy kiểm tra trợ lý.");
        }
        return;
      }
      // On some ROMs picking None returns to the Default apps parent.
      AccessibilityNodeInfo menu = find(root, "default digital assistant app",
          "digital assistant app", "assistant app", "ứng dụng trợ lý kỹ thuật số",
          "trợ lý kỹ thuật số", "ứng dụng trợ lý", "trợ lý mặc định",
          "默认数字助理应用", "数字助理应用", "数字助手");
      if (menu != null && click(menu)) handler.postDelayed(this::step, 650L);
      return;
    }

    if (state == 4) {
      // Some builds show a confirmation dialog before applying Google.
      AccessibilityNodeInfo ok = find(root, "ok", "confirm", "change",
          "đồng ý", "xác nhận", "thay đổi", "确定", "确认");
      if (ok != null && looksLikeConfirm(root)) {
        click(ok);
      }
      stop("Đã thử thực hiện None → Google bằng giao diện thật."
          + " Giữ Home để kiểm tra; HyperOS có thể cần xác nhận thêm.");
    }
  }

  private boolean looksLikePicker(AccessibilityNodeInfo root) {
    return find(root, "google") != null && find(root, "none", "không có",
        "không chọn", "无", "无默认应用", "不使用") != null;
  }

  private boolean looksLikeConfirm(AccessibilityNodeInfo root) {
    return find(root, "change default", "set as default", "thay đổi ứng dụng",
        "xác nhận", "confirm", "继续", "设为默认") != null;
  }

  private AccessibilityNodeInfo find(AccessibilityNodeInfo root, String... names) {
    if (root == null) return null;
    CharSequence text = root.getText();
    CharSequence desc = root.getContentDescription();
    if (matches(text, names) || matches(desc, names)) return root;
    for (int i = 0; i < root.getChildCount(); i++) {
      AccessibilityNodeInfo node = find(root.getChild(i), names);
      if (node != null) return node;
    }
    return null;
  }

  private boolean matches(CharSequence value, String... names) {
    if (value == null) return false;
    String text = value.toString().trim().toLowerCase(Locale.ROOT);
    for (String name : names) {
      if (text.equals(name) || (name.length() > 8
          && text.startsWith(name + " "))) return true;
    }
    return false;
  }

  private boolean click(AccessibilityNodeInfo node) {
    for (int i = 0; node != null && i < 7; i++, node = node.getParent()) {
      if (node.isClickable() && node.isEnabled()
          && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
    }
    return false;
  }

  private void stop(String message) {
    boolean hadNone = state >= 3;
    running = false;
    state = 0;
    handler.removeCallbacks(nextStep);
    report(this, message);
    // Do not issue global HOME/BACK actions: preserve the system page if the
    // operation stopped after None, so the user can restore Google directly.
  }
}
