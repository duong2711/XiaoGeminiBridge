package com.ldd.geminirestore;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

/**
 * Experimental: refresh the user-selected Google assistant after reboot.
 *
 * No root or Accessibility. Requires one-time, user-approved ADB grant of
 * WRITE_SECURE_SETTINGS. Saves and restores the *actual* components selected
 * by the user; it never hardcodes a Google service class and never selects
 * Google when another assistant (e.g. MiCTS) was chosen.
 *
 * This writes secure settings (not the RoleManager role). It may not reproduce
 * the Settings UI's full role-change side effects on every HyperOS build.
 */
public final class AssistantRefresh {
  private static final String PREFS = "assistant_refresh";
  private static final String ENABLED = "enabled";
  private static final String PENDING = "pending";
  private static final String RESULT = "result";
  private static final String ASSISTANT = "assistant";
  private static final String VOICE = "voice_interaction_service";
  private static final String GOOGLE = "com.google.android.googlequicksearchbox";

  private AssistantRefresh() {}

  private static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  public static boolean enabled(Context c) {
    return prefs(c).getBoolean(ENABLED, false);
  }

  public static void setEnabled(Context c, boolean enabled) {
    prefs(c).edit().putBoolean(ENABLED, enabled)
        .putBoolean(PENDING, enabled).apply();
  }

  public static void onBoot(Context c) {
    if (enabled(c)) {
      prefs(c).edit().putBoolean(PENDING, true)
          .putString(RESULT, "Đang chờ làm mới Google sau khi khởi động lại.")
          .apply();
    }
  }

  public static boolean pending(Context c) {
    return enabled(c) && prefs(c).getBoolean(PENDING, false);
  }

  public static boolean permitted(Context c) {
    return Build.VERSION.SDK_INT >= 23
        && c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
            == PackageManager.PERMISSION_GRANTED;
  }

  private static void result(Context c, String message) {
    prefs(c).edit().putString(RESULT, message)
        .putLong("last_checked", System.currentTimeMillis()).apply();
  }

  public static String lastResult(Context c) {
    return prefs(c).getString(RESULT, "Chưa kiểm tra trợ lý Google");
  }

  public static String grantCommand(Context c) {
    return "adb shell pm grant " + c.getPackageName()
        + " android.permission.WRITE_SECURE_SETTINGS";
  }

  private static boolean google(String component) {
    return component != null && component.startsWith(GOOGLE + "/");
  }

  private static boolean same(String a, String b) {
    return a == null ? b == null : a.equals(b);
  }

  /** Called only by an explicit in-app user action or boot retry. */
  public static synchronized boolean refresh(Context c) {
    if (!enabled(c)) return false;
    if (!permitted(c)) {
      result(c, "Chưa cấp WRITE_SECURE_SETTINGS. Cần cấp một lần bằng ADB.");
      return false;
    }

    ContentResolver resolver = c.getContentResolver();
    final String originalAssistant;
    final String originalVoice;
    try {
      originalAssistant = Settings.Secure.getString(resolver, ASSISTANT);
      originalVoice = Settings.Secure.getString(resolver, VOICE);
    } catch (Exception ex) {
      result(c, "Không đọc được trợ lý mặc định: " + ex.getClass().getSimpleName());
      return false;
    }

    if (!google(originalAssistant) && !google(originalVoice)) {
      result(c, "Trợ lý mặc định không phải Google. Không thay đổi MiCTS/None."
          + " Hãy chọn Google bằng Settings trước.");
      return false;
    }

    boolean success = false;
    String failure = null;
    try {
      // Force a change, so system observers can rebind the interaction
      // service. Restore the exact original values below without hardcoding.
      if (!Settings.Secure.putString(resolver, ASSISTANT, "")
          || !Settings.Secure.putString(resolver, VOICE, "")) {
        throw new IllegalStateException("HyperOS không cho ghi secure settings");
      }
      try {
        Thread.sleep(450L);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    } catch (Exception ex) {
      failure = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
    } finally {
      // Never leave the device at None if a write/sleep failed.
      try {
        Settings.Secure.putString(resolver, VOICE, originalVoice);
        Settings.Secure.putString(resolver, ASSISTANT, originalAssistant);
        String restoredAssistant = Settings.Secure.getString(resolver, ASSISTANT);
        String restoredVoice = Settings.Secure.getString(resolver, VOICE);
        success = failure == null && same(originalAssistant, restoredAssistant)
            && same(originalVoice, restoredVoice);
        if (failure == null && !success) failure = "Không khôi phục đúng giá trị ban đầu";
      } catch (Exception ex) {
        failure = "KHẨN: không ghi lại được trợ lý gốc: "
            + ex.getClass().getSimpleName() + " "
            + String.valueOf(ex.getMessage());
      }
    }

    if (success) {
      prefs(c).edit().putBoolean(PENDING, false).apply();
      result(c, "Đã làm mới lựa chọn Google trong Secure Settings."
          + " Hãy thử giữ Home; cần kiểm nghiệm máy thật.");
    } else {
      result(c, "Không làm mới được Google: " + failure);
    }
    return success;
  }
}
