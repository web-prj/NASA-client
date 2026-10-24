package com.example.nasaapod;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONTokener;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Talks to NASA's Astronomy Picture of the Day API.
 * These methods use the network, so call them from a background thread.
 */
public class ApodApi {

    // Comes from local.properties (see app/build.gradle), so it never ends up on GitHub.
    private static final String API_KEY = BuildConfig.NASA_API_KEY;

    private static final String BASE_URL = "https://science.nasa.gov/wp-json/wp/v2/apod-basic";
    private static final int TIMEOUT_MS = 15000;

    /** An error with a message that is safe to show to the user. */
    public static class ApodException extends Exception {
        public ApodException(String message) {
            super(message);
        }
    }

    /** Distinguishes a non-photo entry from connection, parsing or missing-date errors. */
    static class NoPhotoException extends ApodException {
        final String date;
        NoPhotoException(String date) {
            super("No photo for this date. Pick another date or return to the latest photo.");
            this.date = date;
        }
    }

    interface PhotoSource {
        Apod fetch(String date) throws ApodException;
    }

    /** Latest searches backward; day browsing skips non-photo entries in its direction. */
    public static Apod fetchPhoto(String date, int direction) throws ApodException {
        return findPhoto(date, direction, ApodApi::fetchPicture);
    }

    static Apod findPhoto(String date, int direction, PhotoSource source) throws ApodException {
        int step = date == null ? -1 : direction;
        String candidate = date;
        // Bound network work rather than searching indefinitely through unavailable photos.
        for (int i = 0; i < 31; i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new ApodException("Photo loading was cancelled.");
            }
            try {
                return source.fetch(candidate);
            } catch (NoPhotoException e) {
                if (step == 0) { throw e; } // A picked date must never silently change.
                candidate = MainActivity.shiftDate(e.date, step);
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        .format(new java.util.Date());
                if (candidate.compareTo("1995-06-16") < 0 || candidate.compareTo(today) > 0) {
                    throw new ApodException("No more photos in this direction. Pick another date.");
                }
            }
        }
        throw new ApodException("No photo found within 31 entries. Please pick another date.");
    }

    /**
     * Downloads the picture for a date ("yyyy-MM-dd").
     * Pass null to get the latest picture.
     */
    public static Apod fetchPicture(String date) throws ApodException {
        // The new API ignores ?date=. A dated entry uses a /YYMMDD path.
        if (date != null && !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new ApodException("Choose a date in YYYY-MM-DD format.");
        }
        String path = date == null ? "" : "/" + date.substring(2).replace("-", "");
        String address = BASE_URL + path + "?per_page=1&api_key=" + API_KEY;

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            int status = connection.getResponseCode();
            if (status == 200) {
                return parseResponse(readText(connection.getInputStream()), date);
            }
            if (status == 429) {
                throw new ApodException("Too many requests. Wait a while, or use "
                        + "your own free API key from api.nasa.gov.");
            }
            throw new ApodException(readErrorMessage(connection, status));
        } catch (IOException e) {
            throw new ApodException("Could not connect. Check your internet connection.");
        } catch (JSONException e) {
            throw new ApodException("NASA sent data the app could not read.");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Latest returns an array; a dated request returns a single object. */
    static Apod parseResponse(String body, String requestedDate)
            throws JSONException, ApodException {
        Object response = new JSONTokener(body).nextValue();
        if (response instanceof JSONArray) {
            JSONArray entries = (JSONArray) response;
            if (entries.length() == 0) {
                throw new ApodException("NASA has no picture available for this date yet.");
            }
            response = entries.getJSONObject(0);
        }
        if (!(response instanceof JSONObject)) {
            throw new JSONException("Expected an APOD object or array");
        }
        JSONObject json = (JSONObject) response;
        if (!json.has("date")) {
            throw new ApodException(json.optString("message", "NASA has no picture for this date."));
        }
        String date = json.getString("date");
        if (requestedDate != null && !requestedDate.equals(date)) {
            throw new ApodException("NASA returned a different date. Please try another date.");
        }
        if (!"image".equals(json.optString("media_type", "image"))) {
            throw new NoPhotoException(date);
        }
        // Decode NASA's HTML only here, never when reloading saved plain text.
        json.put("title", plainText(json.optString("title", "Untitled")));
        json.put("explanation", plainText(json.optString("explanation", "")));
        json.put("copyright", plainText(json.optString("copyright", "")));
        return Apod.fromJson(json);
    }

    private static String plainText(String html) {
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }

    /** Downloads an image. Returns null if it can't be loaded. */
    public static Bitmap downloadImage(String imageUrl) {
        return downloadImage(imageUrl, 1600);
    }

    /** Smaller previews keep the favorites grid's network and memory use modest. */
    static Bitmap downloadImage(String imageUrl, int size) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }

        HttpURLConnection connection = null;
        try {
            // Avoid decoding huge full-resolution NASA images on a phone.
            Uri uri = Uri.parse(imageUrl);
            if ("assets.science.nasa.gov".equals(uri.getHost())) {
                imageUrl = uri.buildUpon().clearQuery()
                        .appendQueryParameter("w", String.valueOf(size))
                        .appendQueryParameter("h", String.valueOf(size))
                        .appendQueryParameter("fit", "clip").build().toString();
            }
            connection = (HttpURLConnection) new URL(imageUrl).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (InputStream stream = connection.getInputStream()) {
                // Some NASA assets ignore resize parameters. Always sample on-device too.
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) { return null; }
                    if (bytes.size() + count > 32 * 1024 * 1024) { return null; }
                    bytes.write(buffer, 0, count);
                }
                byte[] data = bytes.toByteArray();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, options);
                options.inSampleSize = imageSampleSize(options.outWidth, options.outHeight, size);
                options.inJustDecodeBounds = false;
                return BitmapFactory.decodeByteArray(data, 0, data.length, options);
            }
        } catch (IOException e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static int imageSampleSize(int width, int height, int target) {
        int sample = 1;
        while (Math.max(width, height) / sample > target) { sample *= 2; }
        return sample;
    }

    /** For a bad date NASA sends a body like {"msg": "Date must be between ..."}. */
    private static String readErrorMessage(HttpURLConnection connection, int status) {
        String message = "Could not load the picture (error " + status + ").";
        try {
            InputStream errorStream = connection.getErrorStream();
            if (errorStream != null) {
                JSONObject body = new JSONObject(readText(errorStream));
                message = body.optString("message", body.optString("msg", message));
            }
        } catch (IOException | JSONException e) {
            // Keep the general message
        }
        return message;
    }

    private static String readText(InputStream stream) throws IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        }
        return text.toString();
    }
}
