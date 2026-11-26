package com.fitstreak.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires when a reminder alarm goes off, then books the next day's alarm. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Reminders.show(context, intent.getIntExtra("id", Reminders.MORNING));
        Reminders.scheduleAll(context);
    }
}
