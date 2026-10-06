package com.cocode.vcode.ide.core.terminal;

import android.content.Context;

import com.cocode.vcode.ide.core.terminal.userland.UserlandBootstrapManager;
import com.cocode.vcode.ide.core.terminal.userland.UserlandPackageManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class UserlandPackageManagerTest {

    private Context context;
    private UserlandPackageManager pkgManager;
    private UserlandBootstrapManager bootstrapManager;

    @Before
    public void setUp() throws IOException {
        context = RuntimeEnvironment.getApplication();
        pkgManager = UserlandPackageManager.getInstance();
        bootstrapManager = UserlandBootstrapManager.getInstance();
        bootstrapManager.uninstall(context);
        bootstrapManager.initializeMinimalEnvironment(context);
    }

    @After
    public void tearDown() {
        bootstrapManager.uninstall(context);
    }

    @Test
    public void testSearchPackages() {
        List<UserlandPackageManager.PackageInfo> all = pkgManager.searchPackages(context, "");
        assertFalse(all.isEmpty());

        List<UserlandPackageManager.PackageInfo> pythonMatches = pkgManager.searchPackages(context, "python");
        assertFalse(pythonMatches.isEmpty());
        assertEquals("python", pythonMatches.get(0).name);

        List<UserlandPackageManager.PackageInfo> gitMatches = pkgManager.searchPackages(context, "git");
        assertFalse(gitMatches.isEmpty());
        assertEquals("git", gitMatches.get(0).name);

        List<UserlandPackageManager.PackageInfo> nodeMatches = pkgManager.searchPackages(context, "node");
        assertFalse(nodeMatches.isEmpty());

        List<UserlandPackageManager.PackageInfo> none = pkgManager.searchPackages(context, "non_existent_package_xyz");
        assertTrue(none.isEmpty());
    }

    @Test
    public void testInstallAndUninstallPackage() throws InterruptedException {
        CountDownLatch installLatch = new CountDownLatch(1);
        AtomicBoolean installSuccess = new AtomicBoolean(false);

        pkgManager.installPackage(context, "python", new UserlandPackageManager.PackageOperationCallback() {
            @Override
            public void onSuccess(String message) {
                installSuccess.set(true);
                installLatch.countDown();
            }

            @Override
            public void onError(String error) {
                installLatch.countDown();
            }
        });

        assertTrue("Install timed out", installLatch.await(5, TimeUnit.SECONDS));
        assertTrue("Install should succeed", installSuccess.get());

        List<String> installed = pkgManager.listInstalledPackages(context);
        assertTrue(installed.contains("python"));

        File pythonBin = new File(bootstrapManager.getBinDir(context), "python");
        assertTrue(pythonBin.exists());

        // Uninstall
        CountDownLatch uninstallLatch = new CountDownLatch(1);
        AtomicBoolean uninstallSuccess = new AtomicBoolean(false);

        pkgManager.uninstallPackage(context, "python", new UserlandPackageManager.PackageOperationCallback() {
            @Override
            public void onSuccess(String message) {
                uninstallSuccess.set(true);
                uninstallLatch.countDown();
            }

            @Override
            public void onError(String error) {
                uninstallLatch.countDown();
            }
        });

        assertTrue("Uninstall timed out", uninstallLatch.await(5, TimeUnit.SECONDS));
        assertTrue("Uninstall should succeed", uninstallSuccess.get());

        List<String> afterUninstall = pkgManager.listInstalledPackages(context);
        assertFalse(afterUninstall.contains("python"));
        assertFalse(pythonBin.exists());
    }
}
