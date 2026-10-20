---
name: polymarket-tv-overlay
description: Install and control a Polymarket odds overlay on an Android TV, Google TV or Amazon Fire TV over Wi-Fi. During a live NFL/college football/NBA/MLB/NHL game it shows both teams' win odds and a live graph since kickoff, delayed to match the stream; otherwise the most-traded markets. Use when someone wants Polymarket odds, betting odds or prediction-market prices on their TV, wants to set up, configure, pin a game, change the delay or corner, or remove the overlay.
---

# Polymarket TV overlay

A small Android app (`com.samarei.pmoverlay`) that draws live Polymarket odds over whatever is on the TV
(streaming apps, cable via HDMI, live TV). It only reads Polymarket's public market data; there's no
account and no trading. Everything here runs from the user's computer over the TV's network debugging
(ADB), with no cable and no root.

Repository root (where this skill lives): the folder two levels up from this file. All commands below
run from there.

## What the user sees

- **During a live game:** both teams' win odds in large white numbers and a white line graph of the first
  team's chances since kickoff, with no background. The price is checked every second and shown with a
  delay (30 seconds by default) so it moves with the stream instead of spoiling plays.
- **Otherwise:** the three most-traded open markets with their odds.
- **Holding the remote's Menu (≡) button in any app for about a second** shows or hides it ("Polymarket on/off").
- An app called **Polymarket Overlay** in the TV's app list, for the same settings.

## 1. Before you start (ask the user to do this on the TV)

1. Turn on developer options and network debugging:
   - **Fire TV:** Settings → My Fire TV → About → select the device name 7 times → back → Developer Options → **ADB Debugging** on.
   - **Android TV / Google TV:** Settings → Device Preferences (or System) → About → select **Build** 7 times → back → Developer options → **USB debugging** on (and **Network/Wireless debugging** if the TV shows it).
2. Find the TV's IP address (Settings → Network, or the About page). Keep the TV on.

On the computer you need Python 3, **JDK 17** and the Android SDK (platform 34, build-tools 34.0.0). On macOS:

```bash
brew install openjdk@17 && brew install --cask android-commandlinetools
yes | sdkmanager --sdk_root=/opt/homebrew/share/android-commandlinetools "platforms;android-34" "build-tools;34.0.0"
```

(Ask before installing software on the user's machine.) `tools/install.sh` finds JDK 17 and the SDK in
the usual places, or set `JAVA_HOME` and `ANDROID_HOME`.

## 2. Install

```bash
TV_HOST=<tv-ip> tools/install.sh
```

The first connection makes the TV ask **"Allow USB debugging?"**. Tell the user to tick "Always allow"
and press OK before it times out (two minutes). The script then:

- builds the APK (≈100 KB) and copies it over the network (it doesn't use the `adb` binary, which macOS
  often blocks from the local network);
- grants "show over other apps" (`appops set com.samarei.pmoverlay SYSTEM_ALERT_WINDOW allow`);
- turns on the hold-Menu toggle, an accessibility service that only watches key presses. It is **added
  to** any accessibility services already enabled, never replacing them;
- opens the app, which switches the overlay on.

Check it with `TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py --probe` if the connection fails, and confirm with the user
that the overlay appears and that holding Menu toggles it. Screenshots of streaming video come out black
(DRM), but the overlay still shows in them: `tools/venv/bin/python -I tools/tv.py 'screencap -p /data/local/tmp/s.png'`
then `--pull /data/local/tmp/s.png s.png`.

## 3. Configure

```bash
TV_HOST=<tv-ip> tools/configure.sh --delay 30                 # seconds behind live; 0–180
TV_HOST=<tv-ip> tools/configure.sh --corner bottom-left       # top-right (default), top-left, bottom-right, bottom-left
TV_HOST=<tv-ip> tools/configure.sh --markets "https://polymarket.com/event/nfl-was-sf-2026-10-20"
TV_HOST=<tv-ip> tools/configure.sh --auto                     # back to automatic
TV_HOST=<tv-ip> tools/configure.sh --leagues "nfl,nba"        # which sports to look for (Polymarket tag slugs)
TV_HOST=<tv-ip> tools/configure.sh --off                      # or --on
```

Each call prints the resulting settings. **Delay:** streaming services run behind real time (YouTube TV
roughly 20–45 s, cable/antenna 5–15 s) while the market reacts within seconds. If big moves on the graph
show up before the play does, raise the delay by the gap; if after, lower it.

**Pinning:** `--markets` takes up to three Polymarket slugs or URLs, comma-separated. A game event (a
"Team A vs. Team B" event with a start time) gets the live graph from 30 minutes before kickoff; any
other market shows in the list. To find a game's slug, search
`https://gamma-api.polymarket.com/events?tag_slug=nfl&closed=false&order=volume24hr&ascending=false&limit=10`
and use the event's `slug`.

**Automatic (default):** every few minutes it looks for the most-traded game that kicked off within the
last five hours in the configured leagues; with none, it shows trending markets.

## 4. Remove

```bash
TV_HOST=<tv-ip> tools/uninstall.sh
```

Removes the app and its toggle service and leaves other accessibility services alone. The user can also
uninstall it from the TV's app settings.

## Troubleshooting

- **"Allow USB debugging?" never appears / connection refused:** network debugging isn't on, the IP is
  wrong, or the computer is on a different network or a guest Wi-Fi that isolates devices.
- **Holding Menu does nothing:** check the app's "Hold Menu to toggle" row says Ready. Reinstalling or
  `settings put secure enabled_accessibility_services ...` again reloads it. Some remotes have no Menu
  button; use the app or `configure.sh --on/--off` instead.
- **Overlay missing after an update or reboot:** it restarts itself when it was on; opening the app also
  brings it back.
- **The game didn't switch:** automatic mode re-checks every three minutes; pin the game with `--markets`
  to switch now.
- **Two overlays:** another copy (for example a custom launcher with its own overlay) is also running;
  turn one off.

## Notes for the agent

- Never change other accessibility services, the default launcher or anything else on the TV beyond the
  steps above. Ask before uninstalling apps or changing TV settings the user didn't ask about.
- The adb key that the TV now trusts is in `tools/adbkey/`; it's private to this computer. Tell the user
  they can turn network debugging off again afterwards; the overlay keeps working.
