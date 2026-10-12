package app.shieldlock;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;

/** Shown by LockService on top of a locked app. */
public class PinActivity extends Activity {
    static final String EXTRA_PKG = "pkg";

    private PinScreen screen;
    private String target;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        screen = new PinScreen(this);
        setContentView(screen);
        screen.pad.setListener(new PinPadView.Listener() {
            @Override
            public void onPin(String pin) {
                String error = LockConfig.verify(PinActivity.this, pin);
                if (error == null) {
                    LockService.grant(target);
                    finish();
                    overridePendingTransition(0, 0);
                } else {
                    screen.pad.fail(error);
                }
            }

            @Override
            public void onCancel() {
                leave();
            }
        });
        bind(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        bind(intent);
    }

    private void bind(Intent intent) {
        target = intent.getStringExtra(EXTRA_PKG);
        PackageManager pm = getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(target, 0);
            screen.title.setText(pm.getApplicationLabel(ai));
            screen.icon.setImageDrawable(pm.getApplicationIcon(ai));
        } catch (Exception e) {
            screen.title.setText(target);
            screen.icon.setImageResource(R.drawable.ic_lock);
        }
        screen.subtitle.setText(R.string.pin_locked_subtitle);
        screen.pad.reset();
        screen.pad.setMessage("", false);
        screen.pad.focusCenter();
    }

    private void leave() {
        startActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onBackPressed() {
        leave();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (screen.pad.handleKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Don't leave the PIN screen lying around: the service shows it again when needed.
        if (!isFinishing()) finish();
    }
}
