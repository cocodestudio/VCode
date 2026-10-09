package com.cocode.vcode.ide.core.lsp.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Signature help represents the signature of something callable.
 * Matches LSP 3.17 SignatureHelp.
 */
public final class SignatureHelp {

    public final List<SignatureInformation> signatures;
    public final int activeSignature;
    public final int activeParameter;

    public SignatureHelp(List<SignatureInformation> signatures, int activeSignature, int activeParameter) {
        this.signatures = signatures != null
                ? Collections.unmodifiableList(new ArrayList<>(signatures))
                : Collections.emptyList();
        this.activeSignature = activeSignature;
        this.activeParameter = activeParameter;
    }

    public SignatureHelp(List<SignatureInformation> signatures) {
        this(signatures, 0, 0);
    }

    @NonNull
    @Override
    public String toString() {
        return "SignatureHelp{activeSig=" + activeSignature + ", activeParam=" + activeParameter + ", sigs=" + signatures.size() + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SignatureHelp)) return false;
        SignatureHelp that = (SignatureHelp) o;
        return activeSignature == that.activeSignature &&
                activeParameter == that.activeParameter &&
                Objects.equals(signatures, that.signatures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signatures, activeSignature, activeParameter);
    }
}
