package com.cocode.vcode.ide.core.terminal.userland;

import android.app.ActivityManager;
import android.content.Context;
import android.os.StatFs;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Locale;

/**
 * Pre-flight hardware capability diagnostic checker for the rootless desktop terminal environment.
 * Evaluates device RAM and storage headroom before allocating multi-megabyte Linux userlands or runtimes.
 */
public final class HardwareCapabilityChecker {

    public static final long LOW_RAM_THRESHOLD_MB = 2048; // <= 2GB RAM
    public static final long LOW_STORAGE_THRESHOLD_MB = 300; // < 300MB available storage

    public static final class DeviceDiagnostic {
        public final boolean isLowRam;
        public final long totalRamMb;
        public final long availableStorageMb;

        public DeviceDiagnostic(boolean isLowRam, long totalRamMb, long availableStorageMb) {
            this.isLowRam = isLowRam;
            this.totalRamMb = totalRamMb;
            this.availableStorageMb = availableStorageMb;
        }

        public boolean isStorageLow() {
            return availableStorageMb < LOW_STORAGE_THRESHOLD_MB;
        }

        public boolean isStorageSufficient(long requiredMb) {
            return availableStorageMb >= requiredMb;
        }

        @NonNull
        public String getFormattedRam() {
            return String.format(Locale.US, "%.1f GB", totalRamMb / 1024.0);
        }

        @NonNull
        public String getFormattedStorage() {
            if (availableStorageMb >= 1024) {
                return String.format(Locale.US, "%.1f GB", availableStorageMb / 1024.0);
            }
            return availableStorageMb + " MB";
        }
    }

    private HardwareCapabilityChecker() {
    }

    /**
     * Checks device RAM and available private storage using native Android APIs.
     *
     * @param context the Android context
     * @return the diagnostic assessment of device resources
     */
    @NonNull
    public static DeviceDiagnostic checkDevice(@NonNull Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(mi);
        }

        long totalRamMb = mi.totalMem > 0 ? mi.totalMem / (1024 * 1024) : LOW_RAM_THRESHOLD_MB;
        boolean isLowRam = totalRamMb <= LOW_RAM_THRESHOLD_MB;

        long availableStorageMb = 1024;
        try {
            File filesDir = context.getFilesDir();
            if (filesDir != null) {
                StatFs stat = new StatFs(filesDir.getPath());
                availableStorageMb = stat.getAvailableBytes() / (1024 * 1024);
            }
        } catch (Exception ignored) {
        }

        return new DeviceDiagnostic(isLowRam, totalRamMb, availableStorageMb);
    }

    /**
     * Evaluates diagnostic status from raw values (useful for testing and simulated environments).
     *
     * @param totalRamMb         total RAM in megabytes
     * @param availableStorageMb available storage in megabytes
     * @return device diagnostic assessment
     */
    @NonNull
    public static DeviceDiagnostic evaluate(long totalRamMb, long availableStorageMb) {
        boolean isLowRam = totalRamMb <= LOW_RAM_THRESHOLD_MB;
        return new DeviceDiagnostic(isLowRam, totalRamMb, availableStorageMb);
    }
}
