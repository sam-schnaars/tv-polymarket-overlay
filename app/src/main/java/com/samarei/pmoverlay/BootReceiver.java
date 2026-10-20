package com.samarei.pmoverlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Brings the overlay back after the TV restarts or the app is updated, if it was on. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (OverlayService.isOn(c)) OverlayService.setOn(c, true);
    }
}
