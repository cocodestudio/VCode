package com.cocode.vcode.ide.core.autocomplete;

import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.List;

/**
 * Service Provider Interface (SPI) for contributing code completions to the editor.
 */
public interface CompletionProvider {

    int PRIORITY_HIGH = 100;
    int PRIORITY_NORMAL = 50;
    int PRIORITY_LOW = 10;

    /**
     * Determines whether this provider can contribute completions given the current context.
     *
     * @param context the current completion context (document text, cursor, file type, etc.)
     * @return true if this provider is applicable
     */
    boolean canProvide(CompletionContext context);

    /**
     * Computes completion items for the given context.
     *
     * @param context the current completion context
     * @return list of completion items, or empty list if none
     */
    List<CompletionItem> provideCompletions(CompletionContext context);

    /**
     * Execution and ranking priority (higher value runs/ranks first).
     */
    int getPriority();
}
