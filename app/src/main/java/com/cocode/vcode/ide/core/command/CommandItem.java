package com.cocode.vcode.ide.core.command;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Model representing a registered command in the VCode IDE.
 */
public class CommandItem {

    private final String id;
    private final String title;
    private final String category;
    private final int iconResId;
    private final CommandAction action;

    public CommandItem(@NonNull String id,
                       @NonNull String title,
                       @Nullable String category,
                       int iconResId,
                       @NonNull CommandAction action) {
        this.id = id;
        this.title = title;
        this.category = category != null ? category : "General";
        this.iconResId = iconResId;
        this.action = action;
    }

    public CommandItem(@NonNull String id, @NonNull String title, @NonNull CommandAction action) {
        this(id, title, null, 0, action);
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getCategory() {
        return category;
    }

    public int getIconResId() {
        return iconResId;
    }

    @NonNull
    public CommandAction getAction() {
        return action;
    }

    public void execute(Object... args) {
        if (action != null) {
            action.execute(args);
        }
    }
}
