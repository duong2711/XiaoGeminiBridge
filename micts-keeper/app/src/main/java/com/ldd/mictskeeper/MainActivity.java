package com.ldd.mictskeeper;

import android.Manifest;
import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity {
    private static final String TARGET="com.parallelc.micts/com.parallelc.micts.ui.activity.MainActivity";
    private static final String KEY="assistant";
    private static final int LEGACY_PERIODIC=5100, FIRST=5110;
    private static final long MAX_BOOT_WINDOW_MS=90000L;
    private static final long[] RETRIES={15000,45000,90000};
    private TextView status;
    private LinearLayout panel;

    private static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("keeper",Context.MODE_PRIVATE);
    }
    private static boolean enabled(Context c) {return prefs(c).getBoolean("enabled",true);}
    private static boolean permission(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)==PackageManager.PERMISSION_GRANTED;
    }
    private static boolean installed(Context c) {
        try {c.getPackageManager().getPackageInfo("com.parallelc.micts",0);return true;}
        catch(PackageManager.NameNotFoundException e){return false;}
    }
    private static String current(Context c) {
        try{
            String value=Settings.Secure.getString(c.getContentResolver(),KEY);
            return value==null?"(không có)":value;
        }catch(Exception e){return "Không đọc được: "+e.getMessage();}
    }
    private static synchronized void attempt(Context c){
        String result;
        try{
            if(!enabled(c))result="Đã tắt tự khôi phục";
            else if(!installed(c))result="Chưa cài MiCTS";
            else if(!permission(c))result="Chưa cấp WRITE_SECURE_SETTINGS";
            else if(TARGET.equals(current(c)))result="MiCTS đã được chọn";
            else{
                boolean ok=Settings.Secure.putString(c.getContentResolver(),KEY,TARGET);
                result=ok&&TARGET.equals(current(c))?"Đã khôi phục MiCTS":"HyperOS từ chối hoặc ghi đè thiết lập";
            }
        }catch(Exception e){result="Lỗi: "+e.getClass().getSimpleName()+": "+e.getMessage();}
        prefs(c).edit().putString("result",result).putLong("time",System.currentTimeMillis()).apply();
    }
    // Do not retain the former 15-minute periodic job or old 3/6/10-minute retries.
    private static void cleanupLegacy(JobScheduler js){
        js.cancel(LEGACY_PERIODIC);
        for(int i=3;i<6;i++)js.cancel(FIRST+i);
    }
    private static boolean insideWindow(Context c){
        long start=prefs(c).getLong("bootStartElapsed",-1L);
        long delta=SystemClock.elapsedRealtime()-start;
        return start>=0 && delta>=0 && delta<=MAX_BOOT_WINDOW_MS;
    }
    private static void jobs(Context c,boolean boot){
        JobScheduler js=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(js==null)return;
        cleanupLegacy(js);
        if(!enabled(c)){stop(c);return;}
        if(!boot)return; // Opening the app must not create a permanent periodic check.
        for(int i=0;i<RETRIES.length;i++)js.cancel(FIRST+i);
        prefs(c).edit().putLong("bootStartElapsed",SystemClock.elapsedRealtime()).apply();
        ComponentName svc=new ComponentName(c,KeeperJobService.class);
        for(int i=0;i<RETRIES.length;i++){
            long delay=RETRIES[i];
            // The OS may delay JobScheduler; insideWindow blocks late writes after 90 s.
            js.schedule(new JobInfo.Builder(FIRST+i,svc)
                .setMinimumLatency(delay)
                .setOverrideDeadline(delay+5000L)
                .build());
        }
    }
    private static void stop(Context c){
        JobScheduler js=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(js==null)return;
        js.cancel(LEGACY_PERIODIC);
        for(int i=0;i<6;i++)js.cancel(FIRST+i);
        prefs(c).edit().remove("bootStartElapsed").apply();
    }
    public static class BootReceiver extends BroadcastReceiver{
        @Override public void onReceive(Context c,Intent intent){
            if(enabled(c))jobs(c,true);
        }
    }
    public static class KeeperJobService extends JobService{
        @Override public boolean onStartJob(JobParameters p){
            new Thread(()->{
                try{if(enabled(this)&&insideWindow(this))attempt(this);}
                finally{jobFinished(p,false);}
            },"MiCTS-restore").start();
            return true;
        }
        @Override public boolean onStopJob(JobParameters p){return false;}
    }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,int color){
        TextView v=new TextView(this);
        v.setText(value);v.setTextColor(color);v.setTextSize(size);
        v.setLineSpacing(dp(3),1f);
        return v;
    }
    private void addButton(String label,Runnable handler){
        Button b=new Button(this);
        b.setText(label);b.setAllCaps(false);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.topMargin=dp(11);
        panel.addView(b,p);
        b.setOnClickListener(v->handler.run());
    }
    private void refresh(){
        long t=prefs(this).getLong("time",0L);
        String last=t==0?"Chưa chạy":DateFormat.getDateTimeInstance().format(new Date(t))+
                "\n"+prefs(this).getString("result","");
        status.setText("Tự động: "+(enabled(this)?"BẬT":"TẮT")+
                "\nĐã cài MiCTS: "+(installed(this)?"CÓ":"CHƯA")+
                "\nQuyền ADB: "+(permission(this)?"ĐÃ CẤP":"CHƯA CẤP")+
                "\n\nTrợ lý hiện tại:\n"+current(this)+"\n\nLần kiểm tra cuối:\n"+last);
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xff111827);
        getWindow().setNavigationBarColor(0xff111827);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackgroundColor(0xff111827);
        panel=new LinearLayout(this);panel.setOrientation(1);
        panel.setPadding(dp(20),dp(35),dp(20),dp(30));
        scroll.addView(panel);setContentView(scroll);
        TextView heading=text("MiCTS Keeper",27,Color.WHITE);
        heading.setTypeface(null,Typeface.BOLD);panel.addView(heading);
        TextView sub=text("Tự khôi phục MiCTS sau reboot: chỉ trong 90 giây đầu",15,0xffa9bacf);
        sub.setPadding(0,dp(8),0,dp(18));panel.addView(sub);
        status=text("",14,0xffe1ebff);status.setPadding(dp(15),dp(16),dp(15),dp(16));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xff26354c);bg.setCornerRadius(dp(15));
        status.setBackground(bg);panel.addView(status);
        addButton("Khôi phục MiCTS ngay",()->{prefs(this).edit().putBoolean("enabled",true).apply();jobs(this,false);attempt(this);refresh();});
        addButton("Bật tự khôi phục sau khi khởi động",()->{prefs(this).edit().putBoolean("enabled",true).apply();jobs(this,true);attempt(this);refresh();});
        addButton("Tắt tự khôi phục",()->{prefs(this).edit().putBoolean("enabled",false).apply();stop(this);refresh();});
        addButton("Cài đặt tự khởi động và pin",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        TextView help=text("CẤP QUYỀN ADB (MỘT LẦN):\n"+
             "adb shell pm grant com.ldd.mictskeeper android.permission.WRITE_SECURE_SETTINGS\n\n"+
             "Tự thử lúc 15, 45, 90 giây sau BOOT_COMPLETED, sau đó dừng. Không kiểm tra định kỳ. "+
             "Bật Tự khởi động và Pin không hạn chế cho ứng dụng này. "+
             "HyperOS có thể trì hoãn tác vụ hoặc từ chối quyền; không bảo đảm khôi phục ngay trên mọi ROM.",13,0xffa9bacf);
        help.setPadding(0,dp(20),0,0);panel.addView(help);
        jobs(this,false);attempt(this);refresh();
    }
    @Override protected void onResume(){super.onResume();if(status!=null)refresh();}
}
