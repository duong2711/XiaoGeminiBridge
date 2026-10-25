package com.example.xiaogeminibridge;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.widget.*;
import android.content.SharedPreferences;
public class MainActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);
 LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(35,60,35,30);
 TextView t=new TextView(this);t.setText("Xiao AI → AI (com.ldd.ai)\n\nGiữ AI.apk cài riêng. Bridge chỉ nhận sự kiện Xiao AI và mở ứng dụng AI khi màn hình đã mở khóa.\n\nBật Accessibility cho Bridge. Nếu HyperOS tự tắt quyền, thử Không hạn chế pin và Tự khởi động; ứng dụng không thể tự bật lại quyền trợ năng.\n\nQuyền trợ năng có thể đọc nội dung cửa sổ. Chỉ bật nếu bạn tin tưởng ứng dụng.");t.setTextSize(17);l.addView(t);
 Button x=new Button(this);x.setText("Mở cài đặt Trợ năng");x.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));l.addView(x);
 TextView diag=new TextView(this);diag.setTextSize(15);l.addView(diag);
 Button refresh=new Button(this);refresh.setText("Xem chẩn đoán sau khi gọi Xiao AI");
 refresh.setOnClickListener(v->{SharedPreferences p=getSharedPreferences("bridge_diagnostics",MODE_PRIVATE);
 diag.setText("Trạng thái: "+p.getString("last_status","Chưa có")+"\nXiaomi event: "+p.getString("last_xiaomi_event","Chưa có")+"\nEvent gần nhất: "+p.getString("last_event","Chưa có"));});
 l.addView(refresh);setContentView(l);
 }
}