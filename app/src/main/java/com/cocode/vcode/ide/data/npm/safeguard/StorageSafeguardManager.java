package com.cocode.vcode.ide.data.npm.safeguard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.utils.FileUtils;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Mobile Storage & Stability Safeguards for npm dependencies.
 *
 * <p>Prevents Android MediaScanner battery drain by placing {@code .nomedia} shields,
 * and safeguards Git trees and filesystem walkers from traversing bulky {@code node_modules}.
 */
public final class StorageSafeguardManager {

    public static final String NODE_MODULES = "node_modules";
    public static final String NOMEDIA = ".nomedia";
    public static final String GITIGNORE = ".gitignore";

    private StorageSafeguardManager() {
    }

    /**
     * Ensures all mobile storage shields and Git ignore rules are properly deployed.
     *
     * @param projectRoot the root directory of the active project
     */
    public static void ensureSafeguards(@Nullable File projectRoot) {
        if (projectRoot == null || !projectRoot.exists() || !projectRoot.isDirectory()) {
            return;
        }

        File nodeModulesDir = new File(projectRoot, NODE_MODULES);
        if (nodeModulesDir.exists() && nodeModulesDir.isDirectory()) {
            ensureNomediaShield(nodeModulesDir);
        }

        ensureGitIgnoreRule(projectRoot);
    }

    /**
     * Creates an empty .nomedia file inside the given directory if it doesn't already exist.
     * Prevents Android's MediaScanner from scanning thousands of images or media files inside node_modules.
     *
     * @param directory the directory to protect (e.g. node_modules/)
     */
    public static void ensureNomediaShield(@NonNull File directory) {
        if (!directory.exists()) {
            directory.mkdirs();
        }
        File nomediaFile = new File(directory, NOMEDIA);
        if (!nomediaFile.exists()) {
            try (FileOutputStream fos = new FileOutputStream(nomediaFile)) {
                fos.flush();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Verifies that .gitignore contains node_modules/ and appends it if missing.
     *
     * @param projectRoot the root directory of the project
     */
    public static void ensureGitIgnoreRule(@NonNull File projectRoot) {
        File gitIgnore = new File(projectRoot, GITIGNORE);
        try {
            if (!gitIgnore.exists()) {
                FileUtils.writeFile(gitIgnore, "node_modules/\n.vcode/\n.DS_Store\n*.log\n");
                return;
            }

            String content = FileUtils.readFile(gitIgnore);
            if (content == null) content = "";

            if (!content.contains("node_modules")) {
                String updated = content;
                if (!updated.endsWith("\n") && !updated.isEmpty()) {
                    updated += "\n";
                }
                updated += "node_modules/\n";
                FileUtils.writeFile(gitIgnore, updated);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Checks if the given file or directory is inside node_modules.
     */
    public static boolean isNodeModules(@Nullable File file) {
        if (file == null) return false;
        String path = file.getAbsolutePath().replace('\\', '/');
        return path.contains("/" + NODE_MODULES + "/") || path.endsWith("/" + NODE_MODULES);
    }
}
