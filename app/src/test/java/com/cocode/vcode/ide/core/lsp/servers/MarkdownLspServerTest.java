package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.lsp.LspCompletionItem;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.lsp.ServerCapabilities;
import com.cocode.vcode.ide.core.lsp.model.CodeAction;
import com.cocode.vcode.ide.core.lsp.model.CodeActionContext;
import com.cocode.vcode.ide.core.lsp.model.CodeActionParams;
import com.cocode.vcode.ide.core.lsp.model.CompletionItem;
import com.cocode.vcode.ide.core.lsp.model.CompletionParams;
import com.cocode.vcode.ide.core.lsp.model.DefinitionParams;
import com.cocode.vcode.ide.core.lsp.model.Diagnostic;
import com.cocode.vcode.ide.core.lsp.model.DiagnosticSeverity;
import com.cocode.vcode.ide.core.lsp.model.DocumentDiagnosticParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentFormattingParams;
import com.cocode.vcode.ide.core.lsp.model.DocumentSymbol;
import com.cocode.vcode.ide.core.lsp.model.DocumentSymbolParams;
import com.cocode.vcode.ide.core.lsp.model.Hover;
import com.cocode.vcode.ide.core.lsp.model.HoverParams;
import com.cocode.vcode.ide.core.lsp.model.LspRange;
import com.cocode.vcode.ide.core.lsp.model.SymbolKind;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentContentChangeEvent;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentIdentifier;
import com.cocode.vcode.ide.core.lsp.model.TextDocumentItem;
import com.cocode.vcode.ide.core.lsp.model.TextEdit;
import com.cocode.vcode.ide.core.lsp.model.VersionedTextDocumentIdentifier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class MarkdownLspServerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private MarkdownLspServer server;
    private Context context;
    private File projectRoot;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        server = new MarkdownLspServer(context);
        server.initialize((ProjectIndex) null);
        projectRoot = tempFolder.newFolder("project");
        new File(projectRoot, "image.png").createNewFile();
        new File(projectRoot, "folder").mkdirs();
        new File(projectRoot, "folder/doc.md").createNewFile();
    }

    @Test
    public void testLanguageIdAndCapabilities() {
        assertEquals("markdown", server.getLanguageId());
        assertTrue(server.isReady());

        ServerCapabilities caps = server.getCapabilities();
        assertNotNull(caps);
        assertTrue(caps.hasHover());
        assertTrue(caps.hasCompletion());
        assertTrue(caps.hasDefinition());
        assertTrue(caps.hasReferences());
        assertTrue(caps.hasDocumentSymbol());
        assertTrue(caps.hasCodeAction());
        assertTrue(caps.hasFormatting());
        assertTrue(caps.hasFoldingRange());
    }

    @Test
    public void testDocumentSynchronizationLifecycle() {
        String uri = new File(projectRoot, "test.md").getAbsolutePath();
        TextDocumentItem item = new TextDocumentItem(uri, "markdown", 1, "# Title\n\nInitial content");
        server.didOpen(item);

        LspDocument doc = server.getDocument(uri);
        assertNotNull(doc);
        assertEquals(1, doc.version);
        assertTrue(doc.text.contains("Initial"));

        TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent("# Title\n\nUpdated content");
        server.didChange(new VersionedTextDocumentIdentifier(uri, 2), Collections.singletonList(change));

        LspDocument updated = server.getDocument(uri);
        assertNotNull(updated);
        assertEquals(2, updated.version);
        assertTrue(updated.text.contains("Updated"));

        server.didSave(new TextDocumentIdentifier(uri));
        assertNotNull(server.getDocument(uri));

        server.didClose(new TextDocumentIdentifier(uri));
        assertNull(server.getDocument(uri));
    }

    @Test
    public void testDiagnosticsBrokenLinkAndAnchor() {
        File mdFile = new File(projectRoot, "README.md");
        String uri = mdFile.getAbsolutePath();
        String content = "# Architecture\n\n"
                + "[Valid Image](./image.png)\n"
                + "[Broken File](./non_existent.md)\n"
                + "[Valid Anchor](#architecture)\n"
                + "[Broken Anchor](#missing-heading)\n"
                + "[Empty Link]()\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        List<Diagnostic> diags = server.diagnostics(new DocumentDiagnosticParams(uri), null);
        assertNotNull(diags);
        assertFalse(diags.isEmpty());

        boolean foundBrokenLink = false;
        boolean foundBrokenAnchor = false;
        boolean foundEmptyTarget = false;

        for (Diagnostic d : diags) {
            if (d.message.contains("Broken link: target file '") && d.message.contains("non_existent.md")) foundBrokenLink = true;
            if (d.message.contains("Broken anchor: '#missing-heading'")) foundBrokenAnchor = true;
            if (d.message.contains("Link has no target URL or anchor")) foundEmptyTarget = true;
        }

        assertTrue("Should detect broken relative file link", foundBrokenLink);
        assertTrue("Should detect broken anchor slug", foundBrokenAnchor);
        assertTrue("Should detect empty link target", foundEmptyTarget);
    }

    @Test
    public void testDiagnosticsUnclosedCodeBlock() {
        File mdFile = new File(projectRoot, "code.md");
        String uri = mdFile.getAbsolutePath();
        String content = "# Sample\n\n```javascript\nconst a = 1;\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        List<Diagnostic> diags = server.diagnostics(new DocumentDiagnosticParams(uri), null);
        assertNotNull(diags);

        boolean foundUnclosed = false;
        for (Diagnostic d : diags) {
            if (d.message.contains("Unclosed code block")) {
                foundUnclosed = true;
                assertEquals(DiagnosticSeverity.ERROR, d.severity);
                break;
            }
        }
        assertTrue("Should report unclosed code block error", foundUnclosed);
    }

    @Test
    public void testHeadingHierarchyDocumentSymbol() {
        File mdFile = new File(projectRoot, "structure.md");
        String uri = mdFile.getAbsolutePath();
        String content = "# Architecture\n\n"
                + "## Protocol Layer\n\n"
                + "### Message Framing\n\n"
                + "## Subsystems\n\n"
                + "### Diagnostics\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        List<DocumentSymbol> symbols = server.documentSymbol(new DocumentSymbolParams(uri), null);
        assertNotNull(symbols);
        assertEquals(1, symbols.size());

        DocumentSymbol h1 = symbols.get(0);
        assertEquals("Architecture", h1.name);
        assertEquals(SymbolKind.STRING, h1.kind);
        assertEquals("H1", h1.detail);
        assertEquals(2, h1.children.size());

        DocumentSymbol h2Protocol = h1.children.get(0);
        assertEquals("Protocol Layer", h2Protocol.name);
        assertEquals("H2", h2Protocol.detail);
        assertEquals(1, h2Protocol.children.size());

        DocumentSymbol h3Framing = h2Protocol.children.get(0);
        assertEquals("Message Framing", h3Framing.name);
        assertEquals("H3", h3Framing.detail);

        DocumentSymbol h2Subsystems = h1.children.get(1);
        assertEquals("Subsystems", h2Subsystems.name);
        assertEquals("H2", h2Subsystems.detail);
        assertEquals(1, h2Subsystems.children.size());

        DocumentSymbol h3Diagnostics = h2Subsystems.children.get(0);
        assertEquals("Diagnostics", h3Diagnostics.name);
    }

    @Test
    public void testFencedCodeBlockLanguageCompletions() {
        File mdFile = new File(projectRoot, "fence.md");
        String uri = mdFile.getAbsolutePath();
        String content = "```\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        // Cursor at column 3 right after ```
        CompletionParams params = new CompletionParams(uri, 0, 3);
        List<CompletionItem> completions = server.completion(params, null);
        assertNotNull(completions);
        assertFalse(completions.isEmpty());

        boolean foundHtml = false;
        boolean foundJs = false;
        boolean foundJson = false;
        for (CompletionItem ci : completions) {
            if ("html".equals(ci.label)) foundHtml = true;
            if ("javascript".equals(ci.label) || "js".equals(ci.label)) foundJs = true;
            if ("json".equals(ci.label)) foundJson = true;
        }

        assertTrue("Should offer html code block language completion", foundHtml);
        assertTrue("Should offer javascript code block language completion", foundJs);
        assertTrue("Should offer json code block language completion", foundJson);
    }

    @Test
    public void testHeadingAnchorCompletions() {
        File mdFile = new File(projectRoot, "anchors.md");
        String uri = mdFile.getAbsolutePath();
        String content = "# Getting Started\n\n## Installation\n\nCheck [docs](#\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        // Line 4: Check [docs](#
        int anchorCursor = content.indexOf("(#") + 2;
        LspPosition pos = server.getDocument(uri).toPosition(anchorCursor);

        CompletionParams params = new CompletionParams(uri, pos.line, pos.character);
        List<CompletionItem> completions = server.completion(params, null);
        assertNotNull(completions);

        boolean foundGettingStarted = false;
        boolean foundInstallation = false;
        for (CompletionItem ci : completions) {
            if ("#getting-started".equals(ci.label)) foundGettingStarted = true;
            if ("#installation".equals(ci.label)) foundInstallation = true;
        }

        assertTrue("Should suggest #getting-started anchor", foundGettingStarted);
        assertTrue("Should suggest #installation anchor", foundInstallation);
    }

    @Test
    public void testHeadingAnchorCompletionsWithAngleBrackets() {
        File mdFile = new File(projectRoot, "angle_anchors.md");
        String uri = mdFile.getAbsolutePath();
        String content = "# Getting Started\n\n## Installation\n\nCheck [docs](<#\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        int anchorCursor = content.indexOf("(<#") + 3;
        LspPosition pos = server.getDocument(uri).toPosition(anchorCursor);

        CompletionParams params = new CompletionParams(uri, pos.line, pos.character);
        List<CompletionItem> completions = server.completion(params, null);
        assertNotNull(completions);

        boolean foundGettingStarted = false;
        boolean foundInstallation = false;
        for (CompletionItem ci : completions) {
            if ("#getting-started".equals(ci.label)) foundGettingStarted = true;
            if ("#installation".equals(ci.label)) foundInstallation = true;
        }

        assertTrue("Should suggest #getting-started with angle bracket", foundGettingStarted);
        assertTrue("Should suggest #installation with angle bracket", foundInstallation);
    }

    @Test
    public void testCrossFileHeadingCompletionsFromLspServer() throws Exception {
        File docsDir = new File(projectRoot, "docs");
        docsDir.mkdirs();
        File readme = new File(docsDir, "readme.md");
        com.cocode.vcode.ide.utils.FileUtils.writeFile(readme, "# Installation Guide\n");

        File mainMd = new File(projectRoot, "main.md");
        String uri = mainMd.getAbsolutePath();
        String content = "[Setup](docs/readme.md#";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        CompletionParams params = new CompletionParams(uri, 0, content.length());
        List<CompletionItem> completions = server.completion(params, null);
        assertNotNull(completions);
        assertFalse("Cross-file completion from LSP server should return suggestions", completions.isEmpty());

        boolean foundGuide = false;
        for (CompletionItem ci : completions) {
            if ("#installation-guide".equals(ci.label)) {
                foundGuide = true;
                break;
            }
        }
        assertTrue("Should suggest #installation-guide from docs/readme.md", foundGuide);
    }

    @Test
    public void testCodeActionFixAnchorSlug() {
        File mdFile = new File(projectRoot, "fix_anchor.md");
        String uri = mdFile.getAbsolutePath();
        String content = "# Getting Started\n\nCheck [link](#geting-started)\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        List<Diagnostic> diags = server.diagnostics(new DocumentDiagnosticParams(uri), null);
        assertFalse(diags.isEmpty());

        CodeActionParams params = new CodeActionParams(uri, new LspRange(0, 0, 2, 25), new CodeActionContext(diags));
        List<CodeAction> actions = server.codeAction(params, null);
        assertNotNull(actions);

        boolean foundFix = false;
        for (CodeAction a : actions) {
            if (a.title.contains("Fix anchor to '#getting-started'")) {
                foundFix = true;
                assertNotNull(a.edit);
                List<TextEdit> edits = a.edit.changes.get(uri);
                assertNotNull(edits);
                assertFalse(edits.isEmpty());
                assertEquals("#getting-started", edits.get(0).newText);
            }
        }
        assertTrue("Should offer quick fix to correct anchor slug", foundFix);
    }

    @Test
    public void testCodeActionCreateMissingFile() {
        File mdFile = new File(projectRoot, "create_file.md");
        String uri = mdFile.getAbsolutePath();
        String content = "# Title\n\n[Doc](./missing_guide.md)\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        List<Diagnostic> diags = server.diagnostics(new DocumentDiagnosticParams(uri), null);
        assertFalse(diags.isEmpty());

        CodeActionParams params = new CodeActionParams(uri, new LspRange(0, 0, 2, 25), new CodeActionContext(diags));
        List<CodeAction> actions = server.codeAction(params, null);
        assertNotNull(actions);

        boolean foundCreateAction = false;
        for (CodeAction a : actions) {
            if (a.title.contains("Create missing file")) {
                foundCreateAction = true;
                assertNotNull(a.edit);
                assertNotNull(a.edit.changes);
                assertFalse(a.edit.changes.isEmpty());
                break;
            }
        }
        assertTrue("Should offer quick fix to create missing file", foundCreateAction);
    }

    @Test
    public void testCodeActionCloseCodeFence() {
        File mdFile = new File(projectRoot, "unclosed.md");
        String uri = mdFile.getAbsolutePath();
        String content = "# Code\n\n```js\nconst x = 10;\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        List<Diagnostic> diags = server.diagnostics(new DocumentDiagnosticParams(uri), null);
        assertFalse(diags.isEmpty());

        CodeActionParams params = new CodeActionParams(uri, new LspRange(0, 0, 3, 1), new CodeActionContext(diags));
        List<CodeAction> actions = server.codeAction(params, null);
        assertNotNull(actions);

        boolean foundCloseFix = false;
        for (CodeAction a : actions) {
            if ("Close code block".equals(a.title)) {
                foundCloseFix = true;
                assertNotNull(a.edit);
                List<TextEdit> edits = a.edit.changes.get(uri);
                assertNotNull(edits);
                assertFalse(edits.isEmpty());
                assertTrue(edits.get(0).newText.contains("```"));
            }
        }
        assertTrue("Should offer quick fix to close unclosed code fence", foundCloseFix);
    }

    @Test
    public void testHoverDocumentation() {
        File mdFile = new File(projectRoot, "hover.md");
        String uri = mdFile.getAbsolutePath();
        String content = "# Architecture\n\nCheck [link](#architecture)\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        // Hover over [link](#architecture) on line 2, character 15
        Hover hover = server.hover(new HoverParams(uri, 2, 15), null);
        assertNotNull(hover);
        assertNotNull(hover.contents);
        assertTrue(hover.contents.value.contains("Heading Anchor"));
        assertTrue(hover.contents.value.contains("Architecture"));
    }

    @Test
    public void testFormattingNormalizesHeadingsAndTables() {
        File mdFile = new File(projectRoot, "messy.md");
        String uri = mdFile.getAbsolutePath();
        String content = "#Title\n* item 1\n* item 2\n\n| a | long_name |\n| --- | --- |\n| 1 | 2 |\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        List<TextEdit> edits = server.formatting(new DocumentFormattingParams(uri, null), null);
        assertNotNull(edits);
        assertEquals(1, edits.size());

        String formatted = edits.get(0).newText;
        assertTrue("Heading should have space after #", formatted.contains("# Title"));
        assertTrue("List markers should be normalized to -", formatted.contains("- item 1"));
        assertTrue("List markers should be normalized to -", formatted.contains("- item 2"));
        assertTrue("Table pipe should be formatted", formatted.contains("| a "));
    }

    @Test
    public void testGoToDefinitionForHeadingAnchor() {
        File mdFile = new File(projectRoot, "def.md");
        String uri = mdFile.getAbsolutePath();
        String content = "Intro\n\n# Target Section\n\nJump to [target](#target-section)\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        // Cursor inside [target](#target-section) at line 4, character 18
        DefinitionParams params = new DefinitionParams(uri, 4, 18);
        List<LspLocation> locs = server.definition(params, null);
        assertNotNull(locs);
        assertFalse(locs.isEmpty());

        LspLocation loc = locs.get(0);
        assertEquals(uri, loc.uri);
        assertEquals(2, loc.range.start.line); // Line 2 is "# Target Section"
    }

    @Test
    public void testFileFolderCompletionsInMarkdown() throws Exception {
        File mdFile = new File(projectRoot, "README.md");
        mdFile.createNewFile();

        LspDocument doc = new LspDocument(mdFile.getAbsolutePath(), "Check out this [link](./", "markdown", 1);
        LspPosition pos = new LspPosition(0, "Check out this [link](./".length());

        List<LspCompletionItem> completions = server.completion(doc, pos);
        assertNotNull(completions);

        boolean foundImage = false;
        boolean foundFolder = false;
        for (LspCompletionItem item : completions) {
            if (item.label.equals("image.png")) foundImage = true;
            if (item.label.equals("folder/")) foundFolder = true;
        }

        assertTrue("Should suggest image.png", foundImage);
        assertTrue("Should suggest folder/", foundFolder);
    }

    @Test
    public void testEmbeddedCodeBlockJsCompletions() throws Exception {
        File mdFile = new File(projectRoot, "embedded.md");
        mdFile.createNewFile();
        String uri = mdFile.getAbsolutePath();
        String content = "# Title\n\n```javascript\ncon\n```\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        // Line 3: "con" -> position line 3, character 3
        CompletionParams params = new CompletionParams(uri, 3, 3);
        List<CompletionItem> completions = server.completion(params, null);
        assertNotNull(completions);
        assertFalse(completions.isEmpty());

        boolean foundConsoleOrConst = false;
        boolean foundHeadingSnippet = false;
        for (CompletionItem ci : completions) {
            if ("console".equals(ci.label) || "const".equals(ci.label)) {
                foundConsoleOrConst = true;
                assertNotNull(ci.textEdit);
                assertEquals(3, ci.textEdit.range.start.line);
                assertEquals(0, ci.textEdit.range.start.character);
                assertEquals(3, ci.textEdit.range.end.character);
            }
            if (ci.label.contains("Heading 1")) {
                foundHeadingSnippet = true;
            }
        }

        assertTrue("Should suggest console or const from embedded JS engine", foundConsoleOrConst);
        assertFalse("Should suppress markdown snippets inside embedded code block", foundHeadingSnippet);
    }

    @Test
    public void testLinkFilePathSuppressesSnippetsAndAnchors() throws Exception {
        File mdFile = new File(projectRoot, "links.md");
        mdFile.createNewFile();
        String uri = mdFile.getAbsolutePath();
        String content = "# Main\n\n[Test](./\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        CompletionParams params = new CompletionParams(uri, 2, "[Test](./".length());
        List<CompletionItem> completions = server.completion(params, null);
        assertNotNull(completions);

        boolean foundImage = false;
        boolean foundAnchor = false;
        boolean foundHeading = false;
        for (CompletionItem ci : completions) {
            if ("image.png".equals(ci.label)) foundImage = true;
            if (ci.label.startsWith("#")) foundAnchor = true;
            if (ci.label.contains("Heading 1")) foundHeading = true;
        }

        assertTrue("Should suggest image.png for file path", foundImage);
        assertFalse("Should forbid heading anchors in file path context", foundAnchor);
        assertFalse("Should forbid markdown snippets in file path context", foundHeading);
    }

    @Test
    public void testHeadingAnchorSuppressesFilesAndSnippets() throws Exception {
        File mdFile = new File(projectRoot, "anchors_suppress.md");
        mdFile.createNewFile();
        String uri = mdFile.getAbsolutePath();
        String content = "# First Section\n## Second Section\n\n[Link](#sec\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        CompletionParams params = new CompletionParams(uri, 3, "[Link](#sec".length());
        List<CompletionItem> completions = server.completion(params, null);
        assertNotNull(completions);

        boolean foundAnchor = false;
        boolean foundImage = false;
        boolean foundSnippet = false;
        for (CompletionItem ci : completions) {
            if ("#second-section".equals(ci.label)) {
                foundAnchor = true;
                assertNotNull(ci.textEdit);
                assertEquals(3, ci.textEdit.range.start.line);
                assertEquals(8, ci.textEdit.range.start.character);
                assertEquals(11, ci.textEdit.range.end.character);
            }
            if ("image.png".equals(ci.label)) foundImage = true;
            if (ci.label.contains("Heading 1")) foundSnippet = true;
        }

        assertTrue("Should suggest #second-section anchor", foundAnchor);
        assertFalse("Should forbid file paths in anchor context", foundImage);
        assertFalse("Should forbid snippets in anchor context", foundSnippet);
    }

    @Test
    public void testCodeBlockLanguageFiltersAndSuppressesOtherContexts() {
        File mdFile = new File(projectRoot, "lang_filter.md");
        String uri = mdFile.getAbsolutePath();
        String content = "# Title\n\n```type\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        CompletionParams params = new CompletionParams(uri, 2, "```type".length());
        List<CompletionItem> completions = server.completion(params, null);
        assertNotNull(completions);

        boolean foundTs = false;
        boolean foundImage = false;
        boolean foundAnchor = false;
        for (CompletionItem ci : completions) {
            if ("typescript".equals(ci.label)) {
                foundTs = true;
                assertNotNull(ci.textEdit);
                assertEquals(2, ci.textEdit.range.start.line);
                assertEquals(3, ci.textEdit.range.start.character);
                assertEquals(7, ci.textEdit.range.end.character);
            }
            if ("image.png".equals(ci.label)) foundImage = true;
            if (ci.label.startsWith("#")) foundAnchor = true;
        }

        assertTrue("Should suggest typescript keyword", foundTs);
        assertFalse("Should forbid file paths in code block language context", foundImage);
        assertFalse("Should forbid anchors in code block language context", foundAnchor);
    }

    @Test
    public void testProseContextAllowsInlineSnippetsAndSuppressesLineStructural() {
        File mdFile = new File(projectRoot, "prose.md");
        String uri = mdFile.getAbsolutePath();
        String content = "Here is some bold\n";
        server.didOpen(new TextDocumentItem(uri, "markdown", 1, content));

        CompletionParams params = new CompletionParams(uri, 0, "Here is some bold".length());
        List<CompletionItem> completions = server.completion(params, null);
        assertNotNull(completions);

        boolean foundBold = false;
        boolean foundH1 = false;
        for (CompletionItem ci : completions) {
            if ("**bold**".equals(ci.label)) foundBold = true;
            if (ci.label.contains("Heading 1")) foundH1 = true;
        }

        assertTrue("Should suggest inline bold snippet in middle of prose", foundBold);
        assertFalse("Should forbid line-only structural heading snippet in middle of prose", foundH1);
    }
}
