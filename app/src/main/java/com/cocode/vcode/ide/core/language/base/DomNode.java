package com.cocode.vcode.ide.core.language.base;

import java.util.List;

/**
 * Language-neutral AST node representation for code folding, breadcrumbs,
 * outline trees, and symbol navigation.
 */
public interface DomNode {

    /**
     * Returns the integer node kind/type (e.g. element, property, function, statement).
     */
    int getKind();

    /**
     * Returns the display or identifier name of the node (e.g. tag name, function name, CSS selector),
     * or empty string if anonymous or unnamed. Never null.
     */
    String getName();

    /**
     * Starting character offset in the document (inclusive).
     */
    int getStartOffset();

    /**
     * Ending character offset in the document (exclusive).
     */
    int getEndOffset();

    /**
     * Parent node in the DOM tree, or {@code null} if this is the root node.
     */
    DomNode getParent();

    /**
     * List of direct child DOM nodes. Never null (returns empty list if leaf).
     */
    List<? extends DomNode> getChildren();

    /**
     * Recursively searches for the deepest child node containing the specified character offset.
     */
    DomNode findDeepestNodeAt(int offset);
}
