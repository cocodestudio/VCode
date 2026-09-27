package com.cocode.vcode.ide.core.language.base;

import com.cocode.vcode.ide.core.model.FileType;

/**
 * Root document node representing a completely parsed source file.
 */
public interface DomFile extends DomNode {

    /**
     * The file type or language of this parsed document.
     */
    FileType getFileType();

    /**
     * Total number of AST nodes contained in this document.
     */
    int getNodeCount();
}
