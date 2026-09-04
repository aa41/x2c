package dev.x2c.plugin.runtime;

import android.annotation.TargetApi;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.FileNotFoundException;
import java.util.ArrayList;

/** One non-exported host Provider that forwards routed URIs to installed plugin Providers. */
public final class PluginContainerProvider extends ContentProvider {
    @Override public boolean onCreate() {
        return true;
    }

    @Override public Cursor query(
            Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        return provider(uri).query(
                original(uri), projection, selection, selectionArgs, sortOrder);
    }

    @Override public Cursor query(
            Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal) {
        return provider(uri).query(
                original(uri), projection, selection, selectionArgs, sortOrder,
                cancellationSignal);
    }

    @TargetApi(26)
    @Override public Cursor query(
            Uri uri, String[] projection, Bundle queryArgs,
            CancellationSignal cancellationSignal) {
        return provider(uri).query(original(uri), projection, queryArgs, cancellationSignal);
    }

    @Override public String getType(Uri uri) {
        return provider(uri).getType(original(uri));
    }

    @Override public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        return provider(uri).getStreamTypes(original(uri), mimeTypeFilter);
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        return routeResult(provider(uri).insert(original(uri), values));
    }

    @TargetApi(30)
    @Override public Uri insert(Uri uri, ContentValues values, Bundle extras) {
        return routeResult(provider(uri).insert(original(uri), values, extras));
    }

    @Override public int bulkInsert(Uri uri, ContentValues[] values) {
        return provider(uri).bulkInsert(original(uri), values);
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        return provider(uri).delete(original(uri), selection, selectionArgs);
    }

    @TargetApi(30)
    @Override public int delete(Uri uri, Bundle extras) {
        return provider(uri).delete(original(uri), extras);
    }

    @Override public int update(
            Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return provider(uri).update(original(uri), values, selection, selectionArgs);
    }

    @TargetApi(30)
    @Override public int update(Uri uri, ContentValues values, Bundle extras) {
        return provider(uri).update(original(uri), values, extras);
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        return provider(uri).openFile(original(uri), mode);
    }

    @Override public ParcelFileDescriptor openFile(
            Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
        return provider(uri).openFile(original(uri), mode, signal);
    }

    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode)
            throws FileNotFoundException {
        return provider(uri).openAssetFile(original(uri), mode);
    }

    @Override public AssetFileDescriptor openAssetFile(
            Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
        return provider(uri).openAssetFile(original(uri), mode, signal);
    }

    @Override public AssetFileDescriptor openTypedAssetFile(
            Uri uri, String mimeTypeFilter, Bundle options) throws FileNotFoundException {
        return provider(uri).openTypedAssetFile(original(uri), mimeTypeFilter, options);
    }

    @Override public AssetFileDescriptor openTypedAssetFile(
            Uri uri, String mimeTypeFilter, Bundle options, CancellationSignal signal)
            throws FileNotFoundException {
        return provider(uri).openTypedAssetFile(
                original(uri), mimeTypeFilter, options, signal);
    }

    @Override public Uri canonicalize(Uri uri) {
        return routeResult(provider(uri).canonicalize(original(uri)));
    }

    @Override public Uri uncanonicalize(Uri uri) {
        return routeResult(provider(uri).uncanonicalize(original(uri)));
    }

    @TargetApi(26)
    @Override public boolean refresh(
            Uri uri, Bundle extras, CancellationSignal cancellationSignal) {
        return provider(uri).refresh(original(uri), extras, cancellationSignal);
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        return callInternal(method, arg, extras);
    }

    @Override public Bundle call(
            String authority, String method, String arg, Bundle extras) {
        return callInternal(method, arg, extras);
    }

    private Bundle callInternal(String method, String arg, Bundle extras) {
        Bundle pluginExtras = extras == null ? new Bundle() : new Bundle(extras);
        String originalUri = pluginExtras.getString(PluginProviderManager.CALL_URI_KEY);
        pluginExtras.remove(PluginProviderManager.CALL_URI_KEY);
        if (originalUri == null) {
            throw new SecurityException("Plugin Provider call is missing its routed authority");
        }
        Uri uri = Uri.parse(originalUri);
        return provider(PluginProviderManager.route(uri)).call(method, arg, pluginExtras);
    }

    @Override public ContentProviderResult[] applyBatch(
            ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        throw new OperationApplicationException(
                "Use transformed ContentResolver.applyBatch for plugin Providers");
    }

    @Override public ContentProviderResult[] applyBatch(
            String authority, ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        return applyBatch(operations);
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        PluginProviderManager.onConfigurationChanged(newConfig);
    }

    @Override public void onLowMemory() {
        PluginProviderManager.onLowMemory();
    }

    @Override public void onTrimMemory(int level) {
        PluginProviderManager.onTrimMemory(level);
    }

    private static ContentProvider provider(Uri routed) {
        return PluginProviderManager.provider(routed);
    }

    private static Uri original(Uri routed) {
        return PluginProviderManager.restore(routed);
    }

    private static Uri routeResult(Uri original) {
        return original == null ? null : PluginProviderManager.route(original);
    }
}
