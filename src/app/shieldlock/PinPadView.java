package app.shieldlock;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 4-digit keypad driven by the remote's D-pad, also accepting physical number keys. */
final class PinPadView extends LinearLayout {
    interface Listener {
        void onPin(String pin);

        void onCancel();
    }

    static final int LENGTH = 4;
    private static final String DEL = "←";
    private static final String CANCEL = "×";

    private final StringBuilder entry = new StringBuilder();
    private final GradientDrawable[] dots = new GradientDrawable[LENGTH];
    private final TextView message;
    private TextView centerKey;
    private Listener listener;
    private boolean busy;

    PinPadView(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setClipChildren(false);
        setClipToPadding(false);

        LinearLayout dotRow = new LinearLayout(c);
        for (int i = 0; i < LENGTH; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            dots[i] = g;
            View d = new View(c);
            d.setBackground(g);
            LayoutParams lp = new LayoutParams(Ui.dp(c, 16), Ui.dp(c, 16));
            lp.setMargins(Ui.dp(c, 11), 0, Ui.dp(c, 11), 0);
            dotRow.addView(d, lp);
        }
        addView(dotRow);

        message = Ui.text(c, "", 15, Ui.MUTED, "sans-serif");
        message.setGravity(Gravity.CENTER);
        LayoutParams mlp = new LayoutParams(Ui.dp(c, 320), Ui.dp(c, 22));
        mlp.setMargins(0, Ui.dp(c, 22), 0, Ui.dp(c, 22));
        addView(message, mlp);

        String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {DEL, "0", CANCEL}};
        for (String[] row : rows) {
            LinearLayout r = new LinearLayout(c);
            r.setClipChildren(false);
            for (String k : row) {
                TextView key = makeKey(c, k);
                if ("5".equals(k)) centerKey = key;
                LayoutParams klp = new LayoutParams(Ui.dp(c, 84), Ui.dp(c, 60));
                klp.setMargins(Ui.dp(c, 7), Ui.dp(c, 7), Ui.dp(c, 7), Ui.dp(c, 7));
                r.addView(key, klp);
            }
            addView(r);
        }
        renderDots();
    }

    private TextView makeKey(Context c, String k) {
        boolean digit = Character.isDigit(k.charAt(0));
        TextView t = Ui.text(c, k, digit ? 28 : 26, Ui.TEXT, "sans-serif-medium");
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(Ui.selector(c, digit ? Ui.SURFACE : Ui.SURFACE_LOW, Ui.ACCENT, 16));
        t.setTextColor(Ui.textSelector(digit ? Ui.TEXT : Ui.MUTED, Ui.ON_ACCENT));
        Ui.focusScale(t, 1.08f);
        t.setOnClickListener(v -> press(k));
        return t;
    }

    void setListener(Listener l) {
        listener = l;
    }

    void focusCenter() {
        if (centerKey != null) centerKey.requestFocus();
    }

    void setMessage(CharSequence m, boolean error) {
        message.setText(m);
        message.setTextColor(error ? Ui.ERROR : Ui.MUTED);
    }

    void reset() {
        busy = false;
        entry.setLength(0);
        renderDots();
    }

    void fail(CharSequence m) {
        setMessage(m, true);
        for (GradientDrawable g : dots) {
            g.setStroke(0, 0);
            g.setColor(Ui.ERROR);
        }
        ObjectAnimator.ofFloat(this, "translationX", 0, 22, -18, 14, -8, 4, 0).setDuration(380).start();
        postDelayed(this::reset, 650);
    }

    boolean handleKey(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
        int code = e.getKeyCode();
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            press(String.valueOf(code - KeyEvent.KEYCODE_0));
            return true;
        }
        if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) {
            press(String.valueOf(code - KeyEvent.KEYCODE_NUMPAD_0));
            return true;
        }
        if (code == KeyEvent.KEYCODE_DEL) {
            press(DEL);
            return true;
        }
        return false;
    }

    private void press(String k) {
        if (busy) return;
        if (DEL.equals(k)) {
            if (entry.length() > 0) entry.setLength(entry.length() - 1);
            renderDots();
            return;
        }
        if (CANCEL.equals(k)) {
            if (listener != null) listener.onCancel();
            return;
        }
        if (entry.length() >= LENGTH) return;
        entry.append(k);
        renderDots();
        if (entry.length() == LENGTH) {
            busy = true;
            String pin = entry.toString();
            postDelayed(() -> {
                if (listener != null) listener.onPin(pin);
            }, 140);
        }
    }

    private void renderDots() {
        for (int i = 0; i < LENGTH; i++) {
            if (i < entry.length()) {
                dots[i].setStroke(0, 0);
                dots[i].setColor(Ui.ACCENT);
            } else {
                dots[i].setColor(0);
                dots[i].setStroke(Ui.dp(getContext(), 2), Ui.LINE);
            }
        }
    }
}
