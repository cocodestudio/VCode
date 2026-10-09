package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Declares the token types and token modifiers supported by a language server.
 * Matches LSP 3.17 SemanticTokensLegend.
 */
public final class SemanticTokensLegend {

    public final List<String> tokenTypes;
    public final List<String> tokenModifiers;

    public SemanticTokensLegend(List<String> tokenTypes, List<String> tokenModifiers) {
        this.tokenTypes = tokenTypes != null ? Collections.unmodifiableList(new ArrayList<>(tokenTypes)) : Collections.emptyList();
        this.tokenModifiers = tokenModifiers != null ? Collections.unmodifiableList(new ArrayList<>(tokenModifiers)) : Collections.emptyList();
    }

    public static final SemanticTokensLegend DEFAULT = new SemanticTokensLegend(
            Arrays.asList(
                    "type", "class", "enum", "interface", "struct", "typeParameter", "parameter",
                    "variable", "property", "enumMember", "event", "function", "method", "macro",
                    "keyword", "modifier", "comment", "string", "number", "regexp", "operator"
            ),
            Arrays.asList(
                    "declaration", "definition", "readonly", "static", "deprecated",
                    "abstract", "async", "modification", "documentation", "defaultLibrary"
            )
    );

    @NonNull
    @Override
    public String toString() {
        return "SemanticTokensLegend{types=" + tokenTypes.size() + ", modifiers=" + tokenModifiers.size() + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SemanticTokensLegend)) return false;
        SemanticTokensLegend that = (SemanticTokensLegend) o;
        return Objects.equals(tokenTypes, that.tokenTypes) &&
                Objects.equals(tokenModifiers, that.tokenModifiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenTypes, tokenModifiers);
    }
}
