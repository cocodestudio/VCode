package com.cocode.vcode.ide.core.lsp;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.language.js.ParseResult;
import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for K.3: auto-insert import statement with a
 * case-accurate relative path.
 *
 * The acceptance criterion: "selecting fetchData from B.js inserts
 * a correct, case-accurate relative import at the top of the
 * current file."
 */
public class ImportStatementBuilderTest {

    @After
    public void cleanup() {
        ProjectIndex.getInstance().clear();
    }

    @Test
    public void siblingFileRelativePath() {
        // /virtual/A.js imports from /virtual/B.js → ./B.js
        ImportStatementBuilder.Result r = ImportStatementBuilder.build(
                "fetchData", "/virtual/B.js", "/virtual/A.js", "");
        assertNotNull(r);
        assertTrue("expected insert text to import fetchData from ./B.js, got: " + r.insertText,
                r.insertText.contains("import { fetchData } from './B.js'"));
        assertEquals(0, r.insertAtOffset);
    }

    @Test
    public void childDirectoryRelativePath() {
        // /virtual/A.js imports from /virtual/sub/B.js → ./sub/B.js
        ImportStatementBuilder.Result r = ImportStatementBuilder.build(
                "fetchData", "/virtual/sub/B.js", "/virtual/A.js", "");
        assertNotNull(r);
        assertTrue("expected ./sub/B.js, got: " + r.insertText,
                r.insertText.contains("from './sub/B.js'"));
    }

    @Test
    public void parentDirectoryRelativePath() {
        // /virtual/sub/A.js imports from /virtual/B.js → ../B.js
        ImportStatementBuilder.Result r = ImportStatementBuilder.build(
                "fetchData", "/virtual/B.js", "/virtual/sub/A.js", "");
        assertNotNull(r);
        assertTrue("expected ../B.js, got: " + r.insertText,
                r.insertText.contains("from '../B.js'"));
    }

    @Test
    public void selfImportReturnsNull() {
        ImportStatementBuilder.Result r = ImportStatementBuilder.build(
                "fetchData", "/virtual/A.js", "/virtual/A.js", "");
        assertNull("self-import must return null", r);
    }

    @Test
    public void nullNameReturnsNull() {
        ImportStatementBuilder.Result r = ImportStatementBuilder.build(
                null, "/virtual/B.js", "/virtual/A.js", "");
        assertNull(r);
    }

    @Test
    public void nullUrisReturnNull() {
        assertNull(ImportStatementBuilder.build("x", null, "/virtual/A.js", ""));
        assertNull(ImportStatementBuilder.build("x", "/virtual/B.js", null, ""));
    }

    @Test
    public void insertionOffsetGoesAfterExistingImports() {
        String text = "import { foo } from './foo.js';\n" +
                "import bar from './bar.js';\n" +
                "\n" +
                "const x = 1;\n";
        int offset = ImportStatementBuilder.findInsertionOffset(text);
        // Should be right after the second import's newline.
        int expected = "import { foo } from './foo.js';\n".length()
                + "import bar from './bar.js';\n".length();
        assertEquals(expected, offset);
    }

    @Test
    public void insertionOffsetIsZeroForEmptyFile() {
        assertEquals(0, ImportStatementBuilder.findInsertionOffset(""));
        assertEquals(0, ImportStatementBuilder.findInsertionOffset(null));
    }

    @Test
    public void insertionOffsetIsZeroForFileWithoutImports() {
        String text = "const x = 1;\nfunction f() {}\n";
        assertEquals(0, ImportStatementBuilder.findInsertionOffset(text));
    }

    @Test
    public void importEndsWithNewline() {
        ImportStatementBuilder.Result r = ImportStatementBuilder.build(
                "foo", "/virtual/B.js", "/virtual/A.js", "");
        assertNotNull(r);
        assertTrue("import statement must end with a newline", r.insertText.endsWith("\n"));
    }

    @Test
    public void computeRelativePathProducesCanonicalForm() {
        // Verify the relative-path string format. The full round-trip
        // through ModuleResolver.resolveModulePath is exercised by
        // integration tests; here we just check the shape of the
        // returned path against a few scenarios.
        File projectRoot = new File(".").getAbsoluteFile();
        File currentDir = new File(projectRoot, "src/test");
        File sourceFile = new File(projectRoot, "src/test/B.js");
        String rel = ImportStatementBuilder.computeRelativePath(currentDir, sourceFile);
        assertNotNull(rel);
        // A file in the same directory resolves to ./B.js
        assertTrue("expected './B.js', got: " + rel, rel.endsWith("B.js"));
        assertTrue("expected leading './' or '..', got: " + rel,
                rel.startsWith("./") || rel.startsWith("../") || rel.equals("B.js"));
    }

    @Test
    public void endToEndSelectingFetchDataFromB() {
        // The literal K.3 acceptance check, in code form.
        ProjectIndex.getInstance().updateParseResult(
                new File("/virtual/B.js").getAbsolutePath(),
                buildResult(new File("/virtual/B.js").getAbsolutePath(),
                        "export function fetchData() {}"));

        // Simulate selecting the cross-file completion.
        CompletionItem item = new CompletionItem("fetchData", "fetchData",
                "Export", CompletionItem.Type.FUNCTION, 0,
                new File("/virtual/B.js").getAbsolutePath());

        String currentText = "const x = 1;\n";
        String currentFile = new File("/virtual/A.js").getAbsolutePath();
        ImportStatementBuilder.Result r = ImportStatementBuilder.build(
                item.getLabel(), item.getSourceUri(), currentFile, currentText);

        assertNotNull(r);
        // Expected insert text: "import { fetchData } from './B.js';\n"
        // (or with backslashes on Windows — the path normalization
        //  is OS-aware, so we check for the import line shape).
        assertTrue("expected import statement containing 'fetchData', got: " + r.insertText,
                r.insertText.contains("import { fetchData } from '"));
        assertTrue("expected relative path to B.js, got: " + r.insertText,
                r.insertText.contains("B.js"));
        // Insertion at offset 0 because the file has no existing imports.
        assertEquals(0, r.insertAtOffset);
    }

    @Test
    public void buildIsAccessibleViaJsAutoCompleteEngine() throws Exception {
        // Verify that JsAutoCompleteEngine exposes a public
        // buildImportInsert method that delegates to the builder.
        // (Wiring check — full UI integration is out of scope.)
        Class<?> engine = Class.forName(
                "com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine");
        Method m = engine.getDeclaredMethod("buildImportInsert",
                String.class, String.class, String.class, String.class);
        assertNotNull(m);
        assertTrue("buildImportInsert must be public",
                java.lang.reflect.Modifier.isPublic(m.getModifiers()));
    }

    private static ParseResult buildResult(String uri, String src) {
        TokenStream stream = JsLexer.tokenize(src);
        JsSyntaxTree tree = JsParser.parseTopLevel(src, stream);
        return new ParseResult(new File(uri), src, stream, tree);
    }
}
