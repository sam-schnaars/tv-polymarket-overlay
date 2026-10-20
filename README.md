# Polymarket TV Overlay

Live Polymarket odds over whatever's on your TV. During a game you get both teams' win chances and a
live graph since kickoff, floating in the corner with no background, delayed to match your stream so it
doesn't spoil plays. The rest of the time it shows the most-traded markets. Hold **Menu (≡)** on the remote
in any app to show or hide it.

Works on Android TV, Google TV and Amazon Fire TV. It only reads Polymarket's public data: no account,
no trading.

## Set it up with your agent

This repo is a skill: point a coding agent with shell access (Claude Code or similar) at it and ask.

```text
Set up the Polymarket TV overlay from https://github.com/sam-schnaars/tv-polymarket-overlay on my TV
```

With Claude Code you can also add it as a plugin:

```text
/plugin marketplace add sam-schnaars/tv-polymarket-overlay
/plugin install polymarket-tv-overlay@polymarket-tv-overlay
```

The agent follows [`skills/polymarket-tv-overlay/SKILL.md`](skills/polymarket-tv-overlay/SKILL.md):
you turn on network debugging on the TV and accept one prompt, and it does the rest over Wi-Fi.

## Or by hand

You need Python 3, JDK 17 and the Android SDK (platform 34). Turn on ADB/network debugging on the TV
(see the skill for where that lives on each brand), then:

```bash
TV_HOST=192.168.1.50 tools/install.sh                      # build, install, grant, turn on
TV_HOST=192.168.1.50 tools/configure.sh --delay 30         # seconds behind live (match your stream)
TV_HOST=192.168.1.50 tools/configure.sh --corner top-left  # or top-right, bottom-left, bottom-right
TV_HOST=192.168.1.50 tools/configure.sh --markets "https://polymarket.com/event/<game-slug>"
TV_HOST=192.168.1.50 tools/configure.sh --auto             # back to automatic
TV_HOST=192.168.1.50 tools/uninstall.sh
```

## How it works

- `OverlayService` draws two windows over other apps: the game card (`GameCardView`) and the market list.
- `LiveGame` finds the most-traded game that's live in the chosen leagues (NFL, college football, NBA,
  MLB, NHL by default) from Polymarket's Gamma API, loads the minute-by-minute history from the CLOB API,
  then checks the midpoint price every second and shows it `delay` seconds later.
- `KeyToggleService` is an accessibility service that only watches key presses, for the hold-Menu
  toggle. It never consumes the button and reads nothing on screen.
- `ConfigReceiver` takes settings over adb; the app's own screen shows and changes the same settings.

`tools/tv.py` and `tools/b64push.py` are adapted from [tv-vibehack](https://github.com/Gathin23/tv-vibehack)
(MIT): a pure-Python ADB client, since macOS often blocks the stock `adb` binary from the local network.

Not affiliated with Polymarket. Check that prediction markets are legal where you live.

## License

MIT. The Outfit typeface is under the SIL Open Font License (`FONT-LICENSE-OFL.txt`).
