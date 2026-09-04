package dev.x2c.plugin.runtime;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.session.MediaController;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

/**
 * Compile-time replacement superclass for plugin Activities.
 *
 * <p>It remains an {@link Activity} subtype for binary compatibility, while every stateful
 * operation is delegated to the real manifest-declared container Activity.</p>
 */
@SuppressLint("MissingSuperCall") // Framework super calls are forwarded through the container.
public abstract class PluginActivity extends Activity {
    private PluginContainerActivity container;
    private String pluginId;
    private String targetClassName;
    private Intent pluginIntent;
    private boolean destroyed;
    private boolean attachingBaseContext;
    private boolean baseContextAttached;

    final void attachPlugin(
            PluginContainerActivity container,
            String pluginId,
            String targetClassName,
            Intent pluginIntent) {
        if (this.container != null) {
            throw new IllegalStateException("Plugin Activity is already attached: " + targetClassName);
        }
        this.container = container;
        this.pluginId = pluginId;
        this.targetClassName = targetClassName;
        this.pluginIntent = pluginIntent;
        attachingBaseContext = true;
        try {
            // Deliberately use virtual dispatch. A plugin BaseActivity may wrap the Context or
            // perform other attach-time initialization before calling through to this root.
            attachBaseContext(new PluginContext(
                    container, pluginId, getClass().getClassLoader()));
        } finally {
            attachingBaseContext = false;
        }
        if (!baseContextAttached) {
            throw new IllegalStateException(
                    "Plugin Activity attachBaseContext() must call super: " + targetClassName);
        }
    }

    @Override protected void attachBaseContext(Context newBase) {
        if (!attachingBaseContext) {
            throw new IllegalStateException(
                    "Plugin Activity base Context may only be attached by the host container");
        }
        if (baseContextAttached) {
            throw new IllegalStateException(
                    "Plugin Activity base Context is already attached: " + targetClassName);
        }
        super.attachBaseContext(newBase);
        baseContextAttached = true;
    }

    public final Activity getContainerActivity() {
        return requireContainer();
    }

    public final String getPluginId() {
        requireContainer();
        return pluginId;
    }

    @Override public ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }

    /** Target for compile-time rewriting of Activity#getApplication, which is final. */
    public final Application getPluginApplication() {
        return requireContainer().getApplication();
    }

    /** Target for compile-time rewriting of final Activity#isChild. */
    public final boolean isPluginChild() {
        return requireContainer().isChild();
    }

    /** Target for compile-time rewriting of final Activity#getParent. */
    public final Activity getPluginParent() {
        return requireContainer().getParent();
    }

    @Override public Intent getIntent() {
        requireContainer();
        return pluginIntent;
    }

    @Override public void setIntent(Intent intent) {
        requireContainer();
        pluginIntent = intent == null ? new Intent() : intent;
    }

    @Override public ComponentName getComponentName() {
        return new ComponentName(requireContainer().getPackageName(), targetClassName);
    }

    @Override public String getLocalClassName() {
        requireContainer();
        return targetClassName;
    }

    @Override public Resources getResources() {
        return requireContainer().getResources();
    }

    @Override public Resources.Theme getTheme() {
        return requireContainer().getTheme();
    }

    @Override public void setTheme(int resId) {
        requireContainer().setTheme(resId);
    }

    @Override public Window getWindow() {
        return requireContainer().getWindow();
    }

    @Override public LayoutInflater getLayoutInflater() {
        return requireContainer().getLayoutInflater().cloneInContext(this);
    }

    @Override public MenuInflater getMenuInflater() {
        return new MenuInflater(this);
    }

    @Override public void setContentView(int layoutResId) {
        requireContainer().setContentView(layoutResId);
    }

    @Override public void setContentView(View view) {
        requireContainer().setContentView(view);
    }

    @Override public void setContentView(View view, ViewGroup.LayoutParams params) {
        requireContainer().setContentView(view, params);
    }

    @Override public void addContentView(View view, ViewGroup.LayoutParams params) {
        requireContainer().addContentView(view, params);
    }

    @Override public <T extends View> T findViewById(int id) {
        return requireContainer().findViewById(id);
    }

    @Override public View getCurrentFocus() {
        return requireContainer().getCurrentFocus();
    }

    @Override public SharedPreferences getPreferences(int mode) {
        return requireContainer().getSharedPreferences(targetClassName, mode);
    }

    @Override public void setTitle(CharSequence title) {
        requireContainer().setTitle(title);
    }

    @Override public void setTitle(int titleId) {
        requireContainer().setTitle(titleId);
    }

    @Override public void setTitleColor(int textColor) {
        requireContainer().setTitleColor(textColor);
    }

    /** Target for compile-time rewriting of final Activity#getTitle. */
    public final CharSequence getPluginTitle() {
        return requireContainer().getTitle();
    }

    /** Target for compile-time rewriting of final Activity#getTitleColor. */
    @SuppressWarnings("deprecation")
    public final int getPluginTitleColor() {
        return requireContainer().getTitleColor();
    }

    @Override public void finish() {
        requireContainer().finish();
    }

    @Override public void finishAffinity() {
        requireContainer().finishAffinity();
    }

    @Override public void finishAfterTransition() {
        requireContainer().finishAfterTransition();
    }

    @Override public void finishAndRemoveTask() {
        requireContainer().finishAndRemoveTask();
    }

    @Override public boolean isFinishing() {
        return container != null && container.isFinishing();
    }

    @Override public boolean isDestroyed() {
        return destroyed || (container != null && container.isDestroyed());
    }

    @Override public void startActivity(Intent intent) {
        PluginActivityManager.startFromPlugin(this, intent, null);
    }

    @Override public void startActivity(Intent intent, Bundle options) {
        PluginActivityManager.startFromPlugin(this, intent, options);
    }

    @Override public void startActivityForResult(Intent intent, int requestCode) {
        PluginActivityManager.startFromPluginForResult(this, intent, requestCode, null);
    }

    @Override public void startActivityForResult(
            Intent intent, int requestCode, Bundle options) {
        PluginActivityManager.startFromPluginForResult(this, intent, requestCode, options);
    }

    @Override public void startActivities(Intent[] intents) {
        requireContainer().startActivities(intents);
    }

    @Override public void startActivities(Intent[] intents, Bundle options) {
        requireContainer().startActivities(intents, options);
    }

    @Override public ComponentName startService(Intent intent) {
        String target = PluginServiceManager.targetForPlugin(getPluginId(), intent);
        return target == null
                ? requireContainer().startService(intent)
                : PluginServiceManager.startService(
                        requireContainer(), getPluginId(), target, intent);
    }

    @Override public ComponentName startForegroundService(Intent intent) {
        String target = PluginServiceManager.targetForPlugin(getPluginId(), intent);
        if (target != null) {
            return PluginServiceManager.startForegroundService(
                    requireContainer(), getPluginId(), target, intent);
        }
        return Build.VERSION.SDK_INT >= 26
                ? requireContainer().startForegroundService(intent)
                : requireContainer().startService(intent);
    }

    @Override public boolean stopService(Intent intent) {
        String target = PluginServiceManager.targetForPlugin(getPluginId(), intent);
        return target == null
                ? requireContainer().stopService(intent)
                : PluginServiceManager.stopService(requireContainer(), getPluginId(), target);
    }

    @Override public boolean bindService(
            Intent intent, ServiceConnection connection, int flags) {
        String target = PluginServiceManager.targetForPlugin(getPluginId(), intent);
        return target == null
                ? requireContainer().bindService(intent, connection, flags)
                : PluginServiceManager.bindService(
                        requireContainer(), getPluginId(), target, intent, connection, flags);
    }

    @Override public void unbindService(ServiceConnection connection) {
        if (!PluginServiceManager.unbindIfPlugin(requireContainer(), connection)) {
            requireContainer().unbindService(connection);
        }
    }

    @Override public void sendBroadcast(Intent intent) {
        String target = PluginReceiverManager.targetForPlugin(getPluginId(), intent);
        if (target == null) requireContainer().sendBroadcast(intent);
        else PluginReceiverManager.sendBroadcast(
                requireContainer(), getPluginId(), target, intent);
    }

    @Override public void sendBroadcast(Intent intent, String receiverPermission) {
        String target = PluginReceiverManager.targetForPlugin(getPluginId(), intent);
        if (target == null) requireContainer().sendBroadcast(intent, receiverPermission);
        else PluginReceiverManager.sendBroadcast(
                requireContainer(), getPluginId(), target, intent, receiverPermission);
    }

    @Override public void sendOrderedBroadcast(
            Intent intent,
            String receiverPermission,
            android.content.BroadcastReceiver resultReceiver,
            android.os.Handler scheduler,
            int initialCode,
            String initialData,
            Bundle initialExtras) {
        String target = PluginReceiverManager.targetForPlugin(getPluginId(), intent);
        if (target == null) {
            requireContainer().sendOrderedBroadcast(
                    intent, receiverPermission, resultReceiver, scheduler,
                    initialCode, initialData, initialExtras);
            return;
        }
        if (scheduler != null) {
            throw new IllegalArgumentException(
                    "A custom Handler is not supported for routed plugin ordered broadcasts");
        }
        PluginReceiverManager.sendOrderedBroadcast(
                requireContainer(), getPluginId(), target, intent, receiverPermission,
                resultReceiver, initialCode, initialData, initialExtras);
    }

    @Override public void recreate() {
        requireContainer().recreate();
    }

    @Override public boolean isTaskRoot() {
        return requireContainer().isTaskRoot();
    }

    @Override public int getTaskId() {
        return requireContainer().getTaskId();
    }

    @Override public boolean moveTaskToBack(boolean nonRoot) {
        return requireContainer().moveTaskToBack(nonRoot);
    }

    @Override public void setRequestedOrientation(int requestedOrientation) {
        requireContainer().setRequestedOrientation(requestedOrientation);
    }

    @Override public int getRequestedOrientation() {
        return requireContainer().getRequestedOrientation();
    }

    @Override public void overridePendingTransition(int enterAnim, int exitAnim) {
        requireContainer().overridePendingTransition(enterAnim, exitAnim);
    }

    /** Target for compile-time rewriting of final Activity#setResult. */
    public final void setPluginResult(int resultCode) {
        requireContainer().setResult(resultCode);
    }

    /** Target for compile-time rewriting of final Activity#setResult. */
    public final void setPluginResult(int resultCode, Intent data) {
        requireContainer().setResult(resultCode, data);
    }

    /** Targets for final Activity APIs that must execute on the manifest Activity instance. */
    public final boolean requestPluginWindowFeature(int featureId) {
        return requireContainer().requestWindowFeature(featureId);
    }

    public final void runOnPluginUiThread(Runnable action) {
        requireContainer().runOnUiThread(action);
    }

    @TargetApi(23)
    public final void requestPluginPermissions(String[] permissions, int requestCode) {
        if (Build.VERSION.SDK_INT < 23) {
            throw new UnsupportedOperationException("requestPermissions requires Android API 23+");
        }
        requireContainer().requestPermissions(permissions, requestCode);
    }

    public final View requirePluginViewById(int id) {
        View view = requireContainer().findViewById(id);
        if (view == null) {
            throw new IllegalArgumentException("ID does not reference a View inside this Activity");
        }
        return view;
    }

    public final void setPluginVolumeControlStream(int streamType) {
        requireContainer().setVolumeControlStream(streamType);
    }

    public final int getPluginVolumeControlStream() {
        return requireContainer().getVolumeControlStream();
    }

    public final void setPluginMediaController(MediaController controller) {
        requireContainer().setMediaController(controller);
    }

    public final MediaController getPluginMediaController() {
        return requireContainer().getMediaController();
    }

    @SuppressWarnings("deprecation")
    public final Cursor pluginManagedQuery(
            Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        return requireContainer().managedQuery(
                uri, projection, selection, selectionArgs, sortOrder);
    }

    public final void setPluginDefaultKeyMode(int mode) {
        requireContainer().setDefaultKeyMode(mode);
    }

    public final void setPluginFeatureDrawableResource(int featureId, int resId) {
        requireContainer().setFeatureDrawableResource(featureId, resId);
    }

    public final void setPluginFeatureDrawableUri(int featureId, Uri uri) {
        requireContainer().setFeatureDrawableUri(featureId, uri);
    }

    public final void setPluginFeatureDrawable(int featureId, Drawable drawable) {
        requireContainer().setFeatureDrawable(featureId, drawable);
    }

    public final void setPluginFeatureDrawableAlpha(int featureId, int alpha) {
        requireContainer().setFeatureDrawableAlpha(featureId, alpha);
    }

    public final void setPluginProgressBarVisibility(boolean visible) {
        requireContainer().setProgressBarVisibility(visible);
    }

    public final void setPluginProgressBarIndeterminateVisibility(boolean visible) {
        requireContainer().setProgressBarIndeterminateVisibility(visible);
    }

    public final void setPluginProgressBarIndeterminate(boolean indeterminate) {
        requireContainer().setProgressBarIndeterminate(indeterminate);
    }

    public final void setPluginProgress(int progress) {
        requireContainer().setProgress(progress);
    }

    public final void setPluginSecondaryProgress(int progress) {
        requireContainer().setSecondaryProgress(progress);
    }

    @Override public void onBackPressed() {
        requireContainer().performSystemBack();
    }

    final void performCreate(Bundle state) { onCreate(state); }
    final void performStart() { onStart(); }
    final void performRestart() { onRestart(); }
    final void performPostCreate(Bundle state) { onPostCreate(state); }
    final void performResume() { onResume(); }
    final void performPostResume() { onPostResume(); }
    final void performPause() { onPause(); }
    final void performStop() { onStop(); }
    final void performDestroy() { destroyed = true; onDestroy(); }
    final void performNewIntent(Intent intent) { setIntent(intent); onNewIntent(intent); }
    final void performSaveInstanceState(Bundle state) { onSaveInstanceState(state); }
    final void performRestoreInstanceState(Bundle state) { onRestoreInstanceState(state); }
    final void performActivityResult(int requestCode, int resultCode, Intent data) {
        onActivityResult(requestCode, resultCode, data);
    }
    final void performRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
    final void performConfigurationChanged(Configuration configuration) {
        onConfigurationChanged(configuration);
    }
    final void performLowMemory() { onLowMemory(); }
    final void performTrimMemory(int level) { onTrimMemory(level); }
    final void performContentChanged() { onContentChanged(); }
    final void performWindowFocusChanged(boolean hasFocus) { onWindowFocusChanged(hasFocus); }
    final void performUserInteraction() { onUserInteraction(); }
    final void performUserLeaveHint() { onUserLeaveHint(); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        requireContainer().superOnCreate(savedInstanceState);
    }
    @Override protected void onStart() { requireContainer().superOnStart(); }
    @Override protected void onRestart() { requireContainer().superOnRestart(); }
    @Override protected void onPostCreate(Bundle savedInstanceState) {
        requireContainer().superOnPostCreate(savedInstanceState);
    }
    @Override protected void onResume() { requireContainer().superOnResume(); }
    @Override protected void onPostResume() { requireContainer().superOnPostResume(); }
    @Override protected void onPause() { requireContainer().superOnPause(); }
    @Override protected void onStop() { requireContainer().superOnStop(); }
    @Override protected void onDestroy() { requireContainer().superOnDestroy(); }
    @Override protected void onNewIntent(Intent intent) {
        requireContainer().superOnNewIntent(intent);
    }
    @Override protected void onSaveInstanceState(Bundle outState) {
        requireContainer().superOnSaveInstanceState(outState);
    }
    @Override protected void onRestoreInstanceState(Bundle savedInstanceState) {
        requireContainer().superOnRestoreInstanceState(savedInstanceState);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        requireContainer().superOnActivityResult(requestCode, resultCode, data);
    }
    @Override public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        requireContainer().superOnRequestPermissionsResult(
                requestCode, permissions, grantResults);
    }
    @Override public void onConfigurationChanged(Configuration newConfig) {
        requireContainer().superOnConfigurationChanged(newConfig);
    }
    @Override public void onLowMemory() { requireContainer().superOnLowMemory(); }
    @Override public void onTrimMemory(int level) {
        requireContainer().superOnTrimMemory(level);
    }
    @Override public void onContentChanged() { requireContainer().superOnContentChanged(); }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        requireContainer().superOnWindowFocusChanged(hasFocus);
    }
    @Override public void onUserInteraction() {
        requireContainer().superOnUserInteraction();
    }
    @Override protected void onUserLeaveHint() {
        requireContainer().superOnUserLeaveHint();
    }

    final PluginContainerActivity requireContainer() {
        if (container == null) {
            throw new IllegalStateException("Plugin Activity has not been attached to a container");
        }
        return container;
    }
}
