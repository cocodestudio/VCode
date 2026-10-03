package com.cocode.vcode.ide.data.npm.extractor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * Zero-Node native tarball stream extractor.
 *
 * <p>Streams and decompresses npm {@code .tgz} packages directly from HTTP URLs or streams
 * via {@link GZIPInputStream} and a lightweight, zero-dependency POSIX/GNU TAR parser.
 */
public final class NpmTarballExtractor {

    public interface ExtractionProgressListener {
        void onProgress(long bytesRead, long totalBytes);
    }

    private static final int BLOCK_SIZE = 512;
    private static final int BUFFER_SIZE = 8192;

    private NpmTarballExtractor() {
    }

    /**
     * Downloads and unpacks a .tgz archive directly to the destination directory.
     *
     * @param tarballUrl     the HTTP/HTTPS URL of the npm package tarball
     * @param destinationDir directory where files will be unpacked (e.g. node_modules/package_name/)
     * @param listener       optional progress listener
     * @return the number of files extracted
     * @throws IOException on network or archive parsing error
     */
    public static int extractFromUrl(@NonNull String tarballUrl,
                                     @NonNull File destinationDir,
                                     @Nullable ExtractionProgressListener listener) throws IOException {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs();
        }

        URL url = new URL(tarballUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "VCode-IDE-Mobile/1.0");

        int responseCode = conn.getResponseCode();
        // Handle redirect if not automatically followed
        if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            String newUrl = conn.getHeaderField("Location");
            conn.disconnect();
            if (newUrl != null) {
                return extractFromUrl(newUrl, destinationDir, listener);
            }
        }

        if (responseCode != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            throw new IOException("HTTP error fetching package tarball: " + responseCode);
        }

        long totalBytes = conn.getContentLengthLong();
        try (InputStream rawIn = conn.getInputStream();
             ProgressInputStream progressIn = new ProgressInputStream(rawIn, totalBytes, listener);
             BufferedInputStream bufferedIn = new BufferedInputStream(progressIn, BUFFER_SIZE);
             GZIPInputStream gzipIn = new GZIPInputStream(bufferedIn)) {
            return extractTarStream(gzipIn, destinationDir);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Extracts a .tgz stream (gzip-compressed TAR) directly into the destination directory.
     *
     * @param in             stream delivering gzip-compressed TAR bytes
     * @param destinationDir root folder for extracted files
     * @return count of extracted files
     * @throws IOException on stream or format error
     */
    public static int extractTgzStream(@NonNull InputStream in, @NonNull File destinationDir) throws IOException {
        try (GZIPInputStream gzipIn = new GZIPInputStream(new BufferedInputStream(in, BUFFER_SIZE))) {
            return extractTarStream(gzipIn, destinationDir);
        }
    }

    /**
     * Extracts an uncompressed or already-decompressed TAR stream into the destination directory.
     *
     * @param in             stream delivering raw TAR byte blocks
     * @param destinationDir root folder for extracted files
     * @return count of extracted files
     * @throws IOException on stream or format error
     */
    public static int extractTarStream(@NonNull InputStream in, @NonNull File destinationDir) throws IOException {
        String canonicalDest = destinationDir.getCanonicalPath();
        byte[] headerBlock = new byte[BLOCK_SIZE];
        String nextLongName = null;
        int extractedCount = 0;
        int consecutiveZeroBlocks = 0;

        while (true) {
            int read = readFully(in, headerBlock, 0, BLOCK_SIZE);
            if (read < BLOCK_SIZE) {
                break; // End of stream
            }

            if (isAllZeros(headerBlock)) {
                consecutiveZeroBlocks++;
                if (consecutiveZeroBlocks >= 2) {
                    break; // Standard TAR end-of-archive marker
                }
                continue;
            }
            consecutiveZeroBlocks = 0;

            // Parse entry name
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

            long entrySize = parseOctal(headerBlock, 124, 12);
            byte typeFlag = headerBlock[156];

            // GNU LongLink entry type ('L')
            if (typeFlag == 'L') {
                byte[] longNameBytes = readEntryData(in, entrySize);
                nextLongName = parseNullTerminatedString(longNameBytes);
                continue;
            }

            // PAX extended header ('x' or 'g') - skip payload
            if (typeFlag == 'x' || typeFlag == 'g') {
                skipEntryData(in, entrySize);
                continue;
            }

            // Strip leading "package/" or "package\" directory prefix standard in npm tarballs
            String relPath = stripLeadingPackagePrefix(entryName);
            if (relPath.isEmpty()) {
                skipEntryData(in, entrySize);
                continue;
            }

            File targetFile = new File(destinationDir, relPath);
            String canonicalTarget = targetFile.getCanonicalPath();
            // Path traversal protection (Zip Slip)
            if (!canonicalTarget.startsWith(canonicalDest + File.separator) && !canonicalTarget.equals(canonicalDest)) {
                throw new IOException("Illegal directory traversal entry: " + entryName);
            }

            // Directory entry ('5')
            if (typeFlag == '5' || entryName.endsWith("/")) {
                targetFile.mkdirs();
                skipEntryData(in, entrySize);
                continue;
            }

            // Normal file entry ('0' or '\0')
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                copyStreamBounded(in, fos, entrySize);
            }

            // Consume tar 512-byte block padding
            long remainder = entrySize % BLOCK_SIZE;
            if (remainder > 0) {
                long padding = BLOCK_SIZE - remainder;
                skipFully(in, padding);
            }

            extractedCount++;
        }

        return extractedCount;
    }

    private static String stripLeadingPackagePrefix(@NonNull String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("package/")) {
            normalized = normalized.substring("package/".length());
        } else if (normalized.equals("package")) {
            return "";
        }
        return normalized;
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

        // Skip leading whitespace or zeros
        while (start < end && (header[start] == ' ' || header[start] == 0)) {
            start++;
        }

        for (int i = start; i < end; i++) {
            byte b = header[i];
            if (b >= '0' && b <= '7') {
                result = (result << 3) + (b - '0');
            } else {
                break; // NUL, space, or non-octal char terminates
            }
        }
        return result;
    }

    private static byte[] readEntryData(InputStream in, long size) throws IOException {
        if (size > 64 * 1024) {
            throw new IOException("TAR entry name too long: " + size);
        }
        byte[] data = new byte[(int) size];
        readFully(in, data, 0, (int) size);
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

    private static void copyStreamBounded(InputStream in, FileOutputStream out, long length) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = length;
        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            int read = in.read(buffer, 0, toRead);
            if (read < 0) {
                throw new IOException("Unexpected end of archive stream reading file data");
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static int readFully(InputStream in, byte[] buffer, int offset, int length) throws IOException {
        int totalRead = 0;
        while (totalRead < length) {
            int read = in.read(buffer, offset + totalRead, length - totalRead);
            if (read < 0) break;
            totalRead += read;
        }
        return totalRead;
    }

    private static void skipFully(InputStream in, long bytes) throws IOException {
        long skipped = 0;
        while (skipped < bytes) {
            long s = in.skip(bytes - skipped);
            if (s <= 0) {
                // Read dummy byte if skip returns 0
                if (in.read() < 0) break;
                skipped++;
            } else {
                skipped += s;
            }
        }
    }

    private static class ProgressInputStream extends InputStream {
        private final InputStream delegate;
        private final long totalBytes;
        private final ExtractionProgressListener listener;
        private long bytesRead = 0;

        ProgressInputStream(InputStream delegate, long totalBytes, ExtractionProgressListener listener) {
            this.delegate = delegate;
            this.totalBytes = totalBytes;
            this.listener = listener;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                bytesRead++;
                notifyProgress();
            }
            return b;
        }

        @Override
        public int read(@NonNull byte[] b, int off, int len) throws IOException {
            int count = delegate.read(b, off, len);
            if (count > 0) {
                bytesRead += count;
                notifyProgress();
            }
            return count;
        }

        private void notifyProgress() {
            if (listener != null) {
                listener.onProgress(bytesRead, totalBytes);
            }
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
