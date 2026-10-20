<p align="center"><img src="docs/icon.svg" width="112" alt="HomePanel icon"></p>

<h1 align="center">HomePanel</h1>

<p align="center">One Android home-screen widget for weather, your calendar, your parked car and Claude usage.</p>

HomePanel replaces several separate widgets with a single dark panel built with Jetpack Glance. It
uses the phone's system font, so it follows your font setting (on Samsung, the One UI font).

## What it shows

- **Weather** where you are: current conditions, air quality, rain starting or stopping in the next
  hours, and a three-day forecast. Choose the source: Open-Meteo (best match, DWD ICON, ECMWF,
  Météo-France), MET Norway (yr.no), DHMZ (Croatia, with official warnings) or your Home Assistant
  weather entity. Settings can compare them side by side.
- **Calendar**: events from every calendar on the phone for the next 7, 14 or 30 days, with their
  colours. Tap an event to open it, or the plus to add one.
- **Car** (via Home Assistant): where it's parked on a small map, its picture, range and whether
  it's locked. Tapping it opens the app you choose (e.g. the carmaker's app).
- **Claude usage**: the 5-hour and weekly plan limits with reset times. Tap the bars to refresh.

Two designs (A and B), English or Croatian, adjustable text sizes, and layouts that adapt when the
widget is resized.

A second, small widget, **HomePanel search**, is a search bar in the same colours: tapping it opens
Firefox's search (as Firefox's own widget does), the mic dictates a search for Firefox. Without
Firefox it opens the default browser.

## Install

1. Download the latest `homepanel-*.apk` from [Releases](../../releases).
2. Open it on the phone and allow installing from that source when Android asks.
3. Open **HomePanel**, then add the widget: long-press the home screen → Widgets → HomePanel.

Later versions install from inside the app: **Settings → Updates → Check for updates**.

Needs Android 12 or newer.

## Set up

Everything personal is entered in the app; nothing is built in.

- **Home Assistant**: your local address (`http://…` works only on the home network), a remote
  address (Tailscale, Nabu Casa or similar) and a long-lived access token. **Save and test** checks
  the connection.
- **Weather → Your location**: pick your phone's tracker from the Home Assistant companion app, so
  the forecast follows you. Without it, the forecast is for your Home Assistant home.
- **Car**: pick the entities for the parked address, range, picture, location and lock from lists
  of your Home Assistant entities, give the car a name, and choose which app a tap opens.
- **Claude**: sign in to claude.ai. If that doesn't work in the built-in browser, paste a
  `sessionKey` from a desktop browser instead.
- **Background refresh**: allow it to run unrestricted, or Android may delay updates.

## Privacy

- Tokens and the Claude session are stored encrypted on the phone and never leave it except to the
  service they belong to.
- Plain `http://` is only used for local network addresses; everything else is HTTPS.
- Your location goes to the weather source you chose (and to Open-Meteo for air quality and rain);
  the car's location goes to OpenFreeMap tiles for the map. Nothing is collected by this app.
- Claude usage comes from the same unofficial endpoint claude.ai's own usage page uses. It can stop
  working at any time; the widget then keeps the last numbers.

## Build from source

Requirements: JDK 17+ (Android Studio's bundled one works) and the Android SDK (platform 36).

```sh
./gradlew :app:testDebugUnitTest   # unit tests (parsers, formatting)
./gradlew :app:assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

To sign with your own key, add to `~/.gradle/gradle.properties`:

```properties
HOMEPANEL_KEYSTORE=/path/to/your.keystore
HOMEPANEL_KEYSTORE_PASSWORD=…
HOMEPANEL_KEY_ALIAS=…
HOMEPANEL_KEY_PASSWORD=…
```

Without these, builds are signed with the Android debug key. `scripts/release.sh` builds, tests and
publishes a GitHub Release that the in-app updater picks up.

## Credits

- [Jetpack Glance](https://developer.android.com/jetpack/compose/glance),
  [MapLibre Native](https://maplibre.org), [OkHttp](https://square.github.io/okhttp/)
- Map: [OpenFreeMap](https://openfreemap.org), © [OpenMapTiles](https://openmaptiles.org),
  © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors
- Weather: [Open-Meteo](https://open-meteo.com), [MET Norway](https://api.met.no),
  [DHMZ](https://meteo.hr)
- The Claude sign-in approach follows the idea of
  [claude-usage-widget](https://github.com/utaysi/claude-usage-widget) (no code copied).

Not affiliated with Anthropic, Home Assistant or any carmaker.

## License

[MIT](LICENSE)
