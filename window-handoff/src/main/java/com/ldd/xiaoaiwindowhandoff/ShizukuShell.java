package com.ldd.xiaoaiwindowhandoff;

import android.content.pm.PackageManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import rikka.shizuku.Shizuku;

/**
 * Shell commands through an explicitly user-authorized, ADB-started Shizuku.
 * Never uses root, Accessibility, screen-capture, microphone, or stored credentials.
 * Only constant commands. Shell is UID 2000, not root UID 0.
 */
final class ShizukuShell {
    private ShizukuShell() {}

    static boolean ready() {
        try {
            return Shizuku.pingBinder()
                && Shizuku.getUid() == 2000
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable err) {
            return false;
        }
    }

    static Result run(String... args) {
        if (!ready()) return new Result(-1, "", "Shizuku chưa được cấp quyền hoặc không phải ADB shell.");
        Process proc = null;
        try {
            // Deprecated but still supported by Shizuku API v13; suitable for a fixed command.
            proc = Shizuku.newProcess(args, null, null);
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 500000) break;
                    output.append(line).append('\n');
                }
            }
            if (!proc.waitFor(1800, TimeUnit.MILLISECONDS)) {
                proc.destroy();
                return new Result(-1, "", "Hết thời gian chạy lệnh.");
            }
            return new Result(proc.exitValue(), output.toString(), "");
        } catch (Exception e) {
            return new Result(-1, "", e.getClass().getSimpleName());
        } finally {
            if (proc != null) proc.destroy();
        }
    }

    static Result dumpWindows() {
        return run("/system/bin/dumpsys", "window", "windows");
    }

    static Result closeXiaoAndOpenGemini() {
        // Back is sent first. If it cannot be delivered, do not proceed.
        Result back = run("/system/bin/input", "keyevent", "4");
        if (back.code != 0) return back;
        // Assistant intent uses the user's already-configured default Gemini.
        Result launch = run("/system/bin/am", "start", "-a", "android.intent.action.ASSIST");
        if (launch.code == 0 && !launch.output.contains("Error:")) return launch;
        // Launcher fallback (not voice-session overlay).
        return run("/system/bin/monkey", "-p", "com.google.android.apps.bard", "1");
    }

    static final class Result {
        final int code;
        final String output;
        final String error;
        Result(int code, String output, String error) {
            this.code = code; this.output = output; this.error = error;
        }
    }
}
