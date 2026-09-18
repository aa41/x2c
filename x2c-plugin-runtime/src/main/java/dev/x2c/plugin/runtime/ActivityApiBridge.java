package dev.x2c.plugin.runtime;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.session.MediaController;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import java.util.Objects;

/** Receiver-aware bridge for final Activity APIs used by transformed plugin bytecode. */
public final class ActivityApiBridge {
    private ActivityApiBridge() {}

    private static Activity target(Activity receiver) {
        Activity activity = Objects.requireNonNull(receiver, "activity");
        return activity instanceof PluginActivity
                ? ((PluginActivity) activity).getContainerActivity()
                : activity;
    }

    public static Application getApplication(Activity receiver) {
        return target(receiver).getApplication();
    }

    public static boolean isChild(Activity receiver) {
        return target(receiver).isChild();
    }

    public static Activity getParent(Activity receiver) {
        return target(receiver).getParent();
    }

    @SuppressWarnings("deprecation")
    public static Cursor managedQuery(
            Activity receiver,
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        return target(receiver).managedQuery(
                uri, projection, selection, selectionArgs, sortOrder);
    }

    public static void setResult(Activity receiver, int resultCode) {
        target(receiver).setResult(resultCode);
    }

    public static void setResult(Activity receiver, int resultCode, Intent data) {
        target(receiver).setResult(resultCode, data);
    }

    public static boolean requestWindowFeature(Activity receiver, int featureId) {
        return target(receiver).requestWindowFeature(featureId);
    }

    public static CharSequence getTitle(Activity receiver) {
        return target(receiver).getTitle();
    }

    @SuppressWarnings("deprecation")
    public static int getTitleColor(Activity receiver) {
        return target(receiver).getTitleColor();
    }

    public static void runOnUiThread(Activity receiver, Runnable action) {
        target(receiver).runOnUiThread(action);
    }

    public static void setVolumeControlStream(Activity receiver, int streamType) {
        target(receiver).setVolumeControlStream(streamType);
    }

    public static int getVolumeControlStream(Activity receiver) {
        return target(receiver).getVolumeControlStream();
    }

    public static void setMediaController(Activity receiver, MediaController controller) {
        target(receiver).setMediaController(controller);
    }

    public static MediaController getMediaController(Activity receiver) {
        return target(receiver).getMediaController();
    }

    public static void requestPermissions(
            Activity receiver, String[] permissions, int requestCode) {
        if (Build.VERSION.SDK_INT < 23) {
            throw new UnsupportedOperationException("requestPermissions requires Android API 23+");
        }
        target(receiver).requestPermissions(permissions, requestCode);
    }

    public static <T extends View> T requireViewById(Activity receiver, int id) {
        T view = target(receiver).findViewById(id);
        if (view == null) {
            throw new IllegalArgumentException("ID does not reference a View inside this Activity");
        }
        return view;
    }

    public static void setDefaultKeyMode(Activity receiver, int mode) {
        target(receiver).setDefaultKeyMode(mode);
    }

    public static void setFeatureDrawableResource(
            Activity receiver, int featureId, int resId) {
        target(receiver).setFeatureDrawableResource(featureId, resId);
    }

    public static void setFeatureDrawableUri(Activity receiver, int featureId, Uri uri) {
        target(receiver).setFeatureDrawableUri(featureId, uri);
    }

    public static void setFeatureDrawable(
            Activity receiver, int featureId, Drawable drawable) {
        target(receiver).setFeatureDrawable(featureId, drawable);
    }

    public static void setFeatureDrawableAlpha(
            Activity receiver, int featureId, int alpha) {
        target(receiver).setFeatureDrawableAlpha(featureId, alpha);
    }

    public static void setProgressBarVisibility(Activity receiver, boolean visible) {
        target(receiver).setProgressBarVisibility(visible);
    }

    public static void setProgressBarIndeterminateVisibility(
            Activity receiver, boolean visible) {
        target(receiver).setProgressBarIndeterminateVisibility(visible);
    }

    public static void setProgressBarIndeterminate(
            Activity receiver, boolean indeterminate) {
        target(receiver).setProgressBarIndeterminate(indeterminate);
    }

    public static void setProgress(Activity receiver, int progress) {
        target(receiver).setProgress(progress);
    }

    public static void setSecondaryProgress(Activity receiver, int progress) {
        target(receiver).setSecondaryProgress(progress);
    }
}
