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

/**
 * Security policy for terminal command execution in VCode.
 * <p>
 * Enforces strict workspace boundaries:
 * 1. Restricts deletion or destruction of the {@code .vcode} directory and its contents.
 * 2. Restricts deletion of files or directories outside the active project workspace scope.
 * 3. Restricts deletion of the project root directory itself.
 * 4. Restricts redirection and moves that target {@code .vcode} or out-of-scope paths.
 */
public final class TerminalSecurityPolicy {

    private static final String VCODE_DIR_NAME = ".vcode";

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
            "ash",
            "su"
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
    public static boolean isWithinScope(@NonNull File target, @NonNull File projectRoot) {
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

        // Check output redirection targets (> or >>)
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
                // Skip KEY=VALUE arguments following env
                while (cmdIndex < tokens.size() && tokens.get(cmdIndex).contains("=") && !tokens.get(cmdIndex).startsWith("-")) {
                    cmdIndex++;
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

        // Subshell inspection (sh -c "...", bash -c "...")
        if (SUBSHELL_COMMANDS.contains(baseCmd)) {
            for (int i = cmdIndex + 1; i < tokens.size(); i++) {
                if ("-c".equals(tokens.get(i)) && i + 1 < tokens.size()) {
                    String subScript = tokens.get(i + 1);
                    return validateCommand(subScript, projectRoot, currentDir);
                }
            }
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
        List<String> targets = extractTargetArgs(tokens, cmdIndex);
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
        String dest = targets.get(targets.size() - 1);
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
            } else if ("-exec".equals(token) || "-execdir".equals(token)) {
                if (i + 1 < tokens.size() && DELETE_COMMANDS.contains(tokens.get(i + 1))) {
                    hasDelete = true;
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

            if (!inSingleQuote && !inDoubleQuote && c == '>') {
                // Check if next char is also '>' (append redirection)
                int start = i + 1;
                if (start < len && cmdString.charAt(start) == '>') {
                    start++;
                }
                // Skip whitespace after '>'
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
        String prefix = parent.endsWith(File.separator) ? parent : parent + File.separator;
        return path.startsWith(prefix);
    }

    private static boolean isChildOf(String path, String parent) {
        String prefix = parent.endsWith(File.separator) ? parent : parent + File.separator;
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
                    }
                    if (current.length() > 0) {
                        commands.add(current.toString().trim());
                        current.setLength(0);
                    }
                    continue;
                }
                if (c == '|') {
                    if (i + 1 < len && commandLine.charAt(i + 1) == '|') {
                        i++;
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
}
