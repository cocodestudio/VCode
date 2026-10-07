package com.cocode.vcode.ide.core.terminal.userland;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPInputStream;

/**
 * Pure Java streaming POSIX/GNU TAR and GZIP extractor for the rootless userland environment.
 * Handles directory creation, symlinks, file modes, GNU LongLinks, and Zip Slip defenses.
 */
public final class UserlandTarExtractor {

    public interface ExtractionProgressListener {
        void onProgress(long bytesRead, long totalBytes, @Nullable String currentItem);
    }

    private static final int BLOCK_SIZE = 512;
    private static final int BUFFER_SIZE = 8192;

    private UserlandTarExtractor() {
    }

    /**
     * Downloads and unpacks a .tar.gz archive directly into the destination directory.
     *
     * @param archiveUrl     the HTTP/HTTPS URL of the userland bootstrap archive
     * @param destinationDir directory where files will be unpacked ($PREFIX)
     * @param listener       optional progress listener
     * @return the number of files extracted
     * @throws IOException on network or archive parsing error
     */
    public static int extractFromUrl(@NonNull String archiveUrl,
                                     @NonNull File destinationDir,
                                     @Nullable ExtractionProgressListener listener) throws IOException {
        return extractFromUrl(archiveUrl, destinationDir, null, listener);
    }

    /**
     * Downloads and unpacks a .tar.gz archive directly with optional SHA-256 integrity validation.
     *
     * @param archiveUrl     the HTTP/HTTPS URL of the userland bootstrap archive
     * @param destinationDir directory where files will be unpacked ($PREFIX)
     * @param expectedSha256 optional expected SHA-256 checksum (64-character hex string)
     * @param listener       optional progress listener
     * @return the number of files extracted
     * @throws IOException on network, checksum mismatch, or archive parsing error
     */
    public static int extractFromUrl(@NonNull String archiveUrl,
                                     @NonNull File destinationDir,
                                     @Nullable String expectedSha256,
                                     @Nullable ExtractionProgressListener listener) throws IOException {
        return extractFromUrl(archiveUrl, destinationDir, expectedSha256, listener, 0);
    }

    private static int extractFromUrl(@NonNull String archiveUrl,
                                      @NonNull File destinationDir,
                                      @Nullable String expectedSha256,
                                      @Nullable ExtractionProgressListener listener,
                                      int redirectCount) throws IOException {
        if (redirectCount > 5) {
            throw new IOException("Too many redirects fetching userland archive: " + archiveUrl);
        }
        if (!destinationDir.exists()) {
            destinationDir.mkdirs();
        }

        URL url = new URL(archiveUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "VCode-Desktop-Terminal-Userland/1.0");

        int responseCode = conn.getResponseCode();
        boolean isRedirect = (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                           || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                           || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                           || responseCode == 307
                           || responseCode == 308);

        if (isRedirect) {
            String newUrl = conn.getHeaderField("Location");
            conn.disconnect();
            if (newUrl != null && !newUrl.trim().isEmpty()) {
                URL targetUrl = new URL(url, newUrl);
                return extractFromUrl(targetUrl.toString(), destinationDir, expectedSha256, listener, redirectCount + 1);
            }
        }

        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            conn.disconnect();
            throw new FileNotFoundException("Userland bootstrap archive not found (HTTP 404): " + archiveUrl);
        } else if (responseCode != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            throw new IOException("HTTP error fetching userland archive (" + responseCode + "): " + archiveUrl);
        }

        long totalBytes = conn.getContentLengthLong();
        MessageDigest sha256Digest = null;
        if (expectedSha256 != null && !expectedSha256.trim().isEmpty()) {
            try {
                sha256Digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException ignored) {
            }
        }

        try (InputStream rawIn = conn.getInputStream();
             ProgressInputStream progressIn = new ProgressInputStream(rawIn, totalBytes, sha256Digest, listener);
             BufferedInputStream bufferedIn = new BufferedInputStream(progressIn, BUFFER_SIZE);
             GZIPInputStream gzipIn = new GZIPInputStream(bufferedIn)) {
            int extracted = extractTarStream(gzipIn, destinationDir, listener, progressIn);
            if (expectedSha256 != null && sha256Digest != null) {
                String actualHex = progressIn.getHexDigest();
                if (actualHex != null && !expectedSha256.trim().equalsIgnoreCase(actualHex)) {
                    throw new IOException("SHA-256 checksum mismatch for " + archiveUrl +
                            "\nExpected: " + expectedSha256.trim() + "\nActual:   " + actualHex);
                }
            }
            return extracted;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Extracts a .tar.gz stream into the destination directory.
     */
    public static int extractTgzStream(@NonNull InputStream in,
                                       @NonNull File destinationDir,
                                       @Nullable ExtractionProgressListener listener) throws IOException {
        try (GZIPInputStream gzipIn = new GZIPInputStream(new BufferedInputStream(in, BUFFER_SIZE))) {
            return extractTarStream(gzipIn, destinationDir, listener, null);
        }
    }

    /**
     * Extracts a raw TAR stream into the destination directory.
     */
    public static int extractTarStream(@NonNull InputStream in,
                                       @NonNull File destinationDir,
                                       @Nullable ExtractionProgressListener listener) throws IOException {
        return extractTarStream(in, destinationDir, listener, null);
    }

    private static int extractTarStream(@NonNull InputStream in,
                                        @NonNull File destinationDir,
                                        @Nullable ExtractionProgressListener listener,
                                        @Nullable ProgressInputStream progressStream) throws IOException {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs();
        }

        String canonicalDest = destinationDir.getCanonicalPath();
        byte[] headerBlock = new byte[BLOCK_SIZE];
        String nextLongName = null;
        String nextLongLinkTarget = null;
        int extractedCount = 0;
        long totalExtractedBytes = 0;
        int consecutiveZeroBlocks = 0;

        while (true) {
            int read = readFully(in, headerBlock, 0, BLOCK_SIZE);
            if (read < BLOCK_SIZE) {
                break;
            }

            if (isAllZeros(headerBlock)) {
                consecutiveZeroBlocks++;
                if (consecutiveZeroBlocks >= 2) {
                    break;
                }
                continue;
            }
            consecutiveZeroBlocks = 0;

            String entryName;
            if (nextLongName != null) {
                entryName = nextLongName;
                nextLongName = null;
            } else {
                entryName = parseTarString(headerBlock, 0, 100);
                String prefix = parseTarString(headerBlock, 345, 155);
                if (!prefix.isEmpty()) {
                    entryName = prefix + "/" + entryName;
                }
            }

            long entryMode = parseOctal(headerBlock, 100, 8);
            long entrySize = parseOctal(headerBlock, 124, 12);
            byte typeFlag = headerBlock[156];

            String linkTarget;
            if (nextLongLinkTarget != null) {
                linkTarget = nextLongLinkTarget;
                nextLongLinkTarget = null;
            } else {
                linkTarget = parseTarString(headerBlock, 157, 100);
            }

            // GNU LongLink entry type ('L') for filename
            if (typeFlag == 'L') {
                byte[] longNameBytes = readEntryData(in, entrySize);
                nextLongName = parseNullTerminatedString(longNameBytes);
                continue;
            }

            // GNU LongLink entry type ('K') for symlink target
            if (typeFlag == 'K') {
                byte[] longTargetBytes = readEntryData(in, entrySize);
                nextLongLinkTarget = parseNullTerminatedString(longTargetBytes);
                continue;
            }

            // PAX extended headers ('x' or 'g')
            if (typeFlag == 'x' || typeFlag == 'g') {
                skipEntryData(in, entrySize);
                continue;
            }

            // Normalize path
            String normalizedPath = normalizeEntryPath(entryName);
            if (normalizedPath.isEmpty()) {
                skipEntryData(in, entrySize);
                continue;
            }

            // Guard against negative sizes or excessively large entries (tar bomb protection)
            if (entrySize < 0 || entrySize > 500L * 1024 * 1024) {
                throw new IOException("Invalid entry size in userland archive: " + entrySize);
            }
            totalExtractedBytes += entrySize;
            if (totalExtractedBytes > 2L * 1024 * 1024 * 1024) {
                throw new IOException("Userland archive exceeds maximum allowable uncompressed size (tar bomb protection)");
            }

            File targetFile = new File(destinationDir, normalizedPath);

            // If targetFile is a non-directory entry and already exists on disk (or is an old symlink),
            // unlink it BEFORE canonical path checks and writing. This prevents:
            // 1. getCanonicalPath() resolving pre-existing symlinks (e.g. etc/mtab -> /proc/mounts)
            //    outside destinationDir and triggering false-positive path traversal rejections.
            // 2. FileOutputStream or Os.symlink attempting to write through existing symlinks
            //    into read-only system partitions (EROFS) or failing with EEXIST.
            if (typeFlag != '5' && !entryName.endsWith("/")) {
                deleteOrUnlink(targetFile);
            }

            String canonicalTarget = targetFile.getCanonicalPath();

            // Zip Slip path traversal defense
            if (!canonicalTarget.startsWith(canonicalDest + File.separator) && !canonicalTarget.equals(canonicalDest)) {
                throw new IOException("Illegal path traversal entry in userland archive: " + entryName);
            }

            if (listener != null) {
                long currentBytes = progressStream != null ? progressStream.getBytesRead() : 0;
                long totalBytes = progressStream != null ? progressStream.getTotalBytes() : 0;
                listener.onProgress(currentBytes, totalBytes, targetFile.getName());
            }

            // Directory entry ('5')
            if (typeFlag == '5' || entryName.endsWith("/")) {
                targetFile.mkdirs();
                skipEntryData(in, entrySize);
                continue;
            }

            // Ensure parent directories exist
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Symbolic link entry ('2')
            if (typeFlag == '2') {
                if (!linkTarget.isEmpty()) {
                    createSymlink(linkTarget, targetFile);
                }
                skipEntryData(in, entrySize);
                extractedCount++;
                continue;
            }

            // Hard link entry ('1')
            if (typeFlag == '1') {
                if (!linkTarget.isEmpty()) {
                    File linkTargetFile = new File(destinationDir, normalizeEntryPath(linkTarget));
                    if (linkTargetFile.exists()) {
                        copyFile(linkTargetFile, targetFile);
                    }
                }
                skipEntryData(in, entrySize);
                extractedCount++;
                continue;
            }

            // Regular file entry ('0' or '\0')
            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                copyStreamBounded(in, fos, entrySize);
            }

            // Set executable bit if mode & 0111 != 0 or if located in bin/
            boolean isExecutable = (entryMode & 0111) != 0 || isInBinDirectory(targetFile);
            if (isExecutable) {
                setExecutable(targetFile);
            }

            // Consume block padding
            long remainder = entrySize % BLOCK_SIZE;
            if (remainder > 0) {
                long padding = BLOCK_SIZE - remainder;
                skipFully(in, padding);
            }

            extractedCount++;
        }

        return extractedCount;
    }

    private static String normalizeEntryPath(@NonNull String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/") || normalized.startsWith("./")) {
            if (normalized.startsWith("./")) {
                normalized = normalized.substring(2);
            } else {
                normalized = normalized.substring(1);
            }
        }
        if (normalized.equals(".") || normalized.isEmpty()) {
            return "";
        }
        // If the archive bundles a top-level usr/ prefix, strip it so extraction maps directly to destination
        if (normalized.startsWith("usr/")) {
            normalized = normalized.substring("usr/".length());
        }
        while (normalized.startsWith("/") || normalized.startsWith("./")) {
            if (normalized.startsWith("./")) {
                normalized = normalized.substring(2);
            } else {
                normalized = normalized.substring(1);
            }
        }
        if (normalized.equals(".")) {
            return "";
        }
        return normalized;
    }

    private static boolean isInBinDirectory(@NonNull File file) {
        String path = file.getAbsolutePath().replace('\\', '/');
        return path.contains("/bin/") || path.endsWith("/bin");
    }

    public static void deleteOrUnlink(@NonNull File file) {
        try {
            android.system.Os.remove(file.getAbsolutePath());
            return;
        } catch (Throwable ignored) {
        }
        try {
            java.nio.file.Files.deleteIfExists(file.toPath());
            return;
        } catch (Throwable ignored) {
        }
        file.delete();
    }

    public static boolean createSymlink(@NonNull String target, @NonNull File linkFile) {
        deleteOrUnlink(linkFile);
        try {
            android.system.Os.symlink(target, linkFile.getAbsolutePath());
            return true;
        } catch (Throwable t) {
            // JVM host / test environment fallback
            try {
                java.nio.file.Path link = linkFile.toPath();
                java.nio.file.Path targetPath = java.nio.file.Paths.get(target);
                java.nio.file.Files.createSymbolicLink(link, targetPath);
                return true;
            } catch (Throwable ignored) {
            }
            // Secondary fallback: write target path content for environments without symlink privilege
            try {
                byte[] bytes = target.getBytes(StandardCharsets.UTF_8);
                try (FileOutputStream fos = new FileOutputStream(linkFile)) {
                    fos.write(bytes);
                }
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static void setExecutable(@NonNull File file) {
        try {
            android.system.Os.chmod(file.getAbsolutePath(), 0755);
        } catch (Throwable t) {
            try {
                file.setExecutable(true, false);
                file.setReadable(true, false);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void copyFile(@NonNull File source, @NonNull File target) throws IOException {
        try (InputStream in = new java.io.FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private static boolean isAllZeros(byte[] block) {
        for (byte b : block) {
            if (b != 0) return false;
        }
        return true;
    }

    private static String parseTarString(byte[] header, int offset, int length) {
        int end = offset;
        int max = offset + length;
        while (end < max && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8).trim();
    }

    private static String parseNullTerminatedString(byte[] bytes) {
        int end = 0;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8).trim();
    }

    private static long parseOctal(byte[] header, int offset, int length) {
        long result = 0;
        int end = offset + length;
        int start = offset;

        while (start < end && (header[start] == ' ' || header[start] == 0)) {
            start++;
        }

        for (int i = start; i < end; i++) {
            byte b = header[i];
            if (b >= '0' && b <= '7') {
                result = (result << 3) + (b - '0');
            } else {
                break;
            }
        }
        return result;
    }

    private static byte[] readEntryData(InputStream in, long size) throws IOException {
        if (size > 64 * 1024) {
            size = 64 * 1024;
        }
        byte[] data = new byte[(int) size];
        readFully(in, data, 0, data.length);
        long remainder = size % BLOCK_SIZE;
        if (remainder > 0) {
            skipFully(in, BLOCK_SIZE - remainder);
        }
        return data;
    }

    private static void skipEntryData(InputStream in, long size) throws IOException {
        long totalToSkip = size;
        long remainder = size % BLOCK_SIZE;
        if (remainder > 0) {
            totalToSkip += (BLOCK_SIZE - remainder);
        }
        skipFully(in, totalToSkip);
    }

    private static void copyStreamBounded(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        long remaining = limit;
        while (remaining > 0) {
            int toRead = (int) Math.min(buf.length, remaining);
            int read = in.read(buf, 0, toRead);
            if (read == -1) {
                throw new IOException("Unexpected end of archive stream (needed " + remaining + " more bytes)");
            }
            out.write(buf, 0, read);
            remaining -= read;
        }
    }

    private static int readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int read = in.read(b, off + total, len - total);
            if (read == -1) break;
            total += read;
        }
        return total;
    }

    private static void skipFully(InputStream in, long bytes) throws IOException {
        long remaining = bytes;
        byte[] skipBuffer = new byte[BUFFER_SIZE];
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                int read = in.read(skipBuffer, 0, (int) Math.min(skipBuffer.length, remaining));
                if (read == -1) break;
                remaining -= read;
            } else {
                remaining -= skipped;
            }
        }
    }

    private static final class ProgressInputStream extends FilterInputStream {
        private final long totalBytes;
        private final ExtractionProgressListener listener;
        private final MessageDigest digest;
        private long bytesRead = 0;
        private long lastReported = 0;

        ProgressInputStream(InputStream in, long totalBytes, @Nullable MessageDigest digest, @Nullable ExtractionProgressListener listener) {
            super(in);
            this.totalBytes = totalBytes;
            this.digest = digest;
            this.listener = listener;
        }

        long getBytesRead() {
            return bytesRead;
        }

        long getTotalBytes() {
            return totalBytes;
        }

        @Nullable
        String getHexDigest() {
            if (digest == null) return null;
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                bytesRead++;
                if (digest != null) {
                    digest.update((byte) b);
                }
                reportProgress(false);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = super.read(b, off, len);
            if (read > 0) {
                bytesRead += read;
                if (digest != null) {
                    digest.update(b, off, read);
                }
                reportProgress(false);
            }
            return read;
        }

        private void reportProgress(boolean force) {
            if (listener == null) return;
            if (force || (bytesRead - lastReported) >= 65536 || (totalBytes > 0 && bytesRead == totalBytes)) {
                lastReported = bytesRead;
                listener.onProgress(bytesRead, totalBytes, null);
            }
        }
    }
}
