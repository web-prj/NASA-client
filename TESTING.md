# Regression verification

## Current change: keep days without pictures

- Removed adjacent-date searching, including the latest-entry fallback.
- Non-photo entries, empty API arrays and missing image URLs now show a neutral
  no-picture state with the selected date and working Prev/Next/date controls.
- Favorite and original-image actions are disabled on empty days. Network, parsing
  and image-download failures remain errors with Retry.
- Updated instrumentation checks to assert one exact-date request, preservation of
  the non-photo latest entry's date, empty results and missing image URLs.
- Verified `:app:assembleDebug`, `:app:assembleDebugAndroidTest` and `:app:lintDebug`
  successfully. No device/emulator was connected, so the updated instrumentation
  suite and empty-state UI have not yet been run on a device.
- Manual follow-up: browse January 5 → 6 → 7, 2024 and back, rotate on January 6,
  check disabled image actions, swipe away from the empty day, then verify offline
  Retry does not change the requested date.

## Historical validation before this change

The notes below describe the previous implementation; skipping/fallback behavior
is superseded by the exact-date behavior above.

Tested on the Android emulator (`sdk_gphone64_x86_64`, API 37) using the debug
build. The app remains Java + XML Views with no third-party application libraries.

## Automated checks

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb shell am instrument -w com.example.nasaapod.test/com.example.nasaapod.ApodApiInstrumentation
```

Build and instrumentation passed; lint reported no issues. Instrumentation covers
NASA response formats, date mismatch/empty results, HTML conversion exactly once,
favorites round trips and mixed/malformed legacy entries, direct HD URL selection,
image sampling, leap/year boundaries, photo-only search direction, latest fallback,
search limits, earliest/today boundaries, and preserving errors after skipped media.
Tests use test-APK preferences, not the user's saved collection.

## Emulator checks performed

- Latest photo: image, title, actual date, credit and explanation.
- Full-width hero beginning at screen y=0, transparent status bar, overlay actions.
- First APOD and today navigation limits; conditional Today chip.
- Previous/Next across a year boundary and February 29, 2024.
- Date-picker selection different from launch date; portrait/landscape recreation
  and background process recreation retain that selected date.
- Horizontal image swipes and navigation chips skip January 6, 2024 in both
  directions; December 31, 2023 is also skipped as a non-photo entry.
- Exact non-photo date displays a no-photo error; date-picker and Today recovery.
- Vertical image/explanation scrolling.
- Heart toggle, persistence after restart, two-column favorites, opening a saved
  photo without adding another main activity, Back, and Today tab.
- Long-press removal: cancel, confirm, and refreshed heart on return.
- Empty favorites state after test cleanup.
- Offline error and successful Retry after connectivity returns.
- Forward/backward search across January 6 interrupted offline, then successful
  Retry in the original direction.
- Favorites thumbnail failure offline, then successful image recovery after
  reconnecting and resuming the screen.
- Favorite selection superseding interrupted detail loading; stale result does
  not replace the selected photo.
- HD image VIEW intent and Back to the original date.
- Navigation with system animator scale zero and actions/tabs at 130% font scale.

Temporary test favorites and changed connectivity/rotation/font/animation settings
were restored. No app data was cleared.

## Independent review

Two read-only sub-agents reviewed photo/data behavior and UI/lifecycle behavior.
The identified directional-Retry blocker was fixed and reproduced successfully
in both directions. Also fixed: superseded-request cancellation, failed-thumbnail
retry on resume, and horizontal system/cutout safe insets on content/actions while
leaving the image full-bleed. Follow-up review found no remaining blockers.

## Limits

This is emulator validation, not a guarantee across all physical devices. TalkBack,
physical camera cutouts, three-button side navigation and older Android versions
were not device-tested. Latest-photo fallback when the newest APOD is not a photo
is covered with an injected source; the live latest entry was a photo during testing.
An interrupted active HTTP operation can still wait for its configured 15-second
network timeout. Favorites metadata persists, but images require connectivity;
failed thumbnails retry when Favorites resumes, not immediately on network change.
