package com.example.nasaapod;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

import org.json.JSONObject;

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
            entry.put("media_type", "video");
            entry.put("thumbnail_url", "https://example.com/preview.jpg");
            entry.put("hdurl", "https://example.com/video");
            check(Apod.fromJson(entry).getImageUrl().equals("https://example.com/preview.jpg"),
                    "Video uses thumbnail, not hdurl");

            results.putString("stream", "APOD regression checks passed.\n");
            finish(Activity.RESULT_OK, results);
        } catch (Exception | AssertionError failure) {
            results.putString("stream", "APOD regression checks FAILED: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, results);
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
