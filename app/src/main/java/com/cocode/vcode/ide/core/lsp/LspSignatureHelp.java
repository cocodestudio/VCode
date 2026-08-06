package com.cocode.vcode.ide.core.lsp;

import java.util.List;

/**
 * Signature help result returned when the user positions their caret inside a function call.
 * <p>
 * Mirrors the essential fields of the LSP specification's {@code SignatureHelp} type.
 */
public final class LspSignatureHelp {

    /**
     * Available overload signatures for the function.
     */
    public final List<LspSignatureInformation> signatures;

    /**
     * Index of the active signature in {@link #signatures}.
     */
    public final int activeSignature;

    /**
     * Index of the active parameter within the active signature.
     */
    public final int activeParameter;

    public LspSignatureHelp(List<LspSignatureInformation> signatures, int activeSignature, int activeParameter) {
        this.signatures = signatures;
        this.activeSignature = activeSignature;
        this.activeParameter = activeParameter;
    }

    // -------------------------------------------------------------------------

    /**
     * Describes one overload of a function.
     */
    public static final class LspSignatureInformation {

        /**
         * Full signature label shown in the tooltip (e.g. {@code "fetch(url: string): Promise<Response>"}).
         */
        public final String label;

        /**
         * Optional documentation for this signature.
         */
        public final String documentation;

        /**
         * Individual parameter descriptions.
         */
        public final List<LspParameterInformation> parameters;

        public LspSignatureInformation(String label, String documentation, List<LspParameterInformation> parameters) {
            this.label = label;
            this.documentation = documentation;
            this.parameters = parameters;
        }
    }

    /**
     * Describes one parameter of a function signature.
     */
    public static final class LspParameterInformation {

        /**
         * Parameter label (e.g. {@code "url: string"}).
         */
        public final String label;

        /**
         * Optional documentation for this parameter.
         */
        public final String documentation;

        public LspParameterInformation(String label, String documentation) {
            this.label = label;
            this.documentation = documentation;
        }
    }
}
