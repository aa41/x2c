package dev.x2c.fixture.secondary;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import dev.x2c.plugin.api.X2cPluginProvider;
import dev.x2c.plugin.base.BasePluginProvider;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** In-process virtual ContentProvider example with CRUD, call, observer and file APIs. */
@X2cPluginProvider(authority = ComponentProbeProvider.AUTHORITY)
public final class ComponentProbeProvider extends BasePluginProvider {
    public static final String AUTHORITY = "dev.x2c.fixture.component.provider";
    private String value = "provider-initial";
    private int revision;

    @Override public boolean onCreate() {
        // Native Providers may be created before Application.onCreate; plugin Providers are
        // installed explicitly after host initialization.
        if (dev.x2c.runtime.X2C.isInitialized()) {
            ComponentState.verifyHostApplication(getContext(), ComponentProbeProvider.class);
        }
        ComponentState.provider = "Provider: onCreate";
        return true;
    }

    @Override public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd.x2c.probe";
    }

    @Override public Cursor query(
            Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[] {"value", "revision"});
        cursor.addRow(new Object[] {value, Integer.valueOf(revision)});
        return cursor;
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        this.value = text(values, "value", "inserted");
        revision++;
        changed(uri, "insert");
        return uri.buildUpon().appendPath(Integer.toString(revision)).build();
    }

    @Override public int update(
            Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        this.value = text(values, "value", this.value);
        revision++;
        changed(uri, "update");
        return 1;
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        value = "deleted";
        revision++;
        changed(uri, "delete");
        return 1;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        result.putString("method", method);
        result.putString("value", value);
        result.putInt("revision", revision);
        return result;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Provider demo is read-only");
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            byte[] bytes = ("x2c-provider-file:" + value).getBytes(StandardCharsets.UTF_8);
            new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream output =
                             new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    output.write(bytes);
                } catch (IOException ignored) {
                    // The reader may close early; the descriptor still remains correctly owned.
                }
            }, "x2c-provider-writer").start();
            return pipe[0];
        } catch (IOException error) {
            FileNotFoundException failure = new FileNotFoundException(error.getMessage());
            failure.initCause(error);
            throw failure;
        }
    }

    private void changed(Uri uri, String operation) {
        ComponentState.provider = "Provider: " + operation + " · revision=" + revision;
        if (getContext() != null) getContext().getContentResolver().notifyChange(uri, null);
    }

    private static String text(ContentValues values, String key, String fallback) {
        String result = values == null ? null : values.getAsString(key);
        return result == null ? fallback : result;
    }
}
