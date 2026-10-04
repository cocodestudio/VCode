package com.cocode.vcode.ide.core.extension;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.core.command.CommandRegistry;
import com.cocode.vcode.ide.core.events.Disposable;
import com.cocode.vcode.ide.core.events.IdeEventBus;
import com.cocode.vcode.ide.data.preferences.ConfigurationRegistry;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Execution context supplied to a {@link VCodePlugin} upon activation.
 * Automatically tracks registered {@link Disposable} instances for clean deactivation.
 */
public class ExtensionContext {

    private final String extensionId;
    private final File extensionDir;
    private final File workspaceDir;
    private final List<Disposable> subscriptions = new ArrayList<>();

    public ExtensionContext(@NonNull String extensionId,
                            @Nullable File extensionDir,
                            @Nullable File workspaceDir) {
        this.extensionId = extensionId;
        this.extensionDir = extensionDir;
        this.workspaceDir = workspaceDir;
    }

    @NonNull
    public String getExtensionId() {
        return extensionId;
    }

    @Nullable
    public File getExtensionDir() {
        return extensionDir;
    }

    @Nullable
    public File getWorkspaceDir() {
        return workspaceDir;
    }

    /**
     * Adds a disposable subscription (completion provider, diagnostic provider, command, etc.)
     * to be cleaned up when this extension is deactivated.
     */
    public synchronized void addDisposable(@NonNull Disposable disposable) {
        if (disposable != null) {
            subscriptions.add(disposable);
        }
    }

    @NonNull
    public IdeEventBus getEventBus() {
        return IdeEventBus.getInstance();
    }

    @NonNull
    public CommandRegistry getCommandRegistry() {
        return CommandRegistry.getInstance();
    }

    @NonNull
    public ConfigurationRegistry getConfigurationRegistry() {
        return ConfigurationRegistry.getInstance();
    }

    /**
     * Registers a custom viewer factory and tracks its disposable token for automatic cleanup.
     */
    @NonNull
    public Disposable registerViewer(@NonNull com.cocode.vcode.ide.ui.editor.viewer.ViewerManager.ViewerPredicate predicate,
                                     @NonNull com.cocode.vcode.ide.ui.editor.viewer.ViewerManager.ViewerFactory factory) {
        Disposable d = com.cocode.vcode.ide.ui.editor.viewer.ViewerManager.registerGlobalViewer(predicate, factory);
        addDisposable(d);
        return d;
    }

    /**
     * Cleans up all tracked subscriptions in reverse order.
     */
    public synchronized void dispose() {
        for (int i = subscriptions.size() - 1; i >= 0; i--) {
            try {
                subscriptions.get(i).dispose();
            } catch (Throwable ignored) {
            }
        }
        subscriptions.clear();
    }
}
