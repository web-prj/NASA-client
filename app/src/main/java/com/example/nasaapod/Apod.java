package com.example.nasaapod;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One "Astronomy Picture of the Day" entry from NASA.
 */
public class Apod {

    private final String date;          // Format: yyyy-MM-dd
    private final String title;
    private final String explanation;
    private final String url;           // Legacy image URL or NASA article URL
    private final String copyright;
    private String hdUrl = "";           // Direct image URL in NASA's new API

    public Apod(String date, String title, String explanation, String url, String copyright) {
        this.date = date;
        this.title = title;
        this.explanation = explanation;
        this.url = url;
        this.copyright = copyright;
    }

    /** Reads picture data. Text is already plain text, including in saved favorites. */
    public static Apod fromJson(JSONObject json) throws JSONException {
        if (!"image".equals(json.optString("media_type", "image"))) {
            throw new JSONException("Only photos are supported.");
        }
        Apod apod = new Apod(
                json.getString("date"),
                json.optString("title", "Untitled"),
                json.optString("explanation", ""),
                json.optString("url", ""),
                json.optString("copyright", "")
        );
        apod.hdUrl = json.optString("hdurl", "");
        return apod;
    }

    /** Turns this Apod into JSON, so it can be saved as a favorite. */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("date", date);
        json.put("title", title);
        json.put("explanation", explanation);
        json.put("url", url);
        json.put("hdurl", hdUrl);
        json.put("media_type", "image");
        json.put("copyright", copyright);
        return json;
    }

    /** Direct photo URL, with support for older saved favorites. */
    public String getImageUrl() {
        return hdUrl.isEmpty() ? url : hdUrl;
    }

    public String getOriginalUrl() {
        return getImageUrl();
    }

    public String getDate() {
        return date;
    }

    public String getTitle() {
        return title;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getCopyright() {
        return copyright;
    }
}
