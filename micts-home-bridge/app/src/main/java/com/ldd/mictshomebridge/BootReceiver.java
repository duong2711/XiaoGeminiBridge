package com.ldd.mictshomebridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context, Intent intent) {
    if (!HomeBinding.enabled(context)) {
      BootCheckService.cancel(context);
      return;
    }
    HomeBinding.restore(context);
    BootCheckService.schedule(context);
  }
}
