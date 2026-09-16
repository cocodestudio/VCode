package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;

/**
 * Manages double-buffering for TokenStream and JsSyntaxTree per-file
 * to avoid garbage collection pauses on every keystroke.
 */
public class FileBufferPool {
    private final TokenStream[] tokenBuffers = new TokenStream[2];
    private final JsSyntaxTree[] treeBuffers = new JsSyntaxTree[2];
    private int activeIndex = 0;

    /**
     * Retrieves the buffer pair that is NOT currently being read by consumers.
     */
    public synchronized BufferPair getInactiveBuffer() {
        int inactiveIndex = (activeIndex + 1) % 2;
        return new BufferPair(
                tokenBuffers[inactiveIndex],
                treeBuffers[inactiveIndex]
        );
    }

    /**
     * Swaps the active buffer, marking the recently parsed buffer as ready for consumers.
     * Updates the internal references if new objects were created (e.g. on first pass).
     */
    public synchronized void commitSwap(TokenStream newToken, JsSyntaxTree newTree) {
        int inactiveIndex = (activeIndex + 1) % 2;
        tokenBuffers[inactiveIndex] = newToken;
        treeBuffers[inactiveIndex] = newTree;
        activeIndex = inactiveIndex;
    }

    public static class BufferPair {
        public final TokenStream tokens;
        public final JsSyntaxTree tree;

        public BufferPair(TokenStream tokens, JsSyntaxTree tree) {
            this.tokens = tokens;
            this.tree = tree;
        }
    }
}
