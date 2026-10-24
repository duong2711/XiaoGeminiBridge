package com.example.xiaogeminibridge;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.app.KeyguardManager;
import android.content.*;
import android.os.*;
import android.util.Log;
import java.util.*;
public class BridgeService extends AccessibilityService {
 private static final String TAG="XiaoGeminiBridge";
 private long lastTrigger=0, openedAt=0;
 private boolean awaitingLive=false;
 private static final String PREFS="bridge_diagnostics";
 private void record(String key,String value){getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(key,value).apply();}
 private void recordEvent(String pkg, AccessibilityEvent e){
  if(e.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.getEventType()!=AccessibilityEvent.TYPE_WINDOWS_CHANGED)return;
  String cls=e.getClassName()==null?"":e.getClassName().toString();
  record("last_event",pkg+" | "+cls+" | "+android.view.accessibility.AccessibilityEvent.eventTypeToString(e.getEventType()));
  if(pkg.contains("miui")||pkg.contains("xiaomi"))record("last_xiaomi_event",pkg+" | "+cls);
 }
 private final Handler handler=new Handler(Looper.getMainLooper());
 @Override public void onAccessibilityEvent(AccessibilityEvent event){
  if(event==null)return;
  String pkg=event.getPackageName()==null?"":event.getPackageName().toString();
  long now=SystemClock.elapsedRealtime();
  recordEvent(pkg,event);
  if(awaitingLive && now-openedAt<7000 && (pkg.contains("google")||pkg.contains("bard"))){
   AccessibilityNodeInfo root=getRootInActiveWindow();
   if(root!=null && clickLive(root)){awaitingLive=false;Log.i(TAG,"Live control tapped");}
   return;
  }
  if(now-lastTrigger<12000)return;
  KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
  if(km!=null&&km.isKeyguardLocked()){record("last_status","Blocked: keyguard locked");return;}
  if(!(pkg.startsWith("com.miui.voiceassist")||pkg.startsWith("com.xiaomi.aiasst")||pkg.startsWith("com.xiaomi.micolauncher")||pkg.startsWith("com.miui.aiasst")))return;
  record("last_status","Xiao AI package matched: "+pkg);
  boolean matched=hasWakeText(event.getText());
  AccessibilityNodeInfo source=event.getSource();
  if(!matched && source!=null)matched=containsWakeText(source,0);
  if(!matched){AccessibilityNodeInfo root=getRootInActiveWindow();if(root!=null)matched=containsWakeText(root,0);}
  if(!matched){record("last_status","Package matched but wake text not accessible: "+pkg);return;}
  lastTrigger=now;
  record("last_status","Wake detected, launching Gemini");
  try{
   Intent launch=getPackageManager().getLaunchIntentForPackage("com.google.android.apps.bard");
   if(launch==null)launch=getPackageManager().getLaunchIntentForPackage("com.google.android.googlequicksearchbox");
   if(launch==null){Log.w(TAG,"Gemini/Google not found");record("last_status","Gemini/Google launch intent unavailable");return;}
   launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
   startActivity(launch);record("last_status","Gemini launch requested");awaitingLive=true;openedAt=now;
   handler.postDelayed(()->awaitingLive=false,7500);
  }catch(Exception ex){Log.e(TAG,"Launch blocked",ex);record("last_status","Launch failed: "+ex.getClass().getSimpleName());}
 }
 private boolean hasWakeText(List<CharSequence> texts){if(texts==null)return false;for(CharSequence t:texts)if(t!=null&&(t.toString().contains("我在听")||t.toString().contains("正在聆听")))return true;return false;}
 private boolean containsWakeText(AccessibilityNodeInfo node,int depth){if(node==null||depth>12)return false;
  CharSequence t=node.getText(),d=node.getContentDescription();
  if(hasWakeText(Arrays.asList(t,d)))return true;
  for(int i=0;i<node.getChildCount();i++)if(containsWakeText(node.getChild(i),depth+1))return true;
  return false;
 }
 private boolean clickLive(AccessibilityNodeInfo node){
  if(node==null)return false;
  CharSequence t=node.getText(),d=node.getContentDescription();
  String label=(t==null?"":t.toString())+" "+(d==null?"":d.toString());
  String normalized=label.trim().toLowerCase(Locale.ROOT);
  if(normalized.equals("live")||normalized.equals("gemini live")||normalized.equals("trò chuyện trực tiếp")){
   AccessibilityNodeInfo n=node;while(n!=null){if(n.isClickable())return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);n=n.getParent();}
  }
  for(int i=0;i<node.getChildCount();i++)if(clickLive(node.getChild(i)))return true;
  return false;
 }
 @Override public void onInterrupt(){}
}