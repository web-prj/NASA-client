package com.example.nasaapod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Two-column favorites grid, using only Android Views and a bounded thumbnail cache. */
public class FavoritesActivity extends Activity {
    private final ExecutorService images = Executors.newFixedThreadPool(2);
    private final Set<String> pending = new HashSet<>();
    private final Set<String> failed = new HashSet<>();
    private final Set<String> appeared = new HashSet<>();
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private FavoritesStore favorites;
    private GridView grid;
    private List<Apod> items = new ArrayList<>();
    private final ThumbnailAdapter adapter = new ThumbnailAdapter();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_favorites);
        SpaceUi.edgeToEdge(this, findViewById(R.id.favoritesRoot),
                findViewById(R.id.favoritesHeader), findViewById(R.id.bottomNavigation));
        favorites = new FavoritesStore(this);
        grid = findViewById(R.id.listFavorites);
        grid.setEmptyView(findViewById(R.id.textEmpty));
        grid.setAdapter(adapter);
        findViewById(R.id.buttonFavorites).setSelected(true);
        findViewById(R.id.tabToday).setOnClickListener(v -> {
            Intent result = new Intent().putExtra(MainActivity.EXTRA_DATE, (String) null);
            // Explicit flag distinguishes Today from the system Back action.
            result.putExtra("latest", true);
            setResult(RESULT_OK, result);
            finish();
        });
        grid.setOnItemClickListener((parent, view, position, id) -> {
            setResult(RESULT_OK, new Intent().putExtra(MainActivity.EXTRA_DATE, items.get(position).getDate()));
            finish(); // Native activity fade is the lightweight shared-element fallback.
        });
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            Apod apod = items.get(position);
            new AlertDialog.Builder(this).setTitle(R.string.remove_favorite_title)
                    .setMessage(apod.getTitle()).setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.remove, (dialog, which) -> {
                        favorites.remove(apod.getDate());
                        Toast.makeText(this, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show();
                        refresh();
                    }).show();
            return true;
        });
    }

    @Override protected void onResume() {
        super.onResume();
        failed.clear(); // Returning after connectivity recovers gives blank thumbnails another try.
        refresh();
    }

    private void refresh() {
        items = favorites.getAll();
        adapter.notifyDataSetChanged();
    }

    @Override protected void onDestroy() {
        images.shutdownNow();
        cache.evictAll();
        super.onDestroy();
    }

    private class ThumbnailAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Apod getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View recycled, ViewGroup parent) {
            View cell = recycled == null ? LayoutInflater.from(FavoritesActivity.this)
                    .inflate(R.layout.item_favorite, parent, false) : recycled;
            cell.setClipToOutline(true);
            Apod apod = getItem(position);
            ((TextView) cell.findViewById(R.id.favoriteTitle)).setText(apod.getTitle());
            ((TextView) cell.findViewById(R.id.favoriteDate)).setText(apod.getDate());
            ImageView image = cell.findViewById(R.id.favoriteImage);
            String url = apod.getImageUrl();
            cell.setContentDescription(apod.getTitle() + ", " + apod.getDate());
            image.setImageBitmap(cache.get(url));
            if (cache.get(url) == null && !url.isEmpty() && !pending.contains(url)
                    && !failed.contains(url)) {
                pending.add(url);
                images.execute(() -> {
                    Bitmap bitmap = ApodApi.downloadImage(url, 400);
                    runOnUiThread(() -> {
                        if (isDestroyed()) { return; }
                        pending.remove(url);
                        if (bitmap != null) { cache.put(url, bitmap); }
                        else { failed.add(url); }
                        // Rebinding uses the current item's URL, never a recycled view's old request.
                        adapter.notifyDataSetChanged();
                    });
                });
            }
            if (appeared.add(apod.getDate())) {
                SpaceUi.enter(FavoritesActivity.this, cell, 0, Math.min(position, 5) * 45L);
            } else {
                cell.animate().cancel();
                cell.setAlpha(1f);
                cell.setTranslationY(0f);
                cell.setScaleX(1f);
                cell.setScaleY(1f);
            }
            return cell;
        }
    }
}
