package app.shieldlock;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * First launch: setup wizard (code → apps → service activation).
 * Afterwards: settings screen, protected by the code.
 */
public class MainActivity extends Activity {
    private enum Mode { LOGIN, CONFIG, NEW_PIN, SETUP_PIN, SETUP_APPS, ACTIVATION }

    private static final int STEPS = 3;

    private FrameLayout root;
    private PinScreen pinScreen;
    private Mode mode;
    private String pendingPin;
    private String setupPin;
    private Set<String> locked;
    private TextView countView;
    private TextView activationStatus;
    private TextView activationNext;
    private boolean openedSystemSettings;

    private final Runnable statusPoll = new Runnable() {
        @Override
        public void run() {
            if (mode != Mode.ACTIVATION) return;
            renderActivationStatus();
            root.postDelayed(this, 1000);
        }
    };

    private static final class AppEntry {
        String pkg;
        String label;
        Drawable icon;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        if (LockConfig.isSetupDone(this)) showLogin();
        else showSetupPin();
    }

    @Override
    protected void onResume() {
        super.onResume();
        openedSystemSettings = false;
        if (mode == Mode.ACTIVATION) renderActivationStatus();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Ask for the code on every launch, except during the trip to Accessibility settings.
        if (!openedSystemSettings) finish();
    }

    // ---------------------------------------------------------------- Login

    private void showLogin() {
        mode = Mode.LOGIN;
        pinScreen = newPinScreen(getString(R.string.settings_eyebrow), getString(R.string.app_name),
                getString(R.string.login_subtitle));
        pinScreen.pad.setListener(new PinPadView.Listener() {
            @Override
            public void onPin(String pin) {
                String error = LockConfig.verify(MainActivity.this, pin);
                if (error == null) showConfig();
                else pinScreen.pad.fail(error);
            }

            @Override
            public void onCancel() {
                finish();
            }
        });
    }

    // ---------------------------------------------------------------- Choosing a code

    private void showSetupPin() {
        showPinChooser(Mode.SETUP_PIN, step(1), getString(R.string.welcome),
                getString(R.string.setup_pin_subtitle));
    }

    private void showNewPin() {
        showPinChooser(Mode.NEW_PIN, getString(R.string.new_pin_eyebrow), getString(R.string.new_pin_title),
                getString(R.string.new_pin_subtitle));
    }

    private void showPinChooser(Mode m, String eyebrow, String title, String subtitle) {
        mode = m;
        pendingPin = null;
        pinScreen = newPinScreen(eyebrow, title, subtitle);
        pinScreen.hint.setText(m == Mode.SETUP_PIN ? R.string.hint_back_quit : R.string.hint_back_cancel);
        pinScreen.pad.setListener(new PinPadView.Listener() {
            @Override
            public void onPin(String pin) {
                if (pendingPin == null) {
                    pendingPin = pin;
                    pinScreen.title.setText(R.string.confirm_pin);
                    pinScreen.pad.reset();
                    pinScreen.pad.setMessage(getString(R.string.enter_again), false);
                } else if (pendingPin.equals(pin)) {
                    if (mode == Mode.SETUP_PIN) {
                        setupPin = pin;
                        showApps(true);
                    } else {
                        LockConfig.setPin(MainActivity.this, pin);
                        Toast.makeText(MainActivity.this, R.string.pin_changed, Toast.LENGTH_SHORT).show();
                        showConfig();
                    }
                } else {
                    pendingPin = null;
                    pinScreen.title.setText(title);
                    pinScreen.pad.fail(getString(R.string.pin_mismatch));
                }
            }

            @Override
            public void onCancel() {
                onBackPressed();
            }
        });
    }

    private PinScreen newPinScreen(String eyebrow, String title, String subtitle) {
        PinScreen s = new PinScreen(this);
        s.eyebrow.setText(eyebrow);
        s.title.setText(title);
        s.subtitle.setText(subtitle);
        s.icon.setImageResource(R.drawable.ic_lock);
        root.removeAllViews();
        root.addView(s);
        s.pad.focusCenter();
        return s;
    }

    // ---------------------------------------------------------------- App list (wizard + settings)

    private void showConfig() {
        showApps(false);
    }

    private void showApps(boolean setup) {
        mode = setup ? Mode.SETUP_APPS : Mode.CONFIG;
        pinScreen = null;
        locked = setup ? LockConfig.suggestedPackages(this) : LockConfig.lockedPackages(this);
        root.removeAllViews();

        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(88), dp(56), dp(64), 0);

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setClipChildren(false);
        row.setClipChildren(false);
        side.addView(Ui.eyebrow(this, setup ? step(2) : getString(R.string.app_name)));
        side.addView(Ui.text(this, getString(setup ? R.string.apps_title : R.string.protection_title),
                40, Ui.TEXT, "sans-serif-medium"), margins(0, 20, 0, 0));

        if (setup) {
            TextView intro = Ui.text(this, getString(R.string.setup_apps_intro), 15, Ui.MUTED, "sans-serif");
            intro.setLineSpacing(dp(4), 1f);
            side.addView(intro, margins(0, 18, 0, 0));
            countView = Ui.text(this, "", 15, Ui.TEXT, "sans-serif-medium");
            side.addView(countView, margins(0, 28, 0, 0));
            TextView next = pillButton(getString(R.string.continue_btn), true);
            next.setOnClickListener(v -> {
                LockConfig.completeSetup(this, setupPin, locked);
                setupPin = null;
                showActivation(true);
            });
            side.addView(next, margins(0, 36, 0, 0));
        } else {
            boolean on = LockService.isRunning();
            TextView status = Ui.text(this, getString(on ? R.string.service_on : R.string.service_off),
                    16, on ? Ui.OK : Ui.ERROR, "sans-serif-medium");
            side.addView(status, margins(0, 18, 0, 0));
            countView = Ui.text(this, "", 15, Ui.MUTED, "sans-serif");
            side.addView(countView, margins(0, 32, 0, 0));
            TextView explain = Ui.text(this, getString(R.string.config_explain), 14, Ui.DIM, "sans-serif");
            explain.setLineSpacing(dp(4), 1f);
            side.addView(explain, margins(0, 10, 0, 0));
            if (!on) {
                TextView enable = pillButton(getString(R.string.enable_service), true);
                enable.setOnClickListener(v -> showActivation(false));
                side.addView(enable, margins(0, 36, 0, 0));
            }
            TextView changePin = pillButton(getString(R.string.change_pin), false);
            changePin.setOnClickListener(v -> showNewPin());
            side.addView(changePin, margins(0, on ? 36 : 14, 0, 0));
        }
        updateCount();

        row.addView(side, new LinearLayout.LayoutParams(dp(330), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout listCol = new LinearLayout(this);
        listCol.setOrientation(LinearLayout.VERTICAL);
        TextView header = Ui.text(this, getString(R.string.installed_apps), 14, Ui.MUTED, "sans-serif-medium");
        header.setLetterSpacing(0.08f);
        header.setPadding(dp(18), 0, 0, dp(14));
        listCol.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(4), 0, dp(48));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        View first = null;
        for (AppEntry app : loadApps()) {
            View r = appRow(app, !setup);
            if (first == null) first = r;
            list.addView(r);
        }
        scroll.addView(list);
        listCol.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        row.addView(listCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        root.addView(row, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        if (first != null) first.requestFocus();
    }

    private View appRow(AppEntry app, boolean persist) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPadding(dp(18), dp(10), dp(18), dp(10));
        r.setBackground(Ui.selector(this, 0, Ui.SURFACE_HI, 14));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        r.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(18), 0, dp(12), 0);
        TextView label = Ui.text(this, app.label, 18, Ui.TEXT, "sans-serif-medium");
        label.setSingleLine(true);
        texts.addView(label);
        TextView pkg = Ui.text(this, app.pkg, 12, Ui.DIM, "sans-serif");
        pkg.setSingleLine(true);
        texts.addView(pkg, margins(0, 5, 0, 0));
        r.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView pill = Ui.text(this, "", 13, Ui.TEXT, "sans-serif-medium");
        pill.setPadding(dp(14), dp(6), dp(14), dp(6));
        r.addView(pill);
        renderPill(pill, locked.contains(app.pkg));

        r.setOnClickListener(v -> {
            boolean now = !locked.contains(app.pkg);
            if (now) locked.add(app.pkg);
            else locked.remove(app.pkg);
            if (persist) LockConfig.setLockedPackages(this, locked);
            renderPill(pill, now);
            updateCount();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(2), 0, dp(2));
        r.setLayoutParams(lp);
        return r;
    }

    private void renderPill(TextView pill, boolean isLocked) {
        pill.setText(isLocked ? R.string.locked : R.string.unlocked);
        pill.setTextColor(isLocked ? Ui.ON_ACCENT : Ui.MUTED);
        pill.setBackground(Ui.round(this, isLocked ? Ui.ACCENT : Ui.SURFACE, 999));
    }

    private void updateCount() {
        int n = 0;
        PackageManager pm = getPackageManager();
        for (String p : locked) {
            try {
                pm.getApplicationInfo(p, 0);
                n++;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        countView.setText(getResources().getQuantityString(R.plurals.locked_count, n, n));
    }

    private List<AppEntry> loadApps() {
        PackageManager pm = getPackageManager();
        Set<String> pkgs = new HashSet<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) pkgs.add(ri.activityInfo.packageName);
        }
        // Settings and the package installer don't always have a launcher icon.
        pkgs.addAll(LockConfig.suggestedPackages(this));
        pkgs.addAll(locked);
        pkgs.remove(getPackageName());
        pkgs.removeAll(LockConfig.homePackages(this));

        List<AppEntry> apps = new ArrayList<>();
        for (String p : pkgs) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(p, 0);
                AppEntry e = new AppEntry();
                e.pkg = p;
                e.label = pm.getApplicationLabel(ai).toString();
                e.icon = pm.getApplicationIcon(ai);
                apps.add(e);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        Collator col = Collator.getInstance();
        apps.sort((a, b) -> col.compare(a.label, b.label));
        return apps;
    }

    // ---------------------------------------------------------------- Service activation

    private void showActivation(boolean fromSetup) {
        mode = Mode.ACTIVATION;
        pinScreen = null;
        root.removeAllViews();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(dp(104), dp(52), dp(88), dp(40));

        col.addView(Ui.eyebrow(this, fromSetup ? step(3) : getString(R.string.app_name)));
        col.addView(Ui.text(this, getString(R.string.activate_title), 40, Ui.TEXT, "sans-serif-medium"),
                margins(0, 20, 0, 0));

        activationStatus = Ui.text(this, "", 16, Ui.ERROR, "sans-serif-medium");
        col.addView(activationStatus, margins(0, 18, 0, 0));

        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        int[] lines = {R.string.activate_step1, R.string.activate_step2, R.string.activate_step3};
        for (int i = 0; i < lines.length; i++) {
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView num = Ui.text(this, String.valueOf(i + 1), 15, Ui.ACCENT, "sans-serif-medium");
            num.setGravity(Gravity.CENTER);
            num.setBackground(Ui.round(this, Ui.SURFACE, 999));
            line.addView(num, new LinearLayout.LayoutParams(dp(30), dp(30)));
            TextView t = Ui.text(this, getString(lines[i]), 17, Ui.TEXT, "sans-serif");
            t.setPadding(dp(16), 0, 0, 0);
            line.addView(t);
            steps.addView(line, margins(0, i == 0 ? 0 : 14, 0, 0));
        }
        col.addView(steps, margins(0, 28, 0, 0));

        TextView note = Ui.text(this, getString(R.string.activate_note), 13, Ui.DIM, "sans-serif");
        note.setLineSpacing(dp(4), 1f);
        col.addView(note, margins(0, 22, 0, 0));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setClipChildren(false);
        TextView open = pillButton(getString(R.string.open_accessibility), true);
        open.setOnClickListener(v -> openAccessibilitySettings());
        buttons.addView(open);
        activationNext = pillButton(getString(fromSetup ? R.string.finish : R.string.back), false);
        activationNext.setOnClickListener(v -> showConfig());
        buttons.addView(activationNext, margins(16, 0, 0, 0));
        col.addView(buttons, margins(0, 28, 0, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.addView(col);
        root.addView(scroll);

        renderActivationStatus();
        open.requestFocus();
        root.removeCallbacks(statusPoll);
        root.postDelayed(statusPoll, 1000);
    }

    private void renderActivationStatus() {
        if (activationStatus == null) return;
        boolean on = LockService.isRunning();
        activationStatus.setText(on ? R.string.service_on_long : R.string.service_off);
        activationStatus.setTextColor(on ? Ui.OK : Ui.ERROR);
        if (on && activationNext != null && !activationNext.hasFocus()) activationNext.requestFocus();
    }

    private void openAccessibilitySettings() {
        openedSystemSettings = true;
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (ActivityNotFoundException e2) {
                openedSystemSettings = false;
                Toast.makeText(this, R.string.settings_not_found, Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------------------------------------------------------------- Helpers

    private String step(int n) {
        return getString(R.string.step, n, STEPS);
    }

    private TextView pillButton(String label, boolean primary) {
        TextView b = Ui.text(this, label, 17, primary ? Ui.ON_ACCENT : Ui.TEXT, "sans-serif-medium");
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding(dp(26), dp(14), dp(26), dp(14));
        // Focused button turns white (as on Google TV), so two accent buttons side by side stay distinguishable.
        b.setBackground(Ui.selector(this, primary ? Ui.ACCENT : Ui.SURFACE, Ui.TEXT, 999));
        b.setTextColor(Ui.textSelector(primary ? Ui.ON_ACCENT : Ui.TEXT, Ui.ON_ACCENT));
        return b;
    }

    @Override
    public void onBackPressed() {
        switch (mode) {
            case NEW_PIN:
            case ACTIVATION:
                showConfig();
                break;
            case SETUP_APPS:
                showSetupPin();
                break;
            default:
                finish();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (pinScreen != null && pinScreen.pad.handleKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
