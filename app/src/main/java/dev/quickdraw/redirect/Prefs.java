package dev.quickdraw.redirect;

import android.content.Context;
import android.content.SharedPreferences;

/** All saved settings in one place. */
final class Prefs {
    private Prefs() {}

    static final String FILE = "config";

    static final String KEY_ENABLED = "enabled";
    static final String KEY_MODE = "mode";
    static final String KEY_TARGET = "target_pkg";
    static final String KEY_TRIGGER = "trigger_pkg";
    static final String KEY_ONLY_AFTER_WAKE = "only_after_wake";
    static final String KEY_LAST_REDIRECT = "last_redirect";

    /** Open the app the user picked. */
    static final String MODE_APP = "app";
    /** Unlock (if needed) and go to the home screen. */
    static final String MODE_HOME = "home";
    /** Just close Canvas: shows the lock screen if locked, otherwise home. */
    static final String MODE_WAKE = "wake";

    /** The app Quick Draw opens on the MovinkPad. */
    static final String DEFAULT_TRIGGER = "com.wacom.wacomui.wacomcanvas";

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean enabled(SharedPreferences p) {
        return p.getBoolean(KEY_ENABLED, true);
    }

    static String mode(SharedPreferences p) {
        return p.getString(KEY_MODE, MODE_HOME);
    }

    static String target(SharedPreferences p) {
        return p.getString(KEY_TARGET, "");
    }

    static String trigger(SharedPreferences p) {
        String t = p.getString(KEY_TRIGGER, DEFAULT_TRIGGER);
        return (t == null || t.trim().isEmpty()) ? DEFAULT_TRIGGER : t.trim();
    }

    static boolean onlyAfterWake(SharedPreferences p) {
        return p.getBoolean(KEY_ONLY_AFTER_WAKE, true);
    }
}
