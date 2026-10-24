package com.example.nasaapod;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;

/** Small native UI helpers shared by the two XML screens. */
final class SpaceUi {
    private SpaceUi() { }

    static boolean motionEnabled(Activity activity) {
        if (Build.VERSION.SDK_INT >= 26) {
            return ValueAnimator.areAnimatorsEnabled();
        }
        return Settings.Global.getFloat(activity.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
    }

    static void edgeToEdge(Activity activity, View root, View top, View bottom) {
        activity.getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        activity.getWindow().setNavigationBarColor(activity.getColor(R.color.space_background));
        activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        int topPadding = top.getPaddingTop();
        int bottomPadding = bottom.getPaddingBottom();
        View[] safeViews = {top, bottom, root.findViewById(R.id.titleGlass),
                root.findViewById(R.id.dayNavigation), root.findViewById(R.id.storyCard),
                root.findViewById(R.id.errorView), root.findViewById(R.id.listFavorites)};
        int[] leftPadding = new int[safeViews.length];
        int[] rightPadding = new int[safeViews.length];
        for (int i = 0; i < safeViews.length; i++) {
            if (safeViews[i] != null) {
                leftPadding[i] = safeViews[i].getPaddingLeft();
                rightPadding[i] = safeViews[i].getPaddingRight();
            }
        }
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left = insets.getSystemWindowInsetLeft();
            int right = insets.getSystemWindowInsetRight();
            if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                left = Math.max(left, insets.getDisplayCutout().getSafeInsetLeft());
                right = Math.max(right, insets.getDisplayCutout().getSafeInsetRight());
            }
            for (int i = 0; i < safeViews.length; i++) {
                View safe = safeViews[i];
                if (safe != null) {
                    safe.setPadding(leftPadding[i] + left, safe.getPaddingTop(),
                            rightPadding[i] + right, safe.getPaddingBottom());
                }
            }
            top.setPadding(top.getPaddingLeft(), topPadding + insets.getSystemWindowInsetTop(),
                    top.getPaddingRight(), top.getPaddingBottom());
            bottom.setPadding(bottom.getPaddingLeft(), bottom.getPaddingTop(),
                    bottom.getPaddingRight(), bottomPadding + insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();
    }

    static void enter(Activity activity, View view, int direction, long delay) {
        view.animate().cancel();
        if (!motionEnabled(activity)) {
            view.setAlpha(1f);
            view.setTranslationX(0f);
            view.setTranslationY(0f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            return;
        }
        view.setAlpha(0f);
        view.setScaleX(0.98f);
        view.setScaleY(0.98f);
        if (direction == 0) {
            view.setTranslationY(activity.getResources().getDimension(R.dimen.space_medium));
        } else {
            view.setTranslationX(direction * activity.getResources().getDimension(R.dimen.space_large));
        }
        view.animate().alpha(1f).translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay(delay)
                .setDuration(280).setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f)).start();
    }

    static void heartBounce(Activity activity, View heart) {
        if (!motionEnabled(activity)) {
            return;
        }
        heart.animate().cancel();
        heart.animate().scaleX(1.25f).scaleY(1.25f).setDuration(110).withEndAction(() ->
                heart.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
    }
}
