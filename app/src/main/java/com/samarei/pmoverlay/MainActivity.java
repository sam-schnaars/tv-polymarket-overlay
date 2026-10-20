package com.samarei.pmoverlay;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The app's one screen: switch the overlay on or off, set the stream delay and the corner, see what it
 * follows, and check the two permissions it needs. Opening it the first time turns the overlay on.
 */
public class MainActivity extends Activity {

    private float dp;
    private Typeface font;
    private LinearLayout rows;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        font = getResources().getFont(R.font.outfit);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0C0F);
        int pad = (int) (56 * dp);
        root.setPadding(pad, (int) (40 * dp), pad, pad);
        TextView title = text(getString(R.string.app_name), 34, 0xFFFFFFFF, 300);
        root.addView(title);
        TextView sub = text("Live odds over anything you watch. Hold Menu (≡) in any app to show or hide it.", 15, 0x99FFFFFF, 400);
        sub.setPadding(0, (int) (6 * dp), 0, (int) (24 * dp));
        root.addView(sub);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        root.addView(rows, new LinearLayout.LayoutParams((int) (560 * dp), -2));
        setContentView(root);

        if (savedFirstRun() && Settings.canDrawOverlays(this)) OverlayService.setOn(this, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private boolean savedFirstRun() {
        boolean first = getPreferences(MODE_PRIVATE).getBoolean("first", true);
        getPreferences(MODE_PRIVATE).edit().putBoolean("first", false).apply();
        return first;
    }

    private void render() {
        View focused = getCurrentFocus();
        int focusIndex = focused != null ? rows.indexOfChild(focused) : 0;
        rows.removeAllViews();
        boolean canDraw = Settings.canDrawOverlays(this);

        row("Overlay", OverlayService.isOn(this) ? "On" : "Off", canDraw, () -> OverlayService.toggle(this));
        row("Stream delay", Prefs.delaySeconds(this) + " seconds", true, () -> {
            int cur = Prefs.delaySeconds(this), next = Prefs.DELAYS[0];
            for (int i = 0; i < Prefs.DELAYS.length; i++) {
                if (Prefs.DELAYS[i] > cur) {
                    next = Prefs.DELAYS[i];
                    break;
                }
            }
            Prefs.setDelaySeconds(this, next);
        });
        row("Corner", label(Prefs.corner(this)), true, () -> {
            String cur = Prefs.corner(this);
            int i = 0;
            while (i < Prefs.CORNERS.length && !Prefs.CORNERS[i].equals(cur)) i++;
            Prefs.setCorner(this, Prefs.CORNERS[(i + 1) % Prefs.CORNERS.length]);
            OverlayService.restartIfOn(this);
        });
        boolean pinned = !Prefs.markets(this).isEmpty();
        row("Following", pinned ? String.join(", ", Prefs.markets(this)) + "  (select to go automatic)"
                : "Automatic: the live game, else the most traded", pinned, () -> {
            Prefs.setMarkets(this, "");
            OverlayService.restartIfOn(this);
        });
        status("Show over other apps", canDraw ? "Allowed" : "Not allowed yet. Have your agent run the install steps.");
        status("Hold Menu to toggle", keyToggleOn() ? "Ready" : "Not set up yet. Have your agent run the install steps.");

        View target = rows.getChildAt(Math.max(0, Math.min(focusIndex, rows.getChildCount() - 1)));
        if (target != null && target.isFocusable()) target.requestFocus();
    }

    private boolean keyToggleOn() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/");
    }

    private static String label(String corner) {
        String s = corner.replace('-', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void row(String name, String value, boolean enabled, Runnable action) {
        LinearLayout r = line(name, value);
        r.setFocusable(enabled);
        r.setClickable(enabled);
        r.setAlpha(enabled ? 1f : 0.5f);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, rounded(0xF2FFFFFF));
        bg.addState(new int[]{}, rounded(0x14FFFFFF));
        r.setBackground(bg);
        r.setOnFocusChangeListener((v, f) -> {
            int c = f ? 0xFF111111 : 0xFFFFFFFF;
            ((TextView) r.getChildAt(0)).setTextColor(c);
            ((TextView) r.getChildAt(1)).setTextColor(f ? 0xB3111111 : 0x99FFFFFF);
        });
        r.setOnClickListener(v -> {
            action.run();
            render();
        });
        rows.addView(r);
    }

    private void status(String name, String value) {
        LinearLayout r = line(name, value);
        r.setBackground(rounded(0x00000000));
        rows.addView(r);
    }

    private LinearLayout line(String name, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int p = (int) (18 * dp);
        r.setPadding(p, (int) (14 * dp), p, (int) (14 * dp));
        r.addView(text(name, 17, 0xFFFFFFFF, 500), new LinearLayout.LayoutParams((int) (190 * dp), -2));
        TextView v = text(value, 15, 0x99FFFFFF, 400);
        r.addView(v, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = (int) (8 * dp);
        r.setLayoutParams(lp);
        return r;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(14 * dp);
        return g;
    }

    private TextView text(String s, float sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(font, weight, false));
        return t;
    }
}
