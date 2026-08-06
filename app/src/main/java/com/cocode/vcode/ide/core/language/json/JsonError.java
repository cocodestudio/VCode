package com.cocode.vcode.ide.core.language.json;

/**
 * Immutable data class that describes a single JSON validation fault.
 * Designed for zero-allocation overhead on Android; all fields are final primitives
 * except the diagnostic strings.
 */
public class JsonError {
    public final String message;              // Human-readable problem description
    public final int line;                    // 1-based line number
    public final int column;                  // 1-based column number
    public final int index;                   // 0-based character offset into the raw input
    public final String severity;             // "ERROR" or "WARNING"
    public final String contextSnippet;       // Visual snippet with a ^ pointer
    public final String suggestedReplacement; // Quick-fix text for the IDE

    public JsonError(String message, int line, int column, int index,
                     String severity, String contextSnippet, String suggestedReplacement) {
        this.message = message;
        this.line = line;
        this.column = column;
        this.index = index;
        this.severity = severity;
        this.contextSnippet = contextSnippet;
        this.suggestedReplacement = suggestedReplacement;
    }
}