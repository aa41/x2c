package dev.x2c.compiler.resource;

import dev.x2c.compiler.ResourceCompilationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** File, hash, bitmap-signature and compiler-failure helpers. */
final class FileSupport {
    private FileSupport() {}

    static void validateBitmapSignature(File file, String extension) throws IOException {
        byte[] header;
        try (InputStream input = Files.newInputStream(file.toPath())) {
            header = readAtMost(input, 64);
        }
        boolean valid;
        if ("png".equals(extension)) {
            valid = startsWith(header, new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        } else if ("jpg".equals(extension) || "jpeg".equals(extension)) {
            valid = startsWith(header, new int[] {0xFF, 0xD8, 0xFF});
        } else if ("gif".equals(extension)) {
            valid = startsWithAscii(header, "GIF87a") || startsWithAscii(header, "GIF89a");
        } else if ("webp".equals(extension)) {
            valid = header.length >= 12 && asciiAt(header, 0, "RIFF") && asciiAt(header, 8, "WEBP");
        } else if ("avif".equals(extension)) {
            valid = header.length >= 12 && asciiAt(header, 4, "ftyp")
                    && (containsAscii(header, "avif") || containsAscii(header, "avis"));
        } else {
            valid = false;
        }
        if (!valid) {
            throw fail(file, "Bitmap content does not match ." + extension + " format signature");
        }
    }

    static boolean startsWith(byte[] value, int[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((value[index] & 0xff) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    static boolean startsWithAscii(byte[] value, String prefix) {
        return asciiAt(value, 0, prefix);
    }

    static boolean asciiAt(byte[] value, int offset, String expected) {
        if (offset < 0 || value.length < offset + expected.length()) {
            return false;
        }
        for (int index = 0; index < expected.length(); index++) {
            if ((value[offset + index] & 0xff) != expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    static boolean containsAscii(byte[] value, String expected) {
        for (int offset = 0; offset <= value.length - expected.length(); offset++) {
            if (asciiAt(value, offset, expected)) {
                return true;
            }
        }
        return false;
    }

    static String extension(String name) {
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) {
                result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static void recreateDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> paths = Files.walk(directory)) {
                List<Path> sorted = paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
                for (Path path : sorted) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(directory);
    }

    static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static byte[] readAtMost(InputStream input, int limit) throws IOException {
        byte[] buffer = new byte[limit];
        int offset = 0;
        while (offset < limit) {
            int count = input.read(buffer, offset, limit - offset);
            if (count < 0) {
                break;
            }
            offset += count;
        }
        if (offset == limit) {
            return buffer;
        }
        return java.util.Arrays.copyOf(buffer, offset);
    }

    static ResourceCompilationException fail(File file, String message) {
        return new ResourceCompilationException((file == null ? "<generated>" : file.getAbsolutePath()) + ": " + message);
    }

    static ResourceCompilationException fail(File file, String message, Throwable cause) {
        return new ResourceCompilationException((file == null ? "<generated>" : file.getAbsolutePath()) + ": " + message, cause);
    }
}
