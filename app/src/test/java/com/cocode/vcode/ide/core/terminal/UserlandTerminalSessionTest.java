package com.cocode.vcode.ide.core.terminal;

import android.content.Context;

import com.cocode.vcode.ide.core.terminal.userland.UserlandBootstrapManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class UserlandTerminalSessionTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Context context;
    private File projectRoot;
    private UserlandBootstrapManager bootstrapManager;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        projectRoot = tempFolder.newFolder("MyWebProject");
        File vcodeDir = new File(projectRoot, ".vcode");
        vcodeDir.mkdir();

        bootstrapManager = UserlandBootstrapManager.getInstance();
        bootstrapManager.uninstall(context);
    }

    @After
    public void tearDown() {
        bootstrapManager.uninstall(context);
    }

    @Test
    public void testUserlandCommandTriggersAdvisoryPromptWhenNotInstalled() {
        TerminalSession session = new TerminalSession(context, projectRoot);
        session.start();

        // Type 'python' command
        session.handleInput("python\n");

        String bufferText = session.getBuffer().getAllText();
        assertTrue("Output should contain Desktop Terminal Environment banner",
                bufferText.contains("VCode Desktop Terminal Environment"));
        assertTrue("Output should contain Proceed prompt",
                bufferText.contains("Proceed with desktop environment installation? [Y/n]:"));

        // User enters 'n' to cancel
        session.handleInput("n\n");
        String afterCancel = session.getBuffer().getAllText();
        assertTrue("Should report installation cancelled",
                afterCancel.contains("cancelled"));

        session.destroy();
    }

    @Test
    public void testUserlandConfirmationTriggersInstallation() throws IOException {
        TerminalSession session = new TerminalSession(context, projectRoot);
        session.start();

        session.handleInput("pkg\n");
        assertTrue(session.getBuffer().getAllText().contains("Proceed with desktop environment installation?"));

        // User enters 'y' to proceed
        session.handleInput("y\n");
        assertTrue(session.getBuffer().getAllText().contains("Downloading and configuring rootless desktop environment"));

        session.destroy();
    }

    @Test
    public void testPkgCommandsWhenUserlandInstalled() throws IOException {
        bootstrapManager.initializeMinimalEnvironment(context);
        assertTrue(bootstrapManager.isInstalled(context));

        TerminalSession session = new TerminalSession(context, projectRoot);
        session.start();

        session.handleInput("pkg help\n");
        String output = session.getBuffer().getAllText();
        assertTrue("Should output pkg help", output.contains("VCode Desktop Package Manager (pkg)"));

        session.destroy();
    }

    @Test
    public void testSecurityPolicyEnforcedInUserlandSession() throws IOException {
        bootstrapManager.initializeMinimalEnvironment(context);

        TerminalSession session = new TerminalSession(context, projectRoot);
        session.start();

        session.handleInput("rm -rf .vcode\n");
        String output = session.getBuffer().getAllText();
        assertTrue("Security policy must block .vcode deletion",
                output.contains(".vcode is restricted"));

        session.destroy();
    }

    @Test
    public void testPkgInstallAndList() throws Exception {
        TerminalSession session = new TerminalSession(context, projectRoot);
        session.start();

        // Install a package via pkg
        session.handleInput("pkg install git\n");

        for (int i = 0; i < 50; i++) {
            if (session.getBuffer().getAllText().contains("Package 'git' installed successfully")) {
                break;
            }
            Thread.sleep(50);
        }
        assertTrue("Package installation must succeed",
                session.getBuffer().getAllText().contains("Package 'git' installed successfully"));

        // Check pkg list contains git
        session.handleInput("pkg list\n");
        assertTrue("pkg list should include git",
                session.getBuffer().getAllText().contains("git"));

        session.destroy();
    }

    @Test
    public void testVpmDeprecatedNotice() {
        TerminalSession session = new TerminalSession(context, projectRoot);
        session.start();

        session.handleInput("vpm install express\n");
        String output = session.getBuffer().getAllText();
        assertTrue("vpm should show deprecation message",
                output.contains("vpm is deprecated. Use 'npm' in the desktop terminal environment."));

        session.destroy();
    }

    @Test
    public void testNpmTriggersAdvisoryPromptWhenNotInstalled() {
        TerminalSession session = new TerminalSession(context, projectRoot);
        session.start();

        session.handleInput("npm\n");
        String output = session.getBuffer().getAllText();
        assertTrue("Output should contain Proceed prompt for npm",
                output.contains("Proceed with desktop environment installation? [Y/n]:"));

        session.destroy();
    }

    @Test
    public void testProotCommandStructureUsesLink2SymlinkAndValidRootfs() {
        File prootBin = new File("/system/lib64/libproot.so");
        File prefixDir = bootstrapManager.getPrefixDir(context);
        File homeDir = bootstrapManager.getHomeDir(context);

        java.util.List<String> cmd = TerminalSession.buildProotCommand(
                prootBin, prefixDir.getAbsolutePath(), projectRoot, homeDir);

        // Verify that --link2symlink is used instead of -k
        assertTrue("Command must include --link2symlink", cmd.contains("--link2symlink"));
        assertFalse("Command must NEVER include -k because PRoot treats -k as --kernel-release with argument",
                cmd.contains("-k"));

        // Verify -r is followed by prefixPath
        int rIdx = cmd.indexOf("-r");
        assertTrue("Command must have -r option", rIdx >= 0 && rIdx + 1 < cmd.size());
        assertEquals("Argument after -r must be prefixPath", prefixDir.getAbsolutePath(), cmd.get(rIdx + 1));

        // Verify workspace binding exists
        assertTrue("Command must bind project root to /workspace",
                cmd.contains(projectRoot.getAbsolutePath() + ":/workspace"));

        // Verify shell is the final executable invoked
        String lastCmd = cmd.get(cmd.size() - 2);
        assertTrue("Command must execute a shell (/bin/sh or /bin/bash)",
                lastCmd.equals("/bin/sh") || lastCmd.equals("/bin/bash"));
    }

    @Test
    public void testInteractiveInputEchoingDuringCommandExecution() {
        TerminalSession session = new TerminalSession(context, projectRoot);
        session.start();

        session.executeCommand("cat");

        session.handleInput("hello");
        String buf = session.getBuffer().getAllText();
        assertTrue("Input while executing should echo characters to terminal screen", buf.contains("hello"));

        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_BACKSPACE);
        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_ENTER);
        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_CTRL_C);
        session.destroy();
    }
}
