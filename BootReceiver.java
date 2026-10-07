package com.hadid.islamiccal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-registers saved alarms after reboot, app update, or clock/timezone change. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        Scheduler.cancelAll(c);
        Scheduler.registerAll(c);
    }
}
