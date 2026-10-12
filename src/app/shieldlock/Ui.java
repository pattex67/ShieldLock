package app.shieldlock;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

/** Colors and small view helpers shared by every screen (all UI is built in code). */
final class Ui {
    static final int BG = 0xFF0E0E10;
    static final int SURFACE = 0xFF1C1C21;
    static final int SURFACE_LOW = 0xFF151519;
    static final int SURFACE_HI = 0xFF26262C;
    static final int TEXT = 0xFFF4F4F5;
    static final int MUTED = 0xFF9A9AA3;
    static final int DIM = 0xFF5C5C66;
    static final int LINE = 0xFF4A4A52;
    static final int ACCENT = 0xFFF2A93B;
    static final int ON_ACCENT = 0xFF141416;
    static final int ERROR = 0xFFE5484D;
    static final int OK = 0xFF46A758;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static StateListDrawable selector(Context c, int normal, int focused, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, round(c, focused, radiusDp));
        s.addState(new int[]{android.R.attr.state_focused}, round(c, focused, radiusDp));
        s.addState(new int[]{}, round(c, normal, radiusDp));
        return s;
    }

    static ColorStateList textSelector(int normal, int focused) {
        return new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {android.R.attr.state_pressed}, {}},
                new int[]{focused, focused, normal});
    }

    static TextView text(Context c, CharSequence s, float sp, int color, String family) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView eyebrow(Context c, CharSequence s) {
        TextView t = text(c, s, 13, ACCENT, "sans-serif-medium");
        t.setLetterSpacing(0.18f);
        t.setAllCaps(true);
        return t;
    }

    static void focusScale(View v, float scale) {
        v.setOnFocusChangeListener((view, has) -> view.animate()
                .scaleX(has ? scale : 1f).scaleY(has ? scale : 1f)
                .setDuration(140).setInterpolator(new DecelerateInterpolator()).start());
    }
}
