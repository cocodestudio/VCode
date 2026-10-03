package com.cocode.vcode.ide.core.diagnostic;

import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.List;

/**
 * Service Provider Interface (SPI) for language linters and diagnostic analyzers.
 * Custom diagnostic providers can be registered dynamically with {@link DiagnosticEngine}.
 */
public interface DiagnosticProvider {

    /**
     * Unique identifier for this provider (e.g. "html-linter", "ts-linter", "custom-plugin-linter").
     */
    String getId();

    /**
     * Checks if this provider is applicable to the given file type or path.
     *
     * @param fileType the detected file type
     * @param path the file path (or null/empty if in-memory buffer)
     * @return true if this provider should execute
     */
    boolean canLint(FileType fileType, String path);

    /**
     * Executes linting on the file content and returns discovered problems.
     *
     * @param file the target file (may be null for unsaved buffers)
     * @param content the source code text
     * @param fileType the detected file type
     * @param index optional workspace symbol and parse index
     * @return list of diagnostics or problems found
     */
    List<Problem> lint(File file, String content, FileType fileType, ProjectIndex index);
}
