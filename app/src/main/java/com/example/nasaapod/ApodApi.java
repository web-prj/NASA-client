package com.example.nasaapod;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
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

    private static final String BASE_URL = "https://api.nasa.gov/planetary/apod";
    private static final int TIMEOUT_MS = 15000;

    /** An error with a message that is safe to show to the user. */
    public static class ApodException extends Exception {
        public ApodException(String message) {
            super(message);
        }
    }

    /**
     * Downloads the picture for a date ("yyyy-MM-dd").
     * Pass null to get the latest picture.
     */
    public static Apod fetchPicture(String date) throws ApodException {
        String address = BASE_URL + "?thumbs=true&api_key=" + API_KEY;
        if (date != null) {
            address += "&date=" + date;
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            int status = connection.getResponseCode();
            if (status == 200) {
                String body = readText(connection.getInputStream());
                return Apod.fromJson(new JSONObject(body));
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

    /** Downloads an image. Returns null if it can't be loaded. */
    public static Bitmap downloadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(imageUrl).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            return BitmapFactory.decodeStream(connection.getInputStream());
        } catch (IOException e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** For a bad date NASA sends a body like {"msg": "Date must be between ..."}. */
    private static String readErrorMessage(HttpURLConnection connection, int status) {
        String message = "Could not load the picture (error " + status + ").";
        try {
            InputStream errorStream = connection.getErrorStream();
            if (errorStream != null) {
                JSONObject body = new JSONObject(readText(errorStream));
                message = body.optString("msg", message);
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
