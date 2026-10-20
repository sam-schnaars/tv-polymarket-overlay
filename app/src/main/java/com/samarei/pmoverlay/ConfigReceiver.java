package com.samarei.pmoverlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Settings over adb, so an agent can set things up without the remote:
 *
 *   am broadcast -n com.samarei.pmoverlay/.ConfigReceiver -a com.samarei.pmoverlay.CONFIGURE \
 *     --ez enabled true --ei delay 30 --es corner top-right \
 *     --es markets "nfl-was-sf-2026-10-20" --es leagues "nfl,nba"
 *
 * Every extra is optional. markets: comma-separated Polymarket slugs or URLs (empty string = automatic).
 * corner: top-right, top-left, bottom-right or bottom-left.
 */
public class ConfigReceiver extends BroadcastReceiver {
    static final String ACTION = "com.samarei.pmoverlay.CONFIGURE";

    @Override
    public void onReceive(Context c, Intent i) {
        if (!ACTION.equals(i.getAction())) return;
        if (i.hasExtra("delay")) Prefs.setDelaySeconds(c, i.getIntExtra("delay", 30));
        if (i.hasExtra("corner")) Prefs.setCorner(c, i.getStringExtra("corner"));
        if (i.hasExtra("markets")) Prefs.setMarkets(c, i.getStringExtra("markets"));
        if (i.hasExtra("leagues")) Prefs.setLeagues(c, i.getStringExtra("leagues"));
        if (i.hasExtra("enabled")) OverlayService.setOn(c, i.getBooleanExtra("enabled", true));
        OverlayService.restartIfOn(c);
        setResultData("enabled=" + Prefs.enabled(c) + " delay=" + Prefs.delaySeconds(c) + " corner=" + Prefs.corner(c)
                + " markets=" + Prefs.markets(c) + " leagues=" + String.join(",", Prefs.leagues(c)));
    }
}
