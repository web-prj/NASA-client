package com.example.nasaapod;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Saves favorite pictures on the phone using SharedPreferences.
 * The list is stored as one JSON text, newest favorite first.
 */
public class FavoritesStore {

    private static final String PREFS_NAME = "favorites";
    private static final String KEY_LIST = "list";

    private final SharedPreferences prefs;

    public FavoritesStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<Apod> getAll() {
        List<Apod> favorites = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_LIST, "[]"));
            for (int i = 0; i < array.length(); i++) {
                favorites.add(Apod.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // Saved data is broken: start with an empty list
        }
        return favorites;
    }

    public boolean contains(String date) {
        for (Apod apod : getAll()) {
            if (apod.getDate().equals(date)) {
                return true;
            }
        }
        return false;
    }

    public void add(Apod apod) {
        List<Apod> favorites = getAll();
        favorites.add(0, apod);
        save(favorites);
    }

    public void remove(String date) {
        List<Apod> favorites = getAll();
        favorites.removeIf(apod -> apod.getDate().equals(date));
        save(favorites);
    }

    private void save(List<Apod> favorites) {
        JSONArray array = new JSONArray();
        try {
            for (Apod apod : favorites) {
                array.put(apod.toJson());
            }
        } catch (JSONException e) {
            return; // Don't overwrite the old list with broken data
        }
        prefs.edit().putString(KEY_LIST, array.toString()).apply();
    }
}
