package dev.x2c.plugin.runtime;

import android.annotation.TargetApi;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.content.res.AssetFileDescriptor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Size;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Static bytecode-rewrite targets for ContentResolver URI virtualization. */
public final class PluginContentResolver {
    private PluginContentResolver() {}

    public static Cursor query(
            ContentResolver resolver,
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        return resolver.query(route(uri), projection, selection, selectionArgs, sortOrder);
    }

    public static Cursor query(
            ContentResolver resolver,
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder,
            CancellationSignal signal) {
        return resolver.query(
                route(uri), projection, selection, selectionArgs, sortOrder, signal);
    }

    @TargetApi(26)
    public static Cursor query(
            ContentResolver resolver,
            Uri uri,
            String[] projection,
            Bundle queryArgs,
            CancellationSignal signal) {
        requireApi(26, "ContentResolver.query(Bundle)");
        return resolver.query(route(uri), projection, queryArgs, signal);
    }

    public static String getType(ContentResolver resolver, Uri uri) {
        return resolver.getType(route(uri));
    }

    public static String[] getStreamTypes(
            ContentResolver resolver, Uri uri, String mimeTypeFilter) {
        return resolver.getStreamTypes(route(uri), mimeTypeFilter);
    }

    public static Uri insert(ContentResolver resolver, Uri uri, ContentValues values) {
        return restoreNullable(resolver.insert(route(uri), values));
    }

    @TargetApi(30)
    public static Uri insert(
            ContentResolver resolver, Uri uri, ContentValues values, Bundle extras) {
        requireApi(30, "ContentResolver.insert(Bundle)");
        return restoreNullable(resolver.insert(route(uri), values, extras));
    }

    public static int bulkInsert(ContentResolver resolver, Uri uri, ContentValues[] values) {
        return resolver.bulkInsert(route(uri), values);
    }

    public static int delete(
            ContentResolver resolver, Uri uri, String selection, String[] selectionArgs) {
        return resolver.delete(route(uri), selection, selectionArgs);
    }

    @TargetApi(30)
    public static int delete(ContentResolver resolver, Uri uri, Bundle extras) {
        requireApi(30, "ContentResolver.delete(Bundle)");
        return resolver.delete(route(uri), extras);
    }

    public static int update(
            ContentResolver resolver,
            Uri uri,
            ContentValues values,
            String selection,
            String[] selectionArgs) {
        return resolver.update(route(uri), values, selection, selectionArgs);
    }

    @TargetApi(30)
    public static int update(
            ContentResolver resolver, Uri uri, ContentValues values, Bundle extras) {
        requireApi(30, "ContentResolver.update(Bundle)");
        return resolver.update(route(uri), values, extras);
    }

    public static Bundle call(
            ContentResolver resolver,
            Uri uri,
            String method,
            String arg,
            Bundle extras) {
        Bundle routedExtras = extras == null ? new Bundle() : new Bundle(extras);
        routedExtras.putString(PluginProviderManager.CALL_URI_KEY, uri.toString());
        Bundle result = resolver.call(route(uri), method, arg, routedExtras);
        setResultClassLoader(result, uri.getAuthority());
        return result;
    }

    @TargetApi(29)
    public static Bundle call(
            ContentResolver resolver,
            String authority,
            String method,
            String arg,
            Bundle extras) {
        requireApi(29, "ContentResolver.call(authority)");
        if (PluginProviderManager.classLoaderForAuthority(authority) == null) {
            return resolver.call(authority, method, arg, extras);
        }
        Uri original = new Uri.Builder().scheme("content").authority(authority).build();
        Bundle routedExtras = extras == null ? new Bundle() : new Bundle(extras);
        routedExtras.putString(PluginProviderManager.CALL_URI_KEY, original.toString());
        Bundle result = resolver.call(
                PluginProviderManager.containerAuthority(), method, arg, routedExtras);
        setResultClassLoader(result, authority);
        return result;
    }

    public static ContentProviderResult[] applyBatch(
            ContentResolver resolver,
            String authority,
            ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException, RemoteException {
        ContentProviderResult[] result = PluginProviderManager.applyBatch(authority, operations);
        return result == null ? resolver.applyBatch(authority, operations) : result;
    }

    public static Uri canonicalize(ContentResolver resolver, Uri uri) {
        return restoreNullable(resolver.canonicalize(route(uri)));
    }

    public static Uri uncanonicalize(ContentResolver resolver, Uri uri) {
        return restoreNullable(resolver.uncanonicalize(route(uri)));
    }

    @TargetApi(26)
    public static boolean refresh(
            ContentResolver resolver, Uri uri, Bundle extras, CancellationSignal signal) {
        requireApi(26, "ContentResolver.refresh");
        return resolver.refresh(route(uri), extras, signal);
    }

    public static void notifyChange(
            ContentResolver resolver, Uri uri, ContentObserver observer) {
        resolver.notifyChange(route(uri), observer);
    }

    public static void notifyChange(
            ContentResolver resolver,
            Uri uri,
            ContentObserver observer,
            boolean syncToNetwork) {
        resolver.notifyChange(route(uri), observer, syncToNetwork);
    }

    @TargetApi(24)
    public static void notifyChange(
            ContentResolver resolver, Uri uri, ContentObserver observer, int flags) {
        requireApi(24, "ContentResolver.notifyChange(flags)");
        resolver.notifyChange(route(uri), observer, flags);
    }

    @TargetApi(30)
    public static void notifyChange(
            ContentResolver resolver,
            Collection<Uri> uris,
            ContentObserver observer,
            int flags) {
        requireApi(30, "ContentResolver.notifyChange(Collection)");
        List<Uri> routed = new ArrayList<Uri>(uris.size());
        for (Uri uri : uris) routed.add(route(uri));
        resolver.notifyChange(routed, observer, flags);
    }

    public static void registerContentObserver(
            ContentResolver resolver,
            Uri uri,
            boolean notifyForDescendants,
            ContentObserver observer) {
        resolver.registerContentObserver(route(uri), notifyForDescendants, observer);
    }

    public static InputStream openInputStream(ContentResolver resolver, Uri uri)
            throws FileNotFoundException {
        return resolver.openInputStream(route(uri));
    }

    public static OutputStream openOutputStream(ContentResolver resolver, Uri uri)
            throws FileNotFoundException {
        return resolver.openOutputStream(route(uri));
    }

    public static OutputStream openOutputStream(
            ContentResolver resolver, Uri uri, String mode) throws FileNotFoundException {
        return resolver.openOutputStream(route(uri), mode);
    }

    public static ParcelFileDescriptor openFileDescriptor(
            ContentResolver resolver, Uri uri, String mode) throws FileNotFoundException {
        return resolver.openFileDescriptor(route(uri), mode);
    }

    public static ParcelFileDescriptor openFileDescriptor(
            ContentResolver resolver,
            Uri uri,
            String mode,
            CancellationSignal signal) throws FileNotFoundException {
        return resolver.openFileDescriptor(route(uri), mode, signal);
    }

    public static AssetFileDescriptor openAssetFileDescriptor(
            ContentResolver resolver, Uri uri, String mode) throws FileNotFoundException {
        return resolver.openAssetFileDescriptor(route(uri), mode);
    }

    public static AssetFileDescriptor openAssetFileDescriptor(
            ContentResolver resolver,
            Uri uri,
            String mode,
            CancellationSignal signal) throws FileNotFoundException {
        return resolver.openAssetFileDescriptor(route(uri), mode, signal);
    }

    public static AssetFileDescriptor openTypedAssetFileDescriptor(
            ContentResolver resolver,
            Uri uri,
            String mimeType,
            Bundle options) throws FileNotFoundException {
        return resolver.openTypedAssetFileDescriptor(route(uri), mimeType, options);
    }

    public static AssetFileDescriptor openTypedAssetFileDescriptor(
            ContentResolver resolver,
            Uri uri,
            String mimeType,
            Bundle options,
            CancellationSignal signal) throws FileNotFoundException {
        return resolver.openTypedAssetFileDescriptor(
                route(uri), mimeType, options, signal);
    }

    @TargetApi(29)
    public static Bitmap loadThumbnail(
            ContentResolver resolver, Uri uri, Size size, CancellationSignal signal)
            throws IOException {
        requireApi(29, "ContentResolver.loadThumbnail");
        return resolver.loadThumbnail(route(uri), size, signal);
    }

    public static void takePersistableUriPermission(
            ContentResolver resolver, Uri uri, int modeFlags) {
        resolver.takePersistableUriPermission(route(uri), modeFlags);
    }

    public static void releasePersistableUriPermission(
            ContentResolver resolver, Uri uri, int modeFlags) {
        resolver.releasePersistableUriPermission(route(uri), modeFlags);
    }

    @SuppressWarnings("deprecation")
    public static void startSync(ContentResolver resolver, Uri uri, Bundle extras) {
        resolver.startSync(route(uri), extras);
    }

    @SuppressWarnings("deprecation")
    public static void cancelSync(ContentResolver resolver, Uri uri) {
        resolver.cancelSync(route(uri));
    }

    public static Uri route(Uri uri) {
        return PluginProviderManager.route(uri);
    }

    public static Uri restore(Uri uri) {
        return PluginProviderManager.restore(uri);
    }

    private static Uri restoreNullable(Uri uri) {
        return uri == null ? null : restore(uri);
    }

    private static void setResultClassLoader(Bundle result, String authority) {
        if (result == null) return;
        ClassLoader loader = PluginProviderManager.classLoaderForAuthority(authority);
        if (loader != null) result.setClassLoader(loader);
    }

    private static void requireApi(int api, String operation) {
        if (Build.VERSION.SDK_INT < api) {
            throw new UnsupportedOperationException(operation + " requires Android API " + api + '+');
        }
    }
}
