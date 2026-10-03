package com.cocode.vcode.ide.core.terminal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.data.npm.PackageManagerService;
import com.cocode.vcode.ide.data.npm.model.NpmPackageInfo;
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
import java.util.UUID;

/**
 * Manages the background shell process lifecycle, process streams, and environment variables.
 * Scoped strictly to the opened project root with real project name virtualization.
 * Provides interactive line discipline, local echo, command execution, and prompt loop.
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

    private final Context context;
    private final File projectRoot;
    private final String projectName;
    private final TerminalBuffer buffer;
    private final TerminalEmulator emulator;

    private final StringBuilder currentInput = new StringBuilder();
    private int inputCursorPos = 0;
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private File currentDir;
    private String initialBuffer;

    private Process process;
    private OutputStream outputStream;
    private volatile boolean isRunning = false;
    private volatile boolean isExecutingCommand = false;
    private volatile String activeSentinel = null;
    private volatile String lastExecutedCommand = null;
    private TerminalSessionListener listener;

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
        return buffer;
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

    public synchronized boolean isRunning() {
        return isRunning;
    }

    public static final int MAX_HISTORY_SIZE = 50;

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

    private static String escapeShellArg(String arg) {
        if (arg == null) return "''";
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    /**
     * Spawns a shell process (/system/bin/sh) with the projectRoot or restored currentDir as working directory.
     */
    public synchronized void start() {
        if (isRunning) return;

        try {
            if (currentDir == null || !currentDir.exists() || !currentDir.isDirectory()) {
                currentDir = projectRoot;
            }
            currentInput.setLength(0);
            isExecutingCommand = false;
            activeSentinel = null;

            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh");
            pb.directory(currentDir);
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();
            String filesDir = context.getFilesDir().getAbsolutePath();
            // Scope HOME strictly to the project root to prevent exposing app private storage or SSH keys
            env.put("HOME", projectRoot.getAbsolutePath());
            String currentPath = env.get("PATH");
            // Prioritize standard system bins to avoid PATH hijacking from writable app storage
            env.put("PATH", (currentPath != null ? currentPath : "/system/bin:/system/xbin") + ":" + filesDir + "/bin");
            env.put("TERM", "xterm-256color");
            env.put("VCODE_PROJECT_NAME", projectName);

            process = pb.start();
            outputStream = process.getOutputStream();
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

            // Print initial prompt
            printPrompt();
            notifyOutput();
        } catch (Exception e) {
            isRunning = false;
            emulator.appendOutput("\r\n\033[31mFailed to start terminal: " + e.getMessage() + "\033[0m\r\n");
            notifyOutput();
        }
    }

    private void printPrompt() {
        String relPath = getDisplayPath();
        String prompt = "\033[01;32m" + projectName + "\033[00m:\033[01;34m" + relPath + "\033[00m$ ";
        emulator.appendOutput(prompt);
        currentInput.setLength(0);
        inputCursorPos = 0;
    }

    private String getDisplayPath() {
        if (currentDir == null || projectRoot == null) return "~";
        String rootPath = projectRoot.getAbsolutePath();
        String curPath = currentDir.getAbsolutePath();
        if (curPath.equals(rootPath)) {
            return "~";
        }
        if (curPath.startsWith(rootPath)) {
            String sub = curPath.substring(rootPath.length());
            if (sub.startsWith(File.separator) || sub.startsWith("/")) {
                sub = sub.substring(1);
            }
            return "~/" + sub.replace(File.separatorChar, '/');
        }
        return currentDir.getName();
    }

    private void readProcessOutput() {
        try {
            InputStream is = process.getInputStream();
            byte[] buf = new byte[2048];
            int read;
            StringBuilder pendingOutput = new StringBuilder();

            while (isRunning && (read = is.read(buf)) != -1) {
                String chunk = new String(buf, 0, read, StandardCharsets.UTF_8);
                synchronized (this) {
                    pendingOutput.append(chunk);

                    String currentSentinel = activeSentinel;
                    if (currentSentinel != null) {
                        int sentinelIdx;
                        while ((sentinelIdx = pendingOutput.indexOf(currentSentinel)) != -1) {
                            int newlineIdx = pendingOutput.indexOf("\n", sentinelIdx);
                            if (newlineIdx == -1) {
                                // Sentinel line not yet fully read; wait for next read
                                break;
                            }
                            int exitCode = 0;
                            try {
                                String sentinelLine = pendingOutput.substring(sentinelIdx, newlineIdx).trim();
                                int colonIdx = sentinelLine.lastIndexOf(':');
                                if (colonIdx != -1) {
                                    exitCode = Integer.parseInt(sentinelLine.substring(colonIdx + 1).trim());
                                }
                            } catch (Exception ignored) {
                            }
                            String outputBefore = pendingOutput.substring(0, sentinelIdx);
                            if (!outputBefore.isEmpty()) {
                                emulator.appendOutput(outputBefore);
                            }
                            pendingOutput.delete(0, newlineIdx + 1);

                            String finishedCmd = lastExecutedCommand;
                            isExecutingCommand = false;
                            activeSentinel = null;
                            printPrompt();
                            if (currentInput.length() > 0) {
                                emulator.appendOutput(currentInput.toString());
                            }
                            notifyCommandFinished(finishedCmd, exitCode);
                        }
                    }

                    int sentinelLen = activeSentinel != null ? activeSentinel.length() : 0;
                    if (pendingOutput.length() > sentinelLen) {
                        if (activeSentinel == null || pendingOutput.indexOf(activeSentinel) == -1) {
                            int safeLen = pendingOutput.length() - sentinelLen;
                            String safeChunk = pendingOutput.substring(0, safeLen);
                            emulator.appendOutput(safeChunk);
                            pendingOutput.delete(0, safeLen);
                        }
                    }
                }
                notifyOutput();
            }
        } catch (Exception ignored) {
        }
    }

    private void waitForExit() {
        try {
            if (process != null) {
                int exitCode = process.waitFor();
                isRunning = false;
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (listener != null) {
                        listener.onProcessTerminated(exitCode);
                    }
                });
            }
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

    public synchronized void handleInput(@NonNull String text) {
        if (!isRunning) return;

        if (isExecutingCommand) {
            writeToProcess(text);
            return;
        }

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n') {
                executeCurrentLine();
            } else if (c == '\b' || c == 127) {
                handleBackspace();
            } else if (c == '\t') {
                handleTabCompletion();
            } else if (c >= 32) {
                if (inputCursorPos == currentInput.length()) {
                    currentInput.append(c);
                    inputCursorPos++;
                    emulator.appendOutput(String.valueOf(c));
                } else {
                    currentInput.insert(inputCursorPos, c);
                    inputCursorPos++;
                    String rest = currentInput.substring(inputCursorPos - 1);
                    emulator.appendOutput(rest);
                    int charsBack = currentInput.length() - inputCursorPos;
                    if (charsBack > 0) {
                        emulator.appendOutput("\033[" + charsBack + "D");
                    }
                }
                notifyOutput();
            }
        }
    }

    public synchronized void write(@NonNull String text) {
        handleInput(text);
    }

    private void writeToProcess(String text) {
        if (!isRunning || outputStream == null) return;
        try {
            outputStream.write(text.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (Exception e) {
            destroy();
        }
    }

    public synchronized void sendSpecialKey(int specialKey) {
        if (!isRunning) return;

        switch (specialKey) {
            case SPECIAL_KEY_ENTER:
                if (isExecutingCommand) {
                    writeToProcess("\n");
                } else {
                    executeCurrentLine();
                }
                break;
            case SPECIAL_KEY_BACKSPACE:
                if (isExecutingCommand) {
                    writeToProcess("\b");
                } else {
                    handleBackspace();
                }
                break;
            case SPECIAL_KEY_ARROW_UP:
                if (isExecutingCommand) {
                    writeToProcess("\033[A");
                } else {
                    navigateHistory(-1);
                }
                break;
            case SPECIAL_KEY_ARROW_DOWN:
                if (isExecutingCommand) {
                    writeToProcess("\033[B");
                } else {
                    navigateHistory(1);
                }
                break;
            case SPECIAL_KEY_ARROW_LEFT:
                if (isExecutingCommand) {
                    writeToProcess("\033[D");
                } else {
                    if (inputCursorPos > 0) {
                        inputCursorPos--;
                        emulator.appendOutput("\033[D");
                        notifyOutput();
                    }
                }
                break;
            case SPECIAL_KEY_ARROW_RIGHT:
                if (isExecutingCommand) {
                    writeToProcess("\033[C");
                } else {
                    if (inputCursorPos < currentInput.length()) {
                        inputCursorPos++;
                        emulator.appendOutput("\033[C");
                        notifyOutput();
                    }
                }
                break;
            case SPECIAL_KEY_HOME:
                if (isExecutingCommand) {
                    writeToProcess("\033[H");
                } else {
                    if (inputCursorPos > 0) {
                        emulator.appendOutput("\033[" + inputCursorPos + "D");
                        inputCursorPos = 0;
                        notifyOutput();
                    }
                }
                break;
            case SPECIAL_KEY_END:
                if (isExecutingCommand) {
                    writeToProcess("\033[F");
                } else {
                    int forward = currentInput.length() - inputCursorPos;
                    if (forward > 0) {
                        emulator.appendOutput("\033[" + forward + "C");
                        inputCursorPos = currentInput.length();
                        notifyOutput();
                    }
                }
                break;
            case SPECIAL_KEY_DELETE:
                if (isExecutingCommand) {
                    writeToProcess("\033[3~");
                } else {
                    handleDelete();
                }
                break;
            case SPECIAL_KEY_CTRL_C:
                handleCtrlC();
                break;
            case SPECIAL_KEY_CTRL_L:
                clearScreen();
                break;
            case SPECIAL_KEY_CTRL_D:
                if (isExecutingCommand) {
                    writeToProcess("\u0004");
                } else if (currentInput.length() == 0) {
                    destroy();
                    emulator.appendOutput("[Session terminated]\r\n");
                    notifyOutput();
                }
                break;
            case SPECIAL_KEY_CTRL_Z:
                if (isExecutingCommand) {
                    writeToProcess("\u001A");
                }
                break;
            case SPECIAL_KEY_TAB:
                if (isExecutingCommand) {
                    writeToProcess("\t");
                } else {
                    handleTabCompletion();
                }
                break;
            case SPECIAL_KEY_ESC:
                if (isExecutingCommand) {
                    writeToProcess("\033");
                }
                break;
        }
    }

    public synchronized int getInputCursorPos() {
        return inputCursorPos;
    }

    public synchronized void setInputCursorPos(int newPos) {
        if (isExecutingCommand) return;
        newPos = Math.max(0, Math.min(newPos, currentInput.length()));
        int diff = newPos - inputCursorPos;
        if (diff > 0) {
            emulator.appendOutput("\033[" + diff + "C");
            inputCursorPos = newPos;
            notifyOutput();
        } else if (diff < 0) {
            emulator.appendOutput("\033[" + (-diff) + "D");
            inputCursorPos = newPos;
            notifyOutput();
        }
    }

    private void handleDelete() {
        if (inputCursorPos < currentInput.length()) {
            currentInput.deleteCharAt(inputCursorPos);
            String rest = currentInput.substring(inputCursorPos);
            emulator.appendOutput(rest + " ");
            int charsBack = currentInput.length() - inputCursorPos + 1;
            emulator.appendOutput("\033[" + charsBack + "D");
            notifyOutput();
        }
    }

    private void handleCtrlC() {
        if (isExecutingCommand) {
            writeToProcess("\u0003");
        }
        emulator.appendOutput("^C\r\n");
        currentInput.setLength(0);
        inputCursorPos = 0;
        isExecutingCommand = false;
        activeSentinel = null;
        printPrompt();
        notifyOutput();
    }

    private void handleBackspace() {
        if (inputCursorPos > 0) {
            if (inputCursorPos == currentInput.length()) {
                currentInput.deleteCharAt(currentInput.length() - 1);
                inputCursorPos--;
                emulator.appendOutput("\b");
            } else {
                currentInput.deleteCharAt(inputCursorPos - 1);
                inputCursorPos--;
                emulator.appendOutput("\033[D");
                String rest = currentInput.substring(inputCursorPos);
                emulator.appendOutput(rest + " ");
                int charsBack = currentInput.length() - inputCursorPos + 1;
                emulator.appendOutput("\033[" + charsBack + "D");
            }
            notifyOutput();
        }
    }

    private void navigateHistory(int direction) {
        if (history.isEmpty()) return;
        int newIndex = historyIndex + direction;
        if (newIndex < 0) newIndex = 0;
        if (newIndex > history.size()) newIndex = history.size();

        if (newIndex == historyIndex) return;
        historyIndex = newIndex;

        // Move cursor to end of current input if needed
        int forward = currentInput.length() - inputCursorPos;
        if (forward > 0) {
            emulator.appendOutput("\033[" + forward + "C");
        }

        // Erase current input from screen
        for (int i = 0; i < currentInput.length(); i++) {
            emulator.appendOutput("\b");
        }
        currentInput.setLength(0);
        inputCursorPos = 0;

        if (historyIndex < history.size()) {
            String histCmd = history.get(historyIndex);
            currentInput.append(histCmd);
            inputCursorPos = histCmd.length();
            emulator.appendOutput(histCmd);
        }
        notifyOutput();
    }

    private void executeCurrentLine() {
        emulator.appendOutput("\r\n");
        String cmd = currentInput.toString().trim();
        currentInput.setLength(0);
        inputCursorPos = 0;

        if (cmd.isEmpty()) {
            printPrompt();
            notifyOutput();
            return;
        }

        if (cmd.equals("clear")) {
            clearScreen();
            return;
        }

        if (cmd.equals("history")) {
            if (history.isEmpty()) {
                emulator.appendOutput("No command history.\r\n");
            } else {
                for (int i = 0; i < history.size(); i++) {
                    String line = String.format(java.util.Locale.US, "  %4d  %s\r\n", i + 1, history.get(i));
                    emulator.appendOutput(line);
                }
            }
            printPrompt();
            notifyOutput();
            notifyCommandExecuted("history");
            return;
        }

        if (cmd.startsWith("cd ") || cmd.equals("cd")) {
            handleCd(cmd);
            return;
        }

        if (cmd.equals("exit")) {
            destroy();
            emulator.appendOutput("[Session terminated]\r\n");
            notifyOutput();
            return;
        }

        if (cmd.startsWith("vpm") || cmd.equals("vpm") || cmd.startsWith("npm") || cmd.equals("npm")) {
            handleVpmCommand(cmd);
            return;
        }

        // Validate command security (restricts deleting .vcode or out-of-scope paths)
        TerminalSecurityPolicy.ValidationResult securityResult =
                TerminalSecurityPolicy.validateCommand(cmd, projectRoot, currentDir);
        if (!securityResult.isAllowed()) {
            emulator.appendOutput(securityResult.getErrorMessage() + "\r\n");
            printPrompt();
            notifyOutput();
            notifyCommandExecuted(cmd);
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

        isExecutingCommand = true;
        activeSentinel = "__VCODE_CMD_" + UUID.randomUUID().toString().replace("-", "") + "__";
        try {
            if (outputStream != null) {
                String script = cmd + "\necho \"" + activeSentinel + ":$?\"\n";
                outputStream.write(script.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
        } catch (Exception e) {
            emulator.appendOutput("Error writing to shell: " + e.getMessage() + "\r\n");
            isExecutingCommand = false;
            activeSentinel = null;
            printPrompt();
            notifyOutput();
        }
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

        // Strict project root boundary enforcement
        try {
            String rootCanonical = projectRoot.getCanonicalPath();
            String newCanonical = newDir.getCanonicalPath();
            if (!newCanonical.equals(rootCanonical) && !newCanonical.startsWith(rootCanonical + File.separator)) {
                emulator.appendOutput("cd: permission denied: directory navigation is restricted to project workspace\r\n");
                printPrompt();
                notifyOutput();
                return;
            }
        } catch (Exception e) {
            emulator.appendOutput("cd: permission denied: cannot resolve path: " + target + "\r\n");
            printPrompt();
            notifyOutput();
            return;
        }

        if (TerminalSecurityPolicy.isVCodePath(newDir, projectRoot)) {
            emulator.appendOutput("cd: permission denied: .vcode directory is restricted\r\n");
            printPrompt();
            notifyOutput();
            return;
        }

        if (!newDir.exists()) {
            emulator.appendOutput("cd: no such file or directory: " + target + "\r\n");
            printPrompt();
            notifyOutput();
            return;
        }
        if (!newDir.isDirectory()) {
            emulator.appendOutput("cd: not a directory: " + target + "\r\n");
            printPrompt();
            notifyOutput();
            return;
        }

        currentDir = newDir;
        notifyCommandExecuted("cd");
        try {
            if (outputStream != null) {
                activeSentinel = "__VCODE_CMD_" + UUID.randomUUID().toString().replace("-", "") + "__";
                String script = "cd " + escapeShellArg(currentDir.getAbsolutePath()) + "\necho \"" + activeSentinel + ":$?\"\n";
                isExecutingCommand = true;
                outputStream.write(script.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
        } catch (Exception ignored) {
            isExecutingCommand = false;
            activeSentinel = null;
            printPrompt();
            notifyOutput();
        }
    }

    private void handleTabCompletion() {
        String input = currentInput.toString();
        int lastSpace = input.lastIndexOf(' ');
        String token = lastSpace >= 0 ? input.substring(lastSpace + 1) : input;

        File searchDir = currentDir;
        String prefix = token;
        int slashIdx = token.lastIndexOf('/');
        if (slashIdx >= 0) {
            String dirPart = token.substring(0, slashIdx + 1);
            prefix = token.substring(slashIdx + 1);
            File subDir = dirPart.startsWith("/") ? new File(dirPart) : new File(currentDir, dirPart);
            if (subDir.exists() && subDir.isDirectory()) {
                searchDir = subDir;
            }
        }

        // Strict project boundary check for autocompletion
        try {
            String rootCanonical = projectRoot.getCanonicalPath();
            String searchCanonical = searchDir.getCanonicalPath();
            if (!searchCanonical.equals(rootCanonical) && !searchCanonical.startsWith(rootCanonical + File.separator)) {
                return;
            }
        } catch (Exception ignored) {
            return;
        }

        if (TerminalSecurityPolicy.isVCodePath(searchDir, projectRoot)) {
            return;
        }

        final String filterPrefix = prefix;
        File[] files = searchDir.listFiles((dir, name) -> {
            if (".vcode".equals(name)) return false;
            return name.startsWith(filterPrefix);
        });
        if (files == null || files.length == 0) return;

        if (files.length == 1) {
            String matchName = files[0].getName();
            String completion = matchName.substring(filterPrefix.length());
            if (files[0].isDirectory()) {
                completion += "/";
            } else {
                completion += " ";
            }
            currentInput.append(completion);
            inputCursorPos = currentInput.length();
            emulator.appendOutput(completion);
            notifyOutput();
        } else {
            emulator.appendOutput("\r\n");
            int maxDisplay = Math.min(files.length, 50);
            for (int i = 0; i < maxDisplay; i++) {
                emulator.appendOutput(files[i].getName());
                if (files[i].isDirectory()) emulator.appendOutput("/");
                if (i < maxDisplay - 1) emulator.appendOutput("  ");
            }
            if (files.length > maxDisplay) {
                emulator.appendOutput("  ... (" + (files.length - maxDisplay) + " more)");
            }
            emulator.appendOutput("\r\n");
            printPrompt();
            emulator.appendOutput(currentInput.toString());
            notifyOutput();
        }
    }

    public synchronized void clearScreen() {
        emulator.clear();
        printPrompt();
        if (currentInput.length() > 0) {
            emulator.appendOutput(currentInput.toString());
            inputCursorPos = currentInput.length();
        }
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
        destroy();
        emulator.clear();
        start();
    }

    public synchronized void destroy() {
        isRunning = false;
        isExecutingCommand = false;
        activeSentinel = null;
        if (process != null) {
            try {
                InputStream is = process.getInputStream();
                if (is != null) is.close();
            } catch (Exception ignored) {
            }
            try {
                process.destroy();
                process.destroyForcibly();
            } catch (Exception ignored) {
            }
            process = null;
        }
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (Exception ignored) {
            }
            outputStream = null;
        }
    }

    private void handleVpmCommand(@NonNull String cmd) {
        if (history.isEmpty() || !history.get(history.size() - 1).equals(cmd)) {
            history.add(cmd);
            while (history.size() > MAX_HISTORY_SIZE) {
                history.remove(0);
            }
        }
        historyIndex = history.size();
        lastExecutedCommand = cmd;
        notifyCommandExecuted(cmd);

        String[] parts = cmd.trim().split("\\s+");
        if (parts.length <= 1 || "help".equals(parts[1]) || "-h".equals(parts[1]) || "--help".equals(parts[1])) {
            emulator.appendOutput("VCode Package Manager (vpm) - Zero-Node Mobile Package System\r\n\r\n");
            emulator.appendOutput("Usage:\r\n");
            emulator.appendOutput("  vpm install <package>[@version] [-D|--save-dev]\r\n");
            emulator.appendOutput("  vpm uninstall <package>\r\n");
            emulator.appendOutput("  vpm list\r\n");
            emulator.appendOutput("  vpm search <query>\r\n");
            emulator.appendOutput("  vpm help\r\n\r\n");
            emulator.appendOutput("Aliases:\r\n");
            emulator.appendOutput("  npm i, npm install, npm uninstall, npm rm, npm ls, npm search\r\n");
            printPrompt();
            notifyOutput();
            notifyCommandFinished(cmd, 0);
            return;
        }

        String subCmd = parts[1];
        if ("install".equals(subCmd) || "i".equals(subCmd) || "add".equals(subCmd)) {
            boolean isDev = false;
            String pkgSpec = null;
            for (int i = 2; i < parts.length; i++) {
                if ("-D".equals(parts[i]) || "--save-dev".equals(parts[i]) || "-d".equals(parts[i])) {
                    isDev = true;
                } else if ("-S".equals(parts[i]) || "--save".equals(parts[i])) {
                    isDev = false;
                } else if (pkgSpec == null && !parts[i].startsWith("-")) {
                    pkgSpec = parts[i];
                }
            }

            if (pkgSpec == null) {
                emulator.appendOutput("vpm: specify a package name to install: vpm install <package>\r\n");
                printPrompt();
                notifyOutput();
                notifyCommandFinished(cmd, 1);
                return;
            }

            String packageName = pkgSpec;
            String targetVersion = null;
            int atIdx = pkgSpec.lastIndexOf('@');
            if (atIdx > 0) {
                packageName = pkgSpec.substring(0, atIdx);
                targetVersion = pkgSpec.substring(atIdx + 1);
            }

            isExecutingCommand = true;
            emulator.appendOutput("vpm: resolving " + packageName + (targetVersion != null ? "@" + targetVersion : "") + "...\r\n");
            notifyOutput();

            PackageManagerService.getInstance().installPackage(projectRoot, packageName, targetVersion, isDev,
                    (status, percent) -> {
                        emulator.appendOutput("vpm: " + status + " (" + percent + "%)\r\n");
                        notifyOutput();
                    },
                    new PackageManagerService.PackageOperationCallback() {
                        @Override
                        public void onSuccess(@NonNull String message) {
                            isExecutingCommand = false;
                            emulator.appendOutput("vpm: " + message + "\r\n");
                            printPrompt();
                            notifyOutput();
                            notifyCommandFinished(cmd, 0);
                        }

                        @Override
                        public void onError(@NonNull String error) {
                            isExecutingCommand = false;
                            emulator.appendOutput("vpm: error: " + error + "\r\n");
                            printPrompt();
                            notifyOutput();
                            notifyCommandFinished(cmd, 1);
                        }
                    });
            return;
        }

        if ("uninstall".equals(subCmd) || "remove".equals(subCmd) || "rm".equals(subCmd) || "un".equals(subCmd)) {
            if (parts.length < 3) {
                emulator.appendOutput("vpm: specify a package name to uninstall: vpm uninstall <package>\r\n");
                printPrompt();
                notifyOutput();
                notifyCommandFinished(cmd, 1);
                return;
            }
            String packageName = parts[2];
            isExecutingCommand = true;
            emulator.appendOutput("vpm: uninstalling " + packageName + "...\r\n");
            notifyOutput();

            PackageManagerService.getInstance().uninstallPackage(projectRoot, packageName,
                    new PackageManagerService.PackageOperationCallback() {
                        @Override
                        public void onSuccess(@NonNull String message) {
                            isExecutingCommand = false;
                            emulator.appendOutput("vpm: " + message + "\r\n");
                            printPrompt();
                            notifyOutput();
                            notifyCommandFinished(cmd, 0);
                        }

                        @Override
                        public void onError(@NonNull String error) {
                            isExecutingCommand = false;
                            emulator.appendOutput("vpm: error: " + error + "\r\n");
                            printPrompt();
                            notifyOutput();
                            notifyCommandFinished(cmd, 1);
                        }
                    });
            return;
        }

        if ("list".equals(subCmd) || "ls".equals(subCmd)) {
            isExecutingCommand = true;
            emulator.appendOutput("vpm: reading dependencies from package.json...\r\n");
            notifyOutput();

            PackageManagerService.getInstance().getInstalledPackages(projectRoot,
                    new PackageManagerService.PackageListCallback() {
                        @Override
                        public void onResult(@NonNull List<NpmPackageInfo> packages) {
                            isExecutingCommand = false;
                            if (packages.isEmpty()) {
                                emulator.appendOutput("No packages installed.\r\n");
                            } else {
                                emulator.appendOutput("Installed dependencies (" + packages.size() + "):\r\n");
                                for (NpmPackageInfo p : packages) {
                                    String devTag = p.isDev() ? " (dev)" : "";
                                    emulator.appendOutput("  ├── " + p.getName() + "@" + p.getInstalledVersion() + devTag + "\r\n");
                                }
                            }
                            printPrompt();
                            notifyOutput();
                            notifyCommandFinished(cmd, 0);
                        }

                        @Override
                        public void onError(@NonNull String error) {
                            isExecutingCommand = false;
                            emulator.appendOutput("vpm: " + error + "\r\n");
                            printPrompt();
                            notifyOutput();
                            notifyCommandFinished(cmd, 1);
                        }
                    });
            return;
        }

        if ("search".equals(subCmd) || "s".equals(subCmd)) {
            if (parts.length < 3) {
                emulator.appendOutput("vpm: specify a search query: vpm search <query>\r\n");
                printPrompt();
                notifyOutput();
                notifyCommandFinished(cmd, 1);
                return;
            }
            StringBuilder queryBuilder = new StringBuilder();
            for (int i = 2; i < parts.length; i++) {
                if (queryBuilder.length() > 0) queryBuilder.append(" ");
                queryBuilder.append(parts[i]);
            }
            String query = queryBuilder.toString();
            isExecutingCommand = true;
            emulator.appendOutput("vpm: searching npm registry for \"" + query + "\"...\r\n");
            notifyOutput();

            PackageManagerService.getInstance().searchPackages(projectRoot, query,
                    new PackageManagerService.PackageListCallback() {
                        @Override
                        public void onResult(@NonNull List<NpmPackageInfo> packages) {
                            isExecutingCommand = false;
                            if (packages.isEmpty()) {
                                emulator.appendOutput("No packages found matching \"" + query + "\".\r\n");
                            } else {
                                for (NpmPackageInfo p : packages) {
                                    String installedTag = p.isInstalled() ? " [installed: " + p.getInstalledVersion() + "]" : "";
                                    emulator.appendOutput(p.getName() + "@" + p.getVersion() + installedTag + "\r\n");
                                    if (p.getDescription() != null && !p.getDescription().isEmpty()) {
                                        emulator.appendOutput("  " + p.getDescription() + "\r\n");
                                    }
                                }
                            }
                            printPrompt();
                            notifyOutput();
                            notifyCommandFinished(cmd, 0);
                        }

                        @Override
                        public void onError(@NonNull String error) {
                            isExecutingCommand = false;
                            emulator.appendOutput("vpm: search error: " + error + "\r\n");
                            printPrompt();
                            notifyOutput();
                            notifyCommandFinished(cmd, 1);
                        }
                    });
            return;
        }

        emulator.appendOutput("vpm: unknown command: " + subCmd + ". Run 'vpm help' for available commands.\r\n");
        printPrompt();
        notifyOutput();
        notifyCommandFinished(cmd, 1);
    }
}
