# NASA-client

A simple, photo-only Android app in **Java** for browsing NASA's Astronomy Picture of the Day (APOD).

- Opens on the latest NASA photo, with its actual date, title, credit and explanation
- Tap the date chip to pick any day since June 16, 1995; non-photo days show a
  clear “No photo for this date” message rather than opening other media
- **Prev / Next** or a horizontal swipe browses the nearest photo in that direction,
  skipping non-photo entries
- **Today** returns to the latest available photo (chip hidden on today's date)
- The floating **↗** opens the original HD image in a compatible app
- The floating **heart** saves/removes a favorite (stored on the phone)
- **Favorites** shows a two-column thumbnail grid: tap to open, long-press to remove
  after confirmation
- The bottom **TODAY / FAVORITES** tabs switch between browsing and the collection
- Photo browsing only; no playback controls and no Share feature

No extra libraries: only classes built into Android
(`HttpURLConnection`, `org.json`, `SharedPreferences`, `DatePickerDialog`, `ListView`).

## Before you run

Get a free NASA API key at <https://api.nasa.gov> and add it to the file
`local.properties` in the project folder (create the file if it doesn't exist):

```properties
nasa.api.key=YOUR_KEY
```

`local.properties` is in `.gitignore`, so your key is never pushed to GitHub.
Each group member uses their own key. Without one, the app falls back to
NASA's `DEMO_KEY`. The app uses NASA's current APOD WordPress endpoint;
key requirements and rate limits depend on the NASA service.

## Run it

**Android Studio:** File → Open → choose this folder, then press Run ▶.

**Command line** (with a phone plugged in and USB debugging on):

```bash
./gradlew installDebug
```

## Files

```
app/src/main/
  AndroidManifest.xml          Screens and the INTERNET permission
  java/com/example/nasaapod/
    Apod.java                  The data for one picture (JSON in and out)
    ApodApi.java               Downloads the picture info and image from NASA
    FavoritesStore.java        Saves favorites with SharedPreferences
    MainActivity.java          Hero image, day navigation, date picker, heart
    FavoritesActivity.java     Thumbnail grid with a bounded in-memory cache
    SpaceUi.java               Insets and native motion helpers
  res/layout/
    activity_main.xml          Layout of the main screen
    activity_favorites.xml     Layout of the favorites screen
    item_favorite.xml          Rounded thumbnail card
    day_navigation.xml         Compact day/date controls
    bottom_navigation.xml      Shared two-tab navigation
  res/values/                 Strings, colors, dimensions, dark theme/styles
  res/drawable/               Rounded shapes, scrims, ripple states, vector icons
```

## How it works

1. `MainActivity` starts a background thread (network calls are not allowed on the UI thread).
2. `ApodApi.fetchPhoto(date, direction)` searches for a photo using NASA's current endpoint,
   `https://science.nasa.gov/wp-json/wp/v2/apod-basic?per_page=1&api_key=KEY`,
   for the latest entry. A specific date uses `/YYMMDD` before the query string
   (for example `/240101` for January 1, 2024). The old APOD endpoint can return
   the NASA logo instead of the actual picture. Latest searches backward if the
   latest entry is not a photo; Prev/Next search in their own direction. Only
   non-photo entries are skipped, not connection or missing-date errors. Searches
   stop at the date boundaries or after 31 entries. Picked dates never silently change.
3. The response can be an array (latest) or an object (specific date). The app
   strips HTML once in `ApodApi` and uses `hdurl` for images: `url` is now an
   article page. Saved favorites are already plain text and are not decoded again.
4. `ApodApi.downloadImage(url)` requests a phone-sized NASA image as a `Bitmap`.
5. `runOnUiThread(...)` puts the result on the screen. Image failures show a
   retryable error instead of silently hiding the picture.

The selected date is saved in `onSaveInstanceState()` so rotating the phone
keeps it (the image is downloaded again). Favorites returns the selected date
with `setResult()` and closes itself, instead of creating another main screen.
These use the built-in Android activity APIs, with no additional libraries.

## Visual design and motion

The app stays with **Java + native XML Views**, not Compose. A near-black theme,
24dp dark cards, white headlines, cool-grey metadata and a pale-blue accent keep
the focus on NASA's images. The toolbar is removed; the hero extends behind a
transparent status bar. Window insets keep actions and bottom tabs clear of the
system bars. The native date picker and removal confirmation use the same dark theme.

The image has a translucent title card (not an expensive blurred surface),
center-crop framing and a gradient scrim. A dark pulsing skeleton precedes a
300ms image fade. Day changes slide/fade in over 280ms; scrolling adds light
parallax and title-card fading. Hearts bounce, controls have ripples, and grid
cards have a short staggered entrance. Favorites-to-detail uses a native activity
fade and scale-in instead of a shared-element transition across a network reload.
Animations check the system animator setting; the loading pulse stops while the
activity is paused. No Coil, Glide or other library is needed.

Favorites thumbnails download on two background workers and use an 8MiB memory
cache. Both hero and grid images are downsampled before decoding, since some NASA
assets ignore server-side resize parameters. Favorites metadata is persistent;
thumbnails are not stored for offline use. Only `media_type=image` entries are
accepted; unsupported entries in older favorites are hidden without hiding photos
that follow them. A later favorites write cleans those unsupported entries out.

## On-device regression checks

With an emulator or phone connected:

```bash
./gradlew installDebug installDebugAndroidTest
adb shell am instrument -w com.example.nasaapod.test/com.example.nasaapod.ApodApiInstrumentation
```

The dependency-free checks cover the current response formats, direct image URLs,
HTML text, repeated plain-text favorites serialization, rejection of non-photo
entries, and missing/mismatched dates. They also check original image link selection,
photo-only search directions, bounded searching, preservation of connection errors,
filtering older mixed-media favorites,
day navigation across month/year boundaries and leap days, image downsampling,
and removal of the redundant leading `Explanation:` label for display.

Manual screen checks:

1. Tap the date chip, select an older date, then rotate the device in both
   directions. The selected date should stay the same.
2. Save a picture, choose a different date, then open the saved picture through
   **Favorites**. It should appear on the existing main screen.
3. Open **Favorites** and press Back without selecting anything. The current
   picture should remain selected.
4. Browse with **Prev / Next** and horizontal image swipes, then press **Today**
   to return to the latest photo. Prev is disabled on June 16, 1995; Next is
   disabled at today's date. The Today chip is hidden on today's date.
5. Tap **↗** to view the full image externally. Pick January 6, 2024 (a non-photo
   day): the no-photo error should appear, with no playback controls. From January
   5, Next should skip directly to January 7; Prev should return to January 5.
6. Loading replaces content with a dark skeleton. If loading fails, **Pick date**,
   **Today**, and **Try again** allow recovery.
7. Disable animations in system settings and repeat navigation. Content should
   appear immediately without a pulsing skeleton, bounce or parallax.
8. Check an empty favorites collection, a filled two-column grid, and long-press
   removal confirmation. Both tabs should remain accessible above the system bar.
