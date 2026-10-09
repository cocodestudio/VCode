package com.cocode.vcode.ide.core.lsp;

/**
 * Immutable declaration of LSP language server capabilities, matching
 * standard Language Server Protocol capability negotiation.
 */
public final class ServerCapabilities {

    public static final ServerCapabilities NONE = new Builder().build();

    public static final ServerCapabilities DEFAULT_WEB = new Builder()
            .setHoverProvider(true)
            .setCompletionProvider(true)
            .setDefinitionProvider(true)
            .setTypeDefinitionProvider(true)
            .setReferencesProvider(true)
            .setDocumentHighlightProvider(true)
            .setDocumentSymbolProvider(true)
            .setCodeActionProvider(true)
            .setRenameProvider(true)
            .setFormattingProvider(true)
            .setRangeFormattingProvider(true)
            .setFoldingRangeProvider(true)
            .setSignatureHelpProvider(true)
            .setSemanticTokensProvider(true)
            .build();

    private final boolean hoverProvider;
    private final boolean completionProvider;
    private final boolean definitionProvider;
    private final boolean typeDefinitionProvider;
    private final boolean referencesProvider;
    private final boolean documentHighlightProvider;
    private final boolean documentSymbolProvider;
    private final boolean codeActionProvider;
    private final boolean renameProvider;
    private final boolean formattingProvider;
    private final boolean rangeFormattingProvider;
    private final boolean foldingRangeProvider;
    private final boolean signatureHelpProvider;
    private final boolean semanticTokensProvider;

    private ServerCapabilities(Builder builder) {
        this.hoverProvider = builder.hoverProvider;
        this.completionProvider = builder.completionProvider;
        this.definitionProvider = builder.definitionProvider;
        this.typeDefinitionProvider = builder.typeDefinitionProvider;
        this.referencesProvider = builder.referencesProvider;
        this.documentHighlightProvider = builder.documentHighlightProvider;
        this.documentSymbolProvider = builder.documentSymbolProvider;
        this.codeActionProvider = builder.codeActionProvider;
        this.renameProvider = builder.renameProvider;
        this.formattingProvider = builder.formattingProvider;
        this.rangeFormattingProvider = builder.rangeFormattingProvider;
        this.foldingRangeProvider = builder.foldingRangeProvider;
        this.signatureHelpProvider = builder.signatureHelpProvider;
        this.semanticTokensProvider = builder.semanticTokensProvider;
    }

    public boolean hasHover() { return hoverProvider; }
    public boolean hasCompletion() { return completionProvider; }
    public boolean hasDefinition() { return definitionProvider; }
    public boolean hasTypeDefinition() { return typeDefinitionProvider; }
    public boolean hasReferences() { return referencesProvider; }
    public boolean hasDocumentHighlight() { return documentHighlightProvider; }
    public boolean hasDocumentSymbol() { return documentSymbolProvider; }
    public boolean hasCodeAction() { return codeActionProvider; }
    public boolean hasRename() { return renameProvider; }
    public boolean hasFormatting() { return formattingProvider; }
    public boolean hasRangeFormatting() { return rangeFormattingProvider; }
    public boolean hasFoldingRange() { return foldingRangeProvider; }
    public boolean hasSignatureHelp() { return signatureHelpProvider; }
    public boolean hasSemanticTokens() { return semanticTokensProvider; }

    public static class Builder {
        private boolean hoverProvider;
        private boolean completionProvider;
        private boolean definitionProvider;
        private boolean typeDefinitionProvider;
        private boolean referencesProvider;
        private boolean documentHighlightProvider;
        private boolean documentSymbolProvider;
        private boolean codeActionProvider;
        private boolean renameProvider;
        private boolean formattingProvider;
        private boolean rangeFormattingProvider;
        private boolean foldingRangeProvider;
        private boolean signatureHelpProvider;
        private boolean semanticTokensProvider;

        public Builder setHoverProvider(boolean enabled) {
            this.hoverProvider = enabled;
            return this;
        }

        public Builder setCompletionProvider(boolean enabled) {
            this.completionProvider = enabled;
            return this;
        }

        public Builder setDefinitionProvider(boolean enabled) {
            this.definitionProvider = enabled;
            return this;
        }

        public Builder setTypeDefinitionProvider(boolean enabled) {
            this.typeDefinitionProvider = enabled;
            return this;
        }

        public Builder setReferencesProvider(boolean enabled) {
            this.referencesProvider = enabled;
            return this;
        }

        public Builder setDocumentHighlightProvider(boolean enabled) {
            this.documentHighlightProvider = enabled;
            return this;
        }

        public Builder setDocumentSymbolProvider(boolean enabled) {
            this.documentSymbolProvider = enabled;
            return this;
        }

        public Builder setCodeActionProvider(boolean enabled) {
            this.codeActionProvider = enabled;
            return this;
        }

        public Builder setRenameProvider(boolean enabled) {
            this.renameProvider = enabled;
            return this;
        }

        public Builder setFormattingProvider(boolean enabled) {
            this.formattingProvider = enabled;
            return this;
        }

        public Builder setRangeFormattingProvider(boolean enabled) {
            this.rangeFormattingProvider = enabled;
            return this;
        }

        public Builder setFoldingRangeProvider(boolean enabled) {
            this.foldingRangeProvider = enabled;
            return this;
        }

        public Builder setSignatureHelpProvider(boolean enabled) {
            this.signatureHelpProvider = enabled;
            return this;
        }

        public Builder setSemanticTokensProvider(boolean enabled) {
            this.semanticTokensProvider = enabled;
            return this;
        }

        public ServerCapabilities build() {
            return new ServerCapabilities(this);
        }
    }
}
