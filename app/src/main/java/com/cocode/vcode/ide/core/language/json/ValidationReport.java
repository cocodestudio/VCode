package com.cocode.vcode.ide.core.language.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The outcome of a validation pass. The engine attempts to recover after the first
 * error so that the IDE can display a full error gutter rather than stopping cold.
 */
public class ValidationReport {
    private final List<JsonError> errors;

    public ValidationReport(List<JsonError> errors) {
        this.errors = new ArrayList<>(errors);
    }

    /**
     * Exposes gathered syntax faults as a read-only list to protect interior compilation state vectors.
     */
    public List<JsonError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * Helper evaluation checking whether the document parsed cleanly with zero syntax violations.
     */
    public boolean isValid() {
        return errors.isEmpty();
    }
}