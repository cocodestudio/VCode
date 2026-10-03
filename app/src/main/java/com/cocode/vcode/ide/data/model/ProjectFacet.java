package com.cocode.vcode.ide.data.model;

import androidx.annotation.NonNull;

/**
 * Represents a modular capability facet attached to a {@link Project} (e.g. NPM project facet,
 * Git repository facet, Python/Node runtime facet).
 */
public interface ProjectFacet {

    /**
     * Unique identifier for this facet type (e.g. "npm", "git", "typescript").
     */
    @NonNull
    String getFacetId();

    /**
     * Called when the facet is attached to a project.
     *
     * @param project the project this facet is attached to
     */
    void attach(@NonNull Project project);

    /**
     * Called when the facet is detached or when the project is closed/disposed.
     * Clean up any observers, listeners, or cached file handles.
     */
    void detach();

    /**
     * Returns true if this facet is currently active and attached.
     */
    boolean isAttached();
}
