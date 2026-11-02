package com.ldd.mictshomebridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

/** Launch MiCTS in the exact way pressing its launcher icon would. */
public final class MiCTSOpener {
  private static final String PACKAGE = "com.parallelc.micts";
  private static final String MAIN = "com.parallelc.micts.ui.activity.MainActivity";

  private MiCTSOpener() {}

  public static boolean open(Context c) {
    Intent launch = c.getPackageManager().getLaunchIntentForPackage(PACKAGE);
    if (launch == null) {
      launch = new Intent(Intent.ACTION_MAIN);
      launch.setComponent(new ComponentName(PACKAGE, MAIN));
    }
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    try {
      c.startActivity(launch);
      return true;
    } catch (Exception e) {
      Toast.makeText(c,
          "Không mở được MiCTS. Hãy kiểm tra đã cài MiCTS và có thể bấm icon để mở.",
          Toast.LENGTH_LONG).show();
      return false;
    }
  }
}
