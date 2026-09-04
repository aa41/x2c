package dev.x2c.plugin.compiler;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Rewrites supported ContentResolver calls to URI-virtualizing runtime helpers. */
final class ContentResolverCallRewriter {
    private static final String RESOLVER = "android/content/ContentResolver";
    private static final String CLIENT = "android/content/ContentProviderClient";
    private static final String HELPER = "dev/x2c/plugin/runtime/PluginContentResolver";
    private static final Set<String> SUPPORTED = supported();

    private ContentResolverCallRewriter() {}

    static boolean rewrite(
            MethodVisitor output,
            int opcode,
            String owner,
            String name,
            String descriptor) {
        if (CLIENT.equals(owner)) {
            throw new IllegalStateException(
                    "ContentProviderClient is not safely virtualized in class-only plugins: "
                            + name + descriptor + ". Use ContentResolver directly.");
        }
        if (!RESOLVER.equals(owner)) return false;
        String signature = name + descriptor;
        if (isProviderClientAcquisition(name)) {
            throw new IllegalStateException(
                    "ContentProviderClient acquisition is not supported in class-only plugins: "
                            + signature + ". Use ContentResolver directly.");
        }
        if (!SUPPORTED.contains(signature)) {
            if (descriptor.contains("Landroid/net/Uri;")) {
                throw new IllegalStateException(
                        "Unsupported ContentResolver URI API in class-only plugin bytecode: "
                                + signature + ". Add an explicit virtualization bridge first.");
            }
            return false;
        }
        if (opcode == Opcodes.INVOKESTATIC) {
            throw new IllegalStateException("Unexpected static ContentResolver call: " + signature);
        }
        String helperDescriptor = "(Landroid/content/ContentResolver;" + descriptor.substring(1);
        output.visitMethodInsn(
                Opcodes.INVOKESTATIC, HELPER, name, helperDescriptor, false);
        return true;
    }

    private static boolean isProviderClientAcquisition(String name) {
        return "acquireContentProviderClient".equals(name)
                || "acquireUnstableContentProviderClient".equals(name);
    }

    private static Set<String> supported() {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                "query(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;",
                "query(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;Landroid/os/CancellationSignal;)Landroid/database/Cursor;",
                "query(Landroid/net/Uri;[Ljava/lang/String;Landroid/os/Bundle;Landroid/os/CancellationSignal;)Landroid/database/Cursor;",
                "getType(Landroid/net/Uri;)Ljava/lang/String;",
                "getStreamTypes(Landroid/net/Uri;Ljava/lang/String;)[Ljava/lang/String;",
                "insert(Landroid/net/Uri;Landroid/content/ContentValues;)Landroid/net/Uri;",
                "insert(Landroid/net/Uri;Landroid/content/ContentValues;Landroid/os/Bundle;)Landroid/net/Uri;",
                "bulkInsert(Landroid/net/Uri;[Landroid/content/ContentValues;)I",
                "delete(Landroid/net/Uri;Ljava/lang/String;[Ljava/lang/String;)I",
                "delete(Landroid/net/Uri;Landroid/os/Bundle;)I",
                "update(Landroid/net/Uri;Landroid/content/ContentValues;Ljava/lang/String;[Ljava/lang/String;)I",
                "update(Landroid/net/Uri;Landroid/content/ContentValues;Landroid/os/Bundle;)I",
                "call(Landroid/net/Uri;Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;)Landroid/os/Bundle;",
                "call(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;)Landroid/os/Bundle;",
                "applyBatch(Ljava/lang/String;Ljava/util/ArrayList;)[Landroid/content/ContentProviderResult;",
                "canonicalize(Landroid/net/Uri;)Landroid/net/Uri;",
                "uncanonicalize(Landroid/net/Uri;)Landroid/net/Uri;",
                "refresh(Landroid/net/Uri;Landroid/os/Bundle;Landroid/os/CancellationSignal;)Z",
                "notifyChange(Landroid/net/Uri;Landroid/database/ContentObserver;)V",
                "notifyChange(Landroid/net/Uri;Landroid/database/ContentObserver;Z)V",
                "notifyChange(Landroid/net/Uri;Landroid/database/ContentObserver;I)V",
                "notifyChange(Ljava/util/Collection;Landroid/database/ContentObserver;I)V",
                "registerContentObserver(Landroid/net/Uri;ZLandroid/database/ContentObserver;)V",
                "openInputStream(Landroid/net/Uri;)Ljava/io/InputStream;",
                "openOutputStream(Landroid/net/Uri;)Ljava/io/OutputStream;",
                "openOutputStream(Landroid/net/Uri;Ljava/lang/String;)Ljava/io/OutputStream;",
                "openFileDescriptor(Landroid/net/Uri;Ljava/lang/String;)Landroid/os/ParcelFileDescriptor;",
                "openFileDescriptor(Landroid/net/Uri;Ljava/lang/String;Landroid/os/CancellationSignal;)Landroid/os/ParcelFileDescriptor;",
                "openAssetFileDescriptor(Landroid/net/Uri;Ljava/lang/String;)Landroid/content/res/AssetFileDescriptor;",
                "openAssetFileDescriptor(Landroid/net/Uri;Ljava/lang/String;Landroid/os/CancellationSignal;)Landroid/content/res/AssetFileDescriptor;",
                "openTypedAssetFileDescriptor(Landroid/net/Uri;Ljava/lang/String;Landroid/os/Bundle;)Landroid/content/res/AssetFileDescriptor;",
                "openTypedAssetFileDescriptor(Landroid/net/Uri;Ljava/lang/String;Landroid/os/Bundle;Landroid/os/CancellationSignal;)Landroid/content/res/AssetFileDescriptor;",
                "loadThumbnail(Landroid/net/Uri;Landroid/util/Size;Landroid/os/CancellationSignal;)Landroid/graphics/Bitmap;",
                "takePersistableUriPermission(Landroid/net/Uri;I)V",
                "releasePersistableUriPermission(Landroid/net/Uri;I)V",
                "startSync(Landroid/net/Uri;Landroid/os/Bundle;)V",
                "cancelSync(Landroid/net/Uri;)V"
        )));
    }
}
