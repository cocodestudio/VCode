package com.cocode.vcode.ide.core.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Security policy for terminal command execution in VCode.
 * <p>
 * Enforces strict workspace boundaries:
 * 1. Restricts deletion or destruction of the {@code .vcode} directory and its contents.
 * 2. Restricts deletion of files or directories outside the active project workspace scope.
 * 3. Restricts deletion of the project root directory itself.
 * 4. Restricts redirection and moves that target {@code .vcode} or out-of-scope paths.
 * 5. Restricts dangerous system/hardware modifications, raw device node writes, and fork bombs.
 */
public final class TerminalSecurityPolicy {

    private static final String VCODE_DIR_NAME = ".vcode";

    private static final Pattern FORK_BOMB_EXACT = Pattern.compile("(?s):\\s*\\(\\s*\\)\\s*\\{\\s*:\\s*\\|\\s*:\\s*&\\s*\\}\\s*;\\s*:");
    private static final Pattern FORK_BOMB_GENERIC = Pattern.compile("(?si)(?:^|[;\\s&|])\\s*([a-zA-Z0-9_:]+)\\s*\\(\\s*\\)\\s*\\{[^}]*\\b\\1\\s*\\|\\s*\\1\\s*&[^}]*\\}\\s*;?\\s*\\1");

    private static final Set<String> FORBIDDEN_SYSTEM_COMMANDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // Privilege escalation / root
            "su",
            "sudo",
            "doas",
            "pkexec",
            "magisk",
            "ksu",
            "superkey",
            "daemonsu",
            "run-as",

            // System power and state
            "reboot",
            "shutdown",
            "poweroff",
            "halt",
            "init",
            "telinit",

            // SELinux and kernel security
            "setenforce",
            "chcon",
            "restorecon",

            // Kernel modules
            "insmod",
            "rmmod",
            "modprobe",

            // Filesystem creation, raw partitioning, and mounting
            "mkfs",
            "mke2fs",
            "mkfs.ext2",
            "mkfs.ext3",
            "mkfs.ext4",
            "mkfs.vfat",
            "mkfs.f2fs",
            "mkfs.ntfs",
            "mkfs.btrfs",
            "mkfs.exfat",
            "mkswap",
            "fdisk",
            "parted",
            "gdisk",
            "sfdisk",
            "cfdisk",
            "wipefs",
            "badblocks",
            "fsck",
            "e2fsck",
            "losetup",
            "mount",
            "umount",
            "remount",
            "chroot",
            "pivot_root",

            // Bootloader / recovery / raw flash
            "fastboot",
            "recovery",
            "bootloader",
            "flash_image",
            "flashcp",

            // Network and kernel parameters
            "iptables",
            "ip6tables",
            "nft",
            "sysctl",
            "tcpdump",

            // Android framework & device manipulation (vulnerable on Android host)
            "pm",
            "am",
            "cmd",
            "appops",
            "service",
            "servicemanager",
            "settings",
            "content",
            "input",
            "screencap",
            "screenrecord",
            "logcat",
            "dumpsys",
            "bugreport",
            "svc",
            "setprop",
            "stop",
            "start",
            "vdc",
            "sm",
            "wpa_cli",
            "locksettings",
            "telecom",
            "uiautomator",
            "monkey",
            "ime",
            "bu",
            "bmgr",
            "dmesg",
            "debuggerd",
            "crasher"
    )));

    private static final String[] RESTRICTED_DEVICE_PREFIXES = {
            "/dev/block",
            "/dev/mem",
            "/dev/kmem",
            "/dev/port",
            "/dev/fuse",
            "/dev/socket",
            "/dev/input",
            "/dev/graphics",
            "/dev/binder",
            "/dev/hwbinder",
            "/dev/vndbinder",
            "/dev/qseecom",
            "/dev/ion",
            "/proc/kmsg",
            "/proc/kcore",
            "/proc/keys",
            "/proc/sysrq-trigger",
            "/proc/sys/kernel",
            "/proc/sys/vm",
            "/sys/kernel/debug",
            "/sys/kernel/tracing",
            "/sys/power",
            "/sys/firmware",
            "/sys/class/android_usb",
            "/sys/devices",
            "/sys/fs/selinux"
    };

    private static final Set<String> DELETE_COMMANDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "rm",
            "rmdir",
            "unlink",
            "shred",
            "srm",
            "wipe",
            "trash"
    )));

    private static final Set<String> COMMAND_WRAPPERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "sudo",
            "busybox",
            "toolbox",
            "toybox",
            "command",
            "builtin",
            "nohup",
            "time",
            "exec"
    )));

    private static final Set<String> SUBSHELL_COMMANDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "sh",
            "bash",
            "dash",
            "zsh",
            "ksh",
            "ash"
    )));

    private TerminalSecurityPolicy() {
    }

    /**
     * Represents the outcome of command security validation.
     */
    public static final class ValidationResult {
        private final boolean allowed;
        private final String errorMessage;

        private ValidationResult(boolean allowed, String errorMessage) {
            this.allowed = allowed;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult allow() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult deny(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isAllowed() {
            return allowed;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Validates a command line string before execution.
     *
     * @param commandLine the raw command entered by the user
     * @param projectRoot the canonical project root directory
     * @param currentDir  the current working directory of the terminal session
     * @return {@link ValidationResult#allow()} if safe, or a denied result with a descriptive error
     */
    @NonNull
    public static ValidationResult validateCommand(
            @Nullable String commandLine,
            @NonNull File projectRoot,
            @NonNull File currentDir
    ) {
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return ValidationResult.allow();
        }

        if (isForkBomb(commandLine)) {
            return ValidationResult.deny("bash: execution blocked: fork bomb pattern detected for system stability");
        }

        // Validate command substitutions: $(cmd), `cmd`, <(cmd), >(cmd)
        List<String> substitutions = extractCommandSubstitutions(commandLine);
        for (String sub : substitutions) {
            ValidationResult subResult = validateCommand(sub, projectRoot, currentDir);
            if (!subResult.isAllowed()) {
                return subResult;
            }
        }

        List<String> subCommands = splitCompoundCommands(commandLine);
        for (String subCommand : subCommands) {
            ValidationResult result = validateSingleCommand(subCommand, projectRoot, currentDir);
            if (!result.isAllowed()) {
                return result;
            }
        }

        return ValidationResult.allow();
    }

    /**
     * Checks if a target file or directory is inside or equal to the {@code .vcode} directory.
     */
    public static boolean isVCodePath(@NonNull File target, @NonNull File projectRoot) {
        try {
            File vcodeDir = new File(projectRoot, VCODE_DIR_NAME);
            String vcodeCanonical = vcodeDir.getCanonicalPath();
            String targetCanonical = target.getCanonicalPath();
            return isSameOrChildOf(targetCanonical, vcodeCanonical);
        } catch (IOException e) {
            String vcodeAbsolute = new File(projectRoot, VCODE_DIR_NAME).getAbsolutePath();
            String targetAbsolute = target.getAbsolutePath();
            return isSameOrChildOf(targetAbsolute, vcodeAbsolute);
        }
    }

    /**
     * Checks if a target file or directory is within the project workspace scope.
     */
    public static boolean isWithinScope(@Nullable File target, @Nullable File projectRoot) {
        if (target == null || projectRoot == null) return false;
        try {
            String rootCanonical = projectRoot.getCanonicalPath();
            String targetCanonical = target.getCanonicalPath();
            return isSameOrChildOf(targetCanonical, rootCanonical);
        } catch (IOException e) {
            String rootAbsolute = projectRoot.getAbsolutePath();
            String targetAbsolute = target.getAbsolutePath();
            return isSameOrChildOf(targetAbsolute, rootAbsolute);
        }
    }

    private static ValidationResult validateSingleCommand(
            String cmdString,
            File projectRoot,
            File currentDir
    ) {
        String trimmed = cmdString.trim();
        if (trimmed.isEmpty()) {
            return ValidationResult.allow();
        }

        // Check output and input redirection targets (>, >>, >|, <, etc.)
        ValidationResult redirectResult = validateRedirections(trimmed, projectRoot, currentDir);
        if (!redirectResult.isAllowed()) {
            return redirectResult;
        }

        List<String> tokens = tokenize(trimmed);
        if (tokens.isEmpty()) {
            return ValidationResult.allow();
        }

        // Strip execution wrappers (sudo, toybox, env, etc.)
        int cmdIndex = 0;
        while (cmdIndex < tokens.size()) {
            String token = stripPath(tokens.get(cmdIndex));
            if (COMMAND_WRAPPERS.contains(token)) {
                cmdIndex++;
            } else if ("env".equals(token)) {
                cmdIndex++;
                while (cmdIndex < tokens.size()) {
                    String tok = tokens.get(cmdIndex);
                    if ("-u".equals(tok) || "--unset".equals(tok)) {
                        cmdIndex += 2;
                    } else if (tok.startsWith("-")) {
                        cmdIndex++;
                    } else if (tok.contains("=")) {
                        cmdIndex++;
                    } else {
                        break;
                    }
                }
            } else if ("nice".equals(token) || "ionice".equals(token) || "stdbuf".equals(token)) {
                cmdIndex++;
                while (cmdIndex < tokens.size() && tokens.get(cmdIndex).startsWith("-")) {
                    String opt = tokens.get(cmdIndex);
                    cmdIndex++;
                    if (("-n".equals(opt) || "-c".equals(opt) || "-i".equals(opt) || "-o".equals(opt) || "-e".equals(opt)) && cmdIndex < tokens.size()) {
                        cmdIndex++;
                    }
                }
            } else if ("timeout".equals(token)) {
                cmdIndex++;
                while (cmdIndex < tokens.size() && tokens.get(cmdIndex).startsWith("-")) {
                    String opt = tokens.get(cmdIndex);
                    cmdIndex++;
                    if (("-s".equals(opt) || "--signal".equals(opt) || "-k".equals(opt) || "--kill-after".equals(opt)) && cmdIndex < tokens.size()) {
                        cmdIndex++;
                    }
                }
                if (cmdIndex < tokens.size() && !tokens.get(cmdIndex).startsWith("-")) {
                    cmdIndex++;
                }
            } else if ("watch".equals(token)) {
                cmdIndex++;
                while (cmdIndex < tokens.size() && tokens.get(cmdIndex).startsWith("-")) {
                    String opt = tokens.get(cmdIndex);
                    cmdIndex++;
                    if (("-n".equals(opt) || "--interval".equals(opt)) && cmdIndex < tokens.size()) {
                        cmdIndex++;
                    }
                }
            } else {
                break;
            }
        }

        if (cmdIndex >= tokens.size()) {
            return ValidationResult.allow();
        }

        String rawCmd = tokens.get(cmdIndex);
        String baseCmd = stripPath(rawCmd);

        // Forbidden system commands (su, reboot, shutdown, mkfs, pm, am, etc.)
        if (FORBIDDEN_SYSTEM_COMMANDS.contains(baseCmd)) {
            return ValidationResult.deny(baseCmd + ": Permission denied: command is restricted for system safety");
        }

        // Direct hardware block device access check across arguments
        for (int i = cmdIndex + 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String pathVal = token;
            int eq = token.indexOf('=');
            if (eq >= 0 && eq + 1 < token.length()) {
                pathVal = token.substring(eq + 1);
            }
            if (isRestrictedDevicePath(pathVal) || isRestrictedDevicePath(token)) {
                return ValidationResult.deny(baseCmd + ": Permission denied: direct access to raw device or kernel path is restricted: " + token);
            }
        }

        // Subshell inspection (sh -c "...", bash -lc "...", sh --command "...")
        if (SUBSHELL_COMMANDS.contains(baseCmd)) {
            for (int i = cmdIndex + 1; i < tokens.size(); i++) {
                String token = tokens.get(i);
                if ((token.startsWith("-") && token.contains("c")) || "--command".equals(token)) {
                    if (i + 1 < tokens.size()) {
                        String subScript = tokens.get(i + 1);
                        return validateCommand(subScript, projectRoot, currentDir);
                    }
                }
            }
        }

        // xargs inspection
        if ("xargs".equals(baseCmd)) {
            return validateXargsCommand(tokens, cmdIndex, projectRoot, currentDir);
        }

        // eval "..." inspection
        if ("eval".equals(baseCmd) && cmdIndex + 1 < tokens.size()) {
            StringBuilder evalCmd = new StringBuilder();
            for (int i = cmdIndex + 1; i < tokens.size(); i++) {
                if (evalCmd.length() > 0) evalCmd.append(" ");
                evalCmd.append(tokens.get(i));
            }
            return validateCommand(evalCmd.toString(), projectRoot, currentDir);
        }

        // cd inspection
        if ("cd".equals(baseCmd)) {
            return validateCdCommand(tokens, cmdIndex, projectRoot, currentDir);
        }

        // Delete commands inspection (rm, rmdir, unlink, shred, etc.)
        if (DELETE_COMMANDS.contains(baseCmd)) {
            return validateDeleteCommand(baseCmd, tokens, cmdIndex, projectRoot, currentDir);
        }

        // mv inspection
        if ("mv".equals(baseCmd)) {
            return validateMvCommand(tokens, cmdIndex, projectRoot, currentDir);
        }

        // find inspection (find ... -delete / -exec rm)
        if ("find".equals(baseCmd)) {
            return validateFindCommand(tokens, cmdIndex, projectRoot, currentDir);
        }

        // git rm / git clean inspection
        if ("git".equals(baseCmd)) {
            return validateGitCommand(tokens, cmdIndex, projectRoot, currentDir);
        }

        // dd command inspection
        if ("dd".equals(baseCmd)) {
            return validateDdCommand(tokens, cmdIndex, projectRoot, currentDir);
        }

        // truncate command inspection
        if ("truncate".equals(baseCmd)) {
            return validateTruncateCommand(tokens, cmdIndex, projectRoot, currentDir);
        }

        // cp / install command inspection
        if ("cp".equals(baseCmd) || "install".equals(baseCmd)) {
            return validateCpCommand(baseCmd, tokens, cmdIndex, projectRoot, currentDir);
        }

        // tee command inspection
        if ("tee".equals(baseCmd)) {
            return validateTeeCommand(tokens, cmdIndex, projectRoot, currentDir);
        }

        // chmod / chown command inspection
        if ("chmod".equals(baseCmd) || "chown".equals(baseCmd)) {
            return validateChmodCommand(baseCmd, tokens, cmdIndex, projectRoot, currentDir);
        }

        // touch command inspection
        if ("touch".equals(baseCmd)) {
            return validateTouchCommand(tokens, cmdIndex, projectRoot, currentDir);
        }

        // mkdir command inspection
        if ("mkdir".equals(baseCmd)) {
            return validateMkdirCommand(tokens, cmdIndex, projectRoot, currentDir);
        }

        // ln command inspection
        if ("ln".equals(baseCmd)) {
            return validateLnCommand(tokens, cmdIndex, projectRoot, currentDir);
        }

        return ValidationResult.allow();
    }

    private static ValidationResult validateCdCommand(
            List<String> tokens,
            int cmdIndex,
            File projectRoot,
            File currentDir
    ) {
        String target = cmdIndex + 1 < tokens.size() ? tokens.get(cmdIndex + 1) : "";
        if (target.isEmpty() || "~".equals(target)) {
            return ValidationResult.allow();
        }

        File targetFile = resolveFile(target, projectRoot, currentDir);
        if (isVCodePath(targetFile, projectRoot)) {
            return ValidationResult.deny("cd: permission denied: .vcode directory is restricted");
        }
        if (!isWithinScope(targetFile, projectRoot)) {
            return ValidationResult.deny("cd: permission denied: directory navigation is restricted to project workspace");
        }
        return ValidationResult.allow();
    }

    private static ValidationResult validateDeleteCommand(
            String baseCmd,
            List<String> tokens,
            int cmdIndex,
            File projectRoot,
            File currentDir
    ) {
        List<String> targets = extractTargetArgs(tokens, cmdIndex);
        if (targets.isEmpty()) {
            return ValidationResult.allow();
        }

        for (String target : targets) {
            ValidationResult result = checkDeleteTarget(baseCmd, target, projectRoot, currentDir);
            if (!result.isAllowed()) {
                return result;
            }
        }

        return ValidationResult.allow();
    }

    private static ValidationResult checkDeleteTarget(
            String baseCmd,
            String targetArg,
            File projectRoot,
            File currentDir
    ) {
        String normalizedTarget = normalizeSeparators(targetArg.trim());
        if (normalizedTarget.isEmpty()) {
            return ValidationResult.allow();
        }

        // Check if target is or contains a glob wildcard (*, ?, [)
        if (containsWildcard(normalizedTarget)) {
            return checkWildcardDeleteTarget(baseCmd, normalizedTarget, projectRoot, currentDir);
        }

        File targetFile = resolveFile(normalizedTarget, projectRoot, currentDir);

        try {
            String rootCanonical = projectRoot.getCanonicalPath();
            String vcodeCanonical = new File(projectRoot, VCODE_DIR_NAME).getCanonicalPath();
            String targetCanonical = targetFile.getCanonicalPath();

            // 1. Is target .vcode or inside .vcode?
            if (isSameOrChildOf(targetCanonical, vcodeCanonical)) {
                return ValidationResult.deny(baseCmd + ": cannot remove '" + targetArg + "': Permission denied: .vcode is restricted");
            }

            // 2. Is target the project root itself?
            if (targetCanonical.equals(rootCanonical)) {
                return ValidationResult.deny(baseCmd + ": cannot remove '" + targetArg + "': Permission denied: cannot delete project root");
            }

            // 3. Is target outside the project root scope?
            if (!isChildOf(targetCanonical, rootCanonical)) {
                return ValidationResult.deny(baseCmd + ": cannot remove '" + targetArg + "': Permission denied: target is outside workspace scope");
            }
        } catch (IOException e) {
            // Fallback to absolute paths if canonicalization fails
            String rootAbsolute = projectRoot.getAbsolutePath();
            String vcodeAbsolute = new File(projectRoot, VCODE_DIR_NAME).getAbsolutePath();
            String targetAbsolute = targetFile.getAbsolutePath();

            if (isSameOrChildOf(targetAbsolute, vcodeAbsolute)) {
                return ValidationResult.deny(baseCmd + ": cannot remove '" + targetArg + "': Permission denied: .vcode is restricted");
            }
            if (targetAbsolute.equals(rootAbsolute)) {
                return ValidationResult.deny(baseCmd + ": cannot remove '" + targetArg + "': Permission denied: cannot delete project root");
            }
            if (!isChildOf(targetAbsolute, rootAbsolute)) {
                return ValidationResult.deny(baseCmd + ": cannot remove '" + targetArg + "': Permission denied: target is outside workspace scope");
            }
        }

        return ValidationResult.allow();
    }

    private static ValidationResult checkWildcardDeleteTarget(
            String baseCmd,
            String wildcardTarget,
            File projectRoot,
            File currentDir
    ) {
        // Deconstruct into directory portion and filename/pattern portion
        String dirPart = "";
        String patternPart = wildcardTarget;

        int lastSlash = wildcardTarget.lastIndexOf('/');
        if (lastSlash >= 0) {
            dirPart = wildcardTarget.substring(0, lastSlash);
            patternPart = wildcardTarget.substring(lastSlash + 1);
            if (dirPart.isEmpty()) {
                dirPart = "/";
            }
        }

        File targetDir = dirPart.isEmpty() ? currentDir : resolveFile(dirPart, projectRoot, currentDir);

        try {
            String rootCanonical = projectRoot.getCanonicalPath();
            String vcodeCanonical = new File(projectRoot, VCODE_DIR_NAME).getCanonicalPath();
            String dirCanonical = targetDir.getCanonicalPath();

            // If the target directory is .vcode or inside .vcode, any deletion is blocked
            if (isSameOrChildOf(dirCanonical, vcodeCanonical)) {
                return ValidationResult.deny(baseCmd + ": cannot remove '" + wildcardTarget + "': Permission denied: .vcode is restricted");
            }

            // If the target directory is outside the workspace scope, any deletion is blocked
            if (!isSameOrChildOf(dirCanonical, rootCanonical)) {
                return ValidationResult.deny(baseCmd + ": cannot remove '" + wildcardTarget + "': Permission denied: target is outside workspace scope");
            }

            // If directory is the project root itself, test pattern against .vcode
            if (dirCanonical.equals(rootCanonical)) {
                if (patternPart.contains("vcode") || patternPart.equals(".*") || globMatches(patternPart, VCODE_DIR_NAME)) {
                    return ValidationResult.deny(baseCmd + ": cannot remove '" + wildcardTarget + "': Permission denied: .vcode is restricted");
                }
                if (patternPart.equals(".") || patternPart.equals("..")) {
                    return ValidationResult.deny(baseCmd + ": cannot remove '" + wildcardTarget + "': Permission denied: cannot delete project root");
                }
            }
        } catch (IOException e) {
            return ValidationResult.deny(baseCmd + ": permission denied: cannot resolve path: " + wildcardTarget);
        }

        return ValidationResult.allow();
    }

    private static ValidationResult validateMvCommand(
            List<String> tokens,
            int cmdIndex,
            File projectRoot,
            File currentDir
    ) {
        String dest = null;
        for (int i = cmdIndex + 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.startsWith("-t") && token.length() > 2) {
                dest = token.substring(2);
                break;
            } else if ("-t".equals(token) && i + 1 < tokens.size()) {
                dest = tokens.get(i + 1);
                break;
            } else if (token.startsWith("--target-directory=")) {
                dest = token.substring("--target-directory=".length());
                break;
            }
        }

        List<String> targets = extractTargetArgs(tokens, cmdIndex);
        if (dest != null) {
            for (String src : targets) {
                File srcFile = resolveFile(src, projectRoot, currentDir);
                if (isVCodePath(srcFile, projectRoot)) {
                    return ValidationResult.deny("mv: cannot move '" + src + "': Permission denied: .vcode is restricted");
                }
                if (!isWithinScope(srcFile, projectRoot)) {
                    return ValidationResult.deny("mv: cannot move '" + src + "': Permission denied: source is outside workspace scope");
                }
                if (isProjectRoot(srcFile, projectRoot)) {
                    return ValidationResult.deny("mv: cannot move project root: Permission denied");
                }
            }
            File destFile = resolveFile(dest, projectRoot, currentDir);
            if (isVCodePath(destFile, projectRoot)) {
                return ValidationResult.deny("mv: cannot move to '" + dest + "': Permission denied: .vcode is restricted");
            }
            if (!isWithinScope(destFile, projectRoot)) {
                return ValidationResult.deny("mv: cannot move to '" + dest + "': Permission denied: destination is outside workspace scope");
            }
            return ValidationResult.allow();
        }

        if (targets.size() < 2) {
            return ValidationResult.allow();
        }

        // Sources: all elements except last
        for (int i = 0; i < targets.size() - 1; i++) {
            String src = targets.get(i);
            File srcFile = resolveFile(src, projectRoot, currentDir);

            if (isVCodePath(srcFile, projectRoot)) {
                return ValidationResult.deny("mv: cannot move '" + src + "': Permission denied: .vcode is restricted");
            }
            if (!isWithinScope(srcFile, projectRoot)) {
                return ValidationResult.deny("mv: cannot move '" + src + "': Permission denied: source is outside workspace scope");
            }
            if (isProjectRoot(srcFile, projectRoot)) {
                return ValidationResult.deny("mv: cannot move project root: Permission denied");
            }
        }

        // Destination: last element
        dest = targets.get(targets.size() - 1);
        File destFile = resolveFile(dest, projectRoot, currentDir);

        if (isVCodePath(destFile, projectRoot)) {
            return ValidationResult.deny("mv: cannot move to '" + dest + "': Permission denied: .vcode is restricted");
        }
        if (!isWithinScope(destFile, projectRoot)) {
            return ValidationResult.deny("mv: cannot move to '" + dest + "': Permission denied: destination is outside workspace scope");
        }

        return ValidationResult.allow();
    }

    private static ValidationResult validateFindCommand(
            List<String> tokens,
            int cmdIndex,
            File projectRoot,
            File currentDir
    ) {
        boolean hasDelete = false;
        List<String> searchPaths = new ArrayList<>();

        for (int i = cmdIndex + 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if ("-delete".equals(token)) {
                hasDelete = true;
            } else if ("-exec".equals(token) || "-execdir".equals(token) || "-ok".equals(token) || "-okdir".equals(token)) {
                List<String> subTokens = new ArrayList<>();
                for (int j = i + 1; j < tokens.size(); j++) {
                    String subTok = tokens.get(j);
                    if (";".equals(subTok) || "\\;".equals(subTok) || "+".equals(subTok)) {
                        break;
                    }
                    if (!"{}".equals(subTok)) {
                        subTokens.add(subTok);
                    }
                }
                if (!subTokens.isEmpty()) {
                    String joined = String.join(" ", subTokens);
                    ValidationResult subResult = validateCommand(joined, projectRoot, currentDir);
                    if (!subResult.isAllowed()) {
                        return subResult;
                    }
                }
            } else if (!token.startsWith("-") && !token.equals("(") && !token.equals(")") && !token.equals("!")) {
                if (!hasDelete && searchPaths.isEmpty()) {
                    searchPaths.add(token);
                }
            }
        }

        if (!hasDelete) {
            return ValidationResult.allow();
        }

        if (searchPaths.isEmpty()) {
            searchPaths.add(".");
        }

        for (String searchPath : searchPaths) {
            File pathFile = resolveFile(searchPath, projectRoot, currentDir);
            if (isVCodePath(pathFile, projectRoot)) {
                return ValidationResult.deny("find: -delete on .vcode is restricted: Permission denied");
            }
            if (!isWithinScope(pathFile, projectRoot)) {
                return ValidationResult.deny("find: -delete target is outside workspace scope: Permission denied");
            }
            if (isProjectRoot(pathFile, projectRoot)) {
                return ValidationResult.deny("find: -delete on project root is restricted to protect .vcode");
            }
        }

        return ValidationResult.allow();
    }

    private static ValidationResult validateGitCommand(
            List<String> tokens,
            int cmdIndex,
            File projectRoot,
            File currentDir
    ) {
        if (cmdIndex + 1 >= tokens.size()) {
            return ValidationResult.allow();
        }

        String subCmd = tokens.get(cmdIndex + 1);
        if ("rm".equals(subCmd)) {
            List<String> targets = extractTargetArgs(tokens, cmdIndex + 1);
            for (String target : targets) {
                File targetFile = resolveFile(target, projectRoot, currentDir);
                if (isVCodePath(targetFile, projectRoot)) {
                    return ValidationResult.deny("git rm: cannot remove '" + target + "': Permission denied: .vcode is restricted");
                }
                if (!isWithinScope(targetFile, projectRoot)) {
                    return ValidationResult.deny("git rm: cannot remove '" + target + "': Permission denied: target is outside workspace scope");
                }
            }
        } else if ("clean".equals(subCmd)) {
            for (int i = cmdIndex + 2; i < tokens.size(); i++) {
                String token = tokens.get(i);
                if (token.startsWith("-") && (token.contains("x") || token.contains("X"))) {
                    return ValidationResult.deny("git clean: removing ignored files (.vcode) is restricted");
                }
            }
        }

        return ValidationResult.allow();
    }

    private static ValidationResult validateRedirections(
            String cmdString,
            File projectRoot,
            File currentDir
    ) {
        int len = cmdString.length();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;

        for (int i = 0; i < len; i++) {
            char c = cmdString.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && !inSingleQuote) {
                escaped = true;
                continue;
            }
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote && (c == '>' || c == '<')) {
                // If it's << (here-doc) or <<< (here-string), skip
                if (c == '<' && i + 1 < len && cmdString.charAt(i + 1) == '<') {
                    i++;
                    if (i + 1 < len && cmdString.charAt(i + 1) == '<') {
                        i++;
                    }
                    continue;
                }

                int start = i + 1;
                // If >> (append) or >| (clobber) or >& (redirect fd)
                if (start < len && (cmdString.charAt(start) == '>' || cmdString.charAt(start) == '|' || cmdString.charAt(start) == '&')) {
                    start++;
                }
                // Skip whitespace after redirection operator
                while (start < len && Character.isWhitespace(cmdString.charAt(start))) {
                    start++;
                }
                // Extract target token
                int end = start;
                while (end < len && !Character.isWhitespace(cmdString.charAt(end)) && cmdString.charAt(end) != ';' && cmdString.charAt(end) != '&' && cmdString.charAt(end) != '|') {
                    end++;
                }
                if (start < end) {
                    String target = unquote(cmdString.substring(start, end));
                    // Skip fd numbers like >&1 or >&2
                    if (target.matches("^&?\\d+$")) {
                        continue;
                    }
                    if (isRestrictedDevicePath(target)) {
                        return ValidationResult.deny("bash: " + target + ": Permission denied: raw device or kernel path access is restricted");
                    }
                    File targetFile = resolveFile(target, projectRoot, currentDir);
                    if (isVCodePath(targetFile, projectRoot)) {
                        return ValidationResult.deny("bash: " + target + ": Permission denied: .vcode is restricted");
                    }
                    if (!isWithinScope(targetFile, projectRoot)) {
                        return ValidationResult.deny("bash: " + target + ": Permission denied: target is outside workspace scope");
                    }
                }
            }
        }

        return ValidationResult.allow();
    }

    private static ValidationResult validateXargsCommand(
            List<String> tokens,
            int cmdIndex,
            File projectRoot,
            File currentDir
    ) {
        int i = cmdIndex + 1;
        while (i < tokens.size()) {
            String token = tokens.get(i);
            if ("--".equals(token)) {
                i++;
                break;
            }
            if ("-I".equals(token) || "-n".equals(token) || "-L".equals(token) ||
                "-s".equals(token) || "-d".equals(token) || "-E".equals(token) ||
                "-a".equals(token) || "-P".equals(token)) {
                i += 2; // Skip option and its argument
            } else if (token.startsWith("-")) {
                i++; // Skip flag
            } else {
                break; // Found the sub-command!
            }
        }

        if (i < tokens.size()) {
            StringBuilder subCmd = new StringBuilder();
            for (int k = i; k < tokens.size(); k++) {
                if (subCmd.length() > 0) subCmd.append(" ");
                subCmd.append(tokens.get(k));
            }
            return validateCommand(subCmd.toString(), projectRoot, currentDir);
        }

        return ValidationResult.allow();
    }

    public static boolean isForkBomb(@NonNull String commandLine) {
        if (FORK_BOMB_EXACT.matcher(commandLine).find()) {
            return true;
        }
        if (FORK_BOMB_GENERIC.matcher(commandLine).find()) {
            return true;
        }
        String stripped = commandLine.replaceAll("\\s+", "");
        if (stripped.contains(":(){:|:&};:") || stripped.contains(":(){:|:&}:") || stripped.contains(".(){.|.&};.")) {
            return true;
        }
        return false;
    }

    public static boolean isRestrictedDevicePath(@Nullable String pathArg) {
        if (pathArg == null) return false;
        String p = unquote(pathArg.trim());
        p = normalizeSeparators(p);
        for (String prefix : RESTRICTED_DEVICE_PREFIXES) {
            if (p.equals(prefix) || p.startsWith(prefix + "/") || p.startsWith(prefix)) {
                return true;
            }
        }
        // Protect against path traversal (e.g. ../../dev/block, /workspace/../../dev/mem)
        if (p.contains("dev/block") || p.contains("dev/mem") || p.contains("dev/kmem")
                || p.contains("dev/input") || p.contains("dev/graphics") || p.contains("dev/binder")
                || p.contains("proc/kmsg") || p.contains("proc/sysrq-trigger")
                || p.contains("sys/power") || p.contains("sys/fs/selinux")) {
            return true;
        }
        return false;
    }

    private static ValidationResult validateDdCommand(
            List<String> tokens,
            int cmdIndex,
            File projectRoot,
            File currentDir
    ) {
        for (int i = cmdIndex + 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.startsWith("of=")) {
                String dest = token.substring(3).trim();
                if (isRestrictedDevicePath(dest)) {
                    return ValidationResult.deny("dd: writing to '" + dest + "' is restricted for system safety");
                }
                File destFile = resolveFile(dest, projectRoot, currentDir);
                if (isVCodePath(destFile, projectRoot)) {
                    return ValidationResult.deny("dd: cannot overwrite '" + dest + "': Permission denied: .vcode is restricted");
                }
                if (!isWithinScope(destFile, projectRoot)) {
                    return ValidationResult.deny("dd: cannot write to '" + dest + "': Permission denied: target is outside workspace scope");
                }
                if (isProjectRoot(destFile, projectRoot)) {
                    return ValidationResult.deny("dd: cannot overwrite project root directory: Permission denied");
                }
            }
        }
        return ValidationResult.allow();
    }

    private static ValidationResult validateTruncateCommand(
            List<String> tokens,
            int cmdIndex,
            File projectRoot,
            File currentDir
    ) {
        List<String> targets = extractTargetArgs(tokens, cmdIndex);
        for (String target : targets) {
            if (isRestrictedDevicePath(target)) {
                return ValidationResult.deny("truncate: cannot truncate '" + target + "': Permission denied: raw device access is restricted");
            }
            File targetFile = resolveFile(target, projectRoot, currentDir);
            if (isVCodePath(targetFile, projectRoot)) {
                return ValidationResult.deny("truncate: cannot truncate '" + target + "': Permission denied: .vcode is restricted");
            }
            if (!isWithinScope(targetFile, projectRoot)) {
                return ValidationResult.deny("truncate: cannot truncate '" + target + "': Permission denied: target is outside workspace scope");
            }
            if (isProjectRoot(targetFile, projectRoot)) {
                return ValidationResult.deny("truncate: cannot truncate project root: Permission denied");
            }
        }
        return ValidationResult.allow();
    }

    private static ValidationResult validateCpCommand(
            String baseCmd,
            List<String> tokens,
            int cmdIndex,
            File projectRoot,
            File currentDir
    ) {
        String dest = null;
        for (int i = cmdIndex + 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.startsWith("-t") && token.length() > 2) {
                dest = token.substring(2);
                break;
            } else if ("-t".equals(token) && i + 1 < tokens.size()) {
                dest = tokens.get(i + 1);
                break;
            } else if (token.startsWith("--target-directory=")) {
                dest = token.substring("--target-directory=".length());
                break;
            }
        }

        if (dest == null) {
            List<String> targets = extractTargetArgs(tokens, cmdIndex);
            if (targets.size() >= 2) {
                dest = targets.get(targets.size() - 1);
            }
        }

        if (dest != null) {
            if (isRestrictedDevicePath(dest)) {
                return ValidationResult.deny(baseCmd + ": cannot write to '" + dest + "': Permission denied: raw device access is restricted");
            }
            File destFile = resolveFile(dest, projectRoot, currentDir);
            if (isVCodePath(destFile, projectRoot)) {
                return ValidationResult.deny(baseCmd + ": cannot copy to '" + dest + "': Permission denied: .vcode is restricted");
            }
            if (!isWithinScope(destFile, projectRoot)) {
                return ValidationResult.deny(baseCmd + ": cannot copy to '" + dest + "': Permission denied: destination is outside workspace scope");
            }
        }

        return ValidationResult.allow();
    }

    private static ValidationResult validateTeeCommand(
            List<String> tokens,
            int cmdIndex,
            File projectRoot,
            File currentDir
    ) {
        List<String> targets = extractTargetArgs(tokens, cmdIndex);
        for (String target : targets) {
            if (isRestrictedDevicePath(target)) {
                return ValidationResult.deny("tee: cannot write to '" + target + "': Permission denied: raw device access is restricted");
            }
            File targetFile = resolveFile(target, projectRoot, currentDir);
            if (isVCodePath(targetFile, projectRoot)) {
                return ValidationResult.deny("tee: cannot write to '" + target + "': Permission denied: .vcode is restricted");
            }
            if (!isWithinScope(targetFile, projectRoot)) {
                return ValidationResult.deny("tee: cannot write to '" + target + "': Permission denied: target is outside workspace scope");
            }
            if (isProjectRoot(targetFile, projectRoot)) {
                return ValidationResult.deny("tee: cannot overwrite project root directory: Permission denied");
            }
        }
        return ValidationResult.allow();
    }

    private static ValidationResult validateChmodCommand(
            String baseCmd,
            List<String> tokens,
            int cmdIndex,
            File projectRoot,
            File currentDir
    ) {
        List<String> targets = extractTargetArgs(tokens, cmdIndex);
        if (targets.size() < 2) {
            return ValidationResult.allow();
        }

        // The first non-option argument is the mode or owner specification; subsequent are paths
        for (int i = 1; i < targets.size(); i++) {
            String target = targets.get(i);
            if (isRestrictedDevicePath(target)) {
                return ValidationResult.deny(baseCmd + ": cannot access '" + target + "': Permission denied: raw device access is restricted");
            }
            File targetFile = resolveFile(target, projectRoot, currentDir);
            if (isVCodePath(targetFile, projectRoot)) {
                return ValidationResult.deny(baseCmd + ": changing permissions of '" + target + "' is restricted: .vcode is protected");
            }
            if (!isWithinScope(targetFile, projectRoot)) {
                return ValidationResult.deny(baseCmd + ": changing permissions of '" + target + "' is restricted: target is outside workspace scope");
            }
        }
        return ValidationResult.allow();
    }

    private static ValidationResult validateTouchCommand(
            List<String> tokens,
            int cmdIndex,
            File projectRoot,
            File currentDir
    ) {
        List<String> targets = extractTargetArgs(tokens, cmdIndex);
        for (String target : targets) {
            if (isRestrictedDevicePath(target)) {
                return ValidationResult.deny("touch: cannot touch '" + target + "': Permission denied: raw device access is restricted");
            }
            File targetFile = resolveFile(target, projectRoot, currentDir);
            if (isVCodePath(targetFile, projectRoot)) {
                return ValidationResult.deny("touch: cannot touch '" + target + "': Permission denied: .vcode is restricted");
            }
            if (!isWithinScope(targetFile, projectRoot)) {
                return ValidationResult.deny("touch: cannot touch '" + target + "': Permission denied: target is outside workspace scope");
            }
        }
        return ValidationResult.allow();
    }

    private static ValidationResult validateMkdirCommand(
            List<String> tokens,
            int cmdIndex,
            File projectRoot,
            File currentDir
    ) {
        List<String> targets = extractTargetArgs(tokens, cmdIndex);
        for (String target : targets) {
            if (isRestrictedDevicePath(target)) {
                return ValidationResult.deny("mkdir: cannot create directory '" + target + "': Permission denied: raw device access is restricted");
            }
            File targetFile = resolveFile(target, projectRoot, currentDir);
            if (isVCodePath(targetFile, projectRoot)) {
                return ValidationResult.deny("mkdir: cannot create directory '" + target + "': Permission denied: .vcode is restricted");
            }
            if (!isWithinScope(targetFile, projectRoot)) {
                return ValidationResult.deny("mkdir: cannot create directory '" + target + "': Permission denied: target is outside workspace scope");
            }
        }
        return ValidationResult.allow();
    }

    private static ValidationResult validateLnCommand(
            List<String> tokens,
            int cmdIndex,
            File projectRoot,
            File currentDir
    ) {
        List<String> targets = extractTargetArgs(tokens, cmdIndex);
        if (targets.isEmpty()) {
            return ValidationResult.allow();
        }
        for (String target : targets) {
            if (isRestrictedDevicePath(target)) {
                return ValidationResult.deny("ln: cannot link '" + target + "': Permission denied: raw device access is restricted");
            }
        }
        String dest = targets.get(targets.size() - 1);
        File destFile = resolveFile(dest, projectRoot, currentDir);
        if (isVCodePath(destFile, projectRoot)) {
            return ValidationResult.deny("ln: cannot create link '" + dest + "': Permission denied: .vcode is restricted");
        }
        if (!isWithinScope(destFile, projectRoot)) {
            return ValidationResult.deny("ln: cannot create link '" + dest + "': Permission denied: destination is outside workspace scope");
        }
        return ValidationResult.allow();
    }

    @NonNull
    public static File resolveFile(@NonNull String pathArg, @NonNull File projectRoot, @NonNull File currentDir) {
        String path = pathArg.trim();
        path = unquote(path);
        path = normalizeSeparators(path);

        String rootPath = normalizeSeparators(projectRoot.getAbsolutePath());
        String curPath = normalizeSeparators(currentDir.getAbsolutePath());

        // Variable & home expansion
        if (path.equals("~") || path.equals("$HOME")) {
            return projectRoot;
        }
        if (path.startsWith("~/") || path.startsWith("$HOME/")) {
            int prefixLen = path.startsWith("~/") ? 2 : 6;
            String relative = path.substring(prefixLen);
            return new File(projectRoot, relative.replace('/', File.separatorChar));
        }
        if (path.equals("$PWD") || path.equals(".")) {
            return currentDir;
        }
        if (path.startsWith("$PWD/")) {
            String relative = path.substring(5);
            return new File(currentDir, relative.replace('/', File.separatorChar));
        }

        File file;
        if (path.startsWith("/")) {
            file = new File(path);
        } else {
            file = new File(currentDir, path.replace('/', File.separatorChar));
        }

        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }

    private static boolean isProjectRoot(File file, File projectRoot) {
        try {
            return file.getCanonicalPath().equals(projectRoot.getCanonicalPath());
        } catch (IOException e) {
            return file.getAbsolutePath().equals(projectRoot.getAbsolutePath());
        }
    }

    private static boolean isSameOrChildOf(String path, String parent) {
        if (path.equals(parent)) return true;
        boolean isWindows = File.separatorChar == '\\';
        if (isWindows && path.equalsIgnoreCase(parent)) return true;
        String prefix = parent.endsWith(File.separator) ? parent : parent + File.separator;
        if (isWindows) {
            return path.toLowerCase(java.util.Locale.ROOT).startsWith(prefix.toLowerCase(java.util.Locale.ROOT));
        }
        return path.startsWith(prefix);
    }

    private static boolean isChildOf(String path, String parent) {
        boolean isWindows = File.separatorChar == '\\';
        String prefix = parent.endsWith(File.separator) ? parent : parent + File.separator;
        if (isWindows) {
            return path.toLowerCase(java.util.Locale.ROOT).startsWith(prefix.toLowerCase(java.util.Locale.ROOT));
        }
        return path.startsWith(prefix);
    }

    private static String stripPath(String executable) {
        int slash = Math.max(executable.lastIndexOf('/'), executable.lastIndexOf('\\'));
        return slash >= 0 ? executable.substring(slash + 1) : executable;
    }

    private static boolean containsWildcard(String str) {
        return str.indexOf('*') >= 0 || str.indexOf('?') >= 0 || str.indexOf('[') >= 0;
    }

    public static boolean globMatches(String pattern, String text) {
        if (pattern == null || text == null) return false;
        if (pattern.equals("*")) {
            return !text.startsWith(".");
        }
        if (text.startsWith(".") && !pattern.startsWith(".")) {
            return false;
        }

        String regex = globToRegex(pattern);
        try {
            return text.matches(regex);
        } catch (Exception e) {
            return false;
        }
    }

    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        boolean inBracket = false;
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*' && !inBracket) {
                sb.append(".*");
            } else if (c == '?' && !inBracket) {
                sb.append(".");
            } else if (c == '[' && !inBracket) {
                inBracket = true;
                sb.append("[");
                if (i + 1 < glob.length() && (glob.charAt(i + 1) == '!' || glob.charAt(i + 1) == '^')) {
                    sb.append("^");
                    i++;
                }
            } else if (c == ']' && inBracket) {
                inBracket = false;
                sb.append("]");
            } else if ("\\.[]{}()+^$|".indexOf(c) != -1) {
                sb.append("\\").append(c);
            } else {
                sb.append(c);
            }
        }
        sb.append("$");
        return sb.toString();
    }

    private static List<String> extractTargetArgs(List<String> tokens, int startIndex) {
        List<String> targets = new ArrayList<>();
        boolean endOfOptions = false;

        for (int i = startIndex + 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if ("--".equals(token)) {
                endOfOptions = true;
                continue;
            }
            if (!endOfOptions && token.startsWith("-")) {
                continue;
            }
            targets.add(token);
        }
        return targets;
    }

    @NonNull
    public static List<String> splitCompoundCommands(@NonNull String commandLine) {
        List<String> commands = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int len = commandLine.length();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;

        for (int i = 0; i < len; i++) {
            char c = commandLine.charAt(i);

            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\' && !inSingleQuote) {
                escaped = true;
                current.append(c);
                continue;
            }
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                current.append(c);
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                current.append(c);
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote) {
                if (c == ';' || c == '\n' || c == '\r') {
                    if (current.length() > 0) {
                        commands.add(current.toString().trim());
                        current.setLength(0);
                    }
                    continue;
                }
                if (c == '&') {
                    if (i + 1 < len && commandLine.charAt(i + 1) == '&') {
                        i++;
                        if (current.length() > 0) {
                            commands.add(current.toString().trim());
                            current.setLength(0);
                        }
                        continue;
                    }
                    // If & is followed by >, like &> or &>>
                    if (i + 1 < len && commandLine.charAt(i + 1) == '>') {
                        current.append(c);
                        continue;
                    }
                    // If & is preceded by >, like >& (fd redirection)
                    if (i > 0 && commandLine.charAt(i - 1) == '>') {
                        current.append(c);
                        continue;
                    }
                    // Single & is background separator
                    if (current.length() > 0) {
                        commands.add(current.toString().trim());
                        current.setLength(0);
                    }
                    continue;
                }
                if (c == '|') {
                    if (i + 1 < len && (commandLine.charAt(i + 1) == '|' || commandLine.charAt(i + 1) == '&')) {
                        i++;
                        if (current.length() > 0) {
                            commands.add(current.toString().trim());
                            current.setLength(0);
                        }
                        continue;
                    }
                    // If | is preceded by >, like >| (clobber redirection), do not treat as pipe separator
                    if (i > 0 && commandLine.charAt(i - 1) == '>') {
                        current.append(c);
                        continue;
                    }
                    if (current.length() > 0) {
                        commands.add(current.toString().trim());
                        current.setLength(0);
                    }
                    continue;
                }
            }

            current.append(c);
        }

        if (current.length() > 0) {
            String rem = current.toString().trim();
            if (!rem.isEmpty()) {
                commands.add(rem);
            }
        }

        return commands;
    }

    @NonNull
    public static List<String> tokenize(@NonNull String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int len = command.length();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;

        for (int i = 0; i < len; i++) {
            char c = command.charAt(i);

            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\' && !inSingleQuote) {
                escaped = true;
                continue;
            }
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }

            if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    private static String unquote(String str) {
        if (str == null) return "";
        String s = str.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            if (s.length() >= 2) {
                s = s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    private static String normalizeSeparators(String path) {
        if (path == null) return "";
        return path.replace('\\', '/');
    }

    @NonNull
    public static List<String> extractCommandSubstitutions(@NonNull String commandLine) {
        List<String> substitutions = new ArrayList<>();
        int len = commandLine.length();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;

        for (int i = 0; i < len; i++) {
            char c = commandLine.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && !inSingleQuote) {
                escaped = true;
                continue;
            }
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }

            if (inSingleQuote) {
                continue;
            }

            // Check for $(cmd) or <(cmd) or >(cmd)
            if ((c == '$' || c == '<' || c == '>') && i + 1 < len && commandLine.charAt(i + 1) == '(') {
                int start = i + 2;
                int depth = 1;
                int j = start;
                boolean subInSingleQuote = false;
                boolean subInDoubleQuote = false;
                boolean subEscaped = false;

                while (j < len && depth > 0) {
                    char sc = commandLine.charAt(j);
                    if (subEscaped) {
                        subEscaped = false;
                        j++;
                        continue;
                    }
                    if (sc == '\\' && !subInSingleQuote) {
                        subEscaped = true;
                        j++;
                        continue;
                    }
                    if (sc == '\'' && !subInDoubleQuote) {
                        subInSingleQuote = !subInSingleQuote;
                        j++;
                        continue;
                    }
                    if (sc == '"' && !subInSingleQuote) {
                        subInDoubleQuote = !subInDoubleQuote;
                        j++;
                        continue;
                    }
                    if (!subInSingleQuote && !subInDoubleQuote) {
                        if (sc == '(') {
                            depth++;
                        } else if (sc == ')') {
                            depth--;
                            if (depth == 0) {
                                break;
                            }
                        }
                    }
                    j++;
                }

                if (depth == 0 && start < j) {
                    String inner = commandLine.substring(start, j).trim();
                    if (!inner.isEmpty()) {
                        substitutions.add(inner);
                    }
                    i = j;
                }
                continue;
            }

            // Check for `cmd` (backticks)
            if (c == '`') {
                int start = i + 1;
                int j = start;
                boolean btEscaped = false;
                while (j < len) {
                    char bc = commandLine.charAt(j);
                    if (btEscaped) {
                        btEscaped = false;
                        j++;
                        continue;
                    }
                    if (bc == '\\') {
                        btEscaped = true;
                        j++;
                        continue;
                    }
                    if (bc == '`') {
                        break;
                    }
                    j++;
                }

                if (j < len && start < j) {
                    String inner = commandLine.substring(start, j).trim();
                    if (!inner.isEmpty()) {
                        substitutions.add(inner);
                    }
                    i = j;
                }
            }
        }

        return substitutions;
    }
}
