package dev.quickdraw.redirect;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Settings screen. Built in code so the project needs no layout files or libraries. */
public class MainActivity extends Activity {

    private SharedPreferences prefs;

    private TextView serviceStatus;
    private Button openAccessibility;
    private Switch enabledSwitch;
    private RadioButton rbApp, rbHome, rbWake;
    private Button chooseTarget;
    private CheckBox onlyAfterWake;
    private TextView triggerLabel;
    private TextView lastRedirect;

    private static final class AppEntry {
        final String pkg;
        final String label;

        AppEntry(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ---------- UI ----------

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        // 1. Service status
        root.addView(header("1. Turn on the service"));
        serviceStatus = text("");
        root.addView(serviceStatus);
        openAccessibility = button("Open Accessibility settings");
        openAccessibility.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(openAccessibility);

        // 2. What to do
        root.addView(header("2. When Quick Draw opens Wacom Canvas…"));
        enabledSwitch = new Switch(this);
        enabledSwitch.setText("Redirect is on");
        enabledSwitch.setTextSize(16);
        enabledSwitch.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(Prefs.KEY_ENABLED, checked).apply());
        root.addView(enabledSwitch);

        RadioGroup modes = new RadioGroup(this);
        rbApp = radio("Open another app");
        rbHome = radio("Unlock and go to the home screen");
        rbWake = radio("Just wake the tablet (lock screen stays)");
        modes.addView(rbApp);
        modes.addView(rbHome);
        modes.addView(rbWake);
        modes.setOnCheckedChangeListener((g, id) -> {
            String mode = id == rbApp.getId() ? Prefs.MODE_APP
                    : id == rbWake.getId() ? Prefs.MODE_WAKE : Prefs.MODE_HOME;
            prefs.edit().putString(Prefs.KEY_MODE, mode).apply();
            chooseTarget.setEnabled(Prefs.MODE_APP.equals(mode));
        });
        root.addView(modes);

        chooseTarget = button("Choose app…");
        chooseTarget.setOnClickListener(v -> pickApp(false));
        root.addView(chooseTarget);

        // 3. Options
        root.addView(header("3. Options"));
        onlyAfterWake = new CheckBox(this);
        onlyAfterWake.setText("Only when Quick Draw wakes the tablet\n(opening Wacom Canvas yourself still works normally)");
        onlyAfterWake.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(Prefs.KEY_ONLY_AFTER_WAKE, checked).apply());
        root.addView(onlyAfterWake);

        triggerLabel = text("");
        root.addView(triggerLabel);
        Button chooseTrigger = button("Change which app counts as Quick Draw…");
        chooseTrigger.setOnClickListener(v -> pickApp(true));
        root.addView(chooseTrigger);

        // 4. Test
        root.addView(header("4. Test"));
        Button test = button("Run the redirect now");
        test.setOnClickListener(v -> {
            if (Prefs.MODE_WAKE.equals(Prefs.mode(prefs))) {
                Toast.makeText(this, "\"Just wake\" can only be tested with the pen while the screen is off",
                        Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(this, LaunchActivity.class));
        });
        root.addView(test);
        lastRedirect = text("");
        root.addView(lastRedirect);

        return scroll;
    }

    private void refresh() {
        boolean on = isServiceEnabled();
        serviceStatus.setText(on
                ? "✅ Service is ON"
                : "❌ Service is OFF. Tap below, find \"Android Macro\" (often under \"Downloaded apps\" or \"Installed apps\") and turn it on.");
        openAccessibility.setText(on ? "Accessibility settings" : "Open Accessibility settings");

        enabledSwitch.setChecked(Prefs.enabled(prefs));

        String mode = Prefs.mode(prefs);
        if (Prefs.MODE_APP.equals(mode)) rbApp.setChecked(true);
        else if (Prefs.MODE_WAKE.equals(mode)) rbWake.setChecked(true);
        else rbHome.setChecked(true);
        chooseTarget.setEnabled(Prefs.MODE_APP.equals(mode));

        String target = Prefs.target(prefs);
        chooseTarget.setText(target.isEmpty() ? "Choose app…" : "App: " + labelFor(target) + "  (tap to change)");

        onlyAfterWake.setChecked(Prefs.onlyAfterWake(prefs));

        String trigger = Prefs.trigger(prefs);
        boolean triggerInstalled = isInstalled(trigger);
        triggerLabel.setText("Quick Draw app: " + labelFor(trigger) + "\n" + trigger
                + (triggerInstalled ? "" : "\n⚠ Not found on this tablet. Pick the app that Quick Draw opens."));

        long last = prefs.getLong(Prefs.KEY_LAST_REDIRECT, 0);
        lastRedirect.setText(last == 0 ? "No redirects yet."
                : "Last redirect: " + DateFormat.format("yyyy-MM-dd HH:mm:ss", last));
    }

    // ---------- App picker ----------

    private void pickApp(boolean forTrigger) {
        List<AppEntry> apps = loadLaunchableApps();
        List<String> labels = new ArrayList<>();
        int offset = 0;
        if (forTrigger) {
            labels.add("↺ Reset to Wacom Canvas (default)");
            offset = 1;
        }
        for (AppEntry a : apps) labels.add(a.label);

        final int off = offset;
        new AlertDialog.Builder(this)
                .setTitle(forTrigger ? "Which app does Quick Draw open?" : "Open this app instead")
                .setItems(labels.toArray(new CharSequence[0]), (d, which) -> {
                    if (forTrigger && which == 0) {
                        prefs.edit().putString(Prefs.KEY_TRIGGER, Prefs.DEFAULT_TRIGGER).apply();
                        refresh();
                        return;
                    }
                    String pkg = apps.get(which - off).pkg;
                    if (pkg.equals(getPackageName())) {
                        Toast.makeText(this, "Pick a different app", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (forTrigger) {
                        if (pkg.equals(Prefs.target(prefs))) {
                            Toast.makeText(this, "That's your redirect target. Pick a different app.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        prefs.edit().putString(Prefs.KEY_TRIGGER, pkg).apply();
                    } else {
                        if (pkg.equals(Prefs.trigger(prefs))) {
                            Toast.makeText(this, "That's the Quick Draw app itself. Pick a different app.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        prefs.edit().putString(Prefs.KEY_TARGET, pkg).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private List<AppEntry> loadLaunchableApps() {
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, String> byPkg = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (!byPkg.containsKey(pkg)) {
                byPkg.put(pkg, String.valueOf(ri.loadLabel(pm)));
            }
        }
        List<AppEntry> list = new ArrayList<>();
        for (Map.Entry<String, String> e : byPkg.entrySet()) {
            list.add(new AppEntry(e.getKey(), e.getValue()));
        }
        Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return list;
    }

    // ---------- Helpers ----------

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(this, RedirectService.class);
        for (String part : enabled.split(":")) {
            if (me.equals(ComponentName.unflattenFromString(part))) return true;
        }
        return false;
    }

    private boolean isInstalled(String pkg) {
        try {
            getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String labelFor(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private TextView header(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(20), 0, dp(6));
        return t;
    }

    private TextView text(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    private RadioButton radio(String s) {
        RadioButton r = new RadioButton(this);
        r.setText(s);
        r.setTextSize(16);
        r.setId(View.generateViewId());
        return r;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
