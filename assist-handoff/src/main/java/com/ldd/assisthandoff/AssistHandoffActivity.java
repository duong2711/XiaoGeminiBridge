package com.ldd.assisthandoff;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

/**
 * Event-driven handoff. No foreground/background service, polling, mic, screen
 * capture, accessibility or root. Can only run when OS routes ACTION_ASSIST.
 */
public final class AssistHandoffActivity extends Activity {
    private static final String GEMINI_PACKAGE = "com.google.android.apps.bard";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launchGemini(this);
        finish();
    }

    static boolean launchGemini(Context context) {
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(KEYGUARD_SERVICE);
        if (keyguard != null && keyguard.isKeyguardLocked()) {
            save(context, "Màn hình đang khóa: không tự mở Gemini.");
            return false;
        }
        try {
            PackageManager pm = context.getPackageManager();
            Intent target = pm.getLaunchIntentForPackage(GEMINI_PACKAGE);
            if (target == null) {
                save(context, "Chưa tìm thấy ứng dụng Gemini (" + GEMINI_PACKAGE + ").");
                return false;
            }
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(target);
            save(context, "Đã yêu cầu Android mở Gemini.");
            return true;
        } catch (Exception e) {
            save(context, "Lỗi mở Gemini: " + e.getClass().getSimpleName());
            return false;
        }
    }

    private static void save(Context context, String status) {
        context.getSharedPreferences("handoff", MODE_PRIVATE)
                .edit().putString("last_status", status)
                .putLong("last_time", System.currentTimeMillis())
                .apply();
    }
}
