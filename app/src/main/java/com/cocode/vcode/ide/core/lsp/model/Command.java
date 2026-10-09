package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a reference to a command matching LSP 3.17 Command.
 */
public final class Command {

    public final String title;
    public final String command;
    public final List<Object> arguments;

    public Command(String title, String command, List<Object> arguments) {
        this.title = title != null ? title : "";
        this.command = command != null ? command : "";
        this.arguments = arguments != null ? Collections.unmodifiableList(new ArrayList<>(arguments)) : Collections.emptyList();
    }

    public Command(String title, String command) {
        this(title, command, null);
    }

    @NonNull
    @Override
    public String toString() {
        return "Command{" + title + " (" + command + ")}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Command)) return false;
        Command command1 = (Command) o;
        return Objects.equals(title, command1.title) &&
                Objects.equals(command, command1.command) &&
                Objects.equals(arguments, command1.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, command, arguments);
    }
}
