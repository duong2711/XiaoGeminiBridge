package com.ldd.mictshomebridge;

import android.app.Activity;
import android.os.Bundle;

/** Invoked by ACTION_ASSIST if Android/HyperOS actually dispatches Home here. */
public final class MiCTSAssistActivity extends Activity {
  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    MiCTSOpener.open(this);
    finish();
  }
}
