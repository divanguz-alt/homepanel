# HomePanel: redesign A2 (weather + calendar, grey map)

Visual refresh of the existing HomePanel Glance widget. Read the repo's
CLAUDE.md first; everything there still applies (stack, data sources, refresh,
tap actions) unless this file says otherwise.

Design reference: artboard "A2 · Siva karta" (the mockup's car image and map
are placeholders; the widget keeps the real Home Assistant picture and map).

## Hard rules

1. **Bottom grid: only the map palette changes.** The car column (car name label,
   address, city, vehicle render callout, fuel icon + range) and the Claude
   usage column (5 hours / Week, gradient bars, "resets in" lines) keep their
   layout, sizes, text, data and tap actions exactly. The single allowed change
   is the map look, described under Zone 3. If anything else in the bottom grid
   would shift or resize, stop and ask.
2. **System font only.** No bundled fonts, no FontFamily overrides.
3. Keep the existing panel background, corner radius and text color roles
   (primary / secondary / tertiary). Only add the new colors named below.
4. No data source changes. Everything shown already exists in the widget.
5. Goal of this revision: use the space. Larger type and icons, tighter gaps,
   no dead areas. If the widget is taller than the content, give the extra
   height to the event list (more rows), never to padding.

## Zone 1: Weather

Remove: the date line, the refresh icon (manual refresh stays in the settings
activity), and the word "now" ("Cloudy now" → "Cloudy").

One Row, children aligned to the bottom, 8dp gap.

Left column, 4dp spacing:
- City, 15sp, secondary, medium.
- Current temperature, 76sp, normal weight.
- Condition row, 6dp above it, 14sp secondary, single line, 6dp gaps:
  condition icon 18dp · condition text · 3dp tertiary dot · 8dp AQI dot ·
  "AQI {value}". AQI dot color by US AQI band: 0–50 `#7DCB8E`, 51–100
  `#E5B454`, 101–150 `#E8894A`, above 150 `#E06A6A`. Hide the AQI part when
  the value is missing.

Right: forecast for the next three days, three equal columns, max 196dp wide
in total, columns centered, 10dp vertical spacing:
- Day, title case from locale ("Tue"), 14sp secondary.
- Icon 32dp. Sunny tinted `#E5B454`, others secondary.
- High 18sp primary medium + space + low 18sp tertiary normal. Never wrap.

## Zone 2: Calendar

1dp hairline in the existing divider color above it, 16dp below the weather
zone, 4dp padding under the hairline.

Header row, 44dp:
- "Coming up", 15sp secondary medium.
- Right side: "Nothing today" 14sp tertiary when today has no events
  (replaces "Today: no events"), then the + button: 22dp icon in a 44dp touch
  target bleeding 11dp into the right padding.

Event list (keep the LazyColumn and row taps):
- Group by day. 62dp day column + event column. Day label ("Fri 23",
  locale `EEE d`, "Today" for today) 15sp secondary, shown only on the first
  event of that day.
- 4dp between events of the same day, 12dp extra before a new day.
- No divider lines.
- Row, 26dp line height: 10dp dot in the calendar color, 10dp gap, title 16sp
  primary (one line, ellipsize end), time 15sp tertiary right-aligned.
- All-day events show no time (no "all").
- Full event titles from the calendar, never shortened by the widget.

## Zone 3: Map palette (bottom-left cell only)

Make the map match the panel instead of standing out in blue-grey:
- Background ≈ `#1F2024`, building blocks ≈ `#24262A`, main roads ≈ `#35373D`
  with a `#17181B` casing, minor roads ≈ `#2C2E33`.
- If the map is a bitmap (static map or tiles), apply this when the bitmap is
  prepared in the worker, before it reaches RemoteViews: a ColorMatrix that
  fully desaturates and darkens it so the result lands near those values.
  If the provider offers a dark/greyscale style, use that instead and tune it.
- Vehicle callout background (box + pointer) `#0E0F11`.
- Text and icons on the map (car name label, city, fuel icon) `#A2A4AB`;
  address and range stay primary.

## Spacing summary

Content padding 18dp left/right/top, 12dp between the event list and the
bottom grid's top hairline.

## Acceptance

- Bottom grid identical to before except the map colors and callout color
  (compare screenshots at the same widget size).
- No date line, no refresh icon, no "now", no "all", no row dividers.
- Same-day events share one day label; extra height shows more events.
- AQI dot follows the band and hides when unavailable.
- Samsung system font everywhere.
- Build and install: `./gradlew :app:assembleDebug` then
  `adb install -r app/build/outputs/apk/debug/app-debug.apk`, and send a
  screenshot of the widget.
