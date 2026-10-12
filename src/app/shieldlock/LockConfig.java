package app.shieldlock;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** PIN, locked app list and brute-force protection, stored in SharedPreferences. */
final class LockConfig {

    /**
     * Apps pre-selected by the setup wizard when installed. Missing packages are ignored,
     * so the list can be generous. Pull requests adding packages are welcome.
     */
    static final String[] SUGGESTED = {
            // YouTube and alternative clients
            "com.google.android.youtube.tv",
            "com.google.android.youtube.tvmusic",
            "com.google.android.youtube",
            "app.rvx.android.youtube",
            "app.revanced.android.youtube",
            "com.liskovsoft.smarttubetv",
            "com.liskovsoft.smarttubetv.beta",
            // Video streaming
            "com.netflix.ninja",
            "com.netflix.mediaclient",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.amazon.amazonvideo.livingroom.nvidia",
            "com.amazon.avod.thirdpartyclient",
            "com.crunchyroll.crunchyroid",
            "tv.twitch.android.app",
            "com.wbd.stream",
            "com.cbs.ott",
            "com.apple.atve.androidtv.appletv",
            "com.hulu.livingroomplus",
            "com.peacocktv.peacockandroid",
            "tv.pluto.android",
            "com.tubitv",
            "com.dazn",
            "com.viki.android",
            "com.canal.android.canal",
            "tv.molotov.app",
            "tv.arte.plus7",
            "fr.tf1.mytf1",
            "fr.m6.m6replay",
            "fr.francetv.pluzz",
            "net.oqee.androidtv",
            "net.oqee.androidtv.store",
            // Media servers and players
            "com.plexapp.android",
            "org.jellyfin.androidtv",
            "tv.emby.embyatv",
            "org.xbmc.kodi",
            "org.videolan.vlc",
            "com.mxtech.videoplayer.ad",
            "com.mxtech.videoplayer.television",
            "com.stremio.one",
            // IPTV
            "iptv.smarters.player",
            "com.nst.iptvsmarterstvbox",
            "ar.tvplayer.tv",
            "ru.iptvremote.android.iptv",
            "eu.siptv.video",
            "com.devcoder.iptvxtreamplayer",
            "com.premiumott.premiumottiptvbox",
            "com.aloj22.iptvprostreamplayer",
            "com.flextv.livestore",
            "com.nathnetwork.inos",
            "com.sidhbalitech.ninexplayer",
            "com.shadeed.dinoibo",
            "m.formuler.mol.plus",
            "com.sazpin.iboapp",
            // Browsers (can play online video)
            "com.phlox.tvwebbrowser",
            "com.cloudmosa.puffinTV",
            "com.android.chrome",
            "org.mozilla.firefox",
            // App stores and package installers
            "com.android.vending",
            "com.amazon.venezia",
            "com.aurora.store",
            "org.fdroid.fdroid",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            // Settings (prevents turning the service off)
            "com.android.tv.settings",
            "com.android.settings",
            // Sideloading and file managers
            "com.esaba.downloader",
            "com.alphainventor.filemanager",
            "com.mobisystems.fileman",
            "com.estrongs.android.pop",
            "com.lonelycatgames.Xplore",
            "com.cxinventor.file.explorer",
            "com.revanced.net.revancedmanager",
            "app.revanced.manager.flutter",
            "eu.chainfire.tv.sideloadlauncher",
    };

    private static final String PREFS = "shieldlock";
    private static final String KEY_SETUP_DONE = "setup_done";
    private static final String KEY_PIN = "pin_hash";
    private static final String KEY_SALT = "pin_salt";
    private static final String KEY_LOCKED = "locked";
    private static final String KEY_FAILS = "fails";
    private static final String KEY_LOCKOUT = "lockout_until";

    private LockConfig() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isSetupDone(Context c) {
        return prefs(c).getBoolean(KEY_SETUP_DONE, false);
    }

    /** Ends the wizard: stores the code and the list, and turns locking on. */
    static void completeSetup(Context c, String pin, Set<String> locked) {
        String salt = newSalt();
        prefs(c).edit()
                .putString(KEY_SALT, salt)
                .putString(KEY_PIN, hash(salt, pin))
                .putStringSet(KEY_LOCKED, new HashSet<>(locked))
                .putInt(KEY_FAILS, 0)
                .putLong(KEY_LOCKOUT, 0)
                .putBoolean(KEY_SETUP_DONE, true)
                .commit();
    }

    /** What the service must lock: nothing until the wizard is done. */
    static Set<String> activeLockedPackages(Context c) {
        return isSetupDone(c) ? lockedPackages(c) : Collections.<String>emptySet();
    }

    static Set<String> lockedPackages(Context c) {
        Set<String> s = prefs(c).getStringSet(KEY_LOCKED, null);
        return s == null ? new HashSet<String>() : new HashSet<>(s);
    }

    static void setLockedPackages(Context c, Set<String> s) {
        prefs(c).edit().putStringSet(KEY_LOCKED, new HashSet<>(s)).apply();
    }

    /** Installed apps to pre-select: the known list plus this device's Settings and installer. */
    static Set<String> suggestedPackages(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> candidates = new HashSet<>(Arrays.asList(SUGGESTED));
        addResolved(pm, new Intent(Settings.ACTION_SETTINGS), candidates);
        addResolved(pm, new Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("content://shieldlock/app.apk"), "application/vnd.android.package-archive"), candidates);

        Set<String> result = new HashSet<>();
        for (String p : candidates) {
            try {
                pm.getApplicationInfo(p, 0);
                result.add(p);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        result.remove(c.getPackageName());
        result.removeAll(homePackages(c));
        return result;
    }

    private static void addResolved(PackageManager pm, Intent i, Set<String> out) {
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String p = ri.activityInfo.packageName;
            if (!"android".equals(p)) out.add(p);
        }
    }

    static void setPin(Context c, String pin) {
        String salt = newSalt();
        prefs(c).edit().putString(KEY_SALT, salt).putString(KEY_PIN, hash(salt, pin)).apply();
    }

    /** Returns null when the code is right, otherwise the error message to show. */
    static String verify(Context c, String pin) {
        SharedPreferences p = prefs(c);
        long wait = lockoutRemaining(c);
        if (wait > 0) return c.getString(R.string.err_too_many, seconds(wait));

        String expected = p.getString(KEY_PIN, null);
        String salt = p.getString(KEY_SALT, "");
        if (expected != null && expected.equals(hash(salt, pin))) {
            p.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCKOUT, 0).apply();
            return null;
        }

        // Every 5 wrong codes: 30 s lockout, growing by 30 s each time, capped at 10 min.
        int fails = p.getInt(KEY_FAILS, 0) + 1;
        SharedPreferences.Editor e = p.edit().putInt(KEY_FAILS, fails);
        if (fails % 5 == 0) {
            long penalty = Math.min(30_000L * (fails / 5), 10 * 60_000L);
            e.putLong(KEY_LOCKOUT, System.currentTimeMillis() + penalty);
            e.apply();
            return c.getString(R.string.err_wrong_blocked, seconds(penalty));
        }
        e.apply();
        return c.getString(R.string.err_wrong);
    }

    static Set<String> homePackages(Context c) {
        Set<String> homes = new HashSet<>();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        PackageManager pm = c.getPackageManager();
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            // Android TV Settings declares a FallbackHome with a negative priority: not a real launcher.
            if (ri.priority >= 0) homes.add(ri.activityInfo.packageName);
        }
        ResolveInfo def = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        if (def != null && !"android".equals(def.activityInfo.packageName)) {
            homes.add(def.activityInfo.packageName);
        }
        return homes;
    }

    private static long lockoutRemaining(Context c) {
        return Math.max(0, prefs(c).getLong(KEY_LOCKOUT, 0) - System.currentTimeMillis());
    }

    private static int seconds(long ms) {
        return (int) ((ms + 999) / 1000);
    }

    private static String newSalt() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return hex(b);
    }

    private static String hash(String salt, String pin) {
        try {
            return hex(MessageDigest.getInstance("SHA-256")
                    .digest((salt + ":" + pin).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
