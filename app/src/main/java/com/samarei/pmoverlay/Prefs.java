package com.samarei.pmoverlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;

import java.util.ArrayList;
import java.util.List;

/**
 * Overlay settings, changed from the app's own screen or over adb with {@link ConfigReceiver}.
 */
final class Prefs {

    static final int[] DELAYS = {0, 15, 30, 45, 60};
    static final String[] CORNERS = {"top-right", "top-left", "bottom-right", "bottom-left"};

    private Prefs() {
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("overlay", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return p(c).getBoolean("enabled", false);
    }

    static void setEnabled(Context c, boolean on) {
        p(c).edit().putBoolean("enabled", on).commit();
    }

    /** Seconds the overlay runs behind the live market, to match the stream's lag. */
    static int delaySeconds(Context c) {
        return p(c).getInt("delay", 30);
    }

    static void setDelaySeconds(Context c, int s) {
        p(c).edit().putInt("delay", Math.max(0, Math.min(180, s))).commit();
    }

    static String corner(Context c) {
        return p(c).getString("corner", "top-right");
    }

    static void setCorner(Context c, String corner) {
        for (String k : CORNERS) if (k.equals(corner)) p(c).edit().putString("corner", corner).commit();
    }

    static int gravity(Context c) {
        switch (corner(c)) {
            case "top-left": return Gravity.TOP | Gravity.START;
            case "bottom-right": return Gravity.BOTTOM | Gravity.END;
            case "bottom-left": return Gravity.BOTTOM | Gravity.START;
            default: return Gravity.TOP | Gravity.END;
        }
    }

    /** Pinned Polymarket slugs (games or markets), up to three; empty means automatic. */
    static List<String> markets(Context c) {
        List<String> out = new ArrayList<>();
        for (String s : p(c).getString("markets", "").split("[,\\s]+")) {
            String slug = s.trim();
            int slash = slug.lastIndexOf('/');
            if (slash >= 0) slug = slug.substring(slash + 1);   // a pasted polymarket.com URL works too
            int q = slug.indexOf('?');
            if (q >= 0) slug = slug.substring(0, q);
            if (!slug.isEmpty() && out.size() < 3) out.add(slug);
        }
        return out;
    }

    static void setMarkets(Context c, String commaSeparated) {
        p(c).edit().putString("markets", commaSeparated == null ? "" : commaSeparated.trim()).commit();
    }

    /** Polymarket sport tags searched for a live game, in order. */
    static String[] leagues(Context c) {
        String v = p(c).getString("leagues", "nfl,cfb,nba,mlb,nhl");
        return v.trim().isEmpty() ? new String[0] : v.trim().split("\\s*,\\s*");
    }

    static void setLeagues(Context c, String commaSeparated) {
        p(c).edit().putString("leagues", commaSeparated == null ? "" : commaSeparated.trim().toLowerCase()).commit();
    }
}
