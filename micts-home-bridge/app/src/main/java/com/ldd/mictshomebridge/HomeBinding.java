package com.ldd.mictshomebridge;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/** Xiaomi three-button Home shortcut; private OEM setting, best-effort only. */
public final class HomeBinding {
  private static final String KEY = "long_press_home_key";
  private static final String VALUE = "launch_google_search";
  private static final String PREFS = "home_config";

  private HomeBinding() {}

  public static boolean enabled(Context c) {
    return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean("auto_restore", true);
  }
  public static void setEnabled(Context c, boolean enabled) {
    c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean("auto_restore", enabled).apply();
  }
  public static String current(Context c) {
    try { return Settings.System.getString(c.getContentResolver(), KEY); }
    catch (Exception e) { return null; }
  }
  public static boolean canWrite(Context c) {
    try { return Settings.System.canWrite(c); }
    catch (Exception e) { return false; }
  }
  private static void setStatus(Context c, String value) {
    c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString("status", value).apply();
  }
  public static String lastStatus(Context c) {
    return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("status", "Chưa kiểm tra Home");
  }
  public static boolean restore(Context c) {
    if (!enabled(c)) return false;
    if (VALUE.equals(current(c))) {
      setStatus(c, "Lệnh Home đang đúng: " + VALUE + ". Không cần ghi lại.");
      return true;
    }
    if (!canWrite(c)) {
      setStatus(c, "Chưa có quyền Sửa cài đặt hệ thống; hãy cấp quyền và thử lại.");
      return false;
    }
    try {
      boolean wrote = Settings.System.putString(c.getContentResolver(), KEY, VALUE);
      boolean verified = wrote && VALUE.equals(current(c));
      setStatus(c, verified ? "Đã khôi phục lệnh Home." :
          "Không ghi được khóa Home; HyperOS có thể chặn ứng dụng thường.");
      return verified;
    } catch (Exception e) {
      setStatus(c, "HyperOS chặn khóa Home: " + e.getClass().getSimpleName());
      return false;
    }
  }
  public static void openWriteSettings(Context c) {
    Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.parse("package:" + c.getPackageName()));
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try { c.startActivity(i); }
    catch (Exception e) {
      Intent other = new Intent(Settings.ACTION_SETTINGS);
      other.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      c.startActivity(other);
    }
  }
  public static String adbCommand() {
    return "adb shell settings put system " + KEY + " " + VALUE;
  }
}
