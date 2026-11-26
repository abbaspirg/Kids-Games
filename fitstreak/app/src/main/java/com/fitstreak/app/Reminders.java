package com.fitstreak.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Schedules the morning reminder and the 9 PM "streak in danger" alert. */
final class Reminders {

    static final String CHANNEL = "workout_reminders";
    static final int MORNING = 100;
    static final int EVENING = 101;
    static final int EVENING_HOUR = 21;

    private Reminders() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("fitstreak", Context.MODE_PRIVATE);
    }

    static void saveSettings(Context c, boolean morningOn, int h, int m, boolean eveningOn) {
        prefs(c).edit()
                .putBoolean("morningOn", morningOn)
                .putInt("hour", h)
                .putInt("minute", m)
                .putBoolean("eveningOn", eveningOn)
                .apply();
    }

    static void saveProgress(Context c, String lastDone, int streak) {
        prefs(c).edit().putString("lastDone", lastDone).putInt("streak", streak).apply();
    }

    static void scheduleAll(Context c) {
        SharedPreferences p = prefs(c);
        schedule(c, MORNING, p.getBoolean("morningOn", true), p.getInt("hour", 6), p.getInt("minute", 30));
        schedule(c, EVENING, p.getBoolean("eveningOn", true), EVENING_HOUR, 0);
    }

    private static void schedule(Context c, int id, boolean on, int hour, int minute) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = alarmIntent(c, id);
        am.cancel(pi);
        if (!on) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        // Inexact (no special permission needed); may arrive a few minutes late.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
    }

    private static PendingIntent alarmIntent(Context c, int id) {
        Intent i = new Intent(c, ReminderReceiver.class)
                .setAction("com.fitstreak.app.REMINDER_" + id)
                .putExtra("id", id);
        return PendingIntent.getBroadcast(c, id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void createChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Workout reminders",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Daily workout reminder and streak alerts");
        nm.createNotificationChannel(ch);
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    static void show(Context c, int id) {
        SharedPreferences p = prefs(c);
        if (today().equals(p.getString("lastDone", ""))) return; // already done today 💪

        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        int streak = p.getInt("streak", 0);
        String title;
        String text;
        if (id == EVENING) {
            title = streak > 0 ? "⚠️ Your " + streak + "-day streak is in danger!" : "⚠️ Today's workout is still waiting";
            text = "Today's workout is still waiting. Just 15 minutes — don't break the chain.";
        } else {
            title = "🔥 Earn your screen time!";
            text = streak > 0
                    ? "Day " + (streak + 1) + " is waiting. Work out before you check your phone!"
                    : "Work out before you check your phone! Start your streak today.";
        }

        Intent open = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        createChannel(c);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFF65558F)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify(id, n);
    }
}
