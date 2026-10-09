package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.language.html.HtmlLexer;
import com.cocode.vcode.ide.core.language.html.HtmlParser;
import com.cocode.vcode.ide.core.language.html.HtmlSyntaxTree;
import com.cocode.vcode.ide.core.language.html.HtmlTokenStream;
import com.cocode.vcode.ide.core.language.js.ParseResult;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.model.Problem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Projects embedded CSS (<style>) and JS (<script>) regions from an HTML document
 * into virtual sub-documents with 1:1 coordinate alignment.
 *
 * <p>By replacing all host HTML regions with spaces (while strictly preserving newlines
 * and carriage returns), the virtual documents maintain identical byte lengths, line numbers,
 * and column offsets. Sub-servers can parse and lint these blocks directly without requiring
 * coordinate back-translation.</p>
 */
public final class HtmlEmbeddedProjector {

    private HtmlEmbeddedProjector() {
    }

    public static class EmbeddedRegion {
        public static final int TYPE_CSS = 1;
        public static final int TYPE_JS = 2;

        public final int type;
        public final int startOffset;
        public final int endOffset;
        public final String tag;

        public EmbeddedRegion(int type, int startOffset, int endOffset, String tag) {
            this.type = type;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.tag = tag;
        }

        public boolean contains(int offset) {
            return offset >= startOffset && offset <= endOffset;
        }
    }

    /**
     * Finds all embedded <style> and non-external <script> blocks within the HTML source.
     */
    public static List<EmbeddedRegion> findEmbeddedRegions(String html) {
        if (html == null || html.isEmpty()) return Collections.emptyList();

        List<EmbeddedRegion> regions = new ArrayList<>();
        try {
            HtmlTokenStream stream = HtmlLexer.tokenize(html);
            ParseResult result = HtmlParser.parse(html, stream);
            HtmlSyntaxTree tree = result != null ? result.htmlTree : null;
            if (tree != null) {
                int count = tree.nodeCount;
                for (int i = 1; i < count; i++) {
                    if (tree.nodeType[i] == HtmlSyntaxTree.N_ELEMENT) {
                        String name = tree.nodeName[i];
                        if ("style".equalsIgnoreCase(name)) {
                            collectStyleRegion(tree, i, html, regions);
                        } else if ("script".equalsIgnoreCase(name)) {
                            collectScriptRegion(tree, i, html, regions);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        if (regions.isEmpty()) {
            findRegionsFallback(html, regions);
        }

        return regions;
    }

    private static void collectStyleRegion(HtmlSyntaxTree tree, int node, String html, List<EmbeddedRegion> regions) {
        int child = tree.nodeChild[node];
        boolean found = false;
        while (child > 0 && child < tree.nodeCount) {
            if (tree.nodeType[child] == HtmlSyntaxTree.N_TEXT) {
                int start = tree.nodeStart[child];
                int end = tree.nodeEnd[child];
                if (start <= end && start >= 0 && end <= html.length()) {
                    regions.add(new EmbeddedRegion(EmbeddedRegion.TYPE_CSS, start, end, "style"));
                    found = true;
                }
            }
            child = tree.nodeSibling[child];
        }
        if (!found) {
            int tagStart = tree.nodeStart[node];
            int gt = html.indexOf('>', tagStart);
            if (gt >= 0 && gt < html.length()) {
                int contentStart = gt + 1;
                int contentEnd = Math.max(contentStart, tree.nodeEnd[node]);
                int closeTag = html.toLowerCase().indexOf("</style", contentStart);
                if (closeTag >= contentStart && closeTag <= contentEnd) {
                    contentEnd = closeTag;
                }
                regions.add(new EmbeddedRegion(EmbeddedRegion.TYPE_CSS, contentStart, contentEnd, "style"));
            }
        }
    }

    private static void collectScriptRegion(HtmlSyntaxTree tree, int node, String html, List<EmbeddedRegion> regions) {
        // Discard external scripts (<script src="...">)
        int child = tree.nodeChild[node];
        while (child > 0 && child < tree.nodeCount) {
            if (tree.nodeType[child] == HtmlSyntaxTree.N_ATTRIBUTE && "src".equalsIgnoreCase(tree.nodeName[child])) {
                return;
            }
            child = tree.nodeSibling[child];
        }

        child = tree.nodeChild[node];
        boolean found = false;
        while (child > 0 && child < tree.nodeCount) {
            if (tree.nodeType[child] == HtmlSyntaxTree.N_TEXT) {
                int start = tree.nodeStart[child];
                int end = tree.nodeEnd[child];
                if (start <= end && start >= 0 && end <= html.length()) {
                    regions.add(new EmbeddedRegion(EmbeddedRegion.TYPE_JS, start, end, "script"));
                    found = true;
                }
            }
            child = tree.nodeSibling[child];
        }
        if (!found) {
            int tagStart = tree.nodeStart[node];
            int gt = html.indexOf('>', tagStart);
            if (gt >= 0 && gt < html.length()) {
                int contentStart = gt + 1;
                int contentEnd = Math.max(contentStart, tree.nodeEnd[node]);
                int closeTag = html.toLowerCase().indexOf("</script", contentStart);
                if (closeTag >= contentStart && closeTag <= contentEnd) {
                    contentEnd = closeTag;
                }
                regions.add(new EmbeddedRegion(EmbeddedRegion.TYPE_JS, contentStart, contentEnd, "script"));
            }
        }
    }

    private static void findRegionsFallback(String html, List<EmbeddedRegion> regions) {
        int p = 0;
        int len = html.length();
        while (p < len) {
            int styleStart = html.toLowerCase().indexOf("<style", p);
            if (styleStart == -1) break;
            int gt = html.indexOf('>', styleStart + 6);
            if (gt == -1) break;
            int contentStart = gt + 1;
            int styleEnd = html.toLowerCase().indexOf("</style", contentStart);
            int contentEnd = styleEnd != -1 ? styleEnd : len;
            regions.add(new EmbeddedRegion(EmbeddedRegion.TYPE_CSS, contentStart, contentEnd, "style"));
            p = (styleEnd != -1) ? styleEnd + 8 : len;
        }

        p = 0;
        while (p < len) {
            int scriptStart = html.toLowerCase().indexOf("<script", p);
            if (scriptStart == -1) break;
            int gt = html.indexOf('>', scriptStart + 7);
            if (gt == -1) break;
            String openTag = html.substring(scriptStart, gt + 1);
            if (!openTag.toLowerCase().contains("src=")) {
                int contentStart = gt + 1;
                int scriptEnd = html.toLowerCase().indexOf("</script", contentStart);
                int contentEnd = scriptEnd != -1 ? scriptEnd : len;
                regions.add(new EmbeddedRegion(EmbeddedRegion.TYPE_JS, contentStart, contentEnd, "script"));
                p = (scriptEnd != -1) ? scriptEnd + 9 : len;
            } else {
                p = gt + 1;
            }
        }
    }

    /**
     * Builds a virtual CSS document where all characters outside <style> blocks are replaced
     * with whitespace, preserving line and column coordinates 1:1.
     */
    public static LspDocument createVirtualCssDocument(LspDocument htmlDoc, List<EmbeddedRegion> regions) {
        if (htmlDoc == null || htmlDoc.text == null) return null;
        String text = htmlDoc.text;
        char[] mask = new char[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            mask[i] = (c == '\n' || c == '\r') ? c : ' ';
        }

        boolean hasCss = false;
        if (regions != null) {
            for (EmbeddedRegion r : regions) {
                if (r.type == EmbeddedRegion.TYPE_CSS) {
                    int s = Math.max(0, Math.min(r.startOffset, text.length()));
                    int e = Math.max(s, Math.min(r.endOffset, text.length()));
                    for (int k = s; k < e; k++) {
                        mask[k] = text.charAt(k);
                    }
                    if (s < e) hasCss = true;
                }
            }
        }
        if (!hasCss) return null;
        return new LspDocument(htmlDoc.uri, new String(mask), "css", htmlDoc.version);
    }

    /**
     * Builds a virtual JS document where all characters outside <script> blocks are replaced
     * with whitespace, preserving line and column coordinates 1:1.
     */
    public static LspDocument createVirtualJsDocument(LspDocument htmlDoc, List<EmbeddedRegion> regions) {
        if (htmlDoc == null || htmlDoc.text == null) return null;
        String text = htmlDoc.text;
        char[] mask = new char[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            mask[i] = (c == '\n' || c == '\r') ? c : ' ';
        }

        boolean hasJs = false;
        if (regions != null) {
            for (EmbeddedRegion r : regions) {
                if (r.type == EmbeddedRegion.TYPE_JS) {
                    int s = Math.max(0, Math.min(r.startOffset, text.length()));
                    int e = Math.max(s, Math.min(r.endOffset, text.length()));
                    for (int k = s; k < e; k++) {
                        mask[k] = text.charAt(k);
                    }
                    if (s < e) hasJs = true;
                }
            }
        }
        if (!hasJs) return null;
        return new LspDocument(htmlDoc.uri, new String(mask), "javascript", htmlDoc.version);
    }

    /**
     * Returns the embedded region enclosing the given flat offset, or null if outside.
     */
    public static EmbeddedRegion getRegionAt(List<EmbeddedRegion> regions, int offset) {
        if (regions == null || offset < 0) return null;
        for (EmbeddedRegion r : regions) {
            if (r.contains(offset)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Validates whether a problem offset falls strictly inside an embedded region of the specified type.
     */
    public static boolean isInsideRegion(Problem problem, LspDocument doc, List<EmbeddedRegion> regions, int type) {
        if (problem == null || doc == null || regions == null) return false;
        int line = problem.getLine() - 1;
        int col = problem.getColumn() - 1;
        int offset = doc.toOffset(new LspPosition(line, col));
        if (offset < 0) return false;
        for (EmbeddedRegion r : regions) {
            if (r.type == type && r.contains(offset)) {
                return true;
            }
        }
        return false;
    }
}
