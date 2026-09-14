package com.cocode.vcode.ide.core.lsp;

import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared utility for converting legacy {@link CompletionItem} objects to {@link LspCompletionItem} instances.
 * Centralizes pipe-marker positioning for cursor offsets and maps completion item types to LSP kinds.
 */
public final class LspCompletionConverter {

    private LspCompletionConverter() {}

    /**
     * Converts a list of legacy {@link CompletionItem} suggestions to a list of {@link LspCompletionItem}.
     */
    public static List<LspCompletionItem> convert(List<CompletionItem> legacy) {
        if (legacy == null || legacy.isEmpty()) return Collections.emptyList();
        List<LspCompletionItem> result = new ArrayList<>(legacy.size());
        for (CompletionItem ci : legacy) {
            String insert = ci.getEffectiveInsertText();
            int curOffset = ci.getCursorOffset();
            if (insert != null && insert.indexOf('|') < 0) {
                if (curOffset < 0) {
                    int pipeIdx = insert.length() + curOffset;
                    if (pipeIdx >= 0 && pipeIdx <= insert.length()) {
                        insert = insert.substring(0, pipeIdx) + "|" + insert.substring(pipeIdx);
                    }
                } else if (curOffset > 0 && curOffset <= insert.length()) {
                    insert = insert.substring(0, curOffset) + "|" + insert.substring(curOffset);
                }
            }
            int kind = mapKind(ci.getType());
            result.add(new LspCompletionItem(
                    ci.getLabel(),
                    insert,
                    kind,
                    ci.getDetail(),
                    null,
                    ci.getReplaceLength()
            ));
        }
        return result;
    }

    /**
     * Maps legacy {@link CompletionItem.Type} to LSP completion item kind constants.
     */
    public static int mapKind(CompletionItem.Type type) {
        if (type == null) return LspCompletionItem.KIND_TEXT;
        switch (type) {
            case TAG:
                return LspCompletionItem.KIND_CLASS;
            case ATTRIBUTE:
            case CSS_PROPERTY:
            case JSON_KEY:
                return LspCompletionItem.KIND_PROPERTY;
            case VALUE:
            case CSS_VALUE:
                return LspCompletionItem.KIND_VALUE;
            case FUNCTION:
            case BUILTIN:
                return LspCompletionItem.KIND_FUNCTION;
            case KEYWORD:
                return LspCompletionItem.KIND_KEYWORD;
            case SNIPPET:
                return LspCompletionItem.KIND_SNIPPET;
            case FILE:
                return LspCompletionItem.KIND_FILE;
            case FOLDER:
                return LspCompletionItem.KIND_FOLDER;
            default:
                return LspCompletionItem.KIND_TEXT;
        }
    }
}
