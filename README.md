# Space Pictures

A simple Android app in **Java** for browsing NASA's Astronomy Picture of the Day (APOD).

- Opens on the latest NASA space picture, with its title and explanation
- **Pick date** shows the picture from any day since June 16, 1995
- **♡ Save** adds the picture to your favorites (saved on the phone)
- **Favorites** lists saved pictures: tap to open, long-press to remove

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
    MainActivity.java          Main screen: picture, date picker, save button
    FavoritesActivity.java     List of saved pictures
  res/layout/
    activity_main.xml          Layout of the main screen
    activity_favorites.xml     Layout of the favorites screen
  res/values/strings.xml       All text shown in the app
```

## How it works

1. `MainActivity` starts a background thread (network calls are not allowed on the UI thread).
2. `ApodApi.fetchPicture(date)` calls NASA's current endpoint,
   `https://science.nasa.gov/wp-json/wp/v2/apod-basic?per_page=1&api_key=KEY`,
   for the latest entry. A specific date uses `/YYMMDD` before the query string
   (for example `/240101` for January 1, 2024). The old APOD endpoint can return
   the NASA logo instead of the actual picture.
3. The response can be an array (latest) or an object (specific date). The app
   strips HTML from text and uses `hdurl` for images: `url` is now an article page.
4. `ApodApi.downloadImage(url)` requests a phone-sized NASA image as a `Bitmap`.
5. `runOnUiThread(...)` puts the result on the screen. Image failures show a
   retryable error instead of silently hiding the picture.

## On-device regression checks

With an emulator or phone connected:

```bash
./gradlew installDebug installDebugAndroidTest
adb shell am instrument -w com.example.nasaapod.test/com.example.nasaapod.ApodApiInstrumentation
```

The dependency-free checks cover the current response formats, direct image URLs,
HTML text, favorites serialization, video thumbnails, and missing/mismatched dates.
