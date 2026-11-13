package dev.quickdraw.redirect;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Invisible helper. If the tablet is locked it shows the PIN/pattern prompt first,
 * then opens the chosen app (or the home screen) and closes itself.
 */
public class LaunchActivity extends Activity {

    private boolean done = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);

        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km != null && km.isKeyguardLocked()) {
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override
                public void onDismissSucceeded() {
                    launchTarget();
                }

                @Override
                public void onDismissCancelled() {
                    finishQuietly();
                }

                @Override
                public void onDismissError() {
                    launchTarget();
                }
            });
        } else {
            launchTarget();
        }
    }

    private void launchTarget() {
        if (done) return;
        done = true;

        SharedPreferences p = Prefs.get(this);
        Intent target = null;
        if (Prefs.MODE_APP.equals(Prefs.mode(p))) {
            String pkg = Prefs.target(p);
            if (!pkg.isEmpty()) {
                target = getPackageManager().getLaunchIntentForPackage(pkg);
            }
            if (target == null) {
                Toast.makeText(this, "Chosen app not found, going home instead", Toast.LENGTH_SHORT).show();
            }
        }
        if (target == null) {
            target = new Intent(Intent.ACTION_MAIN);
            target.addCategory(Intent.CATEGORY_HOME);
        }
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        try {
            startActivity(target);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Could not open the chosen app", Toast.LENGTH_SHORT).show();
        }
        finishQuietly();
    }

    private void finishQuietly() {
        done = true;
        finish();
    }
}
