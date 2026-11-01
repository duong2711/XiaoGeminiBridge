package com.ldd.mictskeeper;

import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity {
    private static final String MICTS="com.parallelc.micts/com.parallelc.micts.ui.activity.MainActivity";
    private static final String GOOGLE="com.google.android.googlequicksearchbox";
    private static final int LEGACY_PERIODIC=5100, FIRST=5110;
    private static final long MAX_BOOT_WINDOW_MS=90000L;
    private static final long[] CHECKS={15000,45000,90000};
    private TextView status;
    private LinearLayout panel;

    private static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("keeper",Context.MODE_PRIVATE);
    }
    private static boolean enabled(Context c) {return prefs(c).getBoolean("enabled",true);}
    private static String setting(Context c,String key){
        try {
            String value=Settings.Secure.getString(c.getContentResolver(),key);
            return value==null?"(không có)":value;
        }catch(Exception e){return "Không đọc được: "+e.getClass().getSimpleName();}
    }
    private static synchronized void inspect(Context c){
        String assistant=setting(c,"assistant");
        String voice=setting(c,"voice_interaction_service");
        String result;
        if (!voice.startsWith(GOOGLE+"/")) {
            result="Google VoiceInteractionService chưa được chọn/kết nối. Vào Digital assistant: chọn Không có rồi chọn lại Google.";
        } else if (MICTS.equals(assistant)){
            result="Google VIS đã được chọn, nhưng Assistant đang trỏ MiCTS. Nếu reboot không gọi được, chọn lại Google trong Digital assistant.";
        } else {
            result="Google VIS được chọn. Có thể mở MiCTS bằng biểu tượng hoặc ô Quick Settings; ứng dụng không tự sửa nút nguồn.";
        }
        prefs(c).edit().putString("result",result).putLong("time",System.currentTimeMillis()).apply();
    }
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
        if(!boot)return;
        for(int i=0;i<CHECKS.length;i++)js.cancel(FIRST+i);
        prefs(c).edit().putLong("bootStartElapsed",SystemClock.elapsedRealtime()).apply();
        ComponentName svc=new ComponentName(c,KeeperJobService.class);
        for(int i=0;i<CHECKS.length;i++){
            long delay=CHECKS[i];
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
    public static class BootReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context c,Intent i) {
            if(enabled(c))jobs(c,true); else stop(c);
        }
    }
    public static class KeeperJobService extends JobService {
        @Override public boolean onStartJob(JobParameters p) {
            new Thread(()->{
                try{if(enabled(this)&&insideWindow(this))inspect(this);}
                finally{jobFinished(p,false);}
            },"MiCTS-boot-check").start();
            return true;
        }
        @Override public boolean onStopJob(JobParameters p){return false;}
    }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,int color){
        TextView v=new TextView(this);
        v.setText(value);v.setTextColor(color);v.setTextSize(size);
        v.setLineSpacing(dp(3),1f);return v;
    }
    private void addButton(String label,Runnable handler){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.topMargin=dp(11);
        panel.addView(b,p);b.setOnClickListener(v->handler.run());
    }
    private void openAssistantSettings(){
        try{startActivity(new Intent("android.settings.VOICE_INPUT_SETTINGS"));}
        catch(Exception e){
            try{startActivity(new Intent(Settings.ACTION_SETTINGS));}
            catch(Exception ignored){}
        }
    }
    private void launchMiCTS(){
        try{
            Intent i=new Intent();
            i.setComponent(ComponentName.unflattenFromString(MICTS));
            startActivity(i);
        }catch(Exception e){
            prefs(this).edit().putString("result","Không mở được MiCTS: "+e.getClass().getSimpleName()).putLong("time",System.currentTimeMillis()).apply();
        }
        refresh();
    }
    private void refresh(){
        long t=prefs(this).getLong("time",0L);
        String last=t==0?"Chưa kiểm tra":DateFormat.getDateTimeInstance().format(new Date(t))+"\n"+
                prefs(this).getString("result","");
        status.setText("Kiểm tra sau reboot: "+(enabled(this)?"BẬT (15 / 45 / 90 giây)":"TẮT")+
            "\nChế độ an toàn: KHÔNG tự đổi trợ lý"+
            "\n\nAssistant:\n"+setting(this,"assistant")+
            "\n\nGoogle VoiceInteractionService:\n"+setting(this,"voice_interaction_service")+
            "\n\nKiểm tra gần nhất:\n"+last);
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
        TextView title=text("MiCTS Keeper v1.2",27,Color.WHITE);
        title.setTypeface(null,Typeface.BOLD);panel.addView(title);
        TextView subtitle=text("Google làm trợ lý mặc định, MiCTS kích hoạt tìm kiếm",15,0xffa9bacf);
        subtitle.setPadding(0,dp(8),0,dp(18));panel.addView(subtitle);
        status=text("",14,0xffe1ebff);status.setPadding(dp(15),dp(16),dp(15),dp(16));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xff26354c);bg.setCornerRadius(dp(15));
        status.setBackground(bg);panel.addView(status);
        addButton("Kiểm tra lại",()->{inspect(this);refresh();});
        addButton("Mở cài đặt Digital Assistant",this::openAssistantSettings);
        addButton("Mở MiCTS thử",this::launchMiCTS);
        addButton("Bật kiểm tra sau khởi động",()->{
            prefs(this).edit().putBoolean("enabled",true).apply();jobs(this,true);inspect(this);refresh();
        });
        addButton("Tắt kiểm tra sau khởi động",()->{
            prefs(this).edit().putBoolean("enabled",false).apply();stop(this);refresh();
        });
        addButton("Cài đặt tự khởi động và pin",()->startActivity(new Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        TextView help=text("Nếu MiCTS báo Trigger failed sau reboot: trong Digital assistant đổi Google → Không có → Google, sau đó thử mở lại MiCTS.\n\n"+
            "MiCTS dùng dịch vụ trợ lý Google để gọi Circle to Search khi không root. "+
            "Phiên bản này KHÔNG tự đặt MiCTS thành Digital Assistant. "+
            "Việc chuyển nút nguồn sang MiCTS trên HyperOS chưa bảo đảm làm được bền vững nếu không root.",13,0xffa9bacf);
        help.setPadding(0,dp(20),0,0);panel.addView(help);
        jobs(this,false);inspect(this);refresh();
    }
    @Override protected void onResume(){super.onResume();if(status!=null)refresh();}
}
