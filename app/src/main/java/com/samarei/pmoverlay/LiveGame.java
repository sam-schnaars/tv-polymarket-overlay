package com.samarei.pmoverlay;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A sports game in progress on Polymarket and its win-probability history, for the game overlay.
 * Finds the most-traded game that's live right now (kicked off within the last five hours and not
 * settled) across the configured leagues, or follows a game pinned in the settings. Read-only.
 */
final class LiveGame {

    private static final long LIVE_WINDOW_MS = 5 * 60 * 60_000L;
    private static final long REDISCOVER_MS = 3 * 60_000L;
    private static final long HISTORY_REFRESH_MS = 5 * 60_000L;
    /**
     * What the card draws: the game as it was {@code delayMs} ago, copied so drawing never races updates.
     * Streams run behind real life (YouTube TV ~20–45 s) while the market reacts within seconds, so a
     * delay keeps the graph in step with the picture instead of spoiling plays.
     */
    static final class View {
        final String teamA, teamB;
        final double priceA;
        final List<Point> history;

        View(String teamA, String teamB, double priceA, List<Point> history) {
            this.teamA = teamA;
            this.teamB = teamB;
            this.priceA = priceA;
            this.history = history;
        }
    }

    static synchronized View delayed(Game g, long delayMs) {
        long cutoff = System.currentTimeMillis() - delayMs;
        List<Point> pts = new ArrayList<>();
        for (Point p : g.history) if (p.t <= cutoff) pts.add(p);
        if (pts.isEmpty() && !g.history.isEmpty()) pts.add(g.history.get(0));
        double price = pts.isEmpty() ? g.priceA : pts.get(pts.size() - 1).p;
        return new View(g.teamA, g.teamB, price, pts);
    }

    static final class Point {
        final long t;
        final double p;

        Point(long t, double p) {
            this.t = t;
            this.p = p;
        }
    }

    static final class Game {
        String slug;
        String title;
        String teamA, teamB;   // outcome 0 and 1
        String tokenA;         // CLOB token for outcome 0; the graph follows its probability
        long kickoff;
        double priceA;
        final List<Point> history = new ArrayList<>();
        long historyAt;
    }

    private static Game cached;
    private static long discoveredAt;

    private LiveGame() {
    }

    /** The game to show, or null when none is live. Network; call off the main thread. */
    static synchronized Game current(List<String> chosenSlugs, String[] leagues) {
        long now = System.currentTimeMillis();
        if (now - discoveredAt > REDISCOVER_MS) {
            discoveredAt = now;
            Game found = null;
            try {
                found = chosenSlugs.isEmpty() ? discover(now, leagues) : chosen(chosenSlugs, now);
            } catch (Exception ignored) {
                // offline: keep following the last game until it ages out below
            }
            if (found != null && cached != null && found.slug.equals(cached.slug)) found = cached;
            cached = found != null ? found : (cached != null && now - cached.kickoff < LIVE_WINDOW_MS ? cached : null);
        }
        return cached;
    }

    /** Refreshes the live price (every call) and the history (every few minutes). Network. */
    static synchronized void update(Game g) {
        long now = System.currentTimeMillis();
        try {
            if (g.history.isEmpty() || now - g.historyAt > HISTORY_REFRESH_MS) {
                JSONArray h = getObject("https://clob.polymarket.com/prices-history?market=" + g.tokenA
                        + "&interval=6h&fidelity=1").getJSONArray("history");
                List<Point> pts = new ArrayList<>();
                long from = g.kickoff - 15 * 60_000L;   // a little pre-game, then the whole game
                for (int i = 0; i < h.length(); i++) {
                    JSONObject o = h.getJSONObject(i);
                    long t = o.getLong("t") * 1000L;
                    if (t >= from) pts.add(new Point(t, o.getDouble("p")));
                }
                if (!pts.isEmpty()) {
                    g.history.clear();
                    g.history.addAll(pts);
                }
                g.historyAt = now;
            }
            double mid = getObject("https://clob.polymarket.com/midpoint?token_id=" + g.tokenA).getDouble("mid");
            g.priceA = mid;
            g.history.add(new Point(now, mid));
        } catch (Exception ignored) {
            // keep the last values; the card shows them until the next refresh
        }
    }

    /** Forgets the current game, so the next {@link #current} looks again (settings changed). */
    static synchronized void reset() {
        cached = null;
        discoveredAt = 0;
    }

    private static Game discover(long now, String[] leagues) throws Exception {
        Game best = null;
        double bestVolume = -1;
        for (String league : leagues) {
            JSONArray events = getArray("https://gamma-api.polymarket.com/events?tag_slug=" + league
                    + "&closed=false&order=volume24hr&ascending=false&limit=15");
            for (int i = 0; i < events.length(); i++) {
                JSONObject e = events.getJSONObject(i);
                JSONObject m = moneyline(e);
                if (m == null) continue;
                long kickoff = parseTime(m.optString("gameStartTime"));
                if (kickoff <= 0 || now < kickoff || now - kickoff > LIVE_WINDOW_MS) continue;
                double vol = m.optDouble("volume24hr", 0);
                if (vol > bestVolume) {
                    Game g = fromMarket(e, m, kickoff);
                    if (g != null) {
                        best = g;
                        bestVolume = vol;
                    }
                }
            }
        }
        return best;
    }

    private static Game chosen(List<String> slugs, long now) throws Exception {
        for (String slug : slugs) {
            JSONArray events = getArray("https://gamma-api.polymarket.com/events?slug=" + slug);
            if (events.length() == 0) continue;
            JSONObject e = events.getJSONObject(0);
            JSONObject m = moneyline(e);
            if (m == null) continue;
            long kickoff = parseTime(m.optString("gameStartTime"));
            if (kickoff > 0 && now >= kickoff - 30 * 60_000L) return fromMarket(e, m, kickoff);
        }
        return null;
    }

    /** The event's head-to-head market: the one whose question is the event title ("A vs. B"). */
    private static JSONObject moneyline(JSONObject event) throws Exception {
        String title = event.optString("title");
        if (!title.contains(" vs")) return null;
        JSONArray markets = event.optJSONArray("markets");
        if (markets == null) return null;
        for (int i = 0; i < markets.length(); i++) {
            JSONObject m = markets.getJSONObject(i);
            if (title.equals(m.optString("question")) && !m.optBoolean("closed")) return m;
        }
        return null;
    }

    private static Game fromMarket(JSONObject e, JSONObject m, long kickoff) throws Exception {
        JSONArray outcomes = new JSONArray(m.optString("outcomes", "[]"));
        JSONArray tokens = new JSONArray(m.optString("clobTokenIds", "[]"));
        JSONArray prices = new JSONArray(m.optString("outcomePrices", "[]"));
        if (outcomes.length() != 2 || tokens.length() != 2) return null;
        Game g = new Game();
        g.slug = e.optString("slug");
        g.title = e.optString("title");
        g.teamA = outcomes.getString(0);
        g.teamB = outcomes.getString(1);
        g.tokenA = tokens.getString(0);
        g.kickoff = kickoff;
        g.priceA = prices.length() == 2 ? prices.getDouble(0) : 0.5;
        return g;
    }

    /** "2026-10-20 00:15:00+00" or ISO 8601 → epoch millis, or 0. */
    private static long parseTime(String s) {
        if (s == null || s.isEmpty() || s.equals("null")) return 0;
        String norm = s.replace('T', ' ').replace("Z", "+00");
        if (norm.matches(".*[+-]\\d\\d$")) norm = norm + "00";
        for (String f : new String[]{"yyyy-MM-dd HH:mm:ssZ", "yyyy-MM-dd HH:mm:ss.SSSZ"}) {
            try {
                SimpleDateFormat fmt = new SimpleDateFormat(f, Locale.US);
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                return fmt.parse(norm).getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8_000);
        c.setReadTimeout(12_000);
        c.setRequestProperty("Accept", "application/json");
        try (InputStream in = c.getInputStream();
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append(' ');
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private static JSONArray getArray(String url) throws Exception {
        return new JSONArray(get(url));
    }

    private static JSONObject getObject(String url) throws Exception {
        return new JSONObject(get(url));
    }
}
