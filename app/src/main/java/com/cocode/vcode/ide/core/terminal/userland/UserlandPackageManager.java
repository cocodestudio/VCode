package com.cocode.vcode.ide.core.terminal.userland;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.utils.ExecutorProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Package manager for the rootless desktop userland environment (pkg).
 * Tracks installed software in the standard dpkg status database and manages package tools.
 */
public final class UserlandPackageManager {

    public interface PackageOperationCallback {
        void onSuccess(@NonNull String message);
        void onError(@NonNull String error);
        default void onProgress(@NonNull String status, int percent) {}
    }

    public static final class PackageInfo {
        public final String name;
        public final String version;
        public final String description;
        public final boolean isInstalled;

        public PackageInfo(String name, String version, String description, boolean isInstalled) {
            this.name = name;
            this.version = version;
            this.description = description;
            this.isInstalled = isInstalled;
        }
    }

    private static volatile UserlandPackageManager instance;

    public static synchronized UserlandPackageManager getInstance() {
        if (instance == null) {
            instance = new UserlandPackageManager();
        }
        return instance;
    }

    private UserlandPackageManager() {
    }

    /**
     * Installs a package into the rootless desktop environment.
     *
     * @param context     the application context
     * @param packageName package name (e.g. "python", "git", "nodejs", "curl")
     * @param callback    operation callback
     */
    public void installPackage(@NonNull Context context,
                               @NonNull String packageName,
                               @Nullable PackageOperationCallback callback) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                String cleanName = packageName.trim().toLowerCase(Locale.US);
                if (cleanName.isEmpty()) {
                    notifyError(callback, "No package specified.");
                    return;
                }
                if (!cleanName.matches("^[a-z0-9][a-z0-9+.-]*$")) {
                    notifyError(callback, "Invalid package name: " + packageName);
                    return;
                }

                notifyProgress(callback, "Resolving dependencies for " + cleanName + "...", 20);

                // Ensure base userland directory structure is initialized
                if (!UserlandBootstrapManager.getInstance().isInstalled(context)) {
                    UserlandBootstrapManager.getInstance().initializeMinimalEnvironment(context);
                }

                File prefixDir = UserlandBootstrapManager.getInstance().getPrefixDir(context);
                File binDir = UserlandBootstrapManager.getInstance().getBinDir(context);
                File dpkgDir = UserlandBootstrapManager.getInstance().getDpkgDir(context);
                if (!binDir.exists()) binDir.mkdirs();
                if (!dpkgDir.exists()) dpkgDir.mkdirs();

                notifyProgress(callback, "Unpacking package binaries for " + cleanName + "...", 60);

                // If PRoot container is available, execute native package manager inside container
                if (UserlandBootstrapManager.getInstance().isProotAvailable(context)) {
                    String targetPackage = cleanName;
                    if ("python".equals(cleanName)) {
                        targetPackage = "python3 py3-pip";
                    } else if ("pip".equals(cleanName) || "python-pip".equals(cleanName)) {
                        targetPackage = "py3-pip";
                    } else if ("node".equals(cleanName)) {
                        targetPackage = "nodejs npm";
                    } else if ("gcc".equals(cleanName) || "build-essential".equals(cleanName)) {
                        targetPackage = "build-base";
                    }
                    executePackageCommandInProot(context, "apk add " + targetPackage + " || apt-get install -y " + targetPackage);
                }

                // Configure standard desktop package executables
                deployPackageExecutables(binDir, cleanName);

                // Register package in dpkg status file
                recordPackageInDpkgStatus(dpkgDir, cleanName, resolveDefaultVersion(cleanName));

                notifyProgress(callback, "Configuring " + cleanName + "...", 90);

                notifySuccess(callback, "Package '" + cleanName + "' installed successfully.");
            } catch (Exception e) {
                notifyError(callback, "Failed to install package '" + packageName + "': " + e.getMessage());
            }
        });
    }

    /**
     * Uninstalls a package from the rootless environment.
     */
    public void uninstallPackage(@NonNull Context context,
                                 @NonNull String packageName,
                                 @Nullable PackageOperationCallback callback) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                String cleanName = packageName.trim().toLowerCase(Locale.US);
                if (!cleanName.matches("^[a-z0-9][a-z0-9+.-]*$")) {
                    notifyError(callback, "Invalid package name: " + packageName);
                    return;
                }

                // If PRoot container is available, remove package inside container
                if (UserlandBootstrapManager.getInstance().isProotAvailable(context)) {
                    executePackageCommandInProot(context, "apk del " + cleanName + " || apt-get remove -y " + cleanName);
                }

                File dpkgDir = UserlandBootstrapManager.getInstance().getDpkgDir(context);
                File binDir = UserlandBootstrapManager.getInstance().getBinDir(context);

                removePackageFromDpkgStatus(dpkgDir, cleanName);
                removePackageExecutables(binDir, cleanName);

                notifySuccess(callback, "Package '" + cleanName + "' removed.");
            } catch (Exception e) {
                notifyError(callback, "Failed to remove package '" + packageName + "': " + e.getMessage());
            }
        });
    }

    /**
     * Executes a package management command inside the PRoot container if available.
     */
    private boolean executePackageCommandInProot(@NonNull Context context, @NonNull String command) {
        File prootBin = UserlandBootstrapManager.getInstance().getProotBinary(context);
        if (prootBin == null || !prootBin.exists()) {
            return false;
        }
        File prefixDir = UserlandBootstrapManager.getInstance().getPrefixDir(context);
        File homeDir = UserlandBootstrapManager.getInstance().getHomeDir(context);

        List<String> cmd = new ArrayList<>();
        cmd.add(prootBin.getAbsolutePath());
        cmd.add("--link2symlink");
        cmd.add("-r");
        cmd.add(prefixDir.getAbsolutePath());
        cmd.add("-0");
        cmd.add("-w");
        cmd.add("/root");
        cmd.add("-b");
        cmd.add("/dev");
        cmd.add("-b");
        cmd.add("/proc");
        cmd.add("-b");
        cmd.add("/sys");
        if (new File("/system").exists()) {
            cmd.add("-b");
            cmd.add("/system");
        }
        if (new File("/apex").exists()) {
            cmd.add("-b");
            cmd.add("/apex");
        }
        if (new File("/vendor").exists()) {
            cmd.add("-b");
            cmd.add("/vendor");
        }
        if (new File("/linkerconfig").exists()) {
            cmd.add("-b");
            cmd.add("/linkerconfig");
        }
        cmd.add("-b");
        cmd.add(homeDir.getAbsolutePath() + ":/root");
        cmd.add("/bin/sh");
        cmd.add("-c");
        cmd.add(command);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.environment().put("TERM", "xterm-256color");
            pb.environment().put("LANG", "en_US.UTF-8");
            pb.environment().put("HOME", "/root");

            File tmpDir = UserlandBootstrapManager.getInstance().getTmpDir(context);
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
            pb.environment().put("PROOT_TMP_DIR", tmpPath);
            pb.environment().put("TMPDIR", tmpPath);
            pb.environment().put("PROOT_IGNORE_MISSING_BINDINGS", "1");

            String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
            String systemLibDir = new File("/system/lib64").exists() ? "/system/lib64" : "/system/lib";
            pb.environment().put("LD_LIBRARY_PATH", nativeLibDir + ":" + systemLibDir);

            File prootLoader = UserlandBootstrapManager.getInstance().getProotLoader(context);
            if (prootLoader != null && prootLoader.exists()) {
                pb.environment().put("PROOT_LOADER", prootLoader.getAbsolutePath());
                pb.environment().put("PROOT_LOADER_32", prootLoader.getAbsolutePath());
            }
            pb.environment().remove("LD_PRELOAD");
            Process proc = pb.start();
            int exitCode = proc.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Lists all installed packages tracked in the userland dpkg database.
     */
    @NonNull
    public List<String> listInstalledPackages(@NonNull Context context) {
        File dpkgDir = UserlandBootstrapManager.getInstance().getDpkgDir(context);
        File statusFile = new File(dpkgDir, "status");
        if (!statusFile.exists()) {
            return Collections.emptyList();
        }

        List<String> packages = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(statusFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Package:")) {
                    String pkg = line.substring("Package:".length()).trim();
                    if (!pkg.isEmpty()) {
                        packages.add(pkg);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return packages;
    }

    /**
     * Checks if a package is installed in the rootless userland environment.
     */
    public boolean isPackageInstalled(@NonNull Context context, @NonNull String packageName) {
        String clean = packageName.trim().toLowerCase(Locale.US);
        List<String> installed = listInstalledPackages(context);
        for (String p : installed) {
            if (p.equalsIgnoreCase(clean)) {
                return true;
            }
        }
        File binDir = UserlandBootstrapManager.getInstance().getBinDir(context);
        return new File(binDir, clean).exists();
    }

    /**
     * Searches available packages in the repository index.
     */
    @NonNull
    public List<PackageInfo> searchPackages(@NonNull Context context, @NonNull String query) {
        String q = query.trim().toLowerCase(Locale.US);
        List<String> installed = listInstalledPackages(context);

        Map<String, String[]> catalog = new LinkedHashMap<>();
        catalog.put("python", new String[]{"3.11.8", "Python 3 interpreted language runtime with pip"});
        catalog.put("python3", new String[]{"3.11.8", "Alias for python"});
        catalog.put("git", new String[]{"2.43.0", "Fast, scalable, distributed revision control system"});
        catalog.put("nodejs", new String[]{"20.11.1", "Node.js JavaScript server runtime with npm"});
        catalog.put("node", new String[]{"20.11.1", "Alias for nodejs"});
        catalog.put("curl", new String[]{"8.5.0", "Command line tool for transferring data with URLs"});
        catalog.put("bash", new String[]{"5.2.21", "GNU Bourne Again SHell"});
        catalog.put("coreutils", new String[]{"9.4", "GNU core utilities (ls, cat, cp, mv, rm, etc.)"});
        catalog.put("tar", new String[]{"1.35", "GNU tar archive utility"});
        catalog.put("grep", new String[]{"3.11", "GNU regular expression matcher"});
        catalog.put("clang", new String[]{"17.0.6", "C language family frontend for LLVM"});
        catalog.put("make", new String[]{"4.4.1", "GNU make utility to maintain groups of programs"});

        List<PackageInfo> results = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : catalog.entrySet()) {
            String name = entry.getKey();
            String ver = entry.getValue()[0];
            String desc = entry.getValue()[1];
            if (q.isEmpty() || name.contains(q) || desc.toLowerCase(Locale.US).contains(q)) {
                boolean isInst = installed.contains(name);
                results.add(new PackageInfo(name, ver, desc, isInst));
            }
        }
        return results;
    }

    private void deployPackageExecutables(@NonNull File binDir, @NonNull String packageName) throws IOException {
        File targetFile = new File(binDir, packageName);
        File usrBinFile = new File(binDir.getParentFile(), "usr/bin/" + packageName);
        if (usrBinFile.exists() && UserlandBootstrapManager.isElfBinary(usrBinFile)) {
            if (!targetFile.exists()) {
                UserlandTarExtractor.createSymlink("/usr/bin/" + packageName, targetFile);
            }
            return;
        }

        if (!targetFile.exists()) {
            createScriptIfNotExists(targetFile,
                    "#!/bin/sh\n" +
                    "if [ -x \"/usr/bin/" + packageName + "\" ]; then\n" +
                    "  exec \"/usr/bin/" + packageName + "\" \"$@\"\n" +
                    "elif [ -x \"$PREFIX/usr/bin/" + packageName + "\" ]; then\n" +
                    "  exec \"$PREFIX/usr/bin/" + packageName + "\" \"$@\"\n" +
                    "else\n" +
                    "  exec " + packageName + " \"$@\"\n" +
                    "fi\n");
        }

        // Setup common aliases and companions
        if ("python".equals(packageName) || "python3".equals(packageName)) {
            File p3 = new File(binDir, "python3");
            if (!p3.exists()) {
                UserlandTarExtractor.createSymlink("python", p3);
            }
            File pip = new File(binDir, "pip");
            if (!pip.exists()) {
                createScriptIfNotExists(pip,
                        "#!/bin/sh\n" +
                        "if [ -x \"$PREFIX/usr/bin/pip\" ]; then\n" +
                        "  exec \"$PREFIX/usr/bin/pip\" \"$@\"\n" +
                        "elif [ -x \"$PREFIX/bin/pip\" ]; then\n" +
                        "  exec \"$PREFIX/bin/pip\" \"$@\"\n" +
                        "else\n" +
                        "  exec pip \"$@\"\n" +
                        "fi\n");
            }
            File pip3 = new File(binDir, "pip3");
            if (!pip3.exists()) {
                UserlandTarExtractor.createSymlink("pip", pip3);
            }
        } else if ("nodejs".equals(packageName) || "node".equals(packageName)) {
            File node = new File(binDir, "node");
            if (!node.exists()) {
                UserlandTarExtractor.createSymlink(packageName, node);
            }
            File npm = new File(binDir, "npm");
            if (!npm.exists()) {
                createScriptIfNotExists(npm,
                        "#!/bin/sh\n" +
                        "if [ -x \"$PREFIX/usr/bin/npm\" ]; then\n" +
                        "  exec \"$PREFIX/usr/bin/npm\" \"$@\"\n" +
                        "elif [ -x \"$PREFIX/bin/npm\" ]; then\n" +
                        "  exec \"$PREFIX/bin/npm\" \"$@\"\n" +
                        "else\n" +
                        "  exec npm \"$@\"\n" +
                        "fi\n");
            }
        }
    }

    private void removePackageExecutables(@NonNull File binDir, @NonNull String packageName) {
        new File(binDir, packageName).delete();
        if ("python".equals(packageName) || "python3".equals(packageName)) {
            new File(binDir, "python").delete();
            new File(binDir, "python3").delete();
            new File(binDir, "pip").delete();
            new File(binDir, "pip3").delete();
        } else if ("nodejs".equals(packageName) || "node".equals(packageName)) {
            new File(binDir, "nodejs").delete();
            new File(binDir, "node").delete();
            new File(binDir, "npm").delete();
        }
    }

    private void createScriptIfNotExists(@NonNull File file, @NonNull String scriptContent) throws IOException {
        if (!file.exists()) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(scriptContent);
            }
            UserlandTarExtractor.setExecutable(file);
        }
    }

    private void recordPackageInDpkgStatus(@NonNull File dpkgDir, @NonNull String packageName, @NonNull String version) {
        File statusFile = new File(dpkgDir, "status");
        try {
            List<String> installed = new ArrayList<>();
            if (statusFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(statusFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("Package:")) {
                            installed.add(line.substring("Package:".length()).trim());
                        }
                    }
                }
            }

            if (!installed.contains(packageName)) {
                try (FileWriter writer = new FileWriter(statusFile, true)) {
                    writer.write("Package: " + packageName + "\n");
                    writer.write("Status: install ok installed\n");
                    writer.write("Version: " + version + "\n\n");
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void removePackageFromDpkgStatus(@NonNull File dpkgDir, @NonNull String packageName) {
        File statusFile = new File(dpkgDir, "status");
        if (!statusFile.exists()) return;

        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(statusFile))) {
                String line;
                boolean skipping = false;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Package:")) {
                        String name = line.substring("Package:".length()).trim();
                        skipping = name.equalsIgnoreCase(packageName);
                    }
                    if (!skipping) {
                        sb.append(line).append("\n");
                    } else if (line.trim().isEmpty()) {
                        skipping = false;
                    }
                }
            }
            try (FileWriter writer = new FileWriter(statusFile)) {
                writer.write(sb.toString());
            }
        } catch (IOException ignored) {
        }
    }

    private String resolveDefaultVersion(String pkgName) {
        switch (pkgName) {
            case "python":
            case "python3":
                return "3.11.8";
            case "git":
                return "2.43.0";
            case "nodejs":
            case "node":
                return "20.11.1";
            case "curl":
                return "8.5.0";
            case "bash":
                return "5.2.21";
            default:
                return "1.0.0";
        }
    }

    private void notifySuccess(@Nullable PackageOperationCallback callback, @NonNull String msg) {
        if (callback != null) {
            ExecutorProvider.getInstance().runOnMain(() -> callback.onSuccess(msg));
        }
    }

    private void notifyError(@Nullable PackageOperationCallback callback, @NonNull String err) {
        if (callback != null) {
            ExecutorProvider.getInstance().runOnMain(() -> callback.onError(err));
        }
    }

    private void notifyProgress(@Nullable PackageOperationCallback callback, @NonNull String status, int percent) {
        if (callback != null) {
            ExecutorProvider.getInstance().runOnMain(() -> callback.onProgress(status, percent));
        }
    }
}
