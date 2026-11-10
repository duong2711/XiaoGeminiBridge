package com.ldd.xiaoaiwindowhandoff;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private TextView status;
    private LinearLayout layout;

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
        (requestCode, grantResult) -> runOnUiThread(() -> {
            refresh();
            if (grantResult == PackageManager.PERMISSION_GRANTED)
                note("Đã cấp quyền Shizuku. Bấm Bật theo dõi Xiao AI.");
            else note("Shizuku bị từ chối. Không theo dõi.");
        });

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try { Shizuku.addRequestPermissionResultListener(permissionListener); }
        catch (Throwable ignored) {}
        ScrollView scroll = new ScrollView(this);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int px = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(px, px, px, px);
        scroll.addView(layout);
        addText("Xiao AI → Gemini · Không Trợ năng", 24);
        addText("\nKhông root · Không Accessibility · Không ghi âm/chụp màn hình.\n" +
            "Bản thử nghiệm theo dõi cửa sổ bằng Shizuku (ADB) và gửi Back ngay " +
            "khi phát hiện bảng Xiao AI, rồi gọi Gemini.\n" +
            "Cần cài Shizuku và khởi động bằng Wireless debugging (không root). " +
            "Sau khi khởi động lại máy, phải bật lại Shizuku.\n", 16);

        Button permission = new Button(this);
        permission.setText("Cấp quyền Shizuku cho cầu nối");
        permission.setOnClickListener(v -> {
            try {
                if (!Shizuku.pingBinder()) { note("Shizuku chưa chạy. Mở ứng dụng Shizuku và khởi động bằng ADB."); return; }
                if (Shizuku.getUid() != 2000) { note("Chỉ hỗ trợ Shizuku chạy bằng ADB không root."); return; }
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
                    Shizuku.requestPermission(1001);
                else note("Đã có quyền Shizuku.");
            } catch (Throwable ex) { note("Không kết nối Shizuku: " + ex.getClass().getSimpleName()); }
        });
        layout.addView(permission);

        Button start = new Button(this);
        start.setText("Bật theo dõi Xiao AI");
        start.setOnClickListener(v -> {
            if (!ShizukuShell.ready()) { note("Cần Shizuku (ADB) và quyền đã cấp trước."); return; }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 9);
            try {
                startForegroundService(new Intent(this, WindowWatchService.class));
                note("Đã yêu cầu bắt đầu theo dõi. Có thể dừng bất cứ lúc nào.");
            } catch (Exception ex) { note("Không thể khởi động: " + ex.getClass().getSimpleName()); }
        });
        layout.addView(start);

        Button stop = new Button(this);
        stop.setText("Dừng theo dõi ngay");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, WindowWatchService.class));
            note("Đã gửi lệnh dừng theo dõi.");
        });
        layout.addView(stop);

        Button test = new Button(this);
        test.setText("Thử đóng bảng hiện tại rồi mở Gemini");
        test.setOnClickListener(v -> new Thread(() -> {
            ShizukuShell.Result result = ShizukuShell.closeXiaoAndOpenGemini();
            runOnUiThread(() -> note(result.code == 0
                ? "Đã gửi Back rồi gọi Gemini (không đảm bảo Gemini hiển thị)."
                : "Không thể chuyển: " + result.error + " " + result.output));
        }, "test-handoff").start());
        layout.addView(test);

        Button refresh = new Button(this);
        refresh.setText("Xem trạng thái");
        refresh.setOnClickListener(v -> refresh());
        layout.addView(refresh);

        addText("\nCẢNH BÁO: Chỉ bấm nút thử khi đang mở Xiao AI; lệnh Back " +
            "sẽ đóng giao diện đang ở phía trước. Quyền Shizuku có khả năng điều khiển " +
            "thiết bị bằng ADB. Không dùng khi mở app ngân hàng/VNeID. " +
            "Chưa chứng minh hoạt động ổn định trên HyperOS 3; " +
            "Android có thể không hiển thị cửa sổ Xiao AI trong dumpsys.\n", 14);
        status = addText("", 15);
        setContentView(scroll);
        refresh();
    }

    private TextView addText(String value, int sp) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        layout.addView(v);
        return v;
    }
    private void note(String s) {
        getSharedPreferences("state", MODE_PRIVATE).edit().putString("ui", s).apply();
        refresh();
    }
    private void refresh() {
        if (status == null) return;
        String shizuku = ShizukuShell.ready() ? "ADB đã cấp quyền"
            : "chưa chạy hoặc chưa có quyền";
        String last = getSharedPreferences("state", MODE_PRIVATE)
            .getString("last", "Chưa phát hiện Xiao AI");
        String ui = getSharedPreferences("state", MODE_PRIVATE)
            .getString("ui", "");
        status.setText("Shizuku: " + shizuku + "\nTrạng thái: " + last +
            (ui.isEmpty() ? "" : "\n" + ui));
    }
    @Override protected void onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(permissionListener); }
        catch (Throwable ignored) {}
        super.onDestroy();
    }
}
