package com.ldd.xiaoaiwindowhandoff;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Opt-in foreground service. No accessibility, mic, screenshot, Shizuku or root.
 * It checks Android's WindowManager via explicitly authorized ADB Shizuku.
 *
 * IMPORTANT: A Xiaomi voice bubble may not be represented as a separate
 * WindowManager window. This path cannot be guaranteed on HyperOS 3.
 */
public final class WindowWatchService extends Service {

    private static final String CHANNEL = "handoff";
    private static final String GEMINI = "com.google.android.apps.bard";
    private static final String[] XIAO_PACKAGES = {
        "com.miui.voiceassist", "com.xiaomi.aiasst",
        "com.miui.aiasst", "com.miui.voicetrigger"
    };
    private volatile boolean running;
    private Thread watchThread;
    private long lastLaunch;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
            CHANNEL, "Xiao AI Window Handoff", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Đang theo dõi bảng Xiao AI")
            .setContentText("Không dùng Trợ năng • Chạm để mở ứng dụng")
            .setContentIntent(appPendingIntent())
            .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(3, n);
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (!ShizukuShell.ready()) {
            record("Shizuku chưa chạy / chưa cấp quyền ADB; theo dõi đã dừng.");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (watchThread == null || !watchThread.isAlive()) {
            running = true;
            watchThread = new Thread(this::watchLoop, "xiaoai-window-watch");
            watchThread.setDaemon(true);
            watchThread.start();
        }
        return START_NOT_STICKY; // Don't resurrect without user intent.
    }

    private void watchLoop() {
        boolean prior = false;
        record("Đang theo dõi trạng thái cửa sổ.");
        while (running) {
            try {
                PowerManager power = getSystemService(PowerManager.class);
                if (power != null && !power.isInteractive()) {
                    prior = false;
                    Thread.sleep(3500); // Do not scan while screen is off.
                    continue;
                }
                if (!ShizukuShell.ready()) {
                    record("Mất Shizuku: dừng theo dõi để bảo vệ máy.");
                    stopSelf();
                    break;
                }
                ShizukuShell.Result dumpResult = ShizukuShell.dumpWindows();
                String dump = dumpResult.code == 0 ? dumpResult.output : null;
                if (dump == null) {
                    record("Không đọc được cửa sổ: HyperOS có thể chặn dumpsys.");
                    Thread.sleep(6000);
                    continue;
                }
                boolean present = isXiaoAiVoiceWindowVisible(dump);
                if (present && !prior && SystemClock.elapsedRealtime() - lastLaunch > 6500) {
                    lastLaunch = SystemClock.elapsedRealtime();
                    KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                    if (km != null && km.isKeyguardLocked()) {
                        record("Xiao AI hiện khi đang khóa: không gửi Back.");
                    } else {
                        ShizukuShell.Result handoff = ShizukuShell.closeXiaoAndOpenGemini();
                        if (handoff.code == 0) record("Đã gửi Back → yêu cầu mở Gemini qua Shizuku.");
                        else record("Lỗi gửi Back/mở Gemini: " + handoff.error);
                        showActionNotification();
                    }
                }
                prior = present;
                Thread.sleep(800);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                record("Lỗi theo dõi: " + ex.getClass().getSimpleName());
                try { Thread.sleep(2500); } catch (InterruptedException stop) { break; }
            }
        }
    }

    private static boolean isXiaoAiVoiceWindowVisible(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        String[] windows = raw.split("(?=Window #[0-9]+ )");
        for (String window : windows) {
            String lower = window.toLowerCase(Locale.ROOT);
            boolean candidate = false;
            for (String pkg : XIAO_PACKAGES) {
                if (lower.contains(pkg)) { candidate = true; break; }
            }
            if (!candidate) continue;
            // Exclude typical dumps with hidden windows.
            if (lower.contains("mviewvisibility=0x8") ||
                lower.contains("isvisible=false") ||
                lower.contains("mhasSurface=false".toLowerCase(Locale.ROOT)))
                continue;
            if (lower.contains("isvisible=true") || lower.contains("isonscreen=true")
                || lower.contains("mviewvisibility=0x0") ||
                lower.contains("mhassurface=true")) return true;
        }
        return false;
    }

    static boolean launchGemini(Context context) {
        KeyguardManager keyguard = (KeyguardManager)
            context.getSystemService(KEYGUARD_SERVICE);
        if (keyguard != null && keyguard.isKeyguardLocked()) return false;
        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(GEMINI);
            if (launch == null) return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(launch);
            return true; // Request accepted; target visibility is not confirmed.
        } catch (Exception e) {
            return false;
        }
    }

    private PendingIntent appPendingIntent() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 10, i,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void showActionNotification() {
        Intent target = getPackageManager().getLaunchIntentForPackage(GEMINI);
        if (target == null) return;
        PendingIntent pending = PendingIntent.getActivity(this, 11, target,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Đã nhận tín hiệu Xiao AI")
            .setContentText("Nếu Gemini chưa mở, chạm để mở")
            .setContentIntent(pending)
            .setOngoing(false).build();
        getSystemService(NotificationManager.class).notify(4, n);
    }

    private void record(String status) {
        getSharedPreferences("state", MODE_PRIVATE).edit()
            .putString("last", status).apply();
    }

    @Override public void onDestroy() {
        running = false;
        if (watchThread != null) watchThread.interrupt();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
