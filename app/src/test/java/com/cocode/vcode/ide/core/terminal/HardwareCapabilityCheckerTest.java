package com.cocode.vcode.ide.core.terminal;

import com.cocode.vcode.ide.core.terminal.userland.HardwareCapabilityChecker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for HardwareCapabilityChecker device evaluation, RAM/storage formatting,
 * and low-spec threshold detection.
 */
public class HardwareCapabilityCheckerTest {

    @Test
    public void testLowRamDeviceDetected() {
        // Devices with <= 2048 MB RAM must be flagged as isLowRam = true
        HardwareCapabilityChecker.DeviceDiagnostic diag512 =
                HardwareCapabilityChecker.evaluate(512, 1024);
        assertTrue(diag512.isLowRam);

        HardwareCapabilityChecker.DeviceDiagnostic diag1024 =
                HardwareCapabilityChecker.evaluate(1024, 2048);
        assertTrue(diag1024.isLowRam);

        HardwareCapabilityChecker.DeviceDiagnostic diag1800 =
                HardwareCapabilityChecker.evaluate(1800, 1500);
        assertTrue(diag1800.isLowRam);

        HardwareCapabilityChecker.DeviceDiagnostic diag2048 =
                HardwareCapabilityChecker.evaluate(2048, 4096);
        assertTrue(diag2048.isLowRam);
    }

    @Test
    public void testAdequateRamDevice() {
        // Devices with > 2048 MB RAM must have isLowRam = false
        HardwareCapabilityChecker.DeviceDiagnostic diag3072 =
                HardwareCapabilityChecker.evaluate(3072, 8192);
        assertFalse(diag3072.isLowRam);

        HardwareCapabilityChecker.DeviceDiagnostic diag4096 =
                HardwareCapabilityChecker.evaluate(4096, 16384);
        assertFalse(diag4096.isLowRam);

        HardwareCapabilityChecker.DeviceDiagnostic diag8192 =
                HardwareCapabilityChecker.evaluate(8192, 32768);
        assertFalse(diag8192.isLowRam);
    }

    @Test
    public void testStorageSufficiency() {
        HardwareCapabilityChecker.DeviceDiagnostic diag =
                HardwareCapabilityChecker.evaluate(4096, 500);

        assertTrue(diag.isStorageSufficient(150));
        assertTrue(diag.isStorageSufficient(300));
        assertTrue(diag.isStorageSufficient(500));
        assertFalse(diag.isStorageSufficient(600));
        assertFalse(diag.isStorageLow()); // 500MB > 300MB

        HardwareCapabilityChecker.DeviceDiagnostic lowStorage =
                HardwareCapabilityChecker.evaluate(4096, 200);
        assertTrue(lowStorage.isStorageLow()); // 200MB < 300MB
    }

    @Test
    public void testFormattedOutputs() {
        HardwareCapabilityChecker.DeviceDiagnostic diag =
                HardwareCapabilityChecker.evaluate(1843, 2048);

        assertEquals("1.8 GB", diag.getFormattedRam());
        assertEquals("2.0 GB", diag.getFormattedStorage());

        HardwareCapabilityChecker.DeviceDiagnostic smallStorage =
                HardwareCapabilityChecker.evaluate(3072, 250);
        assertEquals("250 MB", smallStorage.getFormattedStorage());
    }
}
