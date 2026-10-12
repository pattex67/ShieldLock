package app.shieldlock;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Watches the foreground app. When it is locked and not yet unlocked, shows PinActivity on top.
 * An unlock lasts until the user goes home, opens another app, or the device goes to sleep.
 */
public class LockService extends AccessibilityService {
    private static final long POLL_MS = 1500;

    /** Overlays drawn on top of an app without leaving it (voice search, system UI, ...). */
    private static final Set<String> PASS_THROUGH = new HashSet<>(Arrays.asList(
            "android",
            "com.android.systemui",
            "com.google.android.katniss",
            "com.google.android.gms",
            "com.google.android.tvrecommendations"));

    private static volatile String sUnlockedPkg;
    private static volatile boolean sRunning;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Boolean> launchable = new HashMap<>();
    private Set<String> locked = new HashSet<>();
    private Set<String> homes = new HashSet<>();
    private String lastPromptPkg;
    private long lastPromptAt;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            (p, key) -> reload();

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            sUnlockedPkg = null;
        }
    };

    /** Safety net for transitions that don't fire a window event (e.g. leaving the screensaver). */
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            try {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm == null || pm.isInteractive()) {
                    AccessibilityNodeInfo root = getRootInActiveWindow();
                    if (root != null) {
                        CharSequence pkg = root.getPackageName();
                        root.recycle();
                        if (pkg != null) onForeground(pkg.toString());
                    }
                }
            } catch (Throwable ignored) {
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    static void grant(String pkg) {
        sUnlockedPkg = pkg;
    }

    static boolean isRunning() {
        return sRunning;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sRunning = true;
        reload();
        LockConfig.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_DREAMING_STARTED);
        registerReceiver(screenReceiver, f);
        handler.postDelayed(poll, POLL_MS);
    }

    private void reload() {
        homes = LockConfig.homePackages(this);
        Set<String> s = new HashSet<>(LockConfig.activeLockedPackages(this));
        s.removeAll(homes); // never lock the launcher, that would loop forever
        locked = s;
        launchable.clear();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg != null) {
            try {
                onForeground(pkg.toString());
            } catch (Throwable ignored) {
            }
        }
    }

    private void onForeground(String pkg) {
        if (pkg.equals(getPackageName()) || PASS_THROUGH.contains(pkg)) return;

        if (locked.contains(pkg)) {
            if (pkg.equals(sUnlockedPkg)) return;
            sUnlockedPkg = null;
            prompt(pkg);
        } else if (homes.contains(pkg) || isLaunchable(pkg)) {
            sUnlockedPkg = null;
        }
    }

    private boolean isLaunchable(String pkg) {
        Boolean cached = launchable.get(pkg);
        if (cached != null) return cached;
        PackageManager pm = getPackageManager();
        boolean result = pm.getLeanbackLaunchIntentForPackage(pkg) != null
                || pm.getLaunchIntentForPackage(pkg) != null;
        launchable.put(pkg, result);
        return result;
    }

    private void prompt(String pkg) {
        long now = SystemClock.elapsedRealtime();
        if (pkg.equals(lastPromptPkg) && now - lastPromptAt < 800) return;
        lastPromptPkg = pkg;
        lastPromptAt = now;
        Intent i = new Intent(this, PinActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(PinActivity.EXTRA_PKG, pkg);
        startActivity(i);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        sRunning = false;
        handler.removeCallbacks(poll);
        LockConfig.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener);
        try {
            unregisterReceiver(screenReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        super.onDestroy();
    }
}
