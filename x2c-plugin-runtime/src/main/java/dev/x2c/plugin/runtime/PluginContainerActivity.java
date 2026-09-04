package dev.x2c.plugin.runtime;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

/** Real framework Activity that owns the Window and forwards lifecycle to one plugin delegate. */
@SuppressLint("MissingSuperCall") // The plugin root calls the matching superOn* method exactly once.
public abstract class PluginContainerActivity extends Activity {
    private PluginActivity pluginActivity;

    @Override protected void onCreate(Bundle savedInstanceState) {
        pluginActivity = PluginActivityManager.createDelegate(this, getIntent());
        pluginActivity.performCreate(savedInstanceState);
    }

    @Override protected void onStart() {
        requirePlugin().performStart();
    }

    @Override protected void onRestart() {
        requirePlugin().performRestart();
    }

    @Override protected void onPostCreate(Bundle savedInstanceState) {
        requirePlugin().performPostCreate(savedInstanceState);
    }

    @Override protected void onResume() {
        requirePlugin().performResume();
    }

    @Override protected void onPostResume() {
        requirePlugin().performPostResume();
    }

    @Override protected void onPause() {
        requirePlugin().performPause();
    }

    @Override protected void onStop() {
        requirePlugin().performStop();
    }

    @Override protected void onDestroy() {
        if (pluginActivity != null) {
            pluginActivity.performDestroy();
        } else {
            super.onDestroy();
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        setIntent(intent);
        PluginActivity current = requirePlugin();
        current.performNewIntent(PluginActivityManager.requirePluginIntent(intent));
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        requirePlugin().performSaveInstanceState(outState);
    }

    @Override protected void onRestoreInstanceState(Bundle savedInstanceState) {
        requirePlugin().performRestoreInstanceState(savedInstanceState);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        requirePlugin().performActivityResult(requestCode, resultCode, data);
    }

    @Override public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        requirePlugin().performRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        requirePlugin().performConfigurationChanged(newConfig);
    }

    @Override public void onLowMemory() {
        requirePlugin().performLowMemory();
    }

    @Override public void onTrimMemory(int level) {
        requirePlugin().performTrimMemory(level);
    }

    @Override public void onContentChanged() {
        if (pluginActivity == null) {
            super.onContentChanged();
        } else {
            pluginActivity.performContentChanged();
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        if (pluginActivity == null) {
            super.onWindowFocusChanged(hasFocus);
        } else {
            pluginActivity.performWindowFocusChanged(hasFocus);
        }
    }

    @Override public void onUserInteraction() {
        if (pluginActivity == null) {
            super.onUserInteraction();
        } else {
            pluginActivity.performUserInteraction();
        }
    }

    @Override protected void onUserLeaveHint() {
        if (pluginActivity == null) {
            super.onUserLeaveHint();
        } else {
            pluginActivity.performUserLeaveHint();
        }
    }

    @Override public void onBackPressed() {
        requirePlugin().onBackPressed();
    }

    final void performSystemBack() {
        super.onBackPressed();
    }

    final void superOnCreate(Bundle state) { super.onCreate(state); }
    final void superOnStart() { super.onStart(); }
    final void superOnRestart() { super.onRestart(); }
    final void superOnPostCreate(Bundle state) { super.onPostCreate(state); }
    final void superOnResume() { super.onResume(); }
    final void superOnPostResume() { super.onPostResume(); }
    final void superOnPause() { super.onPause(); }
    final void superOnStop() { super.onStop(); }
    final void superOnDestroy() { super.onDestroy(); }
    final void superOnNewIntent(Intent intent) { super.onNewIntent(intent); }
    final void superOnSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); }
    final void superOnRestoreInstanceState(Bundle state) { super.onRestoreInstanceState(state); }
    final void superOnActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
    @TargetApi(23)
    final void superOnRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
    final void superOnConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
    }
    final void superOnLowMemory() { super.onLowMemory(); }
    final void superOnTrimMemory(int level) { super.onTrimMemory(level); }
    final void superOnContentChanged() { super.onContentChanged(); }
    final void superOnWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
    }
    final void superOnUserInteraction() { super.onUserInteraction(); }
    final void superOnUserLeaveHint() { super.onUserLeaveHint(); }

    private PluginActivity requirePlugin() {
        if (pluginActivity == null) {
            throw new IllegalStateException("Plugin delegate has not been created");
        }
        return pluginActivity;
    }
}
