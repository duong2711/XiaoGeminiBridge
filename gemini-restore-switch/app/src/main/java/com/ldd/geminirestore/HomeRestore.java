package com.ldd.geminirestore;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;

/** Restore the user-confirmed Xiaomi Home long-press binding. No root/Accessibility. */
public final class HomeRestore {
  private static final String PREFERENCES = "home_restore";
  private static final String ENABLED = "enabled";
  private static final String KEY = "long_press_home_key";
  private static final String VALUE = "launch_google_search";

  private HomeRestore() {}

  private static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
  }

  public static boolean enabled(Context c) {
    return prefs(c).getBoolean(ENABLED, false);
  }

  public static void setEnabled(Context c, boolean enabled) {
    prefs(c).edit().putBoolean(ENABLED, enabled).apply();
  }

  public static boolean hasWritePermission(Context c) {
    try {
      return Settings.System.canWrite(c);
    } catch (Exception ignored) {
      return false;
    }
  }

  public static String current(Context c) {
    try {
      return Settings.System.getString(c.getContentResolver(), KEY);
    } catch (Exception e) {
      return null;
    }
  }

  public static boolean isActive(Context c) {
    return VALUE.equals(current(c));
  }

  public static void openPermissionScreen(Context c) {
    Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
    intent.setData(Uri.parse("package:" + c.getPackageName()));
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      c.startActivity(intent);
    } catch (Exception ignored) {
      Intent settings = new Intent(Settings.ACTION_SETTINGS);
      settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      c.startActivity(settings);
    }
  }

  /** Call on boot and each scheduled boot retry. Always verify the actual value. */
  public static boolean restore(Context c) {
    if (!enabled(c)) return false;
    if (!hasWritePermission(c)) {
      result(c, "Chưa cấp quyền Sửa cài đặt hệ thống.");
      return false;
    }
    try {
      boolean wrote = Settings.System.putString(c.getContentResolver(), KEY, VALUE);
      boolean verified = wrote && VALUE.equals(current(c));
      result(c, verified
          ? "Đã khôi phục Home → Gemini (" + VALUE + ")."
          : "HyperOS không nhận thay đổi. Có thể cần cấu hình qua ADB.");
      return verified;
    } catch (SecurityException ex) {
      result(c, "HyperOS từ chối quyền: " + ex.getClass().getSimpleName());
      return false;
    } catch (Exception ex) {
      result(c, "Không khôi phục Home: " + ex.getClass().getSimpleName());
      return false;
    }
  }

  private static void result(Context c, String message) {
    prefs(c).edit().putString("result", message)
        .putLong("last_checked", System.currentTimeMillis()).apply();
  }

  public static String lastResult(Context c) {
    return prefs(c).getString("result", "Chưa khôi phục");
  }

  public static String adbCommand() {
    return "adb shell settings put system " + KEY + " " + VALUE;
  }
}
