package dev.x2c.fixture.consumer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import dev.x2c.runtime.ImageAsset;
import dev.x2c.runtime.ImageLoadListener;
import dev.x2c.runtime.ImageLoader;
import dev.x2c.runtime.ImageRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** Small dependency-free host image loader used to verify real HTTPS, digest and cancellation. */
final class VerifiedHttpImageLoader implements ImageLoader {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_DIMENSION = 8_192;
    private static final long MAX_PIXELS = 32_000_000L;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Map<String, Bitmap> MEMORY_CACHE = new ConcurrentHashMap<>();

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public ImageRequest load(ImageView target, ImageAsset asset) {
        return load(target, asset, new ImageLoadListener() {});
    }

    @Override
    public ImageRequest load(
            ImageView target, ImageAsset asset, ImageLoadListener listener) {
        Request request = new Request(target, asset, listener);
        target.setImageDrawable(new ColorDrawable(0xFFE9ECF5));
        listener.onStart(asset);

        Bitmap cached = MEMORY_CACHE.get(asset.sha256);
        if (cached != null && !cached.isRecycled()) {
            request.postSuccess(cached);
            return request;
        }
        request.setFuture(EXECUTOR.submit(() -> download(request)));
        return request;
    }

    private void download(Request request) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(request.asset.url).openConnection();
            request.connection = connection;
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", request.asset.mime);
            connection.setRequestProperty("User-Agent", "X2C-Image-Fixture/1.0");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " for " + request.asset.url);
            }
            if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
                throw new IOException("Refusing non-HTTPS redirect: " + connection.getURL());
            }
            String responseMime = connection.getContentType();
            if (responseMime != null) {
                int separator = responseMime.indexOf(';');
                responseMime = (separator < 0 ? responseMime : responseMime.substring(0, separator))
                        .trim().toLowerCase(Locale.ROOT);
                if (!request.asset.mime.equals(responseMime)) {
                    throw new IOException("MIME mismatch: expected " + request.asset.mime
                            + ", received " + responseMime);
                }
            }
            byte[] encoded;
            try (InputStream input = connection.getInputStream()) {
                encoded = readExactly(input, request.asset.bytes);
            }
            String digest = sha256(encoded);
            if (!request.asset.sha256.equalsIgnoreCase(digest)) {
                throw new IOException("SHA-256 mismatch: expected " + request.asset.sha256
                        + ", received " + digest);
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(encoded, 0, encoded.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                    || bounds.outWidth > MAX_DIMENSION || bounds.outHeight > MAX_DIMENSION
                    || (long) bounds.outWidth * bounds.outHeight > MAX_PIXELS) {
                throw new IOException("Unsafe image dimensions: " + bounds.outWidth + "x" + bounds.outHeight);
            }
            Bitmap bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.length);
            if (bitmap == null) {
                throw new IOException("Android could not decode verified " + request.asset.mime);
            }
            MEMORY_CACHE.put(request.asset.sha256, bitmap);
            request.postSuccess(bitmap);
        } catch (Throwable error) {
            request.postFailure(error);
        } finally {
            request.connection = null;
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] readExactly(InputStream input, long expectedBytes) throws IOException {
        if (expectedBytes < 0 || expectedBytes > Integer.MAX_VALUE - 1L) {
            throw new IOException("Unsupported image size: " + expectedBytes);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) expectedBytes);
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > expectedBytes) {
                throw new IOException("Image exceeds locked byte count " + expectedBytes);
            }
            output.write(buffer, 0, read);
        }
        if (total != expectedBytes) {
            throw new IOException("Byte count mismatch: expected " + expectedBytes + ", received " + total);
        }
        return output.toByteArray();
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            char[] alphabet = "0123456789abcdef".toCharArray();
            char[] output = new char[digest.length * 2];
            for (int index = 0; index < digest.length; index++) {
                int value = digest[index] & 0xff;
                output[index * 2] = alphabet[value >>> 4];
                output[index * 2 + 1] = alphabet[value & 0x0f];
            }
            return new String(output);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private final class Request implements ImageRequest {
        final ImageView target;
        final ImageAsset asset;
        final ImageLoadListener listener;
        final AtomicBoolean terminal = new AtomicBoolean();
        volatile HttpURLConnection connection;
        volatile Future<?> future;

        Request(ImageView target, ImageAsset asset, ImageLoadListener listener) {
            this.target = target;
            this.asset = asset;
            this.listener = listener;
        }

        void setFuture(Future<?> value) {
            future = value;
            if (terminal.get()) {
                value.cancel(true);
            }
        }

        void postSuccess(Bitmap bitmap) {
            main.post(() -> {
                if (!terminal.compareAndSet(false, true)) return;
                target.setImageDrawable(new BitmapDrawable(target.getResources(), bitmap));
                listener.onSuccess(asset);
            });
        }

        void postFailure(Throwable error) {
            main.post(() -> {
                if (!terminal.compareAndSet(false, true)) return;
                target.setImageDrawable(new ColorDrawable(0xFFFFE4E1));
                listener.onFailure(asset, error);
            });
        }

        @Override
        public void cancel() {
            if (!terminal.compareAndSet(false, true)) return;
            HttpURLConnection activeConnection = connection;
            if (activeConnection != null) activeConnection.disconnect();
            Future<?> activeFuture = future;
            if (activeFuture != null) activeFuture.cancel(true);
            if (Looper.myLooper() == Looper.getMainLooper()) {
                listener.onCancelled(asset);
            } else {
                main.post(() -> listener.onCancelled(asset));
            }
        }
    }
}
