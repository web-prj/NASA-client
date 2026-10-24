package com.example.nasaapod;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main screen: shows one picture and lets the user pick another date.
 */
public class MainActivity extends Activity {

    /** Pass a date ("yyyy-MM-dd") with this key to open that day's picture. */
    public static final String EXTRA_DATE = "date";
    private static final int REQUEST_FAVORITE = 1;

    // Network calls must not run on the main (UI) thread, so we use this one.
    private final ExecutorService backgroundThread = Executors.newSingleThreadExecutor();

    private FavoritesStore favorites;
    private Apod currentApod;
    private String requestedDate; // null means "the latest picture"

    private View contentView;
    private View progressBar;
    private View errorView;
    private ImageView imagePicture;
    private TextView textTitle;
    private TextView textInfo;
    private TextView textExplanation;
    private TextView textError;
    private Button buttonPickDate;
    private Button buttonFavorite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        contentView = findViewById(R.id.contentView);
        progressBar = findViewById(R.id.progressBar);
        errorView = findViewById(R.id.errorView);
        imagePicture = findViewById(R.id.imagePicture);
        textTitle = findViewById(R.id.textTitle);
        textInfo = findViewById(R.id.textInfo);
        textExplanation = findViewById(R.id.textExplanation);
        textError = findViewById(R.id.textError);
        buttonPickDate = findViewById(R.id.buttonPickDate);
        buttonFavorite = findViewById(R.id.buttonFavorite);

        buttonPickDate.setOnClickListener(v -> pickDate());
        buttonFavorite.setOnClickListener(v -> toggleFavorite());
        findViewById(R.id.buttonFavorites).setOnClickListener(v ->
                startActivityForResult(new Intent(this, FavoritesActivity.class), REQUEST_FAVORITE));
        findViewById(R.id.buttonRetry).setOnClickListener(v -> loadPicture());

        favorites = new FavoritesStore(this);
        requestedDate = savedInstanceState == null
                ? getIntent().getStringExtra(EXTRA_DATE)
                : savedInstanceState.getString(EXTRA_DATE);
        loadPicture();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_DATE, requestedDate);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FAVORITE && resultCode == RESULT_OK && data != null) {
            String date = data.getStringExtra(EXTRA_DATE);
            if (date != null) {
                requestedDate = date;
                loadPicture();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have removed this picture on the Favorites screen.
        updateFavoriteButton();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backgroundThread.shutdownNow();
    }

    private void loadPicture() {
        showOnly(progressBar);
        buttonPickDate.setEnabled(false);
        buttonFavorite.setEnabled(false);

        String date = requestedDate;
        backgroundThread.execute(() -> {
            try {
                Apod apod = ApodApi.fetchPicture(date);
                Bitmap image = ApodApi.downloadImage(apod.getImageUrl());
                if (apod.isImage() && image == null) {
                    throw new ApodApi.ApodException(
                            "The picture could not be downloaded. Check your connection and try again.");
                }
                runOnUiThread(() -> showPicture(apod, image));
            } catch (ApodApi.ApodException e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        });
    }

    private void showPicture(Apod apod, Bitmap image) {
        if (isDestroyed()) {
            return;
        }
        currentApod = apod;

        if (image != null) {
            imagePicture.setImageBitmap(image);
            imagePicture.setVisibility(View.VISIBLE);
        } else {
            imagePicture.setVisibility(View.GONE);
        }

        String info = apod.getDate();
        if (!apod.getCopyright().isEmpty()) {
            info += "  ·  © " + apod.getCopyright();
        }
        if (!apod.isImage()) {
            info += "\nThis entry is a video: " + apod.getUrl();
            if (image == null) {
                info += "\nNo preview is available. Open the video link above in your browser.";
            }
        }

        textTitle.setText(apod.getTitle());
        textInfo.setText(info);
        textExplanation.setText(apod.getExplanation());
        contentView.scrollTo(0, 0);

        showOnly(contentView);
        buttonPickDate.setEnabled(true);
        updateFavoriteButton();
    }

    private void showError(String message) {
        if (isDestroyed()) {
            return;
        }
        textError.setText(message);
        showOnly(errorView);
        buttonPickDate.setEnabled(true);
    }

    /** Shows one of: the picture, the loading circle, or the error message. */
    private void showOnly(View view) {
        contentView.setVisibility(view == contentView ? View.VISIBLE : View.GONE);
        progressBar.setVisibility(view == progressBar ? View.VISIBLE : View.GONE);
        errorView.setVisibility(view == errorView ? View.VISIBLE : View.GONE);
    }

    private void pickDate() {
        // Start the calendar on the date currently shown, or today.
        Calendar shown = Calendar.getInstance();
        if (currentApod != null) {
            String[] parts = currentApod.getDate().split("-");
            shown.set(Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]) - 1, // Calendar months start at 0
                    Integer.parseInt(parts[2]));
        }

        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    requestedDate = String.format(Locale.US, "%04d-%02d-%02d",
                            year, month + 1, day);
                    loadPicture();
                },
                shown.get(Calendar.YEAR),
                shown.get(Calendar.MONTH),
                shown.get(Calendar.DAY_OF_MONTH));

        // NASA's first picture of the day is from June 16, 1995.
        Calendar firstDay = Calendar.getInstance();
        firstDay.set(1995, Calendar.JUNE, 16);
        dialog.getDatePicker().setMinDate(firstDay.getTimeInMillis());
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void toggleFavorite() {
        if (currentApod == null) {
            return;
        }
        if (favorites.contains(currentApod.getDate())) {
            favorites.remove(currentApod.getDate());
            Toast.makeText(this, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show();
        } else {
            favorites.add(currentApod);
            Toast.makeText(this, R.string.added_to_favorites, Toast.LENGTH_SHORT).show();
        }
        updateFavoriteButton();
    }

    private void updateFavoriteButton() {
        if (currentApod == null || contentView.getVisibility() != View.VISIBLE) {
            buttonFavorite.setEnabled(false);
            return;
        }
        boolean saved = favorites.contains(currentApod.getDate());
        buttonFavorite.setText(saved ? R.string.saved : R.string.save);
        buttonFavorite.setEnabled(true);
    }
}
