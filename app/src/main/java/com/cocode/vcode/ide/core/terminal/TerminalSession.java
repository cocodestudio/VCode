package com.cocode.vcode.ide.core.terminal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.core.terminal.pty.PtyBridge;
import com.cocode.vcode.ide.core.terminal.userland.HardwareCapabilityChecker;
import com.cocode.vcode.ide.core.terminal.userland.UserlandBootstrapManager;
import com.cocode.vcode.ide.core.terminal.userland.UserlandTarExtractor;
import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.UiUtils;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages the authentic desktop-grade pseudo-terminal (PTY) session, stream lifecycle,
 * process signals, window resizing (SIGWINCH), and rootless Linux container environment.
 */
public class TerminalSession {

    public interface TerminalSessionListener {
        void onOutput();
        void onProcessTerminated(int exitCode);
        default void onCommandExecuted(String command) {}
        default void onCommandFinished(String command, int exitCode) {}
    }

    public static final int SPECIAL_KEY_ESC = 1;
    public static final int SPECIAL_KEY_TAB = 2;
    public static final int SPECIAL_KEY_CTRL_C = 3;
    public static final int SPECIAL_KEY_CTRL_D = 4;
    public static final int SPECIAL_KEY_CTRL_Z = 5;
    public static final int SPECIAL_KEY_CTRL_L = 6;
    public static final int SPECIAL_KEY_ARROW_UP = 7;
    public static final int SPECIAL_KEY_ARROW_DOWN = 8;
    public static final int SPECIAL_KEY_ARROW_LEFT = 9;
    public static final int SPECIAL_KEY_ARROW_RIGHT = 10;
    public static final int SPECIAL_KEY_ENTER = 11;
    public static final int SPECIAL_KEY_BACKSPACE = 12;
    public static final int SPECIAL_KEY_HOME = 13;
    public static final int SPECIAL_KEY_END = 14;
    public static final int SPECIAL_KEY_DELETE = 15;

    public static final int MAX_HISTORY_SIZE = 50;

    private final Context context;
    private final File projectRoot;
    private final String projectName;
    private final TerminalBuffer buffer;
    private final TerminalEmulator emulator;

    private int initialCols = 80;
    private int initialRows = 24;

    private final StringBuilder currentInput = new StringBuilder();
    private int inputCursorPos = 0;
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private File currentDir;
    private String initialBuffer;

    private PtyBridge.PtyProcess ptyProcess;
    private Process fallbackProcess;
    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile boolean isRunning = false;
    private volatile boolean isAwaitingBootstrapConfirmation = false;
    private volatile String pendingBootstrapCommand = null;
    private volatile String lastExecutedCommand = null;
    private volatile boolean isCtrlActive = false;
    private volatile boolean isAltActive = false;
    private Runnable modifierResetListener = null;
    private TerminalSessionListener listener;

    public void setModifierResetListener(@Nullable Runnable listener) {
        this.modifierResetListener = listener;
    }

    public synchronized void setCtrlActive(boolean active) {
        this.isCtrlActive = active;
    }

    public synchronized boolean isCtrlActive() {
        return isCtrlActive;
    }

    public synchronized void setAltActive(boolean active) {
        this.isAltActive = active;
    }

    public synchronized boolean isAltActive() {
        return isAltActive;
    }

    public TerminalSession(@NonNull Context context, @NonNull File projectRoot) {
        this.context = context.getApplicationContext();
        this.projectRoot = projectRoot;
        this.currentDir = projectRoot;
        this.projectName = ProjectRepository.getProjectName(projectRoot);
        this.buffer = new TerminalBuffer();
        this.emulator = new TerminalEmulator(buffer);
        this.emulator.setDarkTheme(UiUtils.isNightMode(context));
    }

    public void setListener(@Nullable TerminalSessionListener listener) {
        this.listener = listener;
    }

    @NonNull
    public TerminalBuffer getBuffer() {
        return emulator != null ? emulator.getActiveBuffer() : buffer;
    }

    @NonNull
    public TerminalEmulator getEmulator() {
        return emulator;
    }

    @NonNull
    public String getProjectName() {
        return projectName;
    }

    public synchronized String getCurrentInput() {
        return currentInput.toString();
    }

    public synchronized int getInputCursorPos() {
        return inputCursorPos;
    }

    public synchronized void setInputCursorPos(int newPos) {
        this.inputCursorPos = Math.max(0, newPos);
    }

    public synchronized boolean isRunning() {
        return isRunning;
    }

    public synchronized List<String> getHistory() {
        return new ArrayList<>(history);
    }

    public synchronized void setHistory(@Nullable List<String> newHistory) {
        this.history.clear();
        if (newHistory != null) {
            int start = Math.max(0, newHistory.size() - MAX_HISTORY_SIZE);
            for (int i = start; i < newHistory.size(); i++) {
                this.history.add(newHistory.get(i));
            }
        }
        this.historyIndex = this.history.size();
    }

    @NonNull
    public synchronized File getCurrentDir() {
        return currentDir != null ? currentDir : projectRoot;
    }

    public synchronized void setCurrentDir(@Nullable File dir) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            this.currentDir = dir;
        }
    }

    public synchronized void setInitialBuffer(@Nullable String buffer) {
        this.initialBuffer = buffer;
    }

    public synchronized void setWindowSize(int cols, int rows) {
        this.initialCols = cols;
        this.initialRows = rows;
        if (ptyProcess != null) {
            ptyProcess.setWindowSize(rows, cols);
        }
    }

    private static String escapeShellArg(String arg) {
        if (arg == null) return "''";
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    /**
     * Spawns an authentic POSIX shell process inside a master/slave PTY.
     */
    public synchronized void start() {
        if (isRunning) return;

        try {
            if (currentDir == null || !currentDir.exists() || !currentDir.isDirectory()) {
                currentDir = projectRoot;
            }
            currentInput.setLength(0);
            inputCursorPos = 0;

            UserlandBootstrapManager userlandManager = UserlandBootstrapManager.getInstance();
            boolean isUserland = userlandManager.isInstalled(context);
            String prefixPath = userlandManager.getPrefixDir(context).getAbsolutePath();

            Map<String, String> env = new java.util.HashMap<>();
            String currentPath = System.getenv("PATH");
            env.put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:"
                    + (currentPath != null ? currentPath : "/system/bin:/system/xbin") + ":" + prefixPath + "/bin");
            env.put("HOME", projectRoot.getAbsolutePath());
            env.put("TERM", "xterm-256color");
            env.put("LANG", "en_US.UTF-8");
            env.put("LC_ALL", "en_US.UTF-8");
            String resolvedProjectName = (projectName != null && !projectName.trim().isEmpty())
                    ? projectName.trim() : "vcode";
            env.put("VCODE_PROJECT_NAME", resolvedProjectName);
            env.put("PYTHONUNBUFFERED", "1");
            env.put("PS1", "\033[01;32m" + resolvedProjectName + "\033[00m:\033[01;34m\\w\033[00m$ ");

            PtyBridge.PtyProcess p = null;
            Process fallbackProc = null;

            int rows = initialRows > 0 ? initialRows : 24;
            int cols = initialCols > 0 ? initialCols : 80;

            // Option A: Rootless Linux PRoot container execution inside PTY
            if (isUserland && userlandManager.isProotAvailable(context)) {
                try {
                    userlandManager.ensureBaseEnvironment(context);

                    File prootBin = userlandManager.getProotBinary(context);
                    File prootLoader = userlandManager.getProotLoader(context);
                    File homeDir = userlandManager.getHomeDir(context);
                    if (!homeDir.exists()) {
                        homeDir.mkdirs();
                    }

                    List<String> prootCmd = buildProotCommand(prootBin, prefixPath, projectRoot, homeDir);

                    File libDir = userlandManager.getLibDir(context);
                    if (!libDir.exists()) {
                        libDir.mkdirs();
                    }
                    String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
                    File nativeTalloc = new File(nativeLibDir, "libtalloc.so");
                    if (nativeTalloc.exists()) {
                        File tallocSo = new File(libDir, "libtalloc.so");
                        File tallocSo2 = new File(libDir, "libtalloc.so.2");
                        if (!tallocSo.exists()) {
                            if (!UserlandTarExtractor.createSymlink(nativeTalloc.getAbsolutePath(), tallocSo)) {
                                com.cocode.vcode.ide.utils.FileUtils.copyFile(nativeTalloc, tallocSo);
                            }
                        }
                        if (!tallocSo2.exists()) {
                            if (!UserlandTarExtractor.createSymlink(nativeTalloc.getAbsolutePath(), tallocSo2)) {
                                com.cocode.vcode.ide.utils.FileUtils.copyFile(nativeTalloc, tallocSo2);
                            }
                        }
                    }

                    Map<String, String> prootEnv = new java.util.HashMap<>(env);
                    if (prootLoader.exists()) {
                        prootEnv.put("PROOT_LOADER", prootLoader.getAbsolutePath());
                        prootEnv.put("PROOT_LOADER_32", prootLoader.getAbsolutePath());
                    }
                    File tmpDir = userlandManager.getTmpDir(context);
                    if (!tmpDir.exists() && !tmpDir.mkdirs()) {
                        tmpDir = new File(context.getCacheDir(), "tmp");
                        tmpDir.mkdirs();
                    }
                    tmpDir.setReadable(true, false);
                    tmpDir.setWritable(true, false);
                    tmpDir.setExecutable(true, false);
                    String tmpPath;
                    try {
                        tmpPath = tmpDir.getCanonicalPath();
                    } catch (Exception e) {
                        tmpPath = tmpDir.getAbsolutePath();
                    }
                    prootEnv.put("PROOT_TMP_DIR", tmpPath);
                    prootEnv.put("TMPDIR", tmpPath);
                    prootEnv.put("PROOT_IGNORE_MISSING_BINDINGS", "1");

                    String systemLibDir = new File("/system/lib64").exists() ? "/system/lib64" : "/system/lib";
                    prootEnv.put("LD_LIBRARY_PATH", nativeLibDir + ":" + systemLibDir);
                    prootEnv.put("TERM", "xterm-256color");
                    prootEnv.put("LANG", "C.UTF-8");
                    prootEnv.put("HOME", "/workspace");
                    prootEnv.put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin");
                    prootEnv.put("VCODE_PROJECT_NAME", resolvedProjectName);
                    prootEnv.put("PYTHONUNBUFFERED", "1");
                    prootEnv.put("PS1", "\033[01;32m" + resolvedProjectName + "\033[00m:\033[01;34m\\w\033[00m$ ");
                    prootEnv.put("ENV", "/etc/profile.d/vcode.sh");
                    prootEnv.put("XDG_CACHE_HOME", "/root/.cache");
                    prootEnv.put("XDG_CONFIG_HOME", "/root/.config");
                    prootEnv.put("XDG_DATA_HOME", "/root/.local/share");
                    prootEnv.put("NPM_CONFIG_CACHE", "/root/.npm");
                    prootEnv.put("PIP_CACHE_DIR", "/root/.cache/pip");
                    prootEnv.remove("LD_PRELOAD");

                    String[] envArr = new String[prootEnv.size()];
                    int idx = 0;
                    for (Map.Entry<String, String> entry : prootEnv.entrySet()) {
                        envArr[idx++] = entry.getKey() + "=" + entry.getValue();
                    }

                    p = PtyBridge.createSubprocess(
                            prootCmd.toArray(new String[0]),
                            currentDir.getAbsolutePath(),
                            envArr,
                            rows,
                            cols
                    );
                } catch (Throwable t) {
                    p = null;
                }
            }

            // Option B: Native system shell inside PTY
            if (p == null) {
                String shellPath = "/system/bin/sh";
                if (!new File(shellPath).exists()) {
                    String osName = System.getProperty("os.name", "").toLowerCase();
                    if (osName.contains("win")) {
                        shellPath = "cmd.exe";
                    } else if (new File("/bin/sh").exists()) {
                        shellPath = "/bin/sh";
                    }
                }

                String[] cmd = new String[]{ shellPath, "-l" };
                if (!new File(shellPath).exists()) {
                    cmd = new String[]{ shellPath };
                }

                String[] envArr = new String[env.size()];
                int idx = 0;
                for (Map.Entry<String, String> entry : env.entrySet()) {
                    envArr[idx++] = entry.getKey() + "=" + entry.getValue();
                }

                p = PtyBridge.createSubprocess(
                        cmd,
                        currentDir.getAbsolutePath(),
                        envArr,
                        rows,
                        cols
                );
            }

            if (p != null) {
                ptyProcess = p;
                inputStream = ptyProcess.getInputStream();
                outputStream = ptyProcess.getOutputStream();
            } else {
                // Secondary fallback if native PTY fails
                String shellPath = "/system/bin/sh";
                if (!new File(shellPath).exists()) {
                    if (new File("/bin/sh").exists()) {
                        shellPath = "/bin/sh";
                    } else {
                        shellPath = "sh";
                    }
                }
                ProcessBuilder shPb = new ProcessBuilder(shellPath);
                shPb.directory(currentDir);
                shPb.redirectErrorStream(true);
                shPb.environment().putAll(env);
                fallbackProc = shPb.start();
                fallbackProcess = fallbackProc;
                inputStream = fallbackProc.getInputStream();
                outputStream = fallbackProc.getOutputStream();
            }

            isRunning = true;

            // Start background stream reader thread on a dedicated daemon thread
            Thread readerThread = new Thread(this::readProcessOutput, "vcode-terminal-reader");
            readerThread.setDaemon(true);
            readerThread.start();

            // Wait for exit on a dedicated daemon thread
            Thread exitThread = new Thread(this::waitForExit, "vcode-terminal-exit");
            exitThread.setDaemon(true);
            exitThread.start();

            // Replay previous buffer if present
            if (initialBuffer != null && !initialBuffer.trim().isEmpty()) {
                emulator.appendOutput(initialBuffer);
                if (!initialBuffer.endsWith("\n") && !initialBuffer.endsWith("\r")) {
                    emulator.appendOutput("\r\n");
                }
            }

            notifyOutput();
        } catch (Exception e) {
            isRunning = false;
            emulator.appendOutput("\r\n\033[31mFailed to start terminal: " + e.getMessage() + "\033[0m\r\n");
            notifyOutput();
        }
    }

    private void readProcessOutput() {
        try {
            InputStream is = inputStream;
            if (is == null) return;
            java.io.Reader reader = new java.io.InputStreamReader(is, StandardCharsets.UTF_8);
            char[] buf = new char[2048];
            int read;

            while (isRunning && (read = reader.read(buf)) != -1) {
                String chunk = new String(buf, 0, read);
                chunk = filterLinkerWarning(chunk);
                if (!chunk.isEmpty()) {
                    synchronized (this) {
                        emulator.appendOutput(chunk);
                    }
                    notifyOutput();
                }
            }
        } catch (Exception ignored) {
        } finally {
            isRunning = false;
            notifyOutput();
        }
    }

    private static String filterLinkerWarning(String text) {
        if (text == null || text.isEmpty()) return "";
        if (text.contains("failed to find generated linker configuration")) {
            text = text.replace("WARNING: linker: Warning: failed to find generated linker configuration from \"/linkerconfig/ld.config.txt\"\r\n", "")
                       .replace("WARNING: linker: Warning: failed to find generated linker configuration from \"/linkerconfig/ld.config.txt\"\n", "")
                       .replace("WARNING: linker: Warning: failed to find generated linker configuration from \"/linkerconfig/ld.config.txt\"", "");
        }
        return text;
    }

    private void waitForExit() {
        try {
            int exitCode;
            if (ptyProcess != null) {
                exitCode = ptyProcess.waitFor();
            } else if (fallbackProcess != null) {
                exitCode = fallbackProcess.waitFor();
            } else {
                exitCode = 0;
            }
            isRunning = false;
            ExecutorProvider.getInstance().runOnMain(() -> {
                if (listener != null) {
                    listener.onProcessTerminated(exitCode);
                }
            });
        } catch (Exception ignored) {
        } finally {
            isRunning = false;
        }
    }

    private void notifyOutput() {
        ExecutorProvider.getInstance().runOnMain(() -> {
            if (listener != null) {
                listener.onOutput();
            }
        });
    }

    /**
     * Handles keyboard text input, streaming bytes directly down the PTY.
     */
    public synchronized void handleInput(@NonNull String text) {
        if (!isRunning) return;

        if (isAwaitingBootstrapConfirmation) {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\r' || c == '\n') {
                    String answer = currentInput.toString().trim().toLowerCase(java.util.Locale.US);
                    currentInput.setLength(0);
                    inputCursorPos = 0;
                    isAwaitingBootstrapConfirmation = false;
                    String pending = pendingBootstrapCommand;
                    pendingBootstrapCommand = null;
                    if (answer.isEmpty() || answer.equals("y") || answer.equals("yes")) {
                        startBootstrapInstallation(pending);
                    } else {
                        emulator.appendOutput("\r\nInstallation cancelled.\r\n");
                        writeToProcess("\r");
                        notifyOutput();
                    }
                    return;
                } else if (c == '\b' || c == 127) {
                    if (currentInput.length() > 0) {
                        currentInput.deleteCharAt(currentInput.length() - 1);
                        emulator.appendOutput("\b \b");
                        notifyOutput();
                    }
                } else if (c >= 32) {
                    currentInput.append(c);
                    emulator.appendOutput(String.valueOf(c));
                    notifyOutput();
                }
            }
            return;
        }

        if (isCtrlActive && text.length() > 0) {
            char c = text.charAt(0);
            byte[] ctrlBytes = null;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '@' || (c >= '[' && c <= '_')) {
                byte b = (byte) (Character.toUpperCase(c) - '@');
                ctrlBytes = new byte[]{ b };
            }
            isCtrlActive = false;
            if (modifierResetListener != null) {
                ExecutorProvider.getInstance().runOnMain(modifierResetListener);
            }
            if (ctrlBytes != null) {
                if (ctrlBytes[0] == 0x03) {
                    sendSpecialKey(SPECIAL_KEY_CTRL_C);
                } else if (ctrlBytes[0] == 0x04) {
                    sendSpecialKey(SPECIAL_KEY_CTRL_D);
                } else if (ctrlBytes[0] == 0x1A) {
                    sendSpecialKey(SPECIAL_KEY_CTRL_Z);
                } else if (ctrlBytes[0] == 0x0C) {
                    sendSpecialKey(SPECIAL_KEY_CTRL_L);
                } else {
                    writeBytesToProcess(ctrlBytes);
                }
                if (text.length() > 1) {
                    handleInput(text.substring(1));
                }
                return;
            }
        }

        if (isAltActive && text.length() > 0) {
            char c = text.charAt(0);
            isAltActive = false;
            if (modifierResetListener != null) {
                ExecutorProvider.getInstance().runOnMain(modifierResetListener);
            }
            writeToProcess("\033" + c);
            if (text.length() > 1) {
                handleInput(text.substring(1));
            }
            return;
        }

        if (!UserlandBootstrapManager.getInstance().isInstalled(context)) {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\r' || c == '\n') {
                    String line = currentInput.toString().trim();
                    currentInput.setLength(0);
                    inputCursorPos = 0;
                    if (line.startsWith("pkg ") || line.equals("pkg") || isUserlandCommand(line)) {
                        promptUserlandInstallation(line);
                        return;
                    }
                } else if (c == '\b' || c == 127) {
                    if (currentInput.length() > 0) {
                        currentInput.deleteCharAt(currentInput.length() - 1);
                    }
                } else if (c >= 32) {
                    currentInput.append(c);
                }
            }
        }

        writeToProcess(text);
    }

    public synchronized void write(@NonNull String text) {
        handleInput(text);
    }

    private void writeToProcess(String text) {
        if (!isRunning || outputStream == null || text == null) return;
        try {
            outputStream.write(text.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (Exception e) {
            destroy();
        }
    }

    private void writeBytesToProcess(byte[] bytes) {
        if (!isRunning || outputStream == null || bytes == null) return;
        try {
            outputStream.write(bytes);
            outputStream.flush();
        } catch (Exception e) {
            destroy();
        }
    }

    /**
     * Dispatches standard VT100 / POSIX escape sequences and signals to the terminal.
     */
    public synchronized void sendSpecialKey(int specialKey) {
        if (!isRunning || outputStream == null) return;

        try {
            switch (specialKey) {
                case SPECIAL_KEY_ENTER:
                    if (isAwaitingBootstrapConfirmation) {
                        handleInput("\r");
                    } else if (!UserlandBootstrapManager.getInstance().isInstalled(context)) {
                        String line = currentInput.toString().trim();
                        currentInput.setLength(0);
                        inputCursorPos = 0;
                        if (line.startsWith("pkg ") || line.equals("pkg") || isUserlandCommand(line)) {
                            promptUserlandInstallation(line);
                        } else {
                            outputStream.write("\r".getBytes(StandardCharsets.UTF_8));
                            outputStream.flush();
                        }
                    } else {
                        outputStream.write("\r".getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                    }
                    break;
                case SPECIAL_KEY_BACKSPACE:
                    if (isAwaitingBootstrapConfirmation) {
                        handleInput("\b");
                    } else {
                        if (!UserlandBootstrapManager.getInstance().isInstalled(context) && currentInput.length() > 0) {
                            currentInput.deleteCharAt(currentInput.length() - 1);
                        }
                        outputStream.write(new byte[]{ 0x7F }); // ASCII 127 DEL
                        outputStream.flush();
                    }
                    break;
                case SPECIAL_KEY_ARROW_UP:
                    outputStream.write("\033[A".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    break;
                case SPECIAL_KEY_ARROW_DOWN:
                    outputStream.write("\033[B".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    break;
                case SPECIAL_KEY_ARROW_LEFT:
                    outputStream.write("\033[D".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    break;
                case SPECIAL_KEY_ARROW_RIGHT:
                    outputStream.write("\033[C".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    break;
                case SPECIAL_KEY_HOME:
                    outputStream.write("\033[H".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    break;
                case SPECIAL_KEY_END:
                    outputStream.write("\033[F".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    break;
                case SPECIAL_KEY_DELETE:
                    outputStream.write("\033[3~".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    break;
                case SPECIAL_KEY_CTRL_C:
                    if (ptyProcess != null) {
                        ptyProcess.sendSignal(2); // SIGINT
                    }
                    outputStream.write(new byte[]{ 0x03 });
                    outputStream.flush();
                    isAwaitingBootstrapConfirmation = false;
                    pendingBootstrapCommand = null;
                    break;
                case SPECIAL_KEY_CTRL_D:
                    outputStream.write(new byte[]{ 0x04 });
                    outputStream.flush();
                    break;
                case SPECIAL_KEY_CTRL_Z:
                    if (ptyProcess != null) {
                        ptyProcess.sendSignal(20); // SIGTSTP
                    }
                    outputStream.write(new byte[]{ 0x1A });
                    outputStream.flush();
                    break;
                case SPECIAL_KEY_CTRL_L:
                    clearScreen();
                    break;
                case SPECIAL_KEY_TAB:
                    outputStream.write("\t".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    break;
                case SPECIAL_KEY_ESC:
                    outputStream.write("\033".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    break;
            }
        } catch (Exception e) {
            destroy();
        }
    }

    public synchronized void executeCommand(@NonNull String cmd) {
        if (cmd.isEmpty()) {
            writeToProcess("\r");
            return;
        }

        // Validate command security
        TerminalSecurityPolicy.ValidationResult securityResult =
                TerminalSecurityPolicy.validateCommand(cmd, projectRoot, currentDir);
        if (!securityResult.isAllowed()) {
            emulator.appendOutput(securityResult.getErrorMessage() + "\r\n");
            notifyOutput();
            notifyCommandExecuted(cmd);
            return;
        }

        if (cmd.equals("clear")) {
            clearScreen();
            return;
        }

        if (cmd.equals("exit")) {
            destroy();
            emulator.appendOutput("[Session terminated]\r\n");
            notifyOutput();
            return;
        }

        if (cmd.startsWith("vpm") || cmd.equals("vpm")) {
            emulator.appendOutput("vpm is deprecated. Use 'npm' in the desktop terminal environment.\r\n");
            notifyOutput();
            return;
        }

        if (cmd.equals("pkg bootstrap") || cmd.startsWith("pkg bootstrap ")) {
            if (UserlandBootstrapManager.getInstance().isInstalled(context)) {
                emulator.appendOutput("Desktop terminal environment is already installed and active.\r\n");
                notifyOutput();
                notifyCommandFinished(cmd, 0);
                return;
            }
            promptUserlandInstallation(cmd);
            return;
        }

        if (!UserlandBootstrapManager.getInstance().isInstalled(context)) {
            if (cmd.startsWith("pkg ") || cmd.equals("pkg") || isUserlandCommand(cmd)) {
                promptUserlandInstallation(cmd);
                return;
            }
        }

        if (cmd.startsWith("cd ") || cmd.equals("cd")) {
            handleCd(cmd);
            writeToProcess(cmd + "\r");
            return;
        }

        if (history.isEmpty() || !history.get(history.size() - 1).equals(cmd)) {
            history.add(cmd);
            while (history.size() > MAX_HISTORY_SIZE) {
                history.remove(0);
            }
        }
        historyIndex = history.size();
        lastExecutedCommand = cmd;
        notifyCommandExecuted(cmd);

        writeToProcess(cmd + "\r");
    }

    private void handleCd(String cmd) {
        String target = cmd.length() > 2 ? cmd.substring(2).trim() : "";
        File newDir;
        if (target.isEmpty() || target.equals("~")) {
            newDir = projectRoot;
        } else if (target.startsWith("/")) {
            newDir = new File(target);
        } else {
            newDir = new File(currentDir, target);
        }

        try {
            newDir = newDir.getCanonicalFile();
        } catch (Exception e) {
            newDir = newDir.getAbsoluteFile();
        }

        if (newDir.exists() && newDir.isDirectory()) {
            currentDir = newDir;
        }
        notifyCommandExecuted("cd");
    }

    public synchronized void clearScreen() {
        emulator.clear();
        writeToProcess("\033[H\033[2J");
        notifyOutput();
        notifyCommandExecuted("clear");
    }

    private void notifyCommandExecuted(@NonNull String command) {
        if (listener != null) {
            listener.onCommandExecuted(command);
        }
    }

    private void notifyCommandFinished(@Nullable String command, int exitCode) {
        if (listener != null) {
            listener.onCommandFinished(command, exitCode);
        }
    }

    public synchronized void restart() {
        restart(true);
    }

    public synchronized void restart(boolean clearScreen) {
        destroy();
        if (clearScreen) {
            emulator.clear();
        }
        start();
    }

    public synchronized void destroy() {
        isRunning = false;
        try {
            if (ptyProcess != null) {
                ptyProcess.destroy();
                ptyProcess = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (fallbackProcess != null) {
                fallbackProcess.destroy();
                fallbackProcess.destroyForcibly();
                fallbackProcess = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isUserlandCommand(@NonNull String cmd) {
        if (cmd.trim().isEmpty()) return false;
        String firstWord = cmd.trim().split("\\s+")[0].toLowerCase(java.util.Locale.US);
        return firstWord.equals("pkg") ||
                firstWord.equals("python") ||
                firstWord.equals("python3") ||
                firstWord.equals("pip") ||
                firstWord.equals("pip3") ||
                firstWord.equals("git") ||
                firstWord.equals("node") ||
                firstWord.equals("nodejs") ||
                firstWord.equals("npm") ||
                firstWord.equals("npx") ||
                firstWord.equals("curl") ||
                firstWord.equals("wget") ||
                firstWord.equals("gcc") ||
                firstWord.equals("g++") ||
                firstWord.equals("clang") ||
                firstWord.equals("make") ||
                firstWord.equals("bash") ||
                firstWord.equals("setup-userland");
    }

    private void promptUserlandInstallation(@NonNull String triggeringCommand) {
        if (UserlandBootstrapManager.getInstance().isInstalled(context)) {
            emulator.appendOutput("\r\n\033[01;32mDesktop terminal environment is already installed and active.\033[00m\r\n");
            notifyOutput();
            return;
        }

        HardwareCapabilityChecker.DeviceDiagnostic diag = HardwareCapabilityChecker.checkDevice(context);

        emulator.appendOutput("\r\n\033[01;36m======================================================================\033[00m\r\n");
        emulator.appendOutput("\033[01;36m                  VCode Desktop Terminal Environment                  \033[00m\r\n");
        emulator.appendOutput("\033[01;36m======================================================================\033[00m\r\n");
        emulator.appendOutput("Initializing rootless Linux userland (bash, coreutils, pkg manager)...\r\n");
        emulator.appendOutput("Archive Download: ~45 MB | Installed Footprint: ~140 MB\r\n\r\n");

        if (diag.isLowRam) {
            emulator.appendOutput("\033[01;33m⚠️ Low-End Device Advisory:\033[00m\r\n");
            emulator.appendOutput("  • Device Memory: " + diag.getFormattedRam() + " RAM (Low-spec device)\r\n");
            emulator.appendOutput("  • Free Storage: " + diag.getFormattedStorage() + " Available\r\n\r\n");
            emulator.appendOutput("Running full language runtimes (Python/Node/Git CLI) on devices with\r\n");
            emulator.appendOutput("≤2GB RAM may trigger Android OS background tab killing during heavy tasks.\r\n\r\n");
        } else {
            emulator.appendOutput("Device Diagnostics:\r\n");
            emulator.appendOutput("  • Device Memory: " + diag.getFormattedRam() + " RAM\r\n");
            emulator.appendOutput("  • Free Storage: " + diag.getFormattedStorage() + " Available\r\n\r\n");
        }

        emulator.appendOutput("Proceed with desktop environment installation? [Y/n]: ");
        isAwaitingBootstrapConfirmation = true;
        pendingBootstrapCommand = triggeringCommand;
        notifyOutput();
    }

    private void startBootstrapInstallation(@Nullable String pendingCmd) {
        emulator.appendOutput("Downloading and configuring rootless desktop environment...\r\n");
        notifyOutput();

        UserlandBootstrapManager.getInstance().installBootstrap(context, null,
                new UserlandBootstrapManager.BootstrapCallback() {
                    @Override
                    public void onProgress(int stage, int totalStages, @NonNull String stageName, int percent, @Nullable String detail) {
                        String msg = "[" + stage + "/" + totalStages + "] " + stageName + " (" + percent + "%)";
                        if (detail != null && !detail.isEmpty()) {
                            msg += " - " + detail;
                        }
                        emulator.appendOutput(msg + "\r\n");
                        notifyOutput();
                    }

                    @Override
                    public void onSuccess() {
                        emulator.appendOutput("\r\n\033[01;32mDesktop terminal environment initialized successfully!\033[00m\r\n\r\n");
                        notifyOutput();
                        restart(false);
                        if (pendingCmd != null && !pendingCmd.trim().isEmpty() && !pendingCmd.trim().equals("pkg") && !pendingCmd.trim().equals("setup-userland") && !pendingCmd.trim().equals("pkg bootstrap")) {
                            executeCommand(pendingCmd.trim());
                        }
                    }

                    @Override
                    public void onError(@NonNull String error) {
                        emulator.appendOutput("\r\n\033[01;31mError initializing desktop environment: " + error + "\033[00m\r\n");
                        notifyOutput();
                    }
                });
    }

    static List<String> buildProotCommand(@NonNull File prootBin,
                                          @NonNull String prefixPath,
                                          @NonNull File projectRoot,
                                          @NonNull File homeDir) {
        List<String> prootCmd = new ArrayList<>();
        prootCmd.add(prootBin.getAbsolutePath());
        prootCmd.add("--link2symlink"); // link2symlink extension for emulating hardlinks on Android storage
        prootCmd.add("-r");
        prootCmd.add(prefixPath);
        prootCmd.add("-0"); // root emulation
        prootCmd.add("-w");
        prootCmd.add("/workspace");
        prootCmd.add("-b");
        prootCmd.add("/dev");
        prootCmd.add("-b");
        prootCmd.add("/dev/pts");
        prootCmd.add("-b");
        prootCmd.add("/proc");
        prootCmd.add("-b");
        prootCmd.add("/sys");
        if (new File("/system").exists()) {
            prootCmd.add("-b");
            prootCmd.add("/system");
        }
        if (new File("/apex").exists()) {
            prootCmd.add("-b");
            prootCmd.add("/apex");
        }
        if (new File("/vendor").exists()) {
            prootCmd.add("-b");
            prootCmd.add("/vendor");
        }
        if (new File("/linkerconfig").exists()) {
            File guestLinkerConfig = new File(prefixPath, "linkerconfig");
            if (!guestLinkerConfig.exists()) {
                guestLinkerConfig.mkdirs();
            }
            prootCmd.add("-b");
            prootCmd.add("/linkerconfig");
        }
        prootCmd.add("-b");
        prootCmd.add(projectRoot.getAbsolutePath() + ":/workspace");
        prootCmd.add("-b");
        prootCmd.add(homeDir.getAbsolutePath() + ":/root");
        if (new File("/storage").exists()) {
            File guestStorage = new File(prefixPath, "storage");
            if (!guestStorage.exists()) {
                guestStorage.mkdirs();
            }
            prootCmd.add("-b");
            prootCmd.add("/storage");
        }
        if (new File("/sdcard").exists()) {
            File guestSdcard = new File(prefixPath, "sdcard");
            if (!guestSdcard.exists()) {
                guestSdcard.mkdirs();
            }
            prootCmd.add("-b");
            prootCmd.add("/sdcard");
        }

        // Prefer /bin/sh — Alpine's BusyBox sh is musl-linked with zero external host dependencies
        File containerSh = new File(prefixPath, "bin/sh");
        File containerBash = new File(prefixPath, "bin/bash");
        if (isElfBinary(containerSh)) {
            prootCmd.add("/bin/sh");
            prootCmd.add("-l");
        } else if (isElfBinary(containerBash)) {
            prootCmd.add("/bin/bash");
            prootCmd.add("-l");
        } else if (containerSh.exists()) {
            prootCmd.add("/bin/sh");
            prootCmd.add("-l");
        } else {
            prootCmd.add("/bin/sh");
        }
        return prootCmd;
    }

    private static boolean isElfBinary(File file) {
        return com.cocode.vcode.ide.core.terminal.userland.UserlandBootstrapManager.isElfBinary(file);
    }
}
