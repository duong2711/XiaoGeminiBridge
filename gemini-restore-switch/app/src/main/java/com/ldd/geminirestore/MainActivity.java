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
  private static final long[] CHECKS={15000,45000,90000};
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
    String mode=pref(c).getString("mode",GEMINI);
    if(CIRCLE.equals(mode)){
      // Deliberately DO NOT force MiCTS on reboot; this broke Google VIS on HyperOS.
      log(c,"MiCTS: kiểm tra sau reboot; không ghi đè trợ lý để giữ Google VIS.");
      return;
    }
    if(!pref(c).getBoolean("auto_gemini",true)){
      log(c,"Tự khôi phục Gemini đang tắt.");return;
    }
    if(google(read(c,"assistant"))){capture(c);log(c,"Gemini vẫn là trợ lý.");}
    else change(c,GEMINI,true);
  }
  public static class BootReceiver extends BroadcastReceiver{
    @Override public void onReceive(Context c,Intent i){
      if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())||
         Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()))schedule(c);
    }
  }
  public static class RestoreService extends JobService{
    @Override public boolean onStartJob(JobParameters p){
      new Thread(()->{
        try{
          long start=pref(this).getLong("boot_elapsed",-1L);
          long d=SystemClock.elapsedRealtime()-start;
          if(start>=0&&d>=0&&d<=90000)check(this);
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
    boolean ok=change(this,mode,false);update();
    if(!ok)new AlertDialog.Builder(this).setTitle("Không chuyển được")
      .setMessage(pref(this).getString("result","Không rõ lỗi")+
      "\n\nNếu HyperOS chặn cấp quyền, dùng ADB qua máy tính.")
      .setPositiveButton("Đóng",null).show();
    else Toast.makeText(this,"Đã lưu thiết lập. Hãy thử giữ nút nguồn.",Toast.LENGTH_LONG).show();
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
  private void copy(){
    String mode=pref(this).getString("mode",GEMINI);
    String cmd="adb shell settings put secure assistant "+
      (CIRCLE.equals(mode)?MICTS:original(this));
    ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    cm.setPrimaryClip(ClipData.newPlainText("ADB switch",cmd));
    new AlertDialog.Builder(this).setTitle("Đã sao chép lệnh ADB")
     .setMessage(cmd+"\n\nChạy trong CMD tại platform-tools khi không thể cấp quyền trực tiếp.")
     .setPositiveButton("OK",null).show();
  }
  private void update(){
    String mode=pref(this).getString("mode",GEMINI);
    long at=pref(this).getLong("at",0);
    String stamp=at==0?"Chưa chạy":DateFormat.getDateTimeInstance().format(new Date(at));
    status.setText("Bạn chọn: "+(CIRCLE.equals(mode)?"MiCTS":"Gemini")+
      "\nQuyền hệ thống: "+(grant(this)?"Có":"Không có / ROM chặn")+
      "\n\nAssistant hiện tại:\n"+String.valueOf(read(this,"assistant"))+
      "\n\nGoogle VoiceInteraction:\n"+String.valueOf(read(this,"voice_interaction_service"))+
      "\n\nGiá trị Google đã lưu:\n"+original(this)+
      "\n\nCập nhật: "+stamp+"\n"+pref(this).getString("result","Chưa thao tác"));
    guide.setText("Khởi động lại: kiểm tra tại ~15 / 45 / 90 giây, rồi dừng. "+
      "Chế độ Gemini có thể tự khôi phục nếu được cấp quyền. "+
      "Chế độ MiCTS không tự ghi lại sau reboot để tránh lỗi Google VIS.\n\n"+
      "MiCTS không root hỗ trợ tốt nhất khi Google vẫn là Digital Assistant; "+
      "bạn có thể kích hoạt qua ô Cài đặt nhanh.");
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
    button("Chuyển sang Gemini",0xff2962c9,()->select(GEMINI));
    button("Chuyển sang MiCTS (thử nghiệm)",0xff137f6a,()->select(CIRCLE));
    button("Mở MiCTS để thử",0xff394457,this::openMiCTS);
    button("Mở Digital Assistant",0xff394457,this::settings);
    button("Sao chép lệnh ADB của chế độ đã chọn",0xff394457,this::copy);
    button("Kiểm tra lại",0xff394457,()->{capture(this);update();});
    button("Bật / tắt tự khôi phục Gemini",0xff394457,()->{
      boolean b0=!pref(this).getBoolean("auto_gemini",true);
      pref(this).edit().putBoolean("auto_gemini",b0).apply();
      Toast.makeText(this,"Tự khôi phục Gemini: "+(b0?"Bật":"Tắt"),Toast.LENGTH_SHORT).show();update();
    });
    guide=tv("",13,0xffb2bfd3);guide.setPadding(0,dp(20),0,0);layout.addView(guide);
    TextView help=tv("ADB (nếu HyperOS cho phép):\nadb shell pm grant com.ldd.geminirestore android.permission.WRITE_SECURE_SETTINGS\n\n"+
       "Nếu báo Permission denial, dùng lệnh ADB thủ công; app không thể tự vượt quyền hệ thống.",13,0xffb2bfd3);
    help.setPadding(0,dp(16),0,0);layout.addView(help);
    capture(this);update();
  }
  @Override protected void onResume(){super.onResume();if(status!=null)update();}
}
