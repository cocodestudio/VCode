package com.cocode.vcode.ide.core.command;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.core.events.Disposable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for executable commands in the VCode IDE.
 * Allows core subsystems, facets, and third-party extensions to register,
 * query, and execute named actions.
 */
public class CommandRegistry {

    private static volatile CommandRegistry instance;
    private final Map<String, CommandItem> commands = new ConcurrentHashMap<>();

    private CommandRegistry() {
    }

    public static CommandRegistry getInstance() {
        if (instance == null) {
            synchronized (CommandRegistry.class) {
                if (instance == null) {
                    instance = new CommandRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * Registers a new command and returns a {@link Disposable} for unregistering.
     *
     * @param item the command item to register
     * @return unregistration token
     */
    @NonNull
    public Disposable registerCommand(@NonNull CommandItem item) {
        commands.put(item.getId(), item);
        return () -> unregisterCommand(item.getId());
    }

    /**
     * Helper to register a command by ID, title, and action.
     */
    @NonNull
    public Disposable registerCommand(@NonNull String id, @NonNull String title, @NonNull CommandAction action) {
        return registerCommand(new CommandItem(id, title, action));
    }

    /**
     * Unregisters a command by ID.
     */
    public void unregisterCommand(@Nullable String id) {
        if (id != null) {
            commands.remove(id);
        }
    }

    /**
     * Executes a command by its ID.
     *
     * @param id command identifier
     * @param args optional arguments
     * @return true if the command was found and executed, false otherwise
     */
    public boolean executeCommand(@NonNull String id, Object... args) {
        CommandItem item = commands.get(id);
        if (item != null) {
            item.execute(args);
            return true;
        }
        return false;
    }

    /**
     * Returns the command item for the given ID, or null if not registered.
     */
    @Nullable
    public CommandItem getCommand(@NonNull String id) {
        return commands.get(id);
    }

    /**
     * Returns an unmodifiable list of all registered commands.
     */
    @NonNull
    public List<CommandItem> getAllCommands() {
        return Collections.unmodifiableList(new ArrayList<>(commands.values()));
    }

    /**
     * Returns true if a command with the given ID is registered.
     */
    public boolean hasCommand(@NonNull String id) {
        return commands.containsKey(id);
    }

    /**
     * Clears all registered commands (primarily for testing and workspace resets).
     */
    public void clear() {
        commands.clear();
    }
}
