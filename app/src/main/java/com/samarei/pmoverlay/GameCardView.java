package com.samarei.pmoverlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.util.List;
import java.util.Locale;

/**
 * The game overlay: no background, just white odds and a white line graph floating over the picture
 * (with a soft shadow so they read on bright shots): both teams' odds and a live line graph of the first
 * team's win probability since kickoff. Drawn by hand so the whole card is one cheap view.
 */
class GameCardView extends View {

    static final int W_DP = 360, H_DP = 156;

    private final float dp;
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint team = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path area = new Path();
    private final RectF box = new RectF();

    private LiveGame.View game;
    private float pulse;

    private static final int WHITE = 0xFFFFFFFF;

    GameCardView(Context c, Typeface font) {
        super(c);
        dp = getResources().getDisplayMetrics().density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, dp));
        rim.setColor(0x38FFFFFF);
        small.setColor(0x99FFFFFF);
        small.setTextSize(10 * dp);
        small.setTypeface(Typeface.MONOSPACE);
        small.setLetterSpacing(0.12f);
        team.setColor(0xCCFFFFFF);
        team.setTextSize(13 * dp);
        team.setTypeface(Typeface.create(font, 400, false));
        big.setTextSize(24 * dp);
        big.setTypeface(Typeface.create(font, 300, false));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.2f * dp);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(WHITE);
        mid.setStyle(Paint.Style.STROKE);
        mid.setStrokeWidth(dp);
        mid.setColor(0x33FFFFFF);
        mid.setPathEffect(new DashPathEffect(new float[]{4 * dp, 4 * dp}, 0));
        dot.setColor(WHITE);
        halo.setColor(WHITE);
        big.setColor(WHITE);
        // No card behind anything, so everything carries a soft dark shadow to stay legible.
        float sr = 6 * dp;
        for (Paint p : new Paint[]{team, big, small, line, dot}) p.setShadowLayer(sr, 0, dp, 0x99000000);
        mid.setShadowLayer(3 * dp, 0, 0, 0x66000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);   // shadows on paths need software drawing on this Android
    }

    void setGame(LiveGame.View g) {
        game = g;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postOnAnimation(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    /** Pulses the "now" dot about once a second; redraws only the card, cheaply. */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            pulse = (System.currentTimeMillis() % 1400) / 1400f;
            invalidate();
            postDelayed(this, 100);
        }
    };

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        // Glass: a dark, see-through tint that's lighter at the top, like light catching a pane.
        glass.setShader(new LinearGradient(0, 0, 0, h, 0x8C1A1F27, 0x660A0C10, Shader.TileMode.CLAMP));
        shine.setShader(new LinearGradient(0, 0, 0, h * 0.45f, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        fill.setShader(new LinearGradient(0, h * 0.35f, 0, h, 0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), r = 18 * dp, pad = 14 * dp;
        if (game == null) return;

        List<LiveGame.Point> pts = game.history;

        // Odds: first team left (the team the graph follows), second team right.
        double a = Math.max(0, Math.min(1, game.priceA));
        float oy = pad + 22 * dp;
        String pa = pct(a);
        c.drawText(pa, pad, oy, big);
        c.drawText(game.teamA, pad + big.measureText(pa) + 8 * dp, oy, team);
        // Derive the second side from the first's rounded value so the two always add up to 100%.
        String pb = pa.equals("<1%") ? ">99%" : pa.equals(">99%") ? "<1%" : (100 - Math.round(a * 100)) + "%";
        float pbw = big.measureText(pb);
        c.drawText(pb, w - pad - pbw, oy, big);
        c.drawText(game.teamB, w - pad - pbw - 8 * dp - team.measureText(game.teamB), oy, team);

        // Graph: first team's probability over time, 0–100% bottom to top, with a dashed 50% line.
        float gl = pad, gr = w - pad, gt = oy + 16 * dp, gb = h - pad;
        float gm = (gt + gb) / 2;
        c.drawLine(gl, gm, gr, gm, mid);
        if (pts.size() < 2) return;
        long t0 = pts.get(0).t, t1 = Math.max(pts.get(pts.size() - 1).t, t0 + 1);
        path.reset();
        area.reset();
        float lx = gl, ly = gb;
        for (int i = 0; i < pts.size(); i++) {
            LiveGame.Point p = pts.get(i);
            float x = gl + (gr - gl) * (p.t - t0) / (float) (t1 - t0);
            float yy = gb - (gb - gt) * (float) Math.max(0, Math.min(1, p.p));
            if (i == 0) {
                path.moveTo(x, yy);
                area.moveTo(x, gb);
                area.lineTo(x, yy);
            } else {
                path.lineTo(x, yy);
                area.lineTo(x, yy);
            }
            lx = x;
            ly = yy;
        }
        area.lineTo(lx, gb);
        area.close();
        c.drawPath(area, fill);
        c.drawPath(path, line);
        halo.setAlpha((int) (110 * (1 - pulse)));
        c.drawCircle(lx, ly, (3 + 7 * pulse) * dp, halo);
        c.drawCircle(lx, ly, 3.2f * dp, dot);
    }

    private static String pct(double p) {
        double v = p * 100;
        if (v > 0 && v < 1) return "<1%";
        if (v < 100 && v > 99) return ">99%";
        return String.format(Locale.US, "%.0f%%", v);
    }
}
