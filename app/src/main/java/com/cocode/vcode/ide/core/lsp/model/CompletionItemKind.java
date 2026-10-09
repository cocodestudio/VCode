package com.cocode.vcode.ide.core.lsp.model;

/**
 * The kind of a completion entry matching LSP 3.17 CompletionItemKind.
 */
public enum CompletionItemKind {
    TEXT(1),
    METHOD(2),
    FUNCTION(3),
    CONSTRUCTOR(4),
    FIELD(5),
    VARIABLE(6),
    CLASS(7),
    INTERFACE(8),
    MODULE(9),
    PROPERTY(10),
    UNIT(11),
    VALUE(12),
    ENUM(13),
    KEYWORD(14),
    SNIPPET(15),
    COLOR(16),
    FILE(17),
    REFERENCE(18),
    FOLDER(19),
    ENUM_MEMBER(20),
    CONSTANT(21),
    STRUCT(22),
    EVENT(23),
    OPERATOR(24),
    TYPE_PARAMETER(25);

    private final int value;

    CompletionItemKind(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static CompletionItemKind fromValue(int value) {
        for (CompletionItemKind kind : values()) {
            if (kind.value == value) {
                return kind;
            }
        }
        return TEXT;
    }
}
