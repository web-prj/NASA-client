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
    private final String url;           // The image, or the video link
    private final String mediaType;     // "image" or "video"
    private final String thumbnailUrl;  // Preview image, only for videos
    private final String copyright;

    public Apod(String date, String title, String explanation, String url,
                String mediaType, String thumbnailUrl, String copyright) {
        this.date = date;
        this.title = title;
        this.explanation = explanation;
        this.url = url;
        this.mediaType = mediaType;
        this.thumbnailUrl = thumbnailUrl;
        this.copyright = copyright;
    }

    /** Builds an Apod from the JSON that NASA sends. */
    public static Apod fromJson(JSONObject json) throws JSONException {
        return new Apod(
                json.getString("date"),
                json.optString("title", "Untitled"),
                json.optString("explanation", ""),
                json.optString("url", ""),
                json.optString("media_type", "image"),
                json.optString("thumbnail_url", ""),
                json.optString("copyright", "").trim()
        );
    }

    /** Turns this Apod into JSON, so it can be saved as a favorite. */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("date", date);
        json.put("title", title);
        json.put("explanation", explanation);
        json.put("url", url);
        json.put("media_type", mediaType);
        json.put("thumbnail_url", thumbnailUrl);
        json.put("copyright", copyright);
        return json;
    }

    public boolean isImage() {
        return "image".equals(mediaType);
    }

    /** The picture to show: the image itself, or the preview of a video. */
    public String getImageUrl() {
        return isImage() ? url : thumbnailUrl;
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

    public String getUrl() {
        return url;
    }

    public String getCopyright() {
        return copyright;
    }
}
