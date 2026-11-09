package com.ldd.assisthandoff;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Control panel: no recurring task, service or listener. */
public final class MainActivity extends Activity {
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int p = Math.round(20 * getResources().getDisplayMetrics().density);
        panel.setPadding(p, p, p, p);
        scroll.addView(panel);

        TextView heading = new TextView(this);
        heading.setText("Gemini Assist Handoff");
        heading.setTextSize(24);
        panel.addView(heading);

        TextView help = new TextView(this);
        help.setText("\nKhông root · Không Trợ năng · Không dịch vụ nền · Không micro riêng.\n\n"
            + "Ứng dụng chuyển ACTION_ASSIST của Android sang Gemini. "
            + "Nó KHÔNG đọc được biểu tượng nổi Xiao AI. "
            + "Nếu HyperOS gọi Xiao AI trực tiếp thay vì ACTION_ASSIST, "
            + "chỉ nói '小爱同学' sẽ không kích hoạt ứng dụng này.\n");
        help.setTextSize(16);
        panel.addView(help);

        Button test = new Button(this);
        test.setText("Thử mở Gemini");
        test.setOnClickListener(v -> {
            AssistHandoffActivity.launchGemini(this);
            updateStatus();
        });
        panel.addView(test);

        Button settings = new Button(this);
        settings.setText("Cài đặt trợ lý mặc định");
        settings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
            } catch (Exception e) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)); }
                catch (Exception ignored) {
                    Toast.makeText(this, "HyperOS không có màn hình cài đặt này.", Toast.LENGTH_LONG).show();
                }
            }
        });
        panel.addView(settings);

        status = new TextView(this);
        status.setTextSize(15);
        panel.addView(status);
        setContentView(scroll);
        updateStatus();
    }

    @Override protected void onResume() { super.onResume(); if (status != null) updateStatus(); }

    private void updateStatus() {
        String last = getSharedPreferences("handoff", MODE_PRIVATE)
                .getString("last_status", "Chưa thử mở Gemini");
        status.setText("\nTrạng thái: " + last);
    }
}
