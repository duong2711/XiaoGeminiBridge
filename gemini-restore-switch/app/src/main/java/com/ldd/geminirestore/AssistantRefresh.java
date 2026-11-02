package com.ldd.geminirestore;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.provider.Settings;

/**
 * No Accessibility, no root. Experimental assistant rebind based on the
 * user's Gemini_Restore_Auto_V3.apk sequence: clear both secure assistant
 * values, wait 1500ms, set explicit GSA voice service, wait 1000ms, verify.
 *
 * Only runs if the user enables it and grants WRITE_SECURE_SETTINGS via ADB.
 * Android role/UI side effects are still not guaranteed on all HyperOS ROMs.
 */
public final class AssistantRefresh {
  private static final String PREFS = "assistant_refresh";
  private static final String ENABLED = "enabled";
  private static final String ARMED = "google_selected_by_user";
  private static final String RESULT = "result";
  private static final String ASSISTANT = "assistant";
  private static final String VOICE = "voice_interaction_service";
  private static final String GOOGLE_PKG = "com.google.android.googlequicksearchbox";
  private static final String GOOGLE_SERVICE =
      GOOGLE_PKG + "/com.google.android.voiceinteraction.GsaVoiceInteractionService";

  private AssistantRefresh() {}

  private static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  public static boolean enabled(Context c) {
    return prefs(c).getBoolean(ENABLED, false);
  }

  public static void setEnabled(Context c, boolean on) {
    SharedPreferences.Editor edit = prefs(c).edit().putBoolean(ENABLED, on);
    if (on) {
      String a = get(c, ASSISTANT);
      String v = get(c, VOICE);
      if (isGoogle(a) || isGoogle(v)) edit.putBoolean(ARMED, true);
    } else {
      edit.putBoolean(ARMED, false);
    }
    edit.apply();
  }

  public static boolean permitted(Context c) {
    return Build.VERSION.SDK_INT >= 23
        && c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
            == PackageManager.PERMISSION_GRANTED;
  }

  public static String grantCommand(Context c) {
    return "adb shell pm grant " + c.getPackageName()
        + " android.permission.WRITE_SECURE_SETTINGS";
  }

  private static boolean isGoogle(String value) {
    return value != null && value.startsWith(GOOGLE_PKG + "/");
  }

  private static boolean blank(String value) {
    return value == null || value.length() == 0 || "null".equals(value);
  }

  private static String get(Context c, String key) {
    try {
      return Settings.Secure.getString(c.getContentResolver(), key);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static void report(Context c, String message) {
    prefs(c).edit().putString(RESULT, message)
        .putLong("last_checked", System.currentTimeMillis()).apply();
  }

  public static String lastResult(Context c) {
    return prefs(c).getString(RESULT, "Chưa thử làm mới Google.");
  }

  private static void write(ContentResolver resolver, String key, String value) {
    if (!Settings.Secure.putString(resolver, key, value)) {
      throw new IllegalStateException("Không ghi được " + key);
    }
  }

  private static boolean restore(ContentResolver resolver, String assistant, String voice) {
    boolean ok = true;
    try { write(resolver, VOICE, voice); } catch (Exception ignored) { ok = false; }
    try { write(resolver, ASSISTANT, assistant); } catch (Exception ignored) { ok = false; }
    return ok;
  }

  /** Every Home restoration may call this; no one-time pending flag. */
  public static synchronized boolean refresh(Context c) {
    if (!enabled(c)) return false;
    if (!permitted(c)) {
      report(c, "Cần cấp WRITE_SECURE_SETTINGS một lần qua ADB. "
          + grantCommand(c));
      return false;
    }

    // V3 verifies the Google VoiceInteractionService exists and is enabled.
    try {
      ComponentName component = ComponentName.unflattenFromString(GOOGLE_SERVICE);
      ServiceInfo service = c.getPackageManager().getServiceInfo(component, 0);
      if (!service.enabled || !service.applicationInfo.enabled) {
        report(c, "Dịch vụ Google đang bị tắt, không thay đổi trợ lý.");
        return false;
      }
    } catch (Exception ex) {
      report(c, "Không thấy dịch vụ Google Voice Interaction ("
          + ex.getClass().getSimpleName() + ").");
      return false;
    }

    ContentResolver resolver = c.getContentResolver();
    final String originalAssistant = get(c, ASSISTANT);
    final String originalVoice = get(c, VOICE);
    boolean activeGoogle = isGoogle(originalAssistant) || isGoogle(originalVoice);
    boolean alreadyArmed = prefs(c).getBoolean(ARMED, false);

    // Never override a different assistant such as MiCTS. Empty values are
    // recoverable only if the user had previously enabled Google in this app.
    boolean anotherAssistant = (!blank(originalAssistant) && !isGoogle(originalAssistant))
        || (!blank(originalVoice) && !isGoogle(originalVoice));
    if (anotherAssistant) {
      report(c, "Đang dùng trợ lý khác Google; không ghi đè MiCTS.");
      return false;
    }
    if (!activeGoogle && !alreadyArmed) {
      report(c, "Chưa ghi nhận Google được chọn. Chọn Google trong Settings "
          + "trước rồi bật lại chế độ tự sửa.");
      return false;
    }
    if (activeGoogle && !alreadyArmed) prefs(c).edit().putBoolean(ARMED, true).apply();

    boolean changed = false;
    try {
      // Sequence recovered from the user-supplied v3 APK. No UI automation.
      write(resolver, ASSISTANT, "");
      changed = true;
      write(resolver, VOICE, "");
      Thread.sleep(1500L);

      // A different assistant selected during the delay wins.
      String duringA = get(c, ASSISTANT);
      String duringV = get(c, VOICE);
      boolean changedToAnother = (!blank(duringA) && !isGoogle(duringA))
          || (!blank(duringV) && !isGoogle(duringV));
      if (changedToAnother) {
        report(c, "Hệ thống đã chọn trợ lý khác khi đang chờ. "
            + "Dừng thao tác để không thay đổi lựa chọn đó.");
        return false;
      }

      write(resolver, ASSISTANT, GOOGLE_SERVICE);
      write(resolver, VOICE, GOOGLE_SERVICE);
      Thread.sleep(1000L);
      boolean verified = GOOGLE_SERVICE.equals(get(c, ASSISTANT))
          && GOOGLE_SERVICE.equals(get(c, VOICE));
      if (!verified) {
        report(c, "Đã thực hiện chu trình v3 nhưng Android chưa giữ đúng "
            + "cấu hình Google. Cần kiểm tra Digital assistant app.");
        return false;
      }
      report(c, "Đã ngắt 1,5 giây → đặt lại dịch vụ Google → chờ 1 giây "
          + "và đọc lại đúng hai giá trị. Thử giữ Home để xác nhận.");
      return true;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      report(c, "Quá trình làm mới Google bị ngắt.");
    } catch (Exception ex) {
      report(c, "Không làm mới được Google: "
          + ex.getClass().getSimpleName() + " " + String.valueOf(ex.getMessage()));
    } finally {
      // On success keep explicit Google values. On failure, restore previous
      // components unless the user/system selected a different assistant.
      String a = get(c, ASSISTANT);
      String v = get(c, VOICE);
      boolean good = GOOGLE_SERVICE.equals(a) && GOOGLE_SERVICE.equals(v);
      boolean other = (!blank(a) && !isGoogle(a)) || (!blank(v) && !isGoogle(v));
      if (changed && !good && !other) {
        if (!restore(resolver, originalAssistant, originalVoice)) {
          report(c, "CẢNH BÁO: không phục hồi đủ thiết lập trợ lý cũ. "
              + "Vào Default digital assistant app chọn Google.");
        }
      }
    }
    return false;
  }
}
