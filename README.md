# Android Macro (Wacom MovinkPad Quick Draw redirect)

When the pen's Quick Draw gesture wakes the tablet and opens **Wacom Canvas**, this app
immediately switches to what you choose instead:

- **Open another app** (Clip Studio Paint, ibisPaint X, Krita, anything)
- **Unlock and go to the home screen**
- **Just wake the tablet** (Canvas closes, the lock screen stays)

Opening Wacom Canvas yourself from the home screen still works normally (this can be turned off).

## How it works
Quick Draw itself is part of Wacom's system and can't be changed by a normal app. So this app
runs a small Accessibility service that only watches *which app comes to the front*. It never
reads what's on screen. When Wacom Canvas (`com.wacom.wacomui.wacomcanvas`) comes up within 3 seconds of the
screen turning on, or over the lock screen, it switches away. Canvas may flash for a split second.

## Easiest install: download the built APK
Every push to `main` is built automatically by GitHub Actions. On the MovinkPad, open
**https://github.com/Novaillusion/AndroidMacro/releases/tag/latest**, download
`AndroidMacro.apk`, open it, and allow installing from your browser when asked.
Then follow **Set up on the tablet** below. Because it's installed from a file, you will probably
need the **"Allow restricted settings"** step.

## Build and install yourself (about 10 minutes the first time)
1. Install **Android Studio** on your computer: https://developer.android.com/studio
2. **File → Open** and choose this `AndroidMacro` folder. Let it sync. It will download
   Gradle and the Android SDK the first time. If it suggests upgrading the Android Gradle Plugin,
   accepting is fine.
3. On the MovinkPad: **Settings → About tablet → tap "Build number" 7 times**, then
   **Settings → System → Developer options → USB debugging: ON**.
4. Connect the tablet with USB-C and accept the "Allow USB debugging?" prompt.
5. In Android Studio, pick the tablet in the device list and press **Run ▶**.

(Without Android Studio: run `./gradlew assembleDebug`, then
`adb install app/build/outputs/apk/debug/app-debug.apk`.)

## Set up on the tablet
1. Open **Android Macro**.
2. Tap **Open Accessibility settings**, find **Android Macro** (often under
   "Downloaded apps" or "Installed apps") and turn it on.
3. Back in the app, choose what should happen, and pick the app if needed.
4. Turn the screen off, use the pen to trigger Quick Draw, and check the result.

### "Restricted setting" message?
If you installed the APK by opening the file on the tablet (instead of via Android Studio or ADB),
Android 13+ may block the Accessibility switch. Fix: **Settings → Apps → Android Macro →
⋮ (top-right) → Allow restricted settings**, then turn the service on again.

## Notes and troubleshooting
- **PIN or password lock:** with "Open another app" or "Home screen" you'll see the unlock prompt
  first, then your app opens. Without a secure lock, it goes straight there.
- **Nothing happens:** check that the service is still ON (Android sometimes turns it off after an
  app update). Also make sure battery optimization isn't restricting the app:
  Settings → Apps → Android Macro → Battery → Unrestricted.
- **A Wacom update changed the app Quick Draw opens:** trigger Quick Draw, then in the app use
  **"Change which app counts as Quick Draw…"** and pick it. To find the exact package from a
  computer, run this right after Quick Draw opens:
  `adb shell dumpsys activity activities | grep -i ResumedActivity`
- **Canvas opens when I launch it myself:** make sure "Only when Quick Draw wakes the tablet" is
  ticked. If you unlock and open Canvas within about 3 seconds of turning the screen on, it may
  still redirect. Change `WAKE_WINDOW_MS` in `RedirectService.java` if needed.
- **Right after a reboot**, before the first unlock, the service may not run yet. Unlock once
  and it works from then on.

## Files
- `RedirectService.java`: detects Quick Draw and redirects
- `LaunchActivity.java`: invisible helper that unlocks if needed and opens your app
- `MainActivity.java`: settings screen
- `Prefs.java`: saved settings, including the default Quick Draw package name
