package com.example.nasaapod;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the saved pictures. Tap one to open it, long-press to remove it.
 */
public class FavoritesActivity extends Activity {

    private FavoritesStore favorites;
    private ListView listView;
    private List<Apod> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        favorites = new FavoritesStore(this);
        listView = findViewById(R.id.listFavorites);
        listView.setEmptyView(findViewById(R.id.textEmpty));

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Intent result = new Intent();
            result.putExtra(MainActivity.EXTRA_DATE, items.get(position).getDate());
            setResult(RESULT_OK, result);
            finish();
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            favorites.remove(items.get(position).getDate());
            Toast.makeText(this, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show();
            refreshList();
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload every time, because favorites may have changed on another screen.
        refreshList();
    }

    private void refreshList() {
        items = favorites.getAll();

        List<String> rows = new ArrayList<>();
        for (Apod apod : items) {
            rows.add(apod.getTitle() + "\n" + apod.getDate());
        }
        listView.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, rows));
    }
}
