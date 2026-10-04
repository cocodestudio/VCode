package com.cocode.vcode.ide.core.command;

/**
 * Functional interface representing an executable IDE command action.
 */
@FunctionalInterface
public interface CommandAction {
    /**
     * Executes the command with optional arguments.
     *
     * @param args command arguments
     */
    void execute(Object... args);
}
