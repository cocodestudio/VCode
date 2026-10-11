package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.ServerCapabilities;
import com.cocode.vcode.ide.core.lsp.model.CodeAction;
import com.cocode.vcode.ide.core.lsp.model.CodeActionContext;
import com.cocode.vcode.ide.core.lsp.model.CodeActionParams;
import com.cocode.vcode.ide.core.lsp.model.CompletionItem;
import com.cocode.vcode.ide.core.lsp.model.CompletionParams;
import com.cocode.vcode.ide.core.lsp.model.Diagnostic;
import com.cocode.vcode.ide.core.lsp.model.DiagnosticSeverity;
import com.cocode.vcode.ide.core.lsp.model.DocumentDiagnosticParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentFormattingParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentSymbol;
import com.cocode.vcode.ide.core.lsp.model.DocumentSymbolParams;
import com.cocode.vcode.ide.core.lsp.model.Hover;
import com.cocode.vcode.ide.core.lsp.model.HoverParams;
import com.cocode.vcode.ide.core.lsp.model.LspPosition;
import com.cocode.vcode.ide.core.lsp.model.LspRange;
import com.cocode.vcode.ide.core.lsp.model.SymbolKind;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentContentChangeEvent;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentIdentifier;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentItem;
import com.cocode.vcode.ide.core.lsp.model.TextEdit;
import com.cocode.vcode.ide.core.lsp.model.VersionedTextDocumentIdentifier;
import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class JsonLspServerTest {

    private JsonLspServer server;

    @Before
    public void setUp() {
        server = new JsonLspServer(RuntimeEnvironment.getApplication());
        server.initialize((com.cocode.vcode.ide.core.lsp.ProjectIndex) null);
    }

    @Test
    public void testLanguageIdAndCapabilities() {
        assertEquals("json", server.getLanguageId());
        assertTrue(server.isReady());

        ServerCapabilities caps = server.getCapabilities();
        assertNotNull(caps);
        assertTrue(caps.hasHover());
        assertTrue(caps.hasCompletion());
        assertTrue(caps.hasDefinition());
        assertTrue(caps.hasDocumentSymbol());
        assertTrue(caps.hasCodeAction());
        assertTrue(caps.hasFormatting());
        assertTrue(caps.hasFoldingRange());
    }

    @Test
    public void testDocumentSynchronizationLifecycle() {
        String uri = "file:///workspace/test.json";
        TextDocumentItem item = new TextDocumentItem(uri, "json", 1, "{\n  \"hello\": \"world\"\n}");
        server.didOpen(item);

        LspDocument doc = server.getDocument(uri);
        assertNotNull(doc);
        assertEquals(1, doc.version);
        assertTrue(doc.text.contains("\"hello\""));

        TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent("{\n  \"hello\": \"updated\"\n}");
        server.didChange(new VersionedTextDocumentIdentifier(uri, 2), Collections.singletonList(change));

        LspDocument updated = server.getDocument(uri);
        assertNotNull(updated);
        assertEquals(2, updated.version);
        assertTrue(updated.text.contains("\"updated\""));

        server.didSave(new TextDocumentIdentifier(uri));
        assertNotNull(server.getDocument(uri));

        server.didClose(new TextDocumentIdentifier(uri));
        assertNull(server.getDocument(uri));
    }

    @Test
    public void testValidJsonHasNoDiagnostics() {
        LspDocument doc = new LspDocument("/test.json", "{\n  \"name\": \"vcode\",\n  \"version\": 1\n}", "json", 1);
        List<Problem> problems = server.diagnostics(doc);
        assertNotNull(problems);
        assertEquals(0, problems.size());
    }

    @Test
    public void testLspDiagnosticModelConversion() {
        String uri = "file:///workspace/error.json";
        server.didOpen(new TextDocumentItem(uri, "json", 1, "{ \"a\" 1 }"));

        List<Diagnostic> diags = server.diagnostics(new DocumentDiagnosticParams(uri), null);
        assertNotNull(diags);
        assertFalse(diags.isEmpty());

        Diagnostic err = diags.get(0);
        assertEquals(DiagnosticSeverity.ERROR, err.severity);
        assertNotNull(err.range);
        assertEquals("json", err.source);
        assertTrue(err.message.contains("colon") || err.message.contains("Syntax"));
    }

    @Test
    public void testJsoncModePermitsCommentsAndTrailingCommas() {
        String uri = "file:///workspace/tsconfig.json";
        String content = "{\n  // Permitted comment in tsconfig\n  \"compilerOptions\": {\n    \"target\": \"es2020\",\n  }\n}";
        server.didOpen(new TextDocumentItem(uri, "json", 1, content));

        List<Diagnostic> diags = server.diagnostics(new DocumentDiagnosticParams(uri), null);
        assertNotNull(diags);
        for (Diagnostic d : diags) {
            assertFalse("No trailing comma or comment diagnostics should occur in JSONC: " + d.message,
                    d.message.contains("trailing comma") || d.message.contains("Comments are not permitted"));
        }
    }

    @Test
    public void testPackageJsonSchemaDiagnostics() {
        String uri = "file:///workspace/package.json";
        String content = "{\n  \"description\": \"Incomplete package manifest\"\n}";
        server.didOpen(new TextDocumentItem(uri, "json", 1, content));

        List<Diagnostic> diags = server.diagnostics(new DocumentDiagnosticParams(uri), null);
        assertNotNull(diags);
        boolean foundMissingName = false;
        boolean foundMissingVersion = false;
        for (Diagnostic d : diags) {
            if (d.message.contains("Missing required property 'name'")) foundMissingName = true;
            if (d.message.contains("Missing required property 'version'")) foundMissingVersion = true;
        }
        assertTrue("Expected schema validation diagnostic for 'name'", foundMissingName);
        assertTrue("Expected schema validation diagnostic for 'version'", foundMissingVersion);
    }

    @Test
    public void testHoverDocumentation() {
        String uri = "file:///workspace/package.json";
        String content = "{\n  \"name\": \"vcode-core\"\n}";
        server.didOpen(new TextDocumentItem(uri, "json", 1, content));

        // Hover over "name" key on line 1, column 5
        Hover hover = server.hover(new HoverParams(uri, 1, 5), null);
        assertNotNull("Hover should return schema documentation for package.json 'name'", hover);
        assertNotNull(hover.contents);
        assertTrue(hover.contents.value.contains("name"));
        assertNotNull(hover.range);

        // Hover outside quotes should return null
        Hover emptyHover = server.hover(new HoverParams(uri, 0, 0), null);
        assertNull(emptyHover);
    }

    @Test
    public void testCodeActionAddMissingRequiredProperty() {
        String uri = "file:///workspace/package.json";
        String content = "{\n  \"description\": \"Sample app\"\n}";
        server.didOpen(new TextDocumentItem(uri, "json", 1, content));

        List<Diagnostic> diags = server.diagnostics(new DocumentDiagnosticParams(uri), null);
        assertFalse(diags.isEmpty());

        CodeActionParams params = new CodeActionParams(uri, new LspRange(0, 0, 2, 1), new CodeActionContext(diags));
        List<CodeAction> actions = server.codeAction(params, null);
        assertNotNull(actions);

        boolean foundNameFix = false;
        for (CodeAction action : actions) {
            if (action.title.contains("Add missing required property 'name'")) {
                foundNameFix = true;
                assertNotNull(action.edit);
                assertTrue(action.isPreferred);
                List<TextEdit> edits = action.edit.changes.get(uri);
                assertNotNull(edits);
                assertFalse(edits.isEmpty());
                assertTrue(edits.get(0).newText.contains("\"name\":"));
            }
        }
        assertTrue("Expected quickfix for missing 'name' field", foundNameFix);
    }

    @Test
    public void testCodeActionRemoveTrailingComma() {
        String uri = "file:///workspace/config.json";
        String content = "{\n  \"value\": 10,\n}";
        server.didOpen(new TextDocumentItem(uri, "json", 1, content));

        List<Diagnostic> diags = server.diagnostics(new DocumentDiagnosticParams(uri), null);
        assertFalse(diags.isEmpty());

        CodeActionParams params = new CodeActionParams(uri, new LspRange(0, 0, 2, 1), new CodeActionContext(diags));
        List<CodeAction> actions = server.codeAction(params, null);
        assertNotNull(actions);

        boolean foundTrailingFix = false;
        for (CodeAction action : actions) {
            if ("Remove trailing comma".equals(action.title)) {
                foundTrailingFix = true;
                assertNotNull(action.edit);
                List<TextEdit> edits = action.edit.changes.get(uri);
                assertNotNull(edits);
                assertFalse(edits.isEmpty());
                assertEquals("", edits.get(0).newText);
            }
        }
        assertTrue("Expected quickfix to remove trailing comma", foundTrailingFix);
    }

    @Test
    public void testCodeActionConvertSingleQuotes() {
        String uri = "file:///workspace/config.json";
        String content = "{\n  'title': 'hello'\n}";
        server.didOpen(new TextDocumentItem(uri, "json", 1, content));

        List<Diagnostic> diags = server.diagnostics(new DocumentDiagnosticParams(uri), null);
        assertFalse(diags.isEmpty());

        CodeActionParams params = new CodeActionParams(uri, new LspRange(0, 0, 2, 1), new CodeActionContext(diags));
        List<CodeAction> actions = server.codeAction(params, null);
        assertNotNull(actions);

        boolean foundQuoteFix = false;
        for (CodeAction action : actions) {
            if ("Convert single quotes to valid double quotes".equals(action.title)) {
                foundQuoteFix = true;
                assertNotNull(action.edit);
                List<TextEdit> edits = action.edit.changes.get(uri);
                assertNotNull(edits);
                assertFalse(edits.isEmpty());
                assertTrue(edits.get(0).newText.startsWith("\""));
            }
        }
        assertTrue("Expected quickfix to convert single quotes", foundQuoteFix);
    }

    @Test
    public void testCodeActionRemoveDuplicateKey() {
        String uri = "file:///workspace/config.json";
        String content = "{\n  \"key\": 1,\n  \"key\": 2\n}";
        server.didOpen(new TextDocumentItem(uri, "json", 1, content));

        List<Diagnostic> diags = server.diagnostics(new DocumentDiagnosticParams(uri), null);
        assertFalse(diags.isEmpty());

        CodeActionParams params = new CodeActionParams(uri, new LspRange(0, 0, 3, 1), new CodeActionContext(diags));
        List<CodeAction> actions = server.codeAction(params, null);
        assertNotNull(actions);

        boolean foundDupFix = false;
        for (CodeAction action : actions) {
            if (action.title.contains("Remove duplicate key")) {
                foundDupFix = true;
                assertNotNull(action.edit);
                List<TextEdit> edits = action.edit.changes.get(uri);
                assertNotNull(edits);
                assertFalse(edits.isEmpty());
                assertEquals("", edits.get(0).newText);
            }
        }
        assertTrue("Expected quickfix to remove duplicate key", foundDupFix);
    }

    @Test
    public void testDocumentSymbolHierarchy() {
        String uri = "file:///workspace/tsconfig.json";
        String content = "{\n  \"compilerOptions\": {\n    \"target\": \"es2020\"\n  }\n}";
        server.didOpen(new TextDocumentItem(uri, "json", 1, content));

        List<DocumentSymbol> symbols = server.documentSymbol(new DocumentSymbolParams(uri), null);
        assertNotNull(symbols);
        assertFalse(symbols.isEmpty());

        DocumentSymbol rootSymbol = symbols.get(0);
        assertEquals("compilerOptions", rootSymbol.name);
        assertEquals(SymbolKind.OBJECT, rootSymbol.kind);
        assertNotNull(rootSymbol.children);
        assertFalse(rootSymbol.children.isEmpty());

        DocumentSymbol targetSymbol = rootSymbol.children.get(0);
        assertEquals("target", targetSymbol.name);
    }

    @Test
    public void testFormattingCleansTrailingCommaAndIndents() {
        String uri = "file:///workspace/messy.json";
        String content = "{\"a\":1, \"b\":[2,3]}";
        server.didOpen(new TextDocumentItem(uri, "json", 1, content));

        List<TextEdit> edits = server.formatting(new DocumentFormattingParams(uri, null), null);
        assertNotNull(edits);
        assertEquals(1, edits.size());

        TextEdit edit = edits.get(0);
        assertNotNull(edit.newText);
        assertTrue(edit.newText.contains("\n"));
        assertTrue(edit.newText.contains("  \"a\": 1"));
    }

    @Test
    public void testCompletionSchemaSuggestionsInPackageJson() {
        String uri = "file:///workspace/package.json";
        String content = "{\n  \"n\"\n}";
        server.didOpen(new TextDocumentItem(uri, "json", 1, content));

        CompletionParams params = new CompletionParams(uri, 1, 4);
        List<CompletionItem> completions = server.completion(params, null);
        assertNotNull(completions);
        assertFalse(completions.isEmpty());

        boolean foundName = false;
        boolean foundVersion = false;
        for (CompletionItem item : completions) {
            if ("name".equals(item.label)) foundName = true;
            if ("version".equals(item.label)) foundVersion = true;
        }
        assertTrue("Should offer 'name' schema completion in package.json", foundName);
        assertTrue("Should offer 'version' schema completion in package.json", foundVersion);
    }

    @Test
    public void testCompletionEnumSuggestionsInTsConfig() {
        String uri = "file:///workspace/tsconfig.json";
        String content = "{\n  \"compilerOptions\": {\n    \"target\": \"\"\n  }\n}";
        server.didOpen(new TextDocumentItem(uri, "json", 1, content));

        // Line 2, column 15 is inside the quotes for "target": ""
        CompletionParams params = new CompletionParams(uri, 2, 15);
        List<CompletionItem> completions = server.completion(params, null);
        assertNotNull(completions);

        boolean foundEs2020 = false;
        for (CompletionItem item : completions) {
            if ("es2020".equals(item.label)) {
                foundEs2020 = true;
                break;
            }
        }
        assertTrue("Should offer 'es2020' enum completion for tsconfig target", foundEs2020);
    }

    @Test
    public void testValueContextSuppressesObjectKeys() {
        String uri = "file:///workspace/tsconfig.json";
        String content = "{\n  \"compilerOptions\": {\n    \"target\": \"\"\n  }\n}";
        server.didOpen(new TextDocumentItem(uri, "json", 1, content));

        // Line 2, column 15 is inside "target": ""
        CompletionParams params = new CompletionParams(uri, 2, 15);
        List<CompletionItem> completions = server.completion(params, null);
        assertNotNull(completions);

        for (CompletionItem item : completions) {
            assertFalse("Object keys must NOT appear in value context: " + item.label,
                    "target".equals(item.label) || "compilerOptions".equals(item.label) || "strict".equals(item.label));
        }
    }

    @Test
    public void testDuplicateKeyExclusionInObject() {
        String uri = "file:///workspace/package.json";
        String content = "{\n  \"name\": \"vcode\",\n  \"\"\n}";
        server.didOpen(new TextDocumentItem(uri, "json", 1, content));

        // Line 2, column 3 is inside the second key ""
        CompletionParams params = new CompletionParams(uri, 2, 3);
        List<CompletionItem> completions = server.completion(params, null);
        assertNotNull(completions);

        boolean foundName = false;
        boolean foundVersion = false;
        for (CompletionItem item : completions) {
            if ("name".equals(item.label)) foundName = true;
            if ("version".equals(item.label)) foundVersion = true;
        }
        assertFalse("Already defined key 'name' must be excluded from suggestions", foundName);
        assertTrue("New schema key 'version' must still be offered", foundVersion);
    }

    @Test
    public void testCommentsSuppressCompletionsInJsonc() {
        String uri = "file:///workspace/tsconfig.json";
        String content = "{\n  // typing comment here \n  \"compilerOptions\": {}\n}";
        server.didOpen(new TextDocumentItem(uri, "json", 1, content));

        // Line 1, column 10 is inside line comment
        CompletionParams params = new CompletionParams(uri, 1, 10);
        List<CompletionItem> completions = server.completion(params, null);
        assertNotNull(completions);
        assertTrue("Completions must be suppressed inside JSONC comments", completions.isEmpty());
    }

    @Test
    public void testExplicitTextEditRangesOnAllItems() {
        String uri = "file:///workspace/package.json";
        String content = "{\n  \"n\"\n}";
        server.didOpen(new TextDocumentItem(uri, "json", 1, content));

        CompletionParams params = new CompletionParams(uri, 1, 4);
        List<CompletionItem> completions = server.completion(params, null);
        assertNotNull(completions);
        assertFalse(completions.isEmpty());

        for (CompletionItem item : completions) {
            assertNotNull("Every completion item must carry an explicit textEdit: " + item.label, item.textEdit);
            assertNotNull("TextEdit must carry an explicit range", item.textEdit.range);
            assertTrue("TextEdit start position line must be valid", item.textEdit.range.start.line >= 0);
            assertTrue("TextEdit end position line must be valid", item.textEdit.range.end.line >= 0);
        }
    }
}
