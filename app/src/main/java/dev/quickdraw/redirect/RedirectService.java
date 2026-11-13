package dev.quickdraw.redirect;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Watches which app comes to the front. When the Quick Draw app (Wacom Canvas) appears
 * right after the tablet wakes, it switches to whatever the user chose instead.
 */
public class RedirectService extends AccessibilityService {

    /** Quick Draw opens Canvas within a moment of the screen turning on. */
    private static final long WAKE_WINDOW_MS = 3000;
    /** Ignore repeat events from the same launch. */
    private static final long COOLDOWN_MS = 2000;
    /** Let Canvas finish appearing before switching away. */
    private static final long SWITCH_DELAY_MS = 150;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long screenOnAt = 0;
    private long lastRedirectAt = 0;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                screenOnAt = SystemClock.elapsedRealtime();
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
        receiverRegistered = true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;

        SharedPreferences p = Prefs.get(this);
        if (!Prefs.enabled(p)) return;
        if (!Prefs.trigger(p).contentEquals(pkg)) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastRedirectAt < COOLDOWN_MS) return;

        if (Prefs.onlyAfterWake(p)) {
            // Only act when Canvas was opened by Quick Draw, not when you open it yourself.
            boolean justWoke = screenOnAt > 0 && (now - screenOnAt) < WAKE_WINDOW_MS;
            KeyguardManager km = getSystemService(KeyguardManager.class);
            boolean overLockScreen = km != null && km.isKeyguardLocked();
            if (!justWoke && !overLockScreen) return;
        }

        screenOnAt = 0;
        lastRedirectAt = now;
        p.edit().putLong(Prefs.KEY_LAST_REDIRECT, System.currentTimeMillis()).apply();

        final String mode = Prefs.mode(p);
        handler.postDelayed(() -> redirect(mode), SWITCH_DELAY_MS);
    }

    private void redirect(String mode) {
        if (Prefs.MODE_WAKE.equals(mode)) {
            // Behaves like pressing Home: Canvas closes, you land on the lock screen
            // (if locked) or your home screen.
            performGlobalAction(GLOBAL_ACTION_HOME);
            return;
        }
        // MODE_APP or MODE_HOME: the helper activity asks to unlock if needed, then opens the target.
        Intent i = new Intent(this, LaunchActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
    }

    @Override
    public void onInterrupt() {
        // Nothing to interrupt.
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            receiverRegistered = false;
        }
        super.onDestroy();
    }
}
