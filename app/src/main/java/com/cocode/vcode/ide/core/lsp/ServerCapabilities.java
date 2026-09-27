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
            .setReferencesProvider(true)
            .setRenameProvider(true)
            .setFormattingProvider(true)
            .setSignatureHelpProvider(true)
            .build();

    private final boolean hoverProvider;
    private final boolean completionProvider;
    private final boolean definitionProvider;
    private final boolean referencesProvider;
    private final boolean renameProvider;
    private final boolean formattingProvider;
    private final boolean signatureHelpProvider;

    private ServerCapabilities(Builder builder) {
        this.hoverProvider = builder.hoverProvider;
        this.completionProvider = builder.completionProvider;
        this.definitionProvider = builder.definitionProvider;
        this.referencesProvider = builder.referencesProvider;
        this.renameProvider = builder.renameProvider;
        this.formattingProvider = builder.formattingProvider;
        this.signatureHelpProvider = builder.signatureHelpProvider;
    }

    public boolean hasHover() { return hoverProvider; }
    public boolean hasCompletion() { return completionProvider; }
    public boolean hasDefinition() { return definitionProvider; }
    public boolean hasReferences() { return referencesProvider; }
    public boolean hasRename() { return renameProvider; }
    public boolean hasFormatting() { return formattingProvider; }
    public boolean hasSignatureHelp() { return signatureHelpProvider; }

    public static class Builder {
        private boolean hoverProvider;
        private boolean completionProvider;
        private boolean definitionProvider;
        private boolean referencesProvider;
        private boolean renameProvider;
        private boolean formattingProvider;
        private boolean signatureHelpProvider;

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

        public Builder setReferencesProvider(boolean enabled) {
            this.referencesProvider = enabled;
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

        public Builder setSignatureHelpProvider(boolean enabled) {
            this.signatureHelpProvider = enabled;
            return this;
        }

        public ServerCapabilities build() {
            return new ServerCapabilities(this);
        }
    }
}
