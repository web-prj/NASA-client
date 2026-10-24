package com.example.nasaapod;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** APOD detail screen, built with Java and native XML Views. */
public class MainActivity extends Activity {
    public static final String EXTRA_DATE = "date";
    private static final int REQUEST_FAVORITE = 1;
    private final ExecutorService backgroundThread = Executors.newSingleThreadExecutor();
    private FavoritesStore favorites;
    private Apod currentApod;
    private String requestedDate;
    private boolean loading;
    private int direction;
    private int requestNumber;
    private ObjectAnimator skeletonPulse;
    private ScrollView contentView;
    private View loadingView, errorView, hero, titleGlass, playButton;
    private ImageView imagePicture;
    private TextView textTitle, textInfo, textExplanation, textError;
    private Button buttonPickDate, buttonPrevious, buttonNext, buttonToday;
    private ImageButton buttonFavorite, buttonOpenOriginal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        contentView = findViewById(R.id.contentView);
        loadingView = findViewById(R.id.loadingView);
        errorView = findViewById(R.id.errorView);
        hero = findViewById(R.id.hero);
        hero.setClipToOutline(true);
        titleGlass = findViewById(R.id.titleGlass);
        imagePicture = findViewById(R.id.imagePicture);
        textTitle = findViewById(R.id.textTitle);
        textInfo = findViewById(R.id.textInfo);
        textExplanation = findViewById(R.id.textExplanation);
        textError = findViewById(R.id.textError);
        buttonPickDate = findViewById(R.id.buttonPickDate);
        buttonPrevious = findViewById(R.id.buttonPrevious);
        buttonNext = findViewById(R.id.buttonNext);
        buttonToday = findViewById(R.id.buttonToday);
        buttonFavorite = findViewById(R.id.buttonFavorite);
        buttonOpenOriginal = findViewById(R.id.buttonOpenOriginal);
        playButton = findViewById(R.id.buttonPlay);
        favorites = new FavoritesStore(this);

        // Image extends behind the status bar; only its controls receive the top inset.
        SpaceUi.edgeToEdge(this, findViewById(R.id.mainRoot),
                findViewById(R.id.heroActions), findViewById(R.id.bottomNavigation));
        sizeHero();
        buttonPrevious.setOnClickListener(v -> changeDay(-1));
        buttonNext.setOnClickListener(v -> changeDay(1));
        buttonPickDate.setOnClickListener(v -> pickDate());
        buttonToday.setOnClickListener(v -> latest());
        buttonFavorite.setOnClickListener(v -> toggleFavorite());
        buttonOpenOriginal.setOnClickListener(v -> openOriginal());
        playButton.setOnClickListener(v -> openOriginal());
        findViewById(R.id.buttonRetry).setOnClickListener(v -> loadPicture());
        findViewById(R.id.buttonErrorDate).setOnClickListener(v -> pickDate());
        findViewById(R.id.buttonErrorToday).setOnClickListener(v -> latest());
        findViewById(R.id.tabToday).setSelected(true);
        findViewById(R.id.tabToday).setOnClickListener(v -> latest());
        findViewById(R.id.buttonFavorites).setOnClickListener(v ->
                startActivityForResult(new Intent(this, FavoritesActivity.class), REQUEST_FAVORITE));
        setupImageGestures();
        contentView.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            if (!SpaceUi.motionEnabled(this)) {
                imagePicture.setTranslationY(0f);
                titleGlass.setAlpha(1f);
                titleGlass.setScaleX(1f);
                titleGlass.setScaleY(1f);
                return;
            }
            float progress = Math.min(1f, y / (float) hero.getHeight());
            imagePicture.setTranslationY(Math.min(y, hero.getHeight()) * 0.18f);
            titleGlass.setAlpha(1f - progress * 0.45f);
            titleGlass.setScaleX(1f - progress * 0.04f);
            titleGlass.setScaleY(1f - progress * 0.04f);
        });
        requestedDate = savedInstanceState == null ? getIntent().getStringExtra(EXTRA_DATE)
                : savedInstanceState.getString(EXTRA_DATE);
        loadPicture();
    }

    private void sizeHero() {
        int height = Math.round(getResources().getDisplayMetrics().heightPixels * 0.58f);
        // Keep title and 48dp actions readable in landscape and at larger font sizes.
        int minimum = getResources().getDimensionPixelSize(R.dimen.hero_min_height);
        hero.getLayoutParams().height = Math.max(minimum, height);
        findViewById(R.id.skeletonHero).getLayoutParams().height = Math.max(minimum, height);
    }

    @SuppressLint("ClickableViewAccessibility") // Gesture taps delegate to performClick below.
    private void setupImageGestures() {
        GestureDetector gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent event) {
                imagePicture.performClick();
                return true;
            }
            @Override public boolean onFling(MotionEvent first, MotionEvent last, float vx, float vy) {
                if (first == null || Math.abs(vx) < Math.abs(vy)) { return false; }
                float dx = last.getX() - first.getX();
                float threshold = getResources().getDimension(R.dimen.touch_target);
                if (Math.abs(dx) < threshold) { return false; }
                changeDay(dx < 0 ? 1 : -1);
                return true;
            }
        });
        imagePicture.setOnClickListener(v -> {
            if (currentApod != null && !currentApod.isImage()) { openOriginal(); }
        });
        imagePicture.setOnTouchListener((view, event) -> {
            // Let vertical gestures continue scrolling the explanation.
            return gestures.onTouchEvent(event);
        });
    }

    private void latest() {
        if (loading) { return; }
        requestedDate = null;
        direction = 0;
        loadPicture();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString(EXTRA_DATE, requestedDate);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_FAVORITE && result == RESULT_OK && data != null) {
            String date = data.getStringExtra(EXTRA_DATE);
            if (date != null || data.getBooleanExtra("latest", false)) {
                requestedDate = date;
                direction = 0;
                loadPicture();
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        updateFavoriteButton();
        if (loading) { startSkeleton(); }
    }

    @Override protected void onPause() {
        stopSkeleton();
        super.onPause();
    }

    @Override protected void onDestroy() {
        requestNumber++;
        stopSkeleton();
        backgroundThread.shutdownNow();
        super.onDestroy();
    }

    private void loadPicture() {
        loading = true;
        final int request = ++requestNumber;
        showOnly(loadingView);
        startSkeleton();
        buttonFavorite.setEnabled(false);
        buttonOpenOriginal.setEnabled(false);
        String date = requestedDate;
        backgroundThread.execute(() -> {
            try {
                Apod apod = ApodApi.fetchPicture(date);
                Bitmap image = ApodApi.downloadImage(apod.getImageUrl());
                if (apod.isImage() && image == null) {
                    throw new ApodApi.ApodException(getString(R.string.image_download_error));
                }
                runOnUiThread(() -> {
                    if (!isDestroyed() && request == requestNumber) { showPicture(apod, image); }
                });
            } catch (ApodApi.ApodException e) {
                runOnUiThread(() -> {
                    if (!isDestroyed() && request == requestNumber) { showError(e.getMessage()); }
                });
            }
        });
    }

    private void showPicture(Apod apod, Bitmap image) {
        loading = false;
        stopSkeleton();
        currentApod = apod;
        imagePicture.animate().cancel();
        imagePicture.setImageBitmap(image);
        imagePicture.setContentDescription(apod.getTitle());
        textTitle.setText(apod.getTitle());
        String info = apod.getDate();
        if (!apod.getCopyright().isEmpty()) { info += "  ·  © " + apod.getCopyright(); }
        textInfo.setText(info);
        textExplanation.setText(displayExplanation(apod.getExplanation()));
        playButton.setVisibility(apod.isImage() ? View.GONE : View.VISIBLE);
        if (!apod.isImage() && image == null) {
            textInfo.setText(getString(R.string.video_metadata, info, getString(R.string.no_video_preview)));
        }
        contentView.scrollTo(0, 0);
        imagePicture.setTranslationY(0f);
        titleGlass.setAlpha(1f);
        titleGlass.setScaleX(1f);
        titleGlass.setScaleY(1f);
        showOnly(contentView);
        updateNavigation();
        updateFavoriteButton();
        buttonOpenOriginal.setContentDescription(getString(apod.isImage()
                ? R.string.open_original : R.string.open_video));
        buttonOpenOriginal.setEnabled(!apod.getOriginalUrl().isEmpty());
        SpaceUi.enter(this, contentView, direction, 0);
        if (SpaceUi.motionEnabled(this)) {
            imagePicture.setAlpha(0f);
            imagePicture.animate().alpha(1f).setDuration(300).start();
        } else { imagePicture.setAlpha(1f); }
        direction = 0;
    }

    static String displayExplanation(String explanation) {
        return explanation.replaceFirst("(?i)^\\s*Explanation:\\s*", "");
    }

    private void showError(String message) {
        loading = false;
        stopSkeleton();
        textError.setText(message);
        showOnly(errorView);
        direction = 0;
    }

    private void showOnly(View view) {
        contentView.setVisibility(view == contentView ? View.VISIBLE : View.INVISIBLE);
        loadingView.setVisibility(view == loadingView ? View.VISIBLE : View.GONE);
        errorView.setVisibility(view == errorView ? View.VISIBLE : View.GONE);
    }

    private void startSkeleton() {
        stopSkeleton();
        if (!SpaceUi.motionEnabled(this)) { return; }
        skeletonPulse = ObjectAnimator.ofFloat(loadingView, View.ALPHA, 0.45f, 1f);
        skeletonPulse.setDuration(800);
        skeletonPulse.setRepeatCount(ValueAnimator.INFINITE);
        skeletonPulse.setRepeatMode(ValueAnimator.REVERSE);
        skeletonPulse.start();
    }

    private void stopSkeleton() {
        if (skeletonPulse != null) { skeletonPulse.cancel(); skeletonPulse = null; }
        if (loadingView != null) { loadingView.setAlpha(1f); }
    }

    private String navigationDate() {
        return currentApod == null ? (requestedDate == null ? todayDate() : requestedDate)
                : currentApod.getDate();
    }

    private void updateNavigation() {
        String date = navigationDate();
        buttonPickDate.setText(getString(R.string.date_chip, date));
        buttonPrevious.setEnabled(date.compareTo("1995-06-16") > 0);
        buttonNext.setEnabled(date.compareTo(todayDate()) < 0);
        buttonToday.setVisibility(date.equals(todayDate()) ? View.GONE : View.VISIBLE);
    }

    private void changeDay(int days) {
        if (loading) { return; }
        String date = shiftDate(navigationDate(), days);
        if (date.compareTo("1995-06-16") < 0 || date.compareTo(todayDate()) > 0) { return; }
        requestedDate = date;
        direction = days;
        loadPicture();
    }

    private static String todayDate() { return formatDate(Calendar.getInstance()); }

    static String shiftDate(String date, int days) {
        String[] parts = date.split("-");
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1,
                Integer.parseInt(parts[2]));
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return formatDate(calendar);
    }

    private static String formatDate(Calendar date) {
        return String.format(Locale.US, "%04d-%02d-%02d", date.get(Calendar.YEAR),
                date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH));
    }

    private void openOriginal() {
        if (loading || currentApod == null) { return; }
        Uri uri = Uri.parse(currentApod.getOriginalUrl());
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_SHORT).show();
            return;
        }
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_SHORT).show();
        }
    }

    private void pickDate() {
        Calendar shown = Calendar.getInstance();
        String date = requestedDate != null ? requestedDate : navigationDate();
        String[] parts = date.split("-");
        shown.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1,
                Integer.parseInt(parts[2]));
        DatePickerDialog dialog = new DatePickerDialog(this, R.style.SpaceDatePicker,
                (view, year, month, day) -> {
                    requestedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
                    direction = 0;
                    loadPicture();
                }, shown.get(Calendar.YEAR), shown.get(Calendar.MONTH), shown.get(Calendar.DAY_OF_MONTH));
        Calendar first = Calendar.getInstance();
        first.clear();
        first.set(1995, Calendar.JUNE, 16);
        dialog.getDatePicker().setMinDate(first.getTimeInMillis());
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void toggleFavorite() {
        if (loading || currentApod == null) { return; }
        if (favorites.contains(currentApod.getDate())) {
            favorites.remove(currentApod.getDate());
            Toast.makeText(this, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show();
        } else {
            favorites.add(currentApod);
            Toast.makeText(this, R.string.added_to_favorites, Toast.LENGTH_SHORT).show();
        }
        updateFavoriteButton();
        SpaceUi.heartBounce(this, buttonFavorite);
    }

    private void updateFavoriteButton() {
        if (buttonFavorite == null || favorites == null) { return; }
        boolean available = !loading && currentApod != null;
        buttonFavorite.setEnabled(available);
        boolean saved = available && favorites.contains(currentApod.getDate());
        buttonFavorite.setImageResource(saved ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        buttonFavorite.setImageTintList(ColorStateList.valueOf(getColor(saved
                ? R.color.accent : R.color.text_primary)));
        buttonFavorite.setContentDescription(getString(saved ? R.string.unsave : R.string.save));
        buttonFavorite.setSelected(saved);
    }
}
