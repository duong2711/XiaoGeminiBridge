package com.example.xiaogeminibridge;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.widget.*;
public class MainActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);
 LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(35,60,35,30);
 TextView t=new TextView(this);t.setText("Xiao AI → Gemini Live (thử nghiệm)\n\nKhông xin quyền micro. Chỉ hoạt động khi đã mở khóa.\n\nBật Accessibility cho Xiao Gemini Bridge, rồi gọi Xiao AI để thử. Nếu không hoạt động, tắt quyền Accessibility.\n\nQuyền này có thể đọc nội dung cửa sổ ứng dụng; chỉ cấp nếu bạn tin tưởng mã nguồn.");t.setTextSize(17);l.addView(t);
 Button x=new Button(this);x.setText("Mở cài đặt Trợ năng");x.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));l.addView(x);setContentView(l);
 }
}