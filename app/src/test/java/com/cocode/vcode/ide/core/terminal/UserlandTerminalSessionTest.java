package com.cocode.vcode.ide.core.terminal;

import android.content.Context;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;

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
    public void testUserlandCommandTriggersRedirectionWhenNotInstalled() {
        TerminalSession session = new TerminalSession(context, projectRoot);
        session.start();

        // Type 'python' command
        session.handleInput("python\n");

        String bufferText = session.getBuffer().getAllText();
        assertTrue("Output should indicate Linux Environment is not installed",
                bufferText.contains("Linux Environment is not installed."));
        assertTrue("Output should redirect to Settings > Editor > Linux Environment",
                bufferText.contains("Settings > Editor > Linux Environment"));

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
    public void testNpmTriggersSettingsRedirectionWhenNotInstalled() {
        TerminalSession session = new TerminalSession(context, projectRoot);
        session.start();

        session.handleInput("npm\n");
        String output = session.getBuffer().getAllText();
        assertTrue("Output should indicate Linux Environment is not installed for npm",
                output.contains("Linux Environment is not installed."));
        assertTrue("Output should redirect to Settings > Editor > Linux Environment",
                output.contains("Settings > Editor > Linux Environment"));

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

    @Test
    public void testTerminalSessionMultiListenerDispatch() {
        TerminalSession session = new TerminalSession(context, projectRoot);
        final boolean[] listener1Notified = new boolean[]{false};
        final boolean[] listener2Notified = new boolean[]{false};

        TerminalSession.TerminalSessionListener l1 = new TerminalSession.TerminalSessionListener() {
            @Override public void onOutput() { listener1Notified[0] = true; }
            @Override public void onProcessTerminated(int exitCode) {}
        };
        TerminalSession.TerminalSessionListener l2 = new TerminalSession.TerminalSessionListener() {
            @Override public void onOutput() { listener2Notified[0] = true; }
            @Override public void onProcessTerminated(int exitCode) {}
        };

        session.addListener(l1);
        session.addListener(l2);

        session.clearScreen();
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertTrue("Listener 1 should receive onOutput", listener1Notified[0]);
        assertTrue("Listener 2 should receive onOutput", listener2Notified[0]);

        // Test remove listener
        listener1Notified[0] = false;
        listener2Notified[0] = false;
        session.removeListener(l1);

        session.clearScreen();
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertFalse("Listener 1 was removed and should not receive onOutput", listener1Notified[0]);
        assertTrue("Listener 2 should still receive onOutput", listener2Notified[0]);
    }

    @Test
    public void testTerminalSessionLegacyListenerNonClobber() {
        TerminalSession session = new TerminalSession(context, projectRoot);
        final boolean[] viewerListenerNotified = new boolean[]{false};
        final boolean[] legacyListenerNotified = new boolean[]{false};

        // Viewer registers via addListener
        session.addListener(new TerminalSession.TerminalSessionListener() {
            @Override public void onOutput() { viewerListenerNotified[0] = true; }
            @Override public void onProcessTerminated(int exitCode) {}
        });

        // Another component calls legacy setListener
        session.setListener(new TerminalSession.TerminalSessionListener() {
            @Override public void onOutput() { legacyListenerNotified[0] = true; }
            @Override public void onProcessTerminated(int exitCode) {}
        });

        session.clearScreen();
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertTrue("Viewer listener must NOT be clobbered by setListener", viewerListenerNotified[0]);
        assertTrue("Legacy listener should also be notified", legacyListenerNotified[0]);
    }

    @Test
    public void testTerminalSessionInputCursorPositionAndTracking() {
        TerminalSession session = new TerminalSession(context, projectRoot);
        assertEquals("", session.getCurrentInput());
        assertEquals(0, session.getInputCursorPos());

        // Type characters
        session.handleInput("ls -la");
        assertEquals("ls -la", session.getCurrentInput());
        assertEquals(6, session.getInputCursorPos());

        // Move left
        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_ARROW_LEFT);
        assertEquals("ls -la", session.getCurrentInput());
        assertEquals(5, session.getInputCursorPos());

        // Home key moves cursor to start
        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_HOME);
        assertEquals(0, session.getInputCursorPos());

        // Left arrow at beginning cannot underflow below 0
        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_ARROW_LEFT);
        assertEquals(0, session.getInputCursorPos());

        // Insert at beginning
        session.handleInput("sudo ");
        assertEquals("sudo ls -la", session.getCurrentInput());
        assertEquals(5, session.getInputCursorPos());

        // End key moves cursor to length
        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_END);
        assertEquals(11, session.getInputCursorPos());

        // Right arrow at end cannot exceed length
        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_ARROW_RIGHT);
        assertEquals(11, session.getInputCursorPos());

        // Backspace deletes char before cursor
        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_BACKSPACE);
        assertEquals("sudo ls -l", session.getCurrentInput());
        assertEquals(10, session.getInputCursorPos());

        // Move left twice
        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_ARROW_LEFT);
        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_ARROW_LEFT);
        assertEquals(8, session.getInputCursorPos());

        // Delete key deletes char at cursor ('-')
        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_DELETE);
        assertEquals("sudo ls l", session.getCurrentInput());
        assertEquals(8, session.getInputCursorPos());

        // Enter key resets input tracking
        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_ENTER);
        assertEquals("", session.getCurrentInput());
        assertEquals(0, session.getInputCursorPos());
    }

    @Test
    public void testTerminalSessionCtrlKeysResetInputTracking() {
        TerminalSession session = new TerminalSession(context, projectRoot);
        session.handleInput("npm run dev");
        assertEquals("npm run dev", session.getCurrentInput());
        assertEquals(11, session.getInputCursorPos());

        // Ctrl+C resets input tracking
        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_CTRL_C);
        assertEquals("", session.getCurrentInput());
        assertEquals(0, session.getInputCursorPos());

        session.handleInput("long running script");
        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_CTRL_Z);
        assertEquals("", session.getCurrentInput());
        assertEquals(0, session.getInputCursorPos());

        session.handleInput("clear attempt");
        session.sendSpecialKey(TerminalSession.SPECIAL_KEY_CTRL_L);
        assertEquals("", session.getCurrentInput());
        assertEquals(0, session.getInputCursorPos());
    }

    @Test
    public void testTerminalViewInputConnectionAttrsForIme() {
        TerminalView terminalView = new TerminalView(context);
        EditorInfo outAttrs = new EditorInfo();
        terminalView.onCreateInputConnection(outAttrs);

        assertEquals("TerminalView must include TYPE_CLASS_TEXT",
                InputType.TYPE_CLASS_TEXT, outAttrs.inputType & InputType.TYPE_MASK_CLASS);
        assertTrue("Password variation must be present for direct streaming",
                (outAttrs.inputType & InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) != 0);
        assertTrue("No-suggestions flag must be present",
                (outAttrs.inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0);
        assertEquals("IME flag should be no fullscreen",
                EditorInfo.IME_FLAG_NO_FULLSCREEN, outAttrs.imeOptions);
    }
}
