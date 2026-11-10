package com.ldd.xiaoaiwindowhandoff;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView status;
    private LinearLayout layout;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int px = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(px, px, px, px);
        scroll.addView(layout);
        addText("Xiao AI Window Handoff", 24);
        addText("\nThử nghiệm không root / không Trợ năng / không micro.\n" +
            "Khi chạy, một dịch vụ foreground đọc trạng thái cửa sổ WindowManager " +
            "(cần quyền DUMP cấp qua ADB) để phát hiện bảng Xiao AI. " +
            "Đây không phải chức năng đã được chứng nhận trên HyperOS 3.\n", 16);
        addText("Cấp một lần trên máy tính:\nadb shell pm grant " +
            "com.ldd.xiaoaiwindowhandoff android.permission.DUMP\n", 15);
        Button start = new Button(this);
        start.setText("Bật theo dõi Xiao AI");
        start.setOnClickListener(v -> {
            if (checkSelfPermission("android.permission.DUMP") != PackageManager.PERMISSION_GRANTED) {
                update("Chưa có quyền DUMP. Cần cấp qua ADB trước.");
                return;
            }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9);
            }
            try {
                startForegroundService(new Intent(this, WindowWatchService.class));
                update("Đã yêu cầu khởi động dịch vụ. Xem thông báo/trạng thái.");
            } catch (Exception e) {
                update("Không bật được dịch vụ: " + e.getClass().getSimpleName());
            }
        });
        layout.addView(start);
        Button stop = new Button(this);
        stop.setText("Dừng theo dõi");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, WindowWatchService.class));
            update("Đã yêu cầu dừng theo dõi.");
        });
        layout.addView(stop);
        Button test = new Button(this);
        test.setText("Thử mở Gemini trực tiếp");
        test.setOnClickListener(v -> {
            boolean ok = WindowWatchService.launchGemini(this);
            update(ok ? "Đã gửi yêu cầu mở Gemini." : "Không mở được Gemini.");
        });
        layout.addView(test);
        Button refresh = new Button(this);
        refresh.setText("Xem trạng thái");
        refresh.setOnClickListener(v -> refresh());
        layout.addView(refresh);
        addText("\nChú ý: ứng dụng không đọc nội dung màn hình; chỉ kiểm tra " +
            "thông tin cửa sổ qua dumpsys. DUMP có thể truy xuất dữ liệu hệ thống nhạy cảm, " +
            "nên chỉ dùng mã nguồn tin cậy. Máy đang khóa sẽ không tự mở Gemini. " +
            "Nếu Android chặn mở app từ nền, thông báo sẽ cho phép bạn chạm để mở. " +
            "Không tự force-stop Xiao AI.\n", 14);
        status = addText("", 15);
        setContentView(scroll);
        refresh();
    }

    private TextView addText(String value, int sp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        layout.addView(view);
        return view;
    }

    private void update(String note) {
        getSharedPreferences("state", MODE_PRIVATE).edit().putString("ui", note).apply();
        refresh();
    }

    private void refresh() {
        if (status == null) return;
        String granted = checkSelfPermission("android.permission.DUMP") ==
            PackageManager.PERMISSION_GRANTED ? "đã cấp" : "chưa cấp";
        String recent = getSharedPreferences("state", MODE_PRIVATE)
            .getString("last", "Chưa nhận sự kiện Xiao AI");
        String ui = getSharedPreferences("state", MODE_PRIVATE)
            .getString("ui", "");
        status.setText("Quyền DUMP: " + granted + "\nTrạng thái: " + recent +
            (ui.isEmpty() ? "" : "\n" + ui));
    }
}
