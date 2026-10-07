package com.cocode.vcode.ide.core.terminal;

import android.content.Context;

import com.cocode.vcode.ide.core.terminal.userland.UserlandBootstrapManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class UserlandBootstrapManagerTest {

    private Context context;
    private UserlandBootstrapManager manager;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        manager = UserlandBootstrapManager.getInstance();
        manager.uninstall(context);
    }

    @After
    public void tearDown() {
        manager.uninstall(context);
    }

    @Test
    public void testPrefixDirectoryHierarchyPaths() {
        File prefix = manager.getPrefixDir(context);
        assertEquals("usr", prefix.getName());
        assertEquals("bin", manager.getBinDir(context).getName());
        assertEquals("lib", manager.getLibDir(context).getName());
        assertEquals("etc", manager.getEtcDir(context).getName());
        assertEquals("tmp", manager.getTmpDir(context).getName());
        assertEquals("dpkg", manager.getDpkgDir(context).getName());
        assertEquals("bash", manager.getBashFile(context).getName());
        assertEquals("pkg", manager.getPkgFile(context).getName());
        assertEquals("home", manager.getHomeDir(context).getName());
        assertEquals("libproot.so", manager.getProotBinary(context).getName());
        assertEquals("libloader.so", manager.getProotLoader(context).getName());
    }

    @Test
    public void testArchitectureResolution() {
        String arch = manager.getNormalizedArch();
        assertNotNull(arch);
        assertTrue(arch.equals("aarch64") || arch.equals("arm") || arch.equals("x86_64") || arch.equals("i686"));

        String url = manager.resolveDefaultDownloadUrl();
        assertTrue(url.contains("cocodestudio/vcode-userland-bootstrap"));
        assertTrue(url.contains("bootstrap-" + arch + ".tar.gz"));

        String custom = manager.resolveDownloadUrl("https://mirror.example.com/v1.0.0/bootstrap-%s.tar.gz");
        assertEquals("https://mirror.example.com/v1.0.0/bootstrap-" + arch + ".tar.gz", custom);
    }

    @Test
    public void testInitializeMinimalEnvironmentAndLifecycle() throws IOException {
        assertFalse("Should not be installed initially", manager.isInstalled(context));

        manager.initializeMinimalEnvironment(context);

        assertTrue("Should be installed after initialization", manager.isInstalled(context));
        assertTrue(manager.getPrefixDir(context).exists());
        assertTrue(manager.getBinDir(context).exists());
        assertTrue(manager.getEtcDir(context).exists());
        assertTrue(manager.getTmpDir(context).exists());
        assertTrue(manager.getDpkgDir(context).exists());
        assertTrue(manager.getHomeDir(context).exists());

        File resolvConf = new File(manager.getEtcDir(context), "resolv.conf");
        assertTrue(resolvConf.exists());

        File bashrc = new File(manager.getEtcDir(context), "bash.bashrc");
        assertTrue(bashrc.exists());

        File pkg = manager.getPkgFile(context);
        assertTrue(pkg.exists());

        assertTrue(manager.getLibDir(context).exists());
        File status = new File(manager.getDpkgDir(context), "status");
        assertTrue(status.exists());

        // Test uninstall
        boolean deleted = manager.uninstall(context);
        assertTrue(deleted);
        assertFalse("Should not be installed after uninstall", manager.isInstalled(context));
        assertFalse(manager.getPrefixDir(context).exists());
        assertFalse(manager.getHomeDir(context).exists());
    }

    @Test
    public void testEnsureBaseEnvironmentProvisionsLibAndTallocLinks() throws IOException {
        File nativeLibDir = new File(context.getApplicationInfo().nativeLibraryDir);
        nativeLibDir.mkdirs();
        File mockTalloc = new File(nativeLibDir, "libtalloc.so");
        mockTalloc.createNewFile();

        manager.ensureBaseEnvironment(context);

        File libDir = manager.getLibDir(context);
        assertTrue(libDir.exists());
        File tallocSo = new File(libDir, "libtalloc.so");
        File tallocSo2 = new File(libDir, "libtalloc.so.2");
        assertTrue(tallocSo.exists());
        assertTrue(tallocSo2.exists());
    }

    @Test
    public void testDesktopTerminalPromptAndProfileConfiguration() throws IOException {
        manager.ensureBaseEnvironment(context);

        File etcDir = manager.getEtcDir(context);
        File vcodeProfile = new File(new File(etcDir, "profile.d"), "vcode.sh");
        assertTrue("vcode.sh must exist in etc/profile.d", vcodeProfile.exists());

        String content = com.cocode.vcode.ide.utils.FileUtils.readFile(vcodeProfile);
        assertTrue("vcode.sh must configure VCODE_PROJECT_NAME", content.contains("VCODE_PROJECT_NAME"));
        assertTrue("vcode.sh must configure HOME to /workspace", content.contains("HOME=\"${HOME:-/workspace}\""));
        assertTrue("vcode.sh must configure PS1 ending with dollar sign", content.contains("\\w\\[\\033[00m\\]$ '") || content.contains("\\w\\033[00m$ '"));

        File profile = new File(etcDir, "profile");
        assertTrue("etc/profile must exist", profile.exists());
        String profileContent = com.cocode.vcode.ide.utils.FileUtils.readFile(profile);
        assertTrue("etc/profile must hook vcode.sh or profile.d", profileContent.contains("vcode.sh") || profileContent.contains("profile.d"));
        assertFalse("etc/profile must not export raw unconfigured prompt", profileContent.contains("export PS1='\\h:\\w\\$ '"));
    }

    @Test
    public void testGitSafeDirectoryAndDiscoveryAcrossFilesystemProvisioning() throws IOException {
        manager.ensureBaseEnvironment(context);

        File etcDir = manager.getEtcDir(context);
        File gitConfig = new File(etcDir, "gitconfig");
        assertTrue("etc/gitconfig must be created", gitConfig.exists());
        String gitConfigContent = com.cocode.vcode.ide.utils.FileUtils.readFile(gitConfig);
        assertTrue("etc/gitconfig must configure safe.directory", gitConfigContent.contains("[safe]"));
        assertTrue("etc/gitconfig safe directory must match wildcard", gitConfigContent.contains("directory = *"));

        File homeGitConfig = new File(manager.getHomeDir(context), ".gitconfig");
        assertTrue("root home .gitconfig must be created", homeGitConfig.exists());
        String homeGitContent = com.cocode.vcode.ide.utils.FileUtils.readFile(homeGitConfig);
        assertTrue("root .gitconfig must configure safe.directory", homeGitContent.contains("[safe]"));
        assertTrue("root .gitconfig safe directory must match wildcard", homeGitContent.contains("directory = *"));

        File vcodeProfile = new File(new File(etcDir, "profile.d"), "vcode.sh");
        assertTrue(vcodeProfile.exists());
        String profileContent = com.cocode.vcode.ide.utils.FileUtils.readFile(vcodeProfile);
        assertTrue("vcode.sh must export GIT_DISCOVERY_ACROSS_FILESYSTEM=1", profileContent.contains("export GIT_DISCOVERY_ACROSS_FILESYSTEM=1"));
        assertTrue("vcode.sh must export GIT_CONFIG_PARAMETERS safe.directory=*", profileContent.contains("export GIT_CONFIG_PARAMETERS=\"'safe.directory=*'\""));
    }

    @Test
    public void testUninstallBootstrapAndCancelInstallation() throws IOException {
        manager.initializeMinimalEnvironment(context);
        assertTrue("Environment must be installed", manager.isInstalled(context));

        boolean uninstalled = manager.uninstallBootstrap(context);
        assertTrue("uninstallBootstrap must return true", uninstalled);
        assertFalse("Environment must not be installed after uninstall", manager.isInstalled(context));
        assertFalse("Prefix directory must be cleaned up", manager.getPrefixDir(context).exists());
        assertFalse("Home directory must be cleaned up", manager.getHomeDir(context).exists());

        // Cancel test on inactive token should not throw
        manager.cancelInstall();
    }
}
