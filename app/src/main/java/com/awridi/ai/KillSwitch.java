package com.awridi.ai;

import android.content.SharedPreferences;

/**
 * KillSwitch
 * Emergency Stop mechanism to halt all trading activities and block order creation/execution.
 * Implemented with thread-safe atomic state and SharedPreferences backing.
 */
public class KillSwitch {

    public static final String PREF_KEY_KILL_SWITCH = "kill_switch_active";
    private static volatile boolean activeMemoryState = false;

    private static final Object LOCK = new Object();

    public static boolean isActive(SharedPreferences prefs) {
        synchronized (LOCK) {
            if (prefs != null) {
                activeMemoryState = prefs.getBoolean(PREF_KEY_KILL_SWITCH, false);
            }
            return activeMemoryState;
        }
    }

    public static void activate(SharedPreferences prefs) {
        synchronized (LOCK) {
            activeMemoryState = true;
            if (prefs != null) {
                prefs.edit().putBoolean(PREF_KEY_KILL_SWITCH, true).apply();
            }
        }
    }

    public static void deactivate(SharedPreferences prefs) {
        synchronized (LOCK) {
            activeMemoryState = false;
            if (prefs != null) {
                prefs.edit().putBoolean(PREF_KEY_KILL_SWITCH, false).apply();
            }
        }
    }

    public static void resetMemoryState() {
        synchronized (LOCK) {
            activeMemoryState = false;
        }
    }
}
