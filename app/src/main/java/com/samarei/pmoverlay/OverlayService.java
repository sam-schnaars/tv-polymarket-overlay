package com.samarei.pmoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The overlay: drawn over everything (apps, HDMI, Live TV), with live odds from Polymarket's public market
 * data. Read-only: no account, no trading.
 *
 * While a game is on ({@link LiveGame}) it shows that game: both teams' odds and a live graph since
 * kickoff, checked every second and shown with a delay ({@link Prefs#delaySeconds}) so it moves with the
 * stream instead of ahead of it. Otherwise it lists the most-traded markets (or the pinned ones), every
 * minute. Hold the remote's Menu button in any app to show or hide it ({@link KeyToggleService}).
 */
public class OverlayService extends Service {

    private static final String API = "https://gamma-api.polymarket.com/markets";
    private static final long REFRESH_MS = 60_000L;
    private static final long GAME_REFRESH_MS = 1_000L;
    private static final int MAX_MARKETS = 3;

    static final class Market {
        String question;
        String outcome;
        double price;
        double change;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** One background thread does all the network work, once a second during a game. */
    private android.os.HandlerThread worker;
    private Handler work;
    private WindowManager wm;
    private LinearLayout card;
    private LinearLayout rows;
    private GameCardView gameCard;
    private TextView status;
    private Typeface font;
    private float dp;
    private volatile boolean running;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long started = System.currentTimeMillis();
            long next;
            LiveGame.Game game = LiveGame.current(Prefs.markets(OverlayService.this), Prefs.leagues(OverlayService.this));
            if (game != null) {
                LiveGame.update(game);
                LiveGame.View view = LiveGame.delayed(game, Prefs.delaySeconds(OverlayService.this) * 1000L);
                handler.post(() -> showGame(view));
                next = GAME_REFRESH_MS;
            } else {
                List<Market> markets = fetch();
                handler.post(() -> show(markets));
                next = REFRESH_MS;
            }
            // Keep a steady once-a-second rhythm however long the request took.
            if (running) work.postDelayed(this, Math.max(0, next - (System.currentTimeMillis() - started)));
        }
    };

    /** Shows or hides the overlay from anywhere (the app, adb, or holding the remote's Menu button). */
    static void toggle(Context c) {
        setOn(c, !isOn(c));
    }

    /** Restarts a running overlay so new settings (corner, markets) take effect. */
    static void restartIfOn(Context c) {
        LiveGame.reset();
        if (isOn(c)) {
            c.stopService(new Intent(c, OverlayService.class));
            setOn(c, true);
        }
    }

    static boolean isOn(Context c) {
        return Prefs.enabled(c);
    }

    static void setOn(Context c, boolean on) {
        if (on && !android.provider.Settings.canDrawOverlays(c)) return;
        Prefs.setEnabled(c, on);
        Intent i = new Intent(c, OverlayService.class);
        if (on) c.startForegroundService(i);
        else c.stopService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("overlay", "Polymarket overlay", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "overlay")
                .setContentTitle("Polymarket overlay")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build();
        startForeground(1, n);

        dp = getResources().getDisplayMetrics().density;
        font = getResources().getFont(R.font.outfit);
        wm = getSystemService(WindowManager.class);
        buildCard();
        running = true;
        worker = new android.os.HandlerThread("polymarket");
        worker.start();
        work = new Handler(worker.getLooper());
        work.post(refresh);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (work != null) work.removeCallbacks(refresh);
        if (worker != null) worker.quitSafely();
        if (card != null && card.isAttachedToWindow()) wm.removeView(card);
        if (gameCard != null && gameCard.isAttachedToWindow()) wm.removeView(gameCard);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---- view --------------------------------------------------------------------------------------

    private void buildCard() {
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (14 * dp);
        card.setPadding(p, (int) (12 * dp), p, (int) (12 * dp));
        // No background, like the game card: white text with a soft shadow, straight over the picture.

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = text("POLYMARKET", 10, 0x8CFFFFFF, 500);
        title.setLetterSpacing(0.16f);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        status = text("", 10, 0x66FFFFFF, 400);
        head.addView(status);
        card.addView(head);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        card.addView(rows);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                (int) (300 * dp), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Prefs.gravity(this);
        lp.x = (int) (24 * dp);
        lp.y = (int) (24 * dp);
        card.setAlpha(0f);
        wm.addView(card, lp);

        gameCard = new GameCardView(this, font);
        WindowManager.LayoutParams glp = new WindowManager.LayoutParams(
                (int) (GameCardView.W_DP * dp), (int) (GameCardView.H_DP * dp),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        glp.gravity = Prefs.gravity(this);
        glp.x = (int) (24 * dp);
        glp.y = (int) (24 * dp);
        gameCard.setVisibility(View.GONE);
        wm.addView(gameCard, glp);
    }

    private void showGame(LiveGame.View game) {
        if (!running) return;
        card.animate().alpha(0f).setDuration(200).start();
        gameCard.setGame(game);
        if (gameCard.getVisibility() != View.VISIBLE) {
            gameCard.setAlpha(0f);
            gameCard.setVisibility(View.VISIBLE);
            gameCard.animate().alpha(1f).setDuration(400).start();
        }
    }

    private void show(List<Market> markets) {
        if (!running) return;
        gameCard.setVisibility(View.GONE);
        if (markets == null) {   // offline or API error: keep the last odds, mark them stale
            status.setText("offline");
            if (rows.getChildCount() == 0) card.setAlpha(0f);
            return;
        }
        status.setText("");
        rows.removeAllViews();
        for (Market m : markets) rows.addView(row(m));
        card.animate().alpha(markets.isEmpty() ? 0f : 1f).setDuration(400).start();
    }

    private View row(Market m) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, (int) (10 * dp), 0, 0);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView q = text(m.question, 12, 0xF2FFFFFF, 400);
        q.setMaxLines(2);
        q.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(0, -2, 1f);
        qlp.setMarginEnd((int) (10 * dp));
        top.addView(q, qlp);
        top.addView(text(percent(m.price), 20, 0xFFFFFFFF, 500));
        row.addView(top);

        FrameLayout bar = new FrameLayout(this);
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x24FFFFFF);
        track.setCornerRadius(2 * dp);
        bar.setBackground(track);
        View fill = new View(this);
        GradientDrawable f = new GradientDrawable();
        f.setColor(0xFFFFFFFF);
        f.setCornerRadius(2 * dp);
        fill.setBackground(f);
        bar.addView(fill, new FrameLayout.LayoutParams((int) (272 * dp * Math.max(0.02, Math.min(1, m.price))), -1));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, (int) (3 * dp));
        blp.topMargin = (int) (6 * dp);
        row.addView(bar, blp);

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setPadding(0, (int) (4 * dp), 0, 0);
        foot.addView(text(m.outcome, 11, 0x99FFFFFF, 400), new LinearLayout.LayoutParams(0, -2, 1f));
        int pts = (int) Math.round(m.change * 100);
        if (pts != 0) {
            foot.addView(text((pts > 0 ? "▲ " : "▼ ") + Math.abs(pts) + " today", 11,
                    pts > 0 ? 0xFF5AD18A : 0xFFFF7A6B, 400));
        }
        row.addView(foot);
        return row;
    }

    private TextView text(String s, float sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(font, weight, false));
        t.setIncludeFontPadding(false);
        t.setShadowLayer(6 * dp, 0, dp, 0x99000000);
        return t;
    }

    private static String percent(double p) {
        double v = p * 100;
        if (v > 0 && v < 1) return "<1%";
        if (v < 100 && v > 99) return ">99%";
        return String.format(Locale.US, "%.0f%%", v);
    }

    // ---- data --------------------------------------------------------------------------------------

    /** The markets to show, or null when the request failed. */
    private List<Market> fetch() {
        try {
            List<String> slugs = Prefs.markets(this);
            JSONArray arr;
            if (slugs.isEmpty()) {
                arr = get(API + "?active=true&closed=false&order=volume24hr&ascending=false&limit=25");
            } else {
                StringBuilder q = new StringBuilder(API + "?limit=" + slugs.size());
                for (String s : slugs) q.append("&slug=").append(URLEncoder.encode(s, "UTF-8"));
                arr = get(q.toString());
            }
            List<Market> out = new ArrayList<>();
            for (int i = 0; i < arr.length() && out.size() < MAX_MARKETS; i++) {
                Market m = parse(arr.getJSONObject(i));
                if (m == null) continue;
                // Trending mode skips markets that are all but decided.
                if (slugs.isEmpty() && (m.price > 0.97 || m.price < 0.03)) continue;
                out.add(m);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static Market parse(JSONObject o) throws Exception {
        JSONArray outcomes = new JSONArray(o.optString("outcomes", "[]"));
        JSONArray prices = new JSONArray(o.optString("outcomePrices", "[]"));
        if (outcomes.length() == 0 || outcomes.length() != prices.length()) return null;
        // Yes/No markets show Yes; others show whichever outcome leads.
        int pick = 0;
        if (!"Yes".equalsIgnoreCase(outcomes.optString(0))) {
            for (int i = 1; i < prices.length(); i++) if (prices.getDouble(i) > prices.getDouble(pick)) pick = i;
        }
        Market m = new Market();
        String group = o.optString("groupItemTitle", "");
        m.question = o.optString("question", "");
        m.outcome = outcomes.getString(pick) + (group.isEmpty() || group.equals("null") ? "" : " · " + group);
        m.price = prices.getDouble(pick);
        double ch = o.optDouble("oneDayPriceChange", 0);
        m.change = Double.isNaN(ch) ? 0 : (pick == 0 ? ch : -ch);
        return m;
    }

    private static JSONArray get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(15_000);
        c.setRequestProperty("Accept", "application/json");
        try (InputStream in = c.getInputStream();
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return new JSONArray(sb.toString());
        } finally {
            c.disconnect();
        }
    }
}
