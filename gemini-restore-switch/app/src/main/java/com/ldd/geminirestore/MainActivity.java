package com.ldd.geminirestore;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity {
  private static final String GOOGLE="com.google.android.googlequicksearchbox";
  private static final String GOOGLE_FALLBACK=GOOGLE+"/com.google.android.voiceinteraction.GsaVoiceInteractionService";
  private static final String MICTS="com.parallelc.micts/com.parallelc.micts.ui.activity.MainActivity";
  private static final String GEMINI="gemini", CIRCLE="micts";
  private static final long[] CHECKS={15000,45000,85000};
  private static final long BOOT_WINDOW_MS=90000;
  private static final int JOB=6240;
  private TextView status,guide;
  private LinearLayout layout;

  private static android.content.SharedPreferences pref(Context c){
    return c.getSharedPreferences("switch",Context.MODE_PRIVATE);
  }
  private static String read(Context c,String key){
    try{return Settings.Secure.getString(c.getContentResolver(),key);}
    catch(Exception ex){return null;}
  }
  private static boolean google(String value){return value!=null&&value.startsWith(GOOGLE+"/");}
  private static boolean grant(Context c){
    return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)==PackageManager.PERMISSION_GRANTED;
  }
  private static String original(Context c){
    String saved=pref(c).getString("google_original",null);
    if(google(saved))return saved;
    String current=read(c,"assistant");
    if(google(current))return current;
    String voice=read(c,"voice_interaction_service");
    if(google(voice))return voice;
    return GOOGLE_FALLBACK;
  }
  private static void capture(Context c){
    String a=read(c,"assistant");
    if(google(a))pref(c).edit().putString("google_original",a).apply();
  }
  private static void log(Context c,String value){
    pref(c).edit().putString("result",value).putLong("at",System.currentTimeMillis()).apply();
  }
  private static boolean installed(Context c){
    try{c.getPackageManager().getPackageInfo("com.parallelc.micts",0);return true;}
    catch(PackageManager.NameNotFoundException ex){return false;}
  }
  private static synchronized boolean change(Context c,String mode,boolean boot){
    if(!grant(c)){
      log(c,"Không có WRITE_SECURE_SETTINGS. ROM có thể chặn cấp quyền ADB.");
      return false;
    }
    String target;
    if(CIRCLE.equals(mode)){
      if(!installed(c)){log(c,"Chưa cài MiCTS.");return false;}
      String vis=read(c,"voice_interaction_service");
      if(!google(vis)){log(c,"MiCTS VIS cần Google làm trợ lý mặc định. Hãy chọn Google trong cài đặt trước.");return false;}
      target=MICTS;
    }else{
      capture(c);
      target=original(c);
    }
    try{
      boolean ok=Settings.Secure.putString(c.getContentResolver(),"assistant",target);
      boolean confirmed=ok&&target.equals(read(c,"assistant"));
      if(confirmed&&!boot)pref(c).edit().putString("mode",mode).apply();
      log(c,confirmed?(CIRCLE.equals(mode)
         ?"Đã ghi MiCTS (thử nghiệm). Nếu Circle to Search lỗi, hãy chọn lại Google."
         :"Đã chọn Google/Gemini."):"HyperOS không chấp nhận hoặc ghi đè thay đổi.");
      return confirmed;
    }catch(Exception ex){
      log(c,"Không đổi được trợ lý: "+ex.getClass().getSimpleName());return false;
    }
  }
  private static void cancel(Context c){
    JobScheduler j=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    if(j!=null)for(int n=0;n<3;n++)j.cancel(JOB+n);
  }
  private static void schedule(Context c){
    JobScheduler j=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    if(j==null)return;
    cancel(c);
    pref(c).edit().putLong("boot_elapsed",SystemClock.elapsedRealtime()).apply();
    ComponentName svc=new ComponentName(c,RestoreService.class);
    for(int n=0;n<CHECKS.length;n++){
      long ms=CHECKS[n];
      j.schedule(new JobInfo.Builder(JOB+n,svc)
           .setMinimumLatency(ms).setOverrideDeadline(ms+5000).build());
    }
  }
  private static void check(Context c){
    HomeRestore.restore(c);
    String mode=pref(c).getString("mode",GEMINI);
    String active=read(c,"assistant");
    if(CIRCLE.equals(mode)){
      if(MICTS.equals(active)){
        String vis=read(c,"voice_interaction_service");
        log(c,google(vis)?"MiCTS vẫn được chọn sau khởi động.":"MiCTS được chọn nhưng Google VIS chưa sẵn sàng; Circle to Search có thể lỗi.");
        return;
      }
      // MiCTS on Android without root needs Google's Voice Interaction Service.
      // Never force MiCTS if Google VIS is absent: this could break the trigger.
      String vis=read(c,"voice_interaction_service");
      if(!google(vis)){
        log(c,"HyperOS đã đổi trợ lý nhưng Google VIS chưa kết nối. Không ép MiCTS để tránh lỗi Circle to Search; hãy chọn lại Google rồi thử.");
        return;
      }
      change(c,CIRCLE,true);
    }else{
      if(google(active)){capture(c);log(c,"Gemini vẫn được chọn sau khởi động.");}
      else change(c,GEMINI,true);
    }
  }
  public static class BootReceiver extends BroadcastReceiver{
    @Override public void onReceive(Context c,Intent i){
      if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())||
         Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())){
        HomeRestore.restore(c);
        schedule(c);
      }
    }
  }
  public static class RestoreService extends JobService{
    @Override public boolean onStartJob(JobParameters p){
      new Thread(()->{
        try{
          long start=pref(this).getLong("boot_elapsed",-1L);
          long d=SystemClock.elapsedRealtime()-start;
          if(start>=0&&d>=0&&d<=BOOT_WINDOW_MS)check(this);
        }finally{jobFinished(p,false);}
      },"RestoreCheck").start();
      return true;
    }
    @Override public boolean onStopJob(JobParameters p){return false;}
  }
  private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
  private GradientDrawable rounded(int color){
    GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d;
  }
  private TextView tv(String str,int size,int color){
    TextView t=new TextView(this);t.setText(str);t.setTextColor(color);t.setTextSize(size);
    t.setLineSpacing(dp(3),1f);return t;
  }
  private void button(String title,int color,Runnable fn){
    Button b=new Button(this);b.setText(title);b.setAllCaps(false);
    b.setTextColor(Color.WHITE);b.setBackground(rounded(color));
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(55));
    p.topMargin=dp(12);layout.addView(b,p);b.setOnClickListener(v->fn.run());
  }
  private void select(String mode){
    pref(this).edit().putString("last_requested_mode",mode).apply();
    boolean ok=change(this,mode,false);update();
    if(!ok)new AlertDialog.Builder(this).setTitle("Không chuyển được")
      .setMessage(pref(this).getString("result","Không rõ lỗi")+
      "\n\nNếu HyperOS chặn cấp quyền, dùng ADB qua máy tính.")
      .setPositiveButton("Đóng",null).show();
    else Toast.makeText(this,"Đã lưu thiết lập. Hãy thử giữ nút nguồn.",Toast.LENGTH_LONG).show();
  }
  private void rememberCurrent(){
    String active=read(this,"assistant");
    String mode=MICTS.equals(active)?CIRCLE:google(active)?GEMINI:null;
    if(mode==null){
      new AlertDialog.Builder(this).setTitle("Không nhận diện được")
        .setMessage("Assistant hiện tại không phải Google/Gemini hoặc MiCTS. Hãy chọn chế độ trước.")
        .setPositiveButton("Đóng",null).show();
      return;
    }
    if(GEMINI.equals(mode))capture(this);
    pref(this).edit().putString("mode",mode).apply();
    log(this,"Đã ghi nhớ lựa chọn hiện tại: "+(CIRCLE.equals(mode)?"MiCTS":"Gemini"));
    update();
  }
  private void settings(){
    try{startActivity(new Intent("android.settings.VOICE_INPUT_SETTINGS"));}
    catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}
  }
  private void openMiCTS(){
    try{
      Intent i=new Intent();i.setComponent(ComponentName.unflattenFromString(MICTS));
      startActivity(i);
    }catch(Exception e){Toast.makeText(this,"Không mở được MiCTS.",Toast.LENGTH_LONG).show();}
  }
  private void copy(String mode){
    String cmd="adb shell settings put secure assistant "+
      (CIRCLE.equals(mode)?MICTS:original(this));
    ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    cm.setPrimaryClip(ClipData.newPlainText("ADB switch",cmd));
    new AlertDialog.Builder(this).setTitle("Đã sao chép lệnh ADB")
     .setMessage(cmd+"\n\nChạy trong CMD tại platform-tools khi không thể cấp quyền trực tiếp.")
     .setPositiveButton("OK",null).show();
  }
  private void toggleHome(boolean enabled){
    HomeRestore.setEnabled(this,enabled);
    if(enabled){
      if(HomeRestore.hasWritePermission(this)){
        boolean ok=HomeRestore.restore(this);
        Toast.makeText(this,ok?"Đã đặt nhấn giữ Home → Gemini":"Không đổi được Home. Hãy xem trạng thái.",Toast.LENGTH_LONG).show();
      }else{
        new AlertDialog.Builder(this)
          .setTitle("Cấp quyền Sửa cài đặt hệ thống")
          .setMessage("Bật Cho phép sửa cài đặt hệ thống cho Gemini Restore. Quay lại app để tự áp dụng. Đây là quyền WRITE_SETTINGS, không phải WRITE_SECURE_SETTINGS.")
          .setPositiveButton("Mở cài đặt",(d,w)->HomeRestore.openPermissionScreen(this))
          .setNegativeButton("Để sau",null).show();
      }
    }else{
      Toast.makeText(this,"Đã tắt TỰ KHÔI PHỤC Home. Thiết lập Home hiện tại không bị thay đổi.",Toast.LENGTH_LONG).show();
    }
    update();
  }
  private void copyHome(){
    String cmd=HomeRestore.adbCommand();
    ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    cm.setPrimaryClip(ClipData.newPlainText("ADB Home",cmd));
    Toast.makeText(this,"Đã sao chép lệnh ADB Home",Toast.LENGTH_SHORT).show();
  }
  private void update(){
    String mode=pref(this).getString("mode",GEMINI);
    long at=pref(this).getLong("at",0);
    String stamp=at==0?"Chưa chạy":DateFormat.getDateTimeInstance().format(new Date(at));
    status.setText("Home → Gemini: "+(HomeRestore.enabled(this)?"BẬT tự khôi phục":"TẮT tự khôi phục")+
      "\nQuyền WRITE_SETTINGS: "+(HomeRestore.hasWritePermission(this)?"Đã cấp":"Chưa cấp")+
      "\nGiá trị Home: "+String.valueOf(HomeRestore.current(this))+
      "\nKết quả Home: "+HomeRestore.lastResult(this)+"\n\n"+
      "Chế độ ghi nhớ (sau reboot): "+(CIRCLE.equals(mode)?"MiCTS":"Gemini")+
      "\nQuyền hệ thống: "+(grant(this)?"Có":"Không có / ROM chặn")+
      "\n\nAssistant hiện tại:\n"+String.valueOf(read(this,"assistant"))+
      "\n\nGoogle VoiceInteraction:\n"+String.valueOf(read(this,"voice_interaction_service"))+
      "\n\nGiá trị Google đã lưu:\n"+original(this)+
      "\n\nCập nhật: "+stamp+"\n"+pref(this).getString("result","Chưa thao tác"));
    guide.setText("Sau reboot: tự kiểm tra khoảng 15 / 45 / 85 giây (dừng trước 90 giây). "+
      "Giữ chế độ Gemini hoặc MiCTS đã được chọn và ghi nhớ trước đó. "+"Home → Gemini có cơ chế riêng và chỉ chạy khi đã bật, cấp WRITE_SETTINGS. "+
      "Nếu MiCTS mất kết nối Google VIS, app sẽ không cưỡng ép thay đổi gây lỗi; mở Google rồi chọn lại Google làm trợ lý.\n\n"+
      "Khôi phục cần quyền WRITE_SECURE_SETTINGS và được HyperOS cho chạy sau reboot. "+
      "MiCTS không root có thể không hoạt động khi bị đặt thành trợ lý mặc định.");
  }
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setStatusBarColor(0xff111827);
    getWindow().setNavigationBarColor(0xff111827);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
    scroll.setBackgroundColor(0xff111827);
    layout=new LinearLayout(this);layout.setOrientation(1);
    layout.setPadding(dp(19),dp(26),dp(19),dp(28));
    scroll.addView(layout);setContentView(scroll);
    TextView title=tv("Gemini Restore",27,Color.WHITE);
    title.setTypeface(null,Typeface.BOLD);layout.addView(title);
    TextView sub=tv("Chọn Gemini hoặc MiCTS",15,0xffa7bed9);
    sub.setPadding(0,dp(6),0,dp(20));layout.addView(sub);
    status=tv("",14,0xffe2eafe);status.setPadding(dp(14),dp(16),dp(14),dp(16));
    status.setBackground(rounded(0xff25344b));layout.addView(status);
    button("BẬT tự khôi phục Home → Gemini",0xff2962c9,()->toggleHome(true));
    button("TẮT tự khôi phục Home",0xff394457,()->toggleHome(false));
    button("Cấp quyền sửa cài đặt hệ thống",0xff137f6a,()->HomeRestore.openPermissionScreen(this));
    button("Sao chép lệnh ADB Home",0xff394457,this::copyHome);
    button("Chuyển sang Gemini",0xff2962c9,()->select(GEMINI));
    button("Chuyển sang MiCTS (thử nghiệm)",0xff137f6a,()->select(CIRCLE));
    button("Mở MiCTS để thử",0xff394457,this::openMiCTS);
    button("Mở Digital Assistant",0xff394457,this::settings);
    button("Sao chép ADB chuyển sang Gemini",0xff394457,()->copy(GEMINI));
    button("Sao chép ADB chuyển sang MiCTS",0xff394457,()->copy(CIRCLE));
    button("Kiểm tra lại",0xff394457,()->{capture(this);update();});
    button("Ghi nhớ chế độ đang sử dụng (khi đổi bằng ADB)",0xff394457,this::rememberCurrent);
    guide=tv("",13,0xffb2bfd3);guide.setPadding(0,dp(20),0,0);layout.addView(guide);
    TextView help=tv("ADB (nếu HyperOS cho phép):\nadb shell pm grant com.ldd.geminirestore android.permission.WRITE_SECURE_SETTINGS\n\n"+
       "Nếu báo Permission denial, dùng lệnh ADB thủ công; app không thể tự vượt quyền hệ thống.",13,0xffb2bfd3);
    help.setPadding(0,dp(16),0,0);layout.addView(help);
    // First launch: remember the mode currently configured on this device.
    if(!pref(this).contains("mode")){
      String a=read(this,"assistant");
      pref(this).edit().putString("mode",MICTS.equals(a)?CIRCLE:GEMINI).apply();
    }
    capture(this);update();
  }
  @Override protected void onResume(){super.onResume();if(status!=null){if(HomeRestore.enabled(this)&&HomeRestore.hasWritePermission(this))HomeRestore.restore(this);update();}}
}
