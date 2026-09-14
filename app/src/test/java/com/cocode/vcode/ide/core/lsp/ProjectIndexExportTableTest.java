package com.cocode.vcode.ide.core.lsp;

import com.cocode.vcode.ide.core.language.js.JsExportTable;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsParsePipeline;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.language.js.ParseResult;
import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for K.1's ProjectIndex hook: a parse cycle that
 * lands a ParseResult must rebuild the export table for that file
 * immediately, with no disk read.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ProjectIndexExportTableTest {

    private static final String URI = "/virtual/A.js";

    @After
    public void cleanup() {
        ProjectIndex.getInstance().clear();
    }

    @Test
    public void updateParseResultPopulatesExportTable() throws Exception {
        ProjectIndex idx = ProjectIndex.getInstance();
        String src = "export function fetchData() {}\nexport class Foo {}";
        TokenStream stream = JsLexer.tokenize(src);
        JsSyntaxTree tree = JsParser.parseTopLevel(src, stream);
        ParseResult result = new ParseResult(new File(URI), src, stream, tree);

        idx.updateParseResult(URI, result);

        JsExportTable t = idx.getExportTable(URI);
        assertNotNull("export table must be populated for " + URI, t);
        assertTrue("fetchData must be exported", t.containsName("fetchData"));
        assertTrue("Foo must be exported", t.containsName("Foo"));
    }

    @Test
    public void getExportsByPrefixReturnsMatchingExports() throws Exception {
        ProjectIndex idx = ProjectIndex.getInstance();
        // File A exports fetchData and fetchUser.
        String srcA = "export function fetchData() {}\nexport function fetchUser() {}";
        idx.updateParseResult(URI,
                buildResult(URI, srcA));

        // File B exports a different name.
        String uriB = "/virtual/B.js";
        String srcB = "export function saveRecord() {}";
        idx.updateParseResult(uriB, buildResult(uriB, srcB));

        List<ProjectIndex.ExportRef> results = idx.getExportsByPrefix("fetch");
        assertEquals("expected 2 fetch* matches across the project", 2, results.size());
    }

    @Test
    public void nullParseResultClearsExportTable() throws Exception {
        ProjectIndex idx = ProjectIndex.getInstance();
        idx.updateParseResult(URI,
                buildResult(URI, "export function foo() {}"));
        assertNotNull(idx.getExportTable(URI));

        idx.updateParseResult(URI, null);
        // The map should no longer have an entry for this URI.
        assertEquals(null, idx.getExportTable(URI));
    }

    @Test
    public void reparseReplacesTableAtomically() throws Exception {
        ProjectIndex idx = ProjectIndex.getInstance();
        // First parse: one export.
        idx.updateParseResult(URI,
                buildResult(URI, "export function foo() {}"));
        assertEquals(1, idx.getExportTable(URI).count);

        // Second parse: different source, two exports.
        idx.updateParseResult(URI,
                buildResult(URI, "export function bar() {}\nexport class Baz {}"));
        JsExportTable t = idx.getExportTable(URI);
        assertEquals(2, t.count);
        assertTrue(t.containsName("bar"));
        assertTrue(t.containsName("Baz"));
        // 'foo' from the previous parse must be gone.
        assertTrue("'foo' from the previous parse must be removed",
                !t.containsName("foo"));
    }

    @Test
    public void exportTableIsBuiltFromParseResultWithoutDiskRead() throws Exception {
        // The acceptance check: no disk read happens. The export
        // table is computed purely from the in-memory ParseResult.
        // We verify by passing a source string that references a
        // file path which does not exist on disk, and confirming
        // the table is still built correctly.
        ProjectIndex idx = ProjectIndex.getInstance();
        String bogusUri = "/virtual/does-not-exist-12345.js";
        String src = "export function works() {}";
        TokenStream stream = JsLexer.tokenize(src);
        JsSyntaxTree tree = JsParser.parseTopLevel(src, stream);
        ParseResult result = new ParseResult(new File(bogusUri), src, stream, tree);

        idx.updateParseResult(bogusUri, result);

        JsExportTable t = idx.getExportTable(bogusUri);
        assertNotNull(t);
        assertTrue(t.containsName("works"));
    }

    @Test
    public void clearRemovesAllExportTables() throws Exception {
        ProjectIndex idx = ProjectIndex.getInstance();
        idx.updateParseResult(URI,
                buildResult(URI, "export function foo() {}"));
        assertNotNull(idx.getExportTable(URI));

        idx.clear();
        assertEquals(null, idx.getExportTable(URI));
    }

    private static ParseResult buildResult(String uri, String src) {
        TokenStream stream = JsLexer.tokenize(src);
        JsSyntaxTree tree = JsParser.parseTopLevel(src, stream);
        return new ParseResult(new File(uri), src, stream, tree);
    }
}
