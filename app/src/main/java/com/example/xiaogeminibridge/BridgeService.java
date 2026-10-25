package com.example.xiaogeminibridge;
import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Arrays;
import java.util.List;

public class BridgeService extends AccessibilityService {
 private static final String TAG="XiaoGeminiBridge";
 private static final String TARGET="com.ldd.ai";
 private static final long COOLDOWN_MS=1800L;
 private long lastTrigger=0L;
 private void record(String key,String value){getSharedPreferences("bridge_diagnostics",MODE_PRIVATE).edit().putString(key,value).apply();}
 @Override protected void onServiceConnected(){super.onServiceConnected();record("last_status","Accessibility connected");}
 @Override public void onAccessibilityEvent(AccessibilityEvent event){
  if(event==null)return;
  int type=event.getEventType();
  if(type!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && type!=AccessibilityEvent.TYPE_WINDOWS_CHANGED)return;
  String pkg=event.getPackageName()==null?"":event.getPackageName().toString();
  String cls=event.getClassName()==null?"":event.getClassName().toString();
  record("last_event",pkg+" | "+cls+" | "+AccessibilityEvent.eventTypeToString(type));
  boolean xiao=pkg.equals("com.miui.voiceassist")||pkg.equals("com.xiaomi.aiasst")||pkg.equals("com.miui.aiasst")||pkg.equals("com.xiaomi.micolauncher");
  if(!xiao)return;
  record("last_xiaomi_event",pkg+" | "+cls);
  long now=SystemClock.elapsedRealtime();
  if(now-lastTrigger<COOLDOWN_MS)return;
  KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
  if(km!=null && km.isKeyguardLocked()){record("last_status","Blocked: screen locked");return;}
  boolean wake=hasWakeText(event.getText());
  AccessibilityNodeInfo source=event.getSource();
  if(!wake && source!=null)wake=containsWakeText(source,0);
  if(!wake){AccessibilityNodeInfo root=getRootInActiveWindow();if(root!=null)wake=containsWakeText(root,0);}
  // Xiaomi often hides overlay text from accessibility. Restrict fallback to window
  // transitions of the voice assistant, not generic content changes or settings.
  boolean overlay=type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
    (cls.contains("FrameLayout")||cls.toLowerCase(java.util.Locale.ROOT).contains("voiceassist"));
  if(!wake && !overlay){record("last_status","Xiao AI event without confirmed overlay: "+cls);return;}
  lastTrigger=now;
  try{
   Intent launch=getPackageManager().getLaunchIntentForPackage(TARGET);
   if(launch==null){record("last_status","AI app not installed or no launcher: "+TARGET);return;}
   launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
   startActivity(launch);
   record("last_status","AI launch requested via "+(wake?"wake text":"overlay"));
  }catch(Exception e){Log.e(TAG,"Unable to launch AI",e);record("last_status","Launch failed: "+e.getClass().getSimpleName());}
 }
 private boolean hasWakeText(List<CharSequence> texts){if(texts==null)return false;for(CharSequence t:texts)if(t!=null&&(t.toString().contains("我在听")||t.toString().contains("正在聆听")))return true;return false;}
 private boolean containsWakeText(AccessibilityNodeInfo n,int depth){
  if(n==null||depth>10)return false;
  if(hasWakeText(Arrays.asList(n.getText(),n.getContentDescription())))return true;
  for(int i=0;i<n.getChildCount();i++)if(containsWakeText(n.getChild(i),depth+1))return true;
  return false;
 }
 @Override public void onInterrupt(){record("last_status","Accessibility interrupted");}
 @Override public void onDestroy(){record("last_status","Accessibility service destroyed");super.onDestroy();}
}