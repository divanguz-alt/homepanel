# HomePanel: Android home-screen widget

One Jetpack Glance widget that replaces four separate widgets (weather, calendar,
parked car, Claude usage) with a single dark panel. Personal use, sideloaded.

**Privacy rule:** nothing that identifies the owner goes into the code, samples, tests, docs or git
history: no names, car make/model, cities, addresses, IPs or entity IDs. Everything personal is
entered or picked in settings after installing; samples use neutral placeholders.

## Stack

- Kotlin, Jetpack Glance (`androidx.glance:glance-appwidget`, latest stable), Material3 not needed in the widget.
- minSdk 31, targetSdk 36, JDK 17. Android SDK is installed locally.
- WorkManager for refresh, OkHttp + kotlinx.serialization for HTTP/JSON,
  `androidx.security:security-crypto` (EncryptedSharedPreferences) for secrets.
- Package name suggestion: `hr.divanguz.homepanel`.

## Hard rules

1. **Font: system default only.** No bundled fonts, no `FontFamily` overrides. Glance `Text`
   must render with the OS typeface so it follows the One UI font setting. Weights via
   `FontWeight` only.
2. **Every zone opens its app on tap** (see table; the Claude bars refresh instead, by request). No "Navigate" button anywhere.
3. **No visible scrollbar styling work**: use Glance `LazyColumn` for the calendar. Android may
   show its own short-lived scroll indicator while scrolling; that is acceptable.
4. Never hard-code secrets. Token and Claude session live only in EncryptedSharedPreferences.

## Layout (top to bottom, one card)

Card: background `#15181F`, 1dp hairline `#262A33` (fake with an outer box if needed),
corner radius = the system widget radius (`system_app_widget_background_radius`, set by One UI; 28dp fallback), full widget width; the search bar uses the same, capped at a pill. Text colors: primary `#ECEEF2`, secondary `#A3A9B5`,
tertiary `#8A909C`. Single accent `#F2A65A`, currently unused in the widget. Calendar day labels are all secondary (the
user rejected an accented first row). Claude bars use a green → yellow → red gradient across the track.
Dividers: 1dp `#262A33`.

1. **Weather row** (padding 20/20/16): left column = date ("Monday, 19 October", 13sp secondary)
   above the current temperature (56sp, light weight). Right column, right-aligned = condition
   icon, "Clear now" (14sp), "Tomorrow mostly sunny, 20°" (13sp secondary).
2. **Calendar** (padding 12/20/4): header row "Coming up" left, "Today: no events" right when
   today is empty. Below it a `LazyColumn` with rows of 44dp height. The card fills the whole
   widget frame and the calendar takes all remaining height (weather row and bottom grid keep
   their size), so resizing the widget shows more or fewer rows. Row = day label (e.g. "FRI 23", 52dp wide),
   time ("19:30", or "all" for all-day, 40dp wide), color dot (`Instances.DISPLAY_COLOR`), title
   (single line, ellipsized). A plus at the right of the header opens `ACTION_INSERT` (new event).
   Hairline between rows. At least the next 14 days of events.
3. **Bottom grid, two equal columns** separated by a vertical hairline:
   - **Car (left):** label = car name from settings, "Car" if blank (+ area chip when present), address (19sp semibold),
     city/second line (13sp secondary), optional "Since today 14:20" (Car settings → Parked since; from the entity
     picked there: a timestamp state, else its `last_changed`; default the parked-address sensor), vehicle render image (max ~44dp high, fit width),
     range as a fuel-pump icon + value bottom-left (no "Range" word), render bottom-right.
   - **Claude (right):** "Claude usage", then for 5 hours and Week: label + percent, 4dp bar,
     "resets in …" line (11sp tertiary).

Outside the widget (not our code): a browser's search widget sits under the panel, then empty
space, then app icons and dock.

## User-adjustable settings (no longer hard-coded)

Car name, calendar days ahead (7/14/30), weather refresh interval and move threshold, AQI scale
(US / European via Open-Meteo), official warnings toggle, map style (OpenFreeMap fiord/dark/positron)
and zoom, car lock entity. Official warnings: DHMZ CAP feeds (`meteo.hr/upozorenja/cap_hr_*.xml`),
matched to the county from a Croatian reverse geocode; shown only while DHMZ is the weather source
(settings say so). The car label shows a padlock (closed grey / open amber) when a lock entity exists.

## Themes

Settings → Appearance → Theme: Midnight (default, the colours above), Material You (system dark
neutrals from the wallpaper), Graphite, AMOLED black, Glass (translucent card), Moss, Espresso,
Indigo, Wine, Phosphor. Built from OKLCH with the hue carried into the neutrals; tertiary text
stays ≥ 5.5:1 on the card in the fixed themes (Glass: a 78% veil, checked over a white wallpaper;
Material You takes what the system gives and changes on the next redraw). AMOLED has no outer border. Code: `widget/Palette.kt` (`Themes`); every colour goes through
`Palette`, which the widgets point at the chosen theme before composing. The grid's vertical
hairline nine-patch is tinted. The car map bitmap is not themed.

## Layout, small widgets, notifications, backup

- Settings → Layout: zone order (arrows), each zone on/off, car and Claude halves on/off and swapped
  (`Look.order/hidden/swapGrid`, `Zones` in `widget/Look.kt`). The default is the layout above. With the
  calendar hidden nothing stretches; the empty part of the card goes at the bottom.
- Small widgets (`widget/SmallWidgets.kt`): "HomePanel · Claude", "· Car", "· Weather", one zone each in the
  same card. They share the panel's Glance state; `PanelStore.placed()` lists every data widget class, so
  any new widget class must be added to `PanelStore.kinds` and `Preview.publishOnce`.
- Notifications (`notify/Alerts.kt`, Settings → Notifications, all off by default): the temperature as a
  silent ongoing notification whose small icon is the number drawn as a bitmap (status bar, lock screen;
  AOD shows only icons); Claude limit warnings once per usage window (keyed by its reset time) and a note
  when that window resets. Both run from the periodic refresh, so they work without any widget placed.
- Settings → Backup: JSON export/import through the system file picker. The HA token and every Claude
  session key are never written or read.
- Settings screen type: system font, Material's 0.5sp tracking removed (`Type`, `T()` in SettingsUi.kt),
  SemiBold titles, Medium row titles, coloured line icons per section (`Hues`).
  The big page title collapses into a small bar on scroll (`CollapsedBar`). Appearance and Layout show
  the real widget at the top: `previewViews()` renders `Panel` with `GlanceRemoteViews` (experimental
  Glance API) into RemoteViews hosted in an `AndroidView`; taps on it are swallowed. The calendar's
  LazyColumn rows don't render in that preview.

## Hourly strip, calendars, light themes

- Weather settings → Next hours (`Settings.hoursStyle`, `hoursStep` 1/2/3 h): "list" (default; three short
  rows between the temperature and three days), "columns" (three small hour columns, a hairline, two days),
  "sentence" ("Up to 8° at 15h, rain 14–19h, 4° at 21h" under the conditions; it replaces the rain line),
  "strip" (the old six-hour strip, only when the panel is ≥ 470dp tall; it raises the calendar's cut-off by
  90dp) or off. List/columns/sentence are drawn in design B only; `hoursFit` estimates the text widths and
  drops days (then the hours) rather than clipping. Hours come from the chosen provider (`WeatherData.hours`:
  MET Norway's hourly steps, DHMZ's hourly feed steps with derived conditions and its chance of rain, HA
  `get_forecasts` type hourly), else from Open-Meteo's hourly forecast fetched with the rain nowcast
  (`RainNowcast.hours`). Slots: the next full hour, then two more `hoursStep` apart (`hourSlots`, `outlook`
  in PanelState.kt).
- Calendar settings list the phone's calendars with switches (`Settings.hiddenCalendars`, filtered by
  `Instances.CALENDAR_ID`).
- Themes "Light" and "Day and night" (`Themes.AUTO`): Auto colours are Glance day/night ColorProviders
  (`Palette.p`), so the launcher switches them the moment the system's dark mode changes. Anything drawn
  as a bitmap can't switch, so the usage bar's track is a Box behind a fill-only bitmap and, in Auto,
  the car map is a neutral grey with a day/night veil (`Palette.mapVeilP`) over it.
- Car map per theme: the plain snapshot is kept (`car_map_base.png`) and re-coloured at draw time
  (duotone from the card colour to tertiary text); Midnight keeps the original map.
- Previews (picker, settings) can't host a LazyColumn: `LocalStaticList` makes the calendar a plain
  column of the first rows. Settings → "Last updated" shows when weather, car and Claude last answered.

## Tasks (Google Tasks through Home Assistant)

- Calendar settings → Tasks: switches for HA `todo.*` lists (`Settings.taskLists`); HA's Google Tasks
  integration turns each Google list into one. Open items come from `todo.get_items?return_response`
  every refresh (`HaSnapshot.tasks`) and merge into the calendar list by due day (`withTasks` in
  `CalendarRows.kt`): overdue first with an amber day, undated last under "No date"; a ring instead of
  the colour dot. Tapping the ring completes it (`todo.update_item`, `CompleteTaskAction`); tapping the
  row opens Google Tasks (`com.google.android.apps.tasks`) through `OpenAppActivity`.
- With a list on, the calendar header gets "All · Calendar · Tasks" as three quiet words, the chosen one
  underlined (`ListSwitch`, per widget in Glance state, `FilterAction`; the user found a pill too loud).
  Calendar settings → "Calendar and tasks side by side" (`tasksSideBySide`) swaps the switch for two
  columns (`SplitZone`), each with its own header and plus; tasks there show a date only when overdue. The plus follows it: Calendar → new event, Tasks → quick task entry,
  All → a chooser (`tasks/AddActivity`, a sheet over the home screen; adds via `todo.add_item`).
- Glance keeps a background that is merely left out on reapply: always set one (transparent when off).
  Tinted shape drawables fill in; use vectors for rings.

## Language, rain, location

Widget text lives in `widget/Texts.kt` (English / Croatian, Settings → Appearance → Widget language;
the locale also formats dates). "Rain from 14:20 / until 15:10" under the conditions comes from
Open-Meteo's 15-minute forecast every refresh (toggle in Weather settings); when the chosen
provider has its own hours the line comes from those instead (`hourlyRainText`, next 6 h), so it never
contradicts the hour icons. DHMZ day icons count only daytime rain (7–20h), unless the night is very wet. The weather location is
the device_tracker picked in Weather → Your location (only trackers with coordinates are listed).

## Build, sign, test

- Build: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`
- Build output goes to `HOMEPANEL_BUILD_DIR` (`~/Library/Caches/HomePanel-build`, set in
  `~/.gradle/gradle.properties`), outside the project folder.
  APKs are under `$HOMEPANEL_BUILD_DIR/app/outputs/apk/…`.
- Tests (JVM, offline, parsers and formatting): `./gradlew :app:testDebugUnitTest`. XML parsing goes
  through `XmlParsers.factory` so tests can use kxml2; keep new parsers separate from network calls.
- Signing: debug and release are signed with the key at `HOMEPANEL_KEYSTORE` from
  `~/.gradle/gradle.properties` (outside the repo; currently `~/.homepanel/homepanel.keystore`, a copy
  of the original debug key so existing installs keep updating). Losing it means uninstall + set up again.

## Releases and in-app updates

- versionCode = 100 + commit count, versionName = "1.<count>" (app/build.gradle.kts); tags "v1.<count>".
- `scripts/release.sh` (clean, pushed tree): tests, builds the **release** variant (not debuggable),
  publishes GitHub Release `v1.<count>` on the public repo divanguz-alt/homepanel with
  `homepanel-1.<count>.apk`. Run it after each change.
- Settings → Updates: checks the public repo's `releases/latest` (no token; a token stored by older
  versions is deleted), downloads the asset and opens Android's installer (FileProvider,
  REQUEST_INSTALL_PACKAGES). 0.x builds can't read 1.x tags, so they need one manual install.

## Design B

A second look, chosen in Settings → Appearance → Design: spec in `docs/design-b.md` ("A2 · Siva
karta"). Code: `widget/DesignB.kt` (weather and calendar zones); the bottom grid is shared and only
uses the grey map variant (`car_map_grey.png`, rendered with the colour one) and `#A2A4AB` text on
the map. In B the weather zone has fixed places, sized for the widest values so nothing moves or disappears when the numbers get longer (the user's rule: everything has its own fixed space, and the zone may not grow taller): a top row of small labels ("Today ↑7° ↓2°" left; "● AQI · city · date · source" right, the invisible settings shortcut, with the date, then the source, dropped and the city shortened to fit); then on the left a temperature slot as wide as "-12°", the condition beside it (icon above, the words below, two lines when long) and under them a rain line whose place stays even when empty (four 15-minute bars from Open-Meteo's nowcast when rain falls or starts within the hour, `rainBars`, and then the line comes from the same data); on the right three hours and three days in equal columns (label, icon, value; under it the rain chance or the low), a hairline between. Only the widget width decides how many hours/days are shown, never the data. List and columns styles both draw these columns; a warning chip or the sentence go in a line below. Refresh lives in settings and the Claude bars. Local branches `backup/before-layout-1` (v1.37) and the v1.39 release keep the earlier arrangements. The source carries the hour the current temperature was measured when the provider gives one ("DHMZ (7h)": vrijeme.hr's `<Termin>`, `WeatherData.observedAt`); the hours and days stay the provider's forecast, never corrected (the user rejected bias-correcting them).

## Search widget (secondary)

"HomePanel search" (`search/SearchWidget.kt`, `SearchReceiver`): a 4×1 widget with a 50dp pill bar centred in the cell (Firefox's own bar height, by request) in the card colours
(magnifier, "Search the web" / "Pretraži web" in secondary, mic; Appearance → "Tall search bar" doubles it to 100dp, capped at the cell height, so its corners can equal the panel's), replacing Firefox's own search
widget on the home screen. Text tap sends Firefox the same intent its widget does
(`org.mozilla.fenix.IntentReceiverActivity` + `open_to_search=search_widget`; channels
firefox/firefox_beta/fenix). Firefox's voice activity isn't exported, so the mic opens
`VoiceSearchActivity` (invisible, Android speech prompt) and sends the words as `ACTION_WEB_SEARCH`
to Firefox. No Firefox → default browser.

## Data sources

| Zone | Source | Tap action |
| --- | --- | --- |
| Date + forecast city | device clock/locale; city from the weather provider | opens HomePanel settings (invisible shortcut, user request) |
| Weather | Where the phone is (lat/lon of the phone's HA companion `device_tracker`, auto-detected by Test HA; else HA home) → provider chosen in Settings → Weather source: Open-Meteo (best match / DWD ICON / ECMWF / Météo-France models), MET Norway, DHMZ (Croatia only: nearest vrijeme.hr station + meteo.hr 7-day feed cached by ETag; symbol codes undocumented, condition derived from precipitation), or the HA entity; failures fall back to Open-Meteo then HA. AQI always Open-Meteo `us_aqi`. HRT has no public API (it shows DHMZ data). Settings can compare all providers live. fetched hourly, on a >3 km move, or via the widget's refresh button; city the forecast is for shown under the date. Fallback: HA weather entity (`weather.forecast_home`, forecasts via `weather/get_forecasts`). Samsung Weather data is not accessible to third-party apps. | launch Samsung Weather `com.sec.android.daemonapp` (MAIN activity fallback, it has no launcher icon by default) |
| Calendar header | n/a | launch Samsung Calendar `com.samsung.android.calendar` |
| Task row | HA `todo.*` lists picked in Calendar settings (see Tasks) | Google Tasks app; the ring completes the task |
| Calendar row | `CalendarContract.Instances` (all visible calendars), next 14 days | `ACTION_VIEW` on the event URI with `EXTRA_EVENT_BEGIN_TIME`/`EXTRA_EVENT_END_TIME` (opens in the default calendar app) |
| Car address + area | parked-address sensor picked in Car settings: `state` = address, attribute `area` (chip only if non-empty) | the app picked in Car settings ("Tapping the car opens"), else Home Assistant `io.homeassistant.companion.android`. With "Two tap areas" on, the right (map/pin) half opens that app and the left (text) half a second picked app (`carApp2`, Home Assistant by default) |
| Car range | range sensor + optional backup sensor, picked in Car settings (show `unit_of_measurement`) | same as car |
| Car image | `image.*` entity picked in Car settings: read `entity_picture` from `/api/states/…`, download it with the bearer token, downscale to ≤ 600px wide, cache on disk, re-download only when the entity's `last_updated` changes | same as car |
| Car map (cell background) | lat/lon from the car location entity picked in Car settings; OpenFreeMap `fiord` style rendered with MapLibre's snapshotter (no key; CARTO now requires one; attribution drawn in the corner), blurred + darkened under the text, sharp pin with the render on the right; re-rendered only when the parked address changes | same as car |
| Claude usage | same approach as github.com/utaysi/claude-usage-widget (see below) | "Claude usage" title launches Claude `com.anthropic.claude`; tapping the bars refreshes usage now (user request; the fetch runs in an expedited WorkManager job, since a widget action is stopped after ~10 s; a failed fetch shows "not updated · tap to retry"); signed out → opens the sign-in WebView |

Verify the package names on the device with `adb shell pm list packages | grep -i <name>` before
hard-coding; fall back gracefully (no crash) if a package is missing.

Add a `<queries>` block in the manifest listing every package above, otherwise
`getLaunchIntentForPackage` returns null on Android 11+.

### Home Assistant access

- Settings store: local URL, remote URL (Tailscale or Nabu Casa), long-lived access token, and
  all entity IDs. All start empty (except HA's default `weather.forecast_home`); car entities and
  the lock are picked from lists of HA entities.
- Request strategy: try internal with a short connect timeout (~2 s), then external.
- Cleartext is allowed in `network_security_config.xml` (Android can't scope it to a range) and
  narrowed in code: `HaClient` refuses http:// unless `Lan.isLocal(host)` (private IPv4, Tailscale
  100.64/10, `.local`/`.lan`/single-label names). Every other request is https.

### Claude usage

Sign-in WebView hosts popups ("Continue with Google") as child WebViews, applies system-bar insets and polls the cookie jar (claude.ai signs in without a page load). Fallback: paste a `sessionKey` from a desktop browser in settings; the Cloudflare solver then adopts the WebView UA.

There is no public API for Pro/Max usage. utaysi/claude-usage-widget (Kotlin + Glance) logs in via
a claude.ai WebView, stores `sessionKey`, `cf_clearance`, the WebView User-Agent and org ID in
EncryptedSharedPreferences, calls the undocumented endpoint used by `claude.ai/settings/usage`,
and silently re-solves Cloudflare in an off-screen WebView when needed. Implement the same flow
in our own code. That repository has no LICENSE file, so do not copy its source verbatim; read it
for how the flow works and write our own implementation (or the user asks the author first).
The endpoint is unofficial and can break at any time; show a "Tap to sign in" / stale state
instead of crashing.

## Refresh

- WorkManager periodic job every 15 min (Android minimum) for HA + Claude, then `updateAll`.
- Separate WorkManager job with a content-URI trigger on `CalendarContract.Events.CONTENT_URI`
  so calendar edits show up quickly.
- On every refresh, also update at midnight boundaries (date line, "Today" header).
- Show nothing broken when offline: keep last good values, render them in tertiary color if
  older than 1 h.

## Settings activity (plain Compose screen, launcher icon)

One UI-style, not "cards with explanations": big title, "Refresh" / "Refresh and go home" pills, then
grouped rows (title + one-line summary; colour only when something needs attention) that open
sub-pages: Home Assistant (addresses, token, entities under "Entities", "Save and test"), Weather
(sources with what each says right now, radio to pick), Calendar (opens the app, or asks for
access), Claude (sign in, fetch now, paste a session key, sign out), Appearance (size sliders),
Background refresh (battery optimization). UI code: `settings/SettingsUi.kt`.

## Milestones (build and install on the phone after each: `./gradlew :app:assembleDebug` then
`adb install -r app/build/outputs/apk/debug/app-debug.apk`)

1. Widget skeleton with static data that matches the mockup (sizes, colors, system font).
2. Calendar from `CalendarContract` with scrolling `LazyColumn` and row taps.
3. Settings + Home Assistant: weather, car address/area/range.
4. Vehicle render image with caching.
5. Claude usage sign-in and fetch.
6. Polish: stale states, resize behavior, widget preview image for the picker. Done with
   `SizeMode.Exact` kept on purpose (the usage bars and the map need the real width; Responsive
   buckets would misplace them): below 380dp height the calendar is dropped, below 300dp the
   car/Claude grid; forecast days go 3 → 2 → 0 as width drops under 340/280dp. Picker preview:
   Glance generated preview (Android 15+, cached data or samples, published once per install) plus a
   static `previewLayout` fallback.

## Out of scope

A search bar (the browser's own widget is used), navigation buttons, 3D rendering (widgets
cannot render live 3D; the HA render image is used instead).
