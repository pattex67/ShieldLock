package app.shieldlock;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen PIN layout: info column on the left, keypad on the right. */
final class PinScreen extends FrameLayout {
    final TextView eyebrow;
    final ImageView icon;
    final TextView title;
    final TextView subtitle;
    final TextView hint;
    final PinPadView pad;

    PinScreen(Context c) {
        super(c);
        setBackgroundColor(Ui.BG);
        setClipChildren(false);

        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setPadding(Ui.dp(c, 104), 0, Ui.dp(c, 88), 0);

        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);

        eyebrow = Ui.eyebrow(c, c.getString(R.string.parental_control));
        info.addView(eyebrow);

        icon = new ImageView(c);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 64));
        ilp.setMargins(0, Ui.dp(c, 36), 0, Ui.dp(c, 20));
        info.addView(icon, ilp);

        title = Ui.text(c, "", 44, Ui.TEXT, "sans-serif-medium");
        title.setMaxLines(2);
        info.addView(title);

        subtitle = Ui.text(c, "", 17, Ui.MUTED, "sans-serif");
        subtitle.setLineSpacing(Ui.dp(c, 5), 1f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.setMargins(0, Ui.dp(c, 14), 0, 0);
        info.addView(subtitle, slp);

        hint = Ui.text(c, c.getString(R.string.hint_back_quit), 13, Ui.DIM, "sans-serif");
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.setMargins(0, Ui.dp(c, 64), 0, 0);
        info.addView(hint, hlp);

        row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        pad = new PinPadView(c);
        row.addView(pad);

        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }
}
