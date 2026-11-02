package com.ldd.mictshomebridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class SetupActivity extends Activity {
  private TextView status;
  private Switch bootSwitch;
  private LinearLayout content;

  private int dp(int n) {
    return (int)(getResources().getDisplayMetrics().density * n + 0.5f);
  }
  private GradientDrawable bg(int color) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(14));
    return d;
  }
  private TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextColor(color);
    t.setTextSize(size);
    t.setLineSpacing(dp(3), 1f);
    return t;
  }
  private void button(String name, Runnable task) {
    Button b = new Button(this);
    b.setText(name);
    b.setAllCaps(false);
    b.setTextColor(Color.WHITE);
    b.setBackground(bg(0xff2864ae));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(52));
    p.topMargin = dp(11);
    content.addView(b, p);
    b.setOnClickListener(view -> task.run());
  }

  private boolean isAssistant() {
    if (Build.VERSION.SDK_INT < 29) return false;
    try {
      RoleManager rm = (RoleManager)getSystemService(Context.ROLE_SERVICE);
      return rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
          && rm.isRoleHeld(RoleManager.ROLE_ASSISTANT);
    } catch (Exception e) {
      return false;
    }
  }
  private void showStatus() {
    if (status == null) return;
    String mode = Build.VERSION.SDK_INT >= 29
        ? (isAssistant() ? "Ứng dụng này đang giữ vai trò trợ lý"
                         : "Chưa được chọn làm trợ lý")
        : "Hãy mở Digital assistant app để chọn";
    String key = HomeBinding.current(this);
    status.setText("Vai trò trợ lý: " + mode
        + "\nLệnh Home: " + (key == null ? "(không đọc được)" : key)
        + "\nTự kiểm tra sau reboot: " + (HomeBinding.enabled(this) ? "BẬT" : "TẮT")
        + "\nQuyền sửa hệ thống: " + (HomeBinding.canWrite(this) ? "Đã cấp" : "Chưa cấp")
        + "\n\n" + HomeBinding.lastStatus(this));
    bootSwitch.setChecked(HomeBinding.enabled(this));
  }

  private void chooseAssistant() {
    new AlertDialog.Builder(this)
        .setTitle("Cảnh báo: có thể làm MiCTS ngừng tìm kiếm")
        .setMessage("Để giữ Home gọi APK này, Android có thể yêu cầu bạn chọn "
            + "MiCTS Home Bridge làm trợ lý mặc định thay Google. "
            + "Nhưng MiCTS không root thường cần Google VoiceInteractionService. "
            + "Nếu MiCTS báo Trigger failed, bạn phải chọn lại Google "
            + "trong Default digital assistant app. Đây là bản thử nghiệm.")
        .setNegativeButton("Hủy", null)
        .setPositiveButton("Tiếp tục chọn", (dialog,which) -> openAssistantChooser())
        .show();
  }

  private void openAssistantChooser() {
    if (Build.VERSION.SDK_INT >= 29) {
      try {
        RoleManager rm = (RoleManager)getSystemService(Context.ROLE_SERVICE);
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
          startActivityForResult(
              rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), 111);
          return;
        }
      } catch (Exception ignored) {}
    }
    try {
      startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
    } catch (Exception e) {
      startActivity(new Intent(Settings.ACTION_SETTINGS));
    }
  }

  private void copyAdb() {
    ClipboardManager manager =
        (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
    manager.setPrimaryClip(ClipData.newPlainText("Home ADB",
        HomeBinding.adbCommand()));
    Toast.makeText(this, "Đã sao chép lệnh giữ Home.", Toast.LENGTH_SHORT).show();
  }

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(0xff10182a);
    getWindow().setNavigationBarColor(0xff10182a);
    ScrollView scroller = new ScrollView(this);
    scroller.setBackgroundColor(0xff10182a);
    content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(20), dp(26), dp(20), dp(26));
    scroller.addView(content);
    setContentView(scroller);

    TextView title = text("MiCTS qua giữ Home", 27, Color.WHITE);
    title.setTypeface(null, Typeface.BOLD);
    content.addView(title);
    TextView intro = text(
        "Chỉ là cầu nối: giữ Home → Android Assistant → mở ứng dụng MiCTS. "
        + "Không root, không Trợ năng, không dùng nút nguồn. "
        + "Cần thử xem HyperOS có chuyển được thao tác Home tới ứng dụng này hay không.",
        15, 0xffb8c8e2);
    intro.setPadding(0, dp(9), 0, dp(17));
    content.addView(intro);

    bootSwitch = new Switch(this);
    bootSwitch.setText("Kiểm tra lệnh Home sau khởi động");
    bootSwitch.setTextSize(16);
    bootSwitch.setTextColor(Color.WHITE);
    bootSwitch.setPadding(dp(14), dp(14), dp(14), dp(14));
    bootSwitch.setBackground(bg(0xff27354c));
    bootSwitch.setChecked(HomeBinding.enabled(this));
    content.addView(bootSwitch);
    bootSwitch.setOnCheckedChangeListener((view,yes) -> {
      if (HomeBinding.enabled(this) == yes) return;
      HomeBinding.setEnabled(this, yes);
      if (yes) {
        HomeBinding.restore(this);
        BootCheckService.schedule(this);
      } else {
        BootCheckService.cancel(this);
      }
      showStatus();
    });

    status = text("", 14, 0xffdce6f6);
    status.setPadding(dp(14), dp(14), dp(14), dp(14));
    status.setBackground(bg(0xff27354c));
    LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-1, -2);
    stp.topMargin = dp(14);
    content.addView(status, stp);

    button("1. Thử mở ứng dụng MiCTS", () -> MiCTSOpener.open(this));
    button("2. Chọn trợ lý cho thao tác giữ Home", this::chooseAssistant);
    button("3. Thử khôi phục cài đặt Home", () -> {
      boolean ok = HomeBinding.restore(this);
      showStatus();
      Toast.makeText(this,
          ok ? "Giá trị Home đã đúng." :
              "Không ghi được; cần dùng lệnh ADB dự phòng.",
          Toast.LENGTH_LONG).show();
    });
    button("Cấp quyền Sửa cài đặt hệ thống", () -> HomeBinding.openWriteSettings(this));
    button("Sao chép lệnh ADB lối tắt Home", this::copyAdb);

    TextView caveat = text(
        "LƯU Ý: Nếu chọn cầu nối này làm trợ lý mặc định, Google có thể bị "
        + "thay thế. MiCTS chế độ không root dựa vào Google làm mặc định "
        + "nên có thể không kích hoạt Circle to Search dù ứng dụng mở được. "
        + "Nếu vậy, vào Default digital assistant app chọn lại Google."
        + "\n\nNếu giữ Home vẫn mở Gemini, HyperOS có thể đang gọi Google trực tiếp "
        + "thay vì Assistant role. APK không thể ép ROM chuyển sang MiCTS "
        + "bằng một giá trị home_key không được hệ thống hỗ trợ.",
        13, 0xffb8c8e2);
    caveat.setPadding(0, dp(18), 0, 0);
    content.addView(caveat);
    showStatus();
  }
  @Override protected void onResume() {
    super.onResume();
    if (status != null) showStatus();
  }
  @Override protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    showStatus();
  }
}
