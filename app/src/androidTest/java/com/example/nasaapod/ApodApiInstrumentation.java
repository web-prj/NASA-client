package com.example.nasaapod;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import android.content.Context;

/** Small on-device regression suite using only Android's built-in test APIs. */
public class ApodApiInstrumentation extends Instrumentation {
    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle results = new Bundle();
        try {
            JSONObject entry = new JSONObject();
            entry.put("date", "2026-10-19");
            entry.put("title", "Stars &amp; galaxies");
            entry.put("explanation", "<strong>Explanation:</strong> A <a href='https://nasa.gov'>galaxy</a>.");
            entry.put("copyright", "<a href='https://nasa.gov'>NASA</a>");
            entry.put("media_type", "image");
            entry.put("url", "https://science.nasa.gov/image-article/example/");
            entry.put("hdurl", "https://assets.science.nasa.gov/example.jpg");

            Apod dated = ApodApi.parseResponse(entry.toString(), "2026-10-19");
            check(dated.getImageUrl().equals(entry.getString("hdurl")), "Use direct hdurl, not article URL");
            check(dated.getOriginalUrl().equals(entry.getString("hdurl")), "Open full image, not article URL");
            check(dated.getTitle().equals("Stars & galaxies"), "Decode title entities");
            check(dated.getExplanation().equals("Explanation: A galaxy."), "Strip explanation HTML");
            check(dated.getCopyright().equals("NASA"), "Strip credit HTML");
            check(Apod.fromJson(dated.toJson()).getImageUrl().equals(dated.getImageUrl()),
                    "Preserve direct image URL in favorites");

            // Literal tags and entity-looking text must survive repeated favorite loads.
            JSONObject textEntry = new JSONObject(entry.toString());
            textEntry.put("title", "Stars &lt;blue&gt; &amp;amp;");
            textEntry.put("explanation", "Keep &lt;example&gt;, &amp;lt; and line breaks.<br>Next line.");
            textEntry.put("copyright", "A &lt;B&gt; &amp;amp; C");
            Apod original = ApodApi.parseResponse(textEntry.toString(), "2026-10-19");
            check(original.getTitle().equals("Stars <blue> &amp;"), "Decode NASA text exactly once");
            Apod restored = original;
            for (int i = 0; i < 3; i++) {
                restored = Apod.fromJson(restored.toJson());
                check(restored.getTitle().equals(original.getTitle()), "Preserve saved title");
                check(restored.getExplanation().equals(original.getExplanation()), "Preserve saved explanation");
                check(restored.getCopyright().equals(original.getCopyright()), "Preserve saved credit");
            }

            Apod latest = ApodApi.parseResponse("[" + entry + "]", null);
            check(latest.getDate().equals("2026-10-19"), "Read latest array");
            expectFailure("[]", null);
            expectFailure(entry.toString(), "2026-10-18");
            expectFailure("{\"code\":\"not_found\",\"message\":\"No entry\"}", null);

            entry.remove("hdurl");
            entry.put("url", "https://example.com/legacy.jpg");
            check(Apod.fromJson(entry).getImageUrl().equals("https://example.com/legacy.jpg"),
                    "Old saved entries still load");
            check(Apod.fromJson(entry).getOriginalUrl().equals("https://example.com/legacy.jpg"),
                    "Open old image URL when hdurl is absent");
            JSONObject nonPhoto = new JSONObject(entry.toString()).put("media_type", "video");
            try {
                Apod.fromJson(nonPhoto);
                throw new AssertionError("Non-photo data must not become a photo");
            } catch (JSONException expected) { }
            try {
                ApodApi.parseResponse(nonPhoto.toString(), "2026-10-19");
                throw new AssertionError("Picked non-photo date must show a no-photo error");
            } catch (ApodApi.NoPhotoException expected) {
                check(expected.date.equals("2026-10-19"), "Keep rejected date for browsing");
            }
            checkPhotoSearch();
            checkPhotoFavorites(entry, nonPhoto);

            check(MainActivity.shiftDate("2024-01-01", -1).equals("2023-12-31"), "Previous year");
            check(MainActivity.shiftDate("2023-12-31", 1).equals("2024-01-01"), "Next year");
            check(MainActivity.shiftDate("2024-03-01", -1).equals("2024-02-29"), "Leap day");
            check(MainActivity.shiftDate("2024-02-29", 1).equals("2024-03-01"), "After leap day");
            check(MainActivity.shiftDate("2023-03-01", -1).equals("2023-02-28"), "Non-leap year");

            check(ApodApi.imageSampleSize(4000, 3969, 400) == 16,
                    "Sample oversized source images for grid memory safety");
            check(ApodApi.imageSampleSize(1600, 900, 1600) == 1, "Keep phone-sized images");
            check(MainActivity.displayExplanation("Explanation: A galaxy.").equals("A galaxy."),
                    "Remove redundant explanation prefix for display");
            check(MainActivity.displayExplanation("  EXPLANATION: A galaxy.").equals("A galaxy."),
                    "Handle prefix case and whitespace");
            check(MainActivity.displayExplanation("An explanation: of stars.")
                    .equals("An explanation: of stars."), "Keep explanation text inside sentences");
            check(MainActivity.displayExplanation(original.getExplanation()).equals(original.getExplanation()),
                    "Do not decode literal text again for display");

            results.putString("stream", "APOD regression checks passed.\n");
            finish(Activity.RESULT_OK, results);
        } catch (Exception | AssertionError failure) {
            results.putString("stream", "APOD regression checks FAILED: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, results);
        }
    }

    private static void checkPhotoSearch() throws Exception {
        ApodApi.PhotoSource source = date -> {
            if (date == null || date.equals("2024-01-06")) {
                throw new ApodApi.NoPhotoException("2024-01-06");
            }
            return new Apod(date, "Photo", "Description", "https://example.com/photo.jpg", "NASA");
        };
        check(ApodApi.findPhoto(null, 0, source).getDate().equals("2024-01-05"),
                "Latest falls back to previous photo");
        check(ApodApi.findPhoto("2024-01-06", 1, source).getDate().equals("2024-01-07"),
                "Next skips non-photo entry forward");
        check(ApodApi.findPhoto("2024-01-06", -1, source).getDate().equals("2024-01-05"),
                "Previous skips non-photo entry backward");
        try {
            ApodApi.findPhoto("2024-01-06", 0, source);
            throw new AssertionError("Date picker must not silently change dates");
        } catch (ApodApi.NoPhotoException expected) { }
        int[] calls = {0};
        try {
            ApodApi.findPhoto("2024-01-06", 1, date -> {
                calls[0]++;
                throw new ApodApi.ApodException("Connection failed");
            });
            throw new AssertionError("Connection errors must not be skipped");
        } catch (ApodApi.ApodException expected) {
            check(calls[0] == 1, "Stop immediately on real errors");
        }
        calls[0] = 0;
        try {
            ApodApi.findPhoto("2024-01-06", 1, date -> {
                calls[0]++;
                if (date.equals("2024-01-06")) { throw new ApodApi.NoPhotoException(date); }
                throw new ApodApi.ApodException("Connection failed after skip");
            });
            throw new AssertionError("Errors after skipped entries must stop the search");
        } catch (ApodApi.ApodException expected) {
            check(calls[0] == 2 && expected.getMessage().equals("Connection failed after skip"),
                    "Preserve the original error after skipping unsupported media");
        }
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
        calls[0] = 0;
        try {
            ApodApi.findPhoto(today, 1, date -> {
                calls[0]++;
                throw new ApodApi.NoPhotoException(date);
            });
            throw new AssertionError("Search must stop at today");
        } catch (ApodApi.ApodException expected) {
            check(calls[0] == 1, "Do not browse into the future");
        }
        calls[0] = 0;
        try {
            ApodApi.findPhoto("2024-01-06", -1, date -> {
                calls[0]++;
                throw new ApodApi.NoPhotoException(date);
            });
            throw new AssertionError("Search must be bounded");
        } catch (ApodApi.ApodException expected) {
            check(calls[0] == 31, "Cap search at 31 entries");
        }
        calls[0] = 0;
        try {
            ApodApi.findPhoto("1995-06-16", -1, date -> {
                calls[0]++;
                throw new ApodApi.NoPhotoException(date);
            });
            throw new AssertionError("Search must stop at first APOD date");
        } catch (ApodApi.ApodException expected) {
            check(calls[0] == 1, "Do not browse before the first APOD date");
        }
    }

    private void checkPhotoFavorites(JSONObject photo, JSONObject nonPhoto) throws Exception {
        // Use the test APK's preferences, never overwrite the user's collection.
        android.content.SharedPreferences prefs = getContext()
                .getSharedPreferences("favorites", Context.MODE_PRIVATE);
        String before = prefs.getString("list", "[]");
        try {
            JSONArray saved = new JSONArray().put(nonPhoto).put(photo).put(new JSONObject())
                    .put(new JSONObject(photo.toString()).put("date", "2024-01-01"));
            prefs.edit().putString("list", saved.toString()).commit();
            FavoritesStore store = new FavoritesStore(getContext());
            check(store.getAll().size() == 2, "Hide non-photo/malformed favorites and retain all photos");
            check(prefs.getString("list", "").equals(saved.toString()), "Reading must not rewrite saved data");
            check(store.getAll().get(1).getDate().equals("2024-01-01"),
                    "Continue reading photos after unsupported favorites");
            store.remove("2024-01-01");
            check(new FavoritesStore(getContext()).getAll().size() == 1,
                    "Photo favorite removal persists across store reconstruction");
            check(new JSONArray(prefs.getString("list", "[]")).length() == 1,
                    "A successful write removes unsupported old entries");
        } finally {
            prefs.edit().putString("list", before).commit();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectFailure(String body, String date) throws Exception {
        try {
            ApodApi.parseResponse(body, date);
        } catch (ApodApi.ApodException expected) {
            return;
        }
        throw new AssertionError("Expected a visible error for missing or mismatched date");
    }
}
