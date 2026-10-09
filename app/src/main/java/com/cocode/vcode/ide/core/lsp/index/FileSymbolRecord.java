package com.cocode.vcode.ide.core.lsp.index;

import com.cocode.vcode.ide.core.lsp.SymbolEntry;

import java.util.Collections;
import java.util.List;

/**
 * In-memory record holding file metadata (timestamp and length) and its extracted symbols.
 */
public final class FileSymbolRecord {
    public final long lastModified;
    public final long fileLength;
    public final List<SymbolEntry> symbols;

    public FileSymbolRecord(long lastModified, long fileLength, List<SymbolEntry> symbols) {
        this.lastModified = lastModified;
        this.fileLength = fileLength;
        this.symbols = symbols != null ? symbols : Collections.emptyList();
    }
}
