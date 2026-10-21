package com.cocode.vcode.ide.core.quickfix;

import com.cocode.vcode.ide.core.diagnostic.ProblemCode;
import com.cocode.vcode.ide.core.lsp.model.CodeAction;
import com.cocode.vcode.ide.core.lsp.model.TextEdit;
import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link QuickFixRegistry} verifying 0-fix, 1-fix, and multi-fix
 * intentions across HTML, CSS, JS, TS, Markdown, and JSON.
 */
public class QuickFixRegistryTest {

    private final File mockFile = new File("/workspace/test.any");

    // =========================================================================
    // 0-Fix Problem Verification
    // =========================================================================

    @Test
    public void testZeroFixProblems() {
        // Syntax error - fundamentally ambiguous, no automated fix
        Problem syntaxProb = new Problem(mockFile, 1, 1, 5, "Syntax error", Problem.Severity.ERROR, ProblemCode.SYNTAX_ERROR);
        assertTrue(QuickFixRegistry.getQuickFixes(syntaxProb, "const x = ;").isEmpty());

        // Unknown CSS property - requires developer intent, no auto-fix
        Problem cssUnknown = new Problem(mockFile, 1, 1, 10, "Unknown property 'foo-bar'", Problem.Severity.WARNING, ProblemCode.CSS_UNKNOWN_PROPERTY);
        assertTrue(QuickFixRegistry.getQuickFixes(cssUnknown, ".btn { foo-bar: 1; }").isEmpty());

        // Duplicate HTML ID - requires developer semantic naming, no auto-fix
        Problem dupId = new Problem(mockFile, 2, 5, 8, "Duplicate ID 'header'", Problem.Severity.ERROR, ProblemCode.HTML_DUPLICATE_ID);
        assertTrue(QuickFixRegistry.getQuickFixes(dupId, "<div id=\"header\"></div>\n<div id=\"header\"></div>").isEmpty());

        // Undefined JS variable - could be spelling, import, or local declaration
        Problem undefVar = new Problem(mockFile, 1, 1, 4, "'testVar' is not defined", Problem.Severity.ERROR, ProblemCode.JS_UNDEFINED_VARIABLE);
        assertTrue(QuickFixRegistry.getQuickFixes(undefVar, "console.log(testVar);").isEmpty());
    }

    // =========================================================================
    // 1-Fix Problems Across Languages
    // =========================================================================

    @Test
    public void testHtmlOneFix() {
        String doc = "<html><head></head><body><img src=\"pic.png\"><button>Click</button></body></html>";

        // HTML_MISSING_ALT
        Problem altProb = new Problem(mockFile, 1, 26, 4, "img tag missing 'alt' attribute", Problem.Severity.WARNING, ProblemCode.HTML_MISSING_ALT);
        List<QuickFix> altFixes = QuickFixRegistry.getQuickFixes(altProb, doc);
        assertEquals(1, altFixes.size());
        assertTrue(altFixes.get(0).getTitle().contains("alt"));

        // HTML_BUTTON_MISSING_TYPE
        Problem btnProb = new Problem(mockFile, 1, 44, 7, "button tag missing 'type' attribute", Problem.Severity.WARNING, ProblemCode.HTML_BUTTON_MISSING_TYPE);
        List<QuickFix> btnFixes = QuickFixRegistry.getQuickFixes(btnProb, doc);
        assertEquals(1, btnFixes.size());
        assertTrue(btnFixes.get(0).getTitle().contains("type=\"button\""));

        // HTML_MISSING_CHARSET
        Problem charsetProb = new Problem(mockFile, 1, 1, 6, "Missing charset <meta> tag", Problem.Severity.WARNING, ProblemCode.HTML_MISSING_CHARSET);
        List<QuickFix> charsetFixes = QuickFixRegistry.getQuickFixes(charsetProb, doc);
        assertEquals(1, charsetFixes.size());
        assertTrue(charsetFixes.get(0).getTitle().contains("charset"));

        // HTML_VOID_ELEMENT_CLOSING
        String voidDoc = "<br/>";
        Problem voidProb = new Problem(mockFile, 1, 1, 5, "Unnecessary self-closing slash", Problem.Severity.INFO, ProblemCode.HTML_VOID_ELEMENT_CLOSING);
        List<QuickFix> voidFixes = QuickFixRegistry.getQuickFixes(voidProb, voidDoc);
        assertEquals(1, voidFixes.size());
        assertTrue(voidFixes.get(0).getTitle().contains("self-closing slash"));
    }

    @Test
    public void testCssOneFix() {
        // CSS_ZERO_UNIT
        String zeroDoc = ".card { margin: 0px; }";
        Problem zeroProb = new Problem(mockFile, 1, 17, 3, "Unexpected unit 'px' on 0", Problem.Severity.INFO, ProblemCode.CSS_ZERO_UNIT);
        List<QuickFix> zeroFixes = QuickFixRegistry.getQuickFixes(zeroProb, zeroDoc);
        assertEquals(1, zeroFixes.size());
        assertEquals("Remove unit from zero value", zeroFixes.get(0).getTitle());

        // CSS_IMPORTANT_USAGE
        String impDoc = ".card { color: red !important; }";
        Problem impProb = new Problem(mockFile, 1, 20, 10, "Avoid !important", Problem.Severity.WARNING, ProblemCode.CSS_IMPORTANT_USAGE);
        List<QuickFix> impFixes = QuickFixRegistry.getQuickFixes(impProb, impDoc);
        assertEquals(1, impFixes.size());
        assertEquals("Remove '!important'", impFixes.get(0).getTitle());

        // CSS_HEX_SHORTENABLE
        String hexDoc = ".card { color: #ffffff; }";
        Problem hexProb = new Problem(mockFile, 1, 16, 7, "Hex color '#ffffff' can be shortened", Problem.Severity.INFO, ProblemCode.CSS_HEX_SHORTENABLE);
        List<QuickFix> hexFixes = QuickFixRegistry.getQuickFixes(hexProb, hexDoc);
        assertEquals(1, hexFixes.size());
        assertEquals("Shorten hex color to '#fff'", hexFixes.get(0).getTitle());

        // CSS_VENDOR_PREFIX_NEEDED
        String vendorDoc = ".card { transform: scale(1); }";
        Problem vendorProb = new Problem(mockFile, 1, 9, 9, "'transform' may need '-webkit-transform' for broader browser support", Problem.Severity.WARNING, ProblemCode.CSS_VENDOR_PREFIX_NEEDED);
        List<QuickFix> vendorFixes = QuickFixRegistry.getQuickFixes(vendorProb, vendorDoc);
        assertEquals(1, vendorFixes.size());
        assertEquals("Add standard property declaration", vendorFixes.get(0).getTitle());
        TestEditorTarget editorVendor = new TestEditorTarget(vendorDoc);
        vendorFixes.get(0).apply(editorVendor, vendorProb);
        assertTrue(editorVendor.getText().toString().contains("transform: scale(1);"));
        assertTrue(editorVendor.getText().toString().contains("-webkit-transform: scale(1);"));
    }

    @Test
    public void testJsOneFix() {
        // JS_UNUSED_VARIABLE
        String jsDoc = "const unused = 42;";
        Problem unusedProb = new Problem(mockFile, 1, 7, 6, "Variable 'unused' is declared but never read", Problem.Severity.WARNING, ProblemCode.JS_UNUSED_VARIABLE);
        List<QuickFix> unusedFixes = QuickFixRegistry.getQuickFixes(unusedProb, jsDoc);
        assertEquals(1, unusedFixes.size());
        assertEquals("Remove unused variable", unusedFixes.get(0).getTitle());

        // JS_CONST_REASSIGNMENT
        String reassignDoc = "const a = 1; a = 2;";
        Problem reassignProb = new Problem(mockFile, 1, 14, 1, "Assignment to constant variable 'a'", Problem.Severity.ERROR, ProblemCode.JS_CONST_REASSIGNMENT);
        List<QuickFix> reassignFixes = QuickFixRegistry.getQuickFixes(reassignProb, reassignDoc);
        assertEquals(1, reassignFixes.size());
        assertEquals("Change 'const a' declaration to 'let'", reassignFixes.get(0).getTitle());

        // JS_MISSING_SEMICOLON
        String semiDoc = "const a = 1\n";
        Problem semiProb = new Problem(mockFile, 1, 11, 1, "Missing semicolon", Problem.Severity.INFO, ProblemCode.JS_MISSING_SEMICOLON);
        List<QuickFix> semiFixes = QuickFixRegistry.getQuickFixes(semiProb, semiDoc);
        assertEquals(1, semiFixes.size());
        assertEquals("Insert missing semicolon ';'", semiFixes.get(0).getTitle());
    }

    @Test
    public void testTsOneFix() {
        // TS_INTERFACE_NOT_IMPLEMENTED
        Problem ifaceProb = new Problem(mockFile, 1, 1, 10, "Class 'Foo' incorrectly implements interface 'Bar'", Problem.Severity.ERROR, ProblemCode.TS_INTERFACE_NOT_IMPLEMENTED);
        List<QuickFix> ifaceFixes = QuickFixRegistry.getQuickFixes(ifaceProb, "class Foo implements Bar {}");
        assertEquals(1, ifaceFixes.size());
        assertEquals("Implement missing members", ifaceFixes.get(0).getTitle());

        // TS_REDUNDANT_TYPE
        Problem redTypeProb = new Problem(mockFile, 1, 7, 10, "Redundant type annotation", Problem.Severity.INFO, ProblemCode.TS_REDUNDANT_TYPE);
        List<QuickFix> redTypeFixes = QuickFixRegistry.getQuickFixes(redTypeProb, "const flag: boolean = true;");
        assertEquals(1, redTypeFixes.size());
        assertEquals("Remove redundant type annotation", redTypeFixes.get(0).getTitle());

        // TS_UNION_UNDEFINED: converts to optional and removes "| undefined"
        String fnDoc = "function f(arg: string | undefined) {}";
        Problem unionProb = new Problem(mockFile, 1, 12, 3, "Parameter can be optional instead of union with undefined", Problem.Severity.INFO, ProblemCode.TS_UNION_UNDEFINED);
        List<QuickFix> unionFixes = QuickFixRegistry.getQuickFixes(unionProb, fnDoc);
        assertEquals(1, unionFixes.size());
        assertEquals("Convert parameter to optional '?:'", unionFixes.get(0).getTitle());
        TestEditorTarget editorUnion = new TestEditorTarget(fnDoc);
        unionFixes.get(0).apply(editorUnion, unionProb);
        assertEquals("function f(arg?: string) {}", editorUnion.getText().toString());

        // TS_READONLY_ARRAY: inserts "readonly " before property name
        String roDoc = "interface List { items: string[]; }";
        Problem roProb = new Problem(mockFile, 1, 18, 5, "Array<T> should be readonly", Problem.Severity.INFO, ProblemCode.TS_READONLY_ARRAY);
        List<QuickFix> roFixes = QuickFixRegistry.getQuickFixes(roProb, roDoc);
        assertEquals(1, roFixes.size());
        TestEditorTarget editorRo = new TestEditorTarget(roDoc);
        roFixes.get(0).apply(editorRo, roProb);
        assertEquals("interface List { readonly items: string[]; }", editorRo.getText().toString());

        // TS_MISSING_RETURN_TYPE: inserts ": void" after closing parenthesis
        String fnRetDoc = "export function doWork() {}";
        Problem retProb = new Problem(mockFile, 1, 17, 6, "Missing return type", Problem.Severity.WARNING, ProblemCode.TS_MISSING_RETURN_TYPE);
        List<QuickFix> retFixes = QuickFixRegistry.getQuickFixes(retProb, fnRetDoc);
        assertEquals(1, retFixes.size());
        TestEditorTarget editorRet = new TestEditorTarget(fnRetDoc);
        retFixes.get(0).apply(editorRet, retProb);
        assertEquals("export function doWork(): void {}", editorRet.getText().toString());
    }

    @Test
    public void testMarkdownOneFix() {
        // MD_UNCLOSED_CODE_BLOCK
        Problem unclosedBlock = new Problem(mockFile, 1, 1, 3, "Unclosed code block", Problem.Severity.ERROR, ProblemCode.MD_UNCLOSED_CODE_BLOCK);
        List<QuickFix> blockFixes = QuickFixRegistry.getQuickFixes(unclosedBlock, "```js\nconsole.log(1);");
        assertEquals(1, blockFixes.size());
        assertEquals("Close code block", blockFixes.get(0).getTitle());

        // MD_TRAILING_WHITESPACE
        Problem trailingProb = new Problem(mockFile, 1, 5, 2, "Trailing whitespace detected", Problem.Severity.INFO, ProblemCode.MD_TRAILING_WHITESPACE);
        List<QuickFix> trailingFixes = QuickFixRegistry.getQuickFixes(trailingProb, "line  \n");
        assertEquals(1, trailingFixes.size());
        assertEquals("Trim trailing whitespace", trailingFixes.get(0).getTitle());

        // MD_HEADING_INCREMENT: replaces only heading hashes, preserves title text
        String mdDoc = "### My Title";
        Problem mdProb = new Problem(mockFile, 1, 1, 12, "Heading levels should only increment by one level", Problem.Severity.WARNING, ProblemCode.MD_HEADING_INCREMENT);
        List<QuickFix> mdFixes = QuickFixRegistry.getQuickFixes(mdProb, mdDoc);
        assertEquals(1, mdFixes.size());
        TestEditorTarget editorMd = new TestEditorTarget(mdDoc);
        mdFixes.get(0).apply(editorMd, mdProb);
        assertEquals("## My Title", editorMd.getText().toString());
    }

    @Test
    public void testJsonOneFix() {
        // JSON_DUPLICATE_KEY
        String jsonDup = "{\n  \"a\": 1,\n  \"a\": 2\n}";
        Problem dupProb = new Problem(mockFile, 3, 3, 3, "Duplicate key 'a'", Problem.Severity.WARNING, ProblemCode.JSON_DUPLICATE_KEY);
        List<QuickFix> dupFixes = QuickFixRegistry.getQuickFixes(dupProb, jsonDup);
        assertEquals(1, dupFixes.size());
        assertEquals("Remove duplicate key", dupFixes.get(0).getTitle());

        // JSON_COMMENTS_DISALLOWED
        String jsonComm = "{\n  // comment\n  \"a\": 1\n}";
        Problem commProb = new Problem(mockFile, 2, 3, 10, "Comments are not permitted in standard JSON", Problem.Severity.ERROR, ProblemCode.JSON_COMMENTS_DISALLOWED);
        List<QuickFix> commFixes = QuickFixRegistry.getQuickFixes(commProb, jsonComm);
        assertEquals(1, commFixes.size());
        assertEquals("Remove disallowed JSON comment", commFixes.get(0).getTitle());

        // JSON_MULTIPLE_ROOTS
        String jsonMulti = "{\"a\": 1}\n{\"b\": 2}";
        Problem multiProb = new Problem(mockFile, 2, 1, 8, "Multiple JSON root values", Problem.Severity.ERROR, ProblemCode.JSON_MULTIPLE_ROOTS);
        List<QuickFix> multiFixes = QuickFixRegistry.getQuickFixes(multiProb, jsonMulti);
        assertEquals(1, multiFixes.size());
        assertEquals("Wrap multiple JSON values in array '[ ... ]'", multiFixes.get(0).getTitle());

        // JSON_TRAILING_COMMA
        String jsonDoc = "{\n  \"a\": 1,\n}";
        Problem commaProb = new Problem(mockFile, 2, 10, 1, "Trailing comma is not permitted", Problem.Severity.ERROR, ProblemCode.JSON_TRAILING_COMMA);
        List<QuickFix> commaFixes = QuickFixRegistry.getQuickFixes(commaProb, jsonDoc);
        assertEquals(1, commaFixes.size());
        TestEditorTarget editorComma = new TestEditorTarget(jsonDoc);
        commaFixes.get(0).apply(editorComma, commaProb);
        assertEquals("{\n  \"a\": 1\n}", editorComma.getText().toString());

        // JSON_SINGLE_QUOTES
        String sqDoc = "{\n  'a': 'val'\n}";
        Problem sqProb = new Problem(mockFile, 2, 3, 3, "Strings must use double quotes", Problem.Severity.ERROR, ProblemCode.JSON_SINGLE_QUOTES);
        List<QuickFix> sqFixes = QuickFixRegistry.getQuickFixes(sqProb, sqDoc);
        assertEquals(1, sqFixes.size());
        TestEditorTarget editorSq = new TestEditorTarget(sqDoc);
        sqFixes.get(0).apply(editorSq, sqProb);
        assertTrue(editorSq.getText().toString().contains("\"a\""));

        // JSON_UNQUOTED_KEY
        String uqDoc = "{\n  key: 1\n}";
        Problem uqProb = new Problem(mockFile, 2, 3, 3, "Enclose key in double quotes", Problem.Severity.ERROR, ProblemCode.JSON_UNQUOTED_KEY);
        List<QuickFix> uqFixes = QuickFixRegistry.getQuickFixes(uqProb, uqDoc);
        assertEquals(1, uqFixes.size());
        TestEditorTarget editorUq = new TestEditorTarget(uqDoc);
        uqFixes.get(0).apply(editorUq, uqProb);
        assertTrue(editorUq.getText().toString().contains("\"key\""));

        // JSON_MISSING_PROPERTY
        String mpDoc = "{\n  \"name\": \"app\"\n}";
        Problem mpProb = new Problem(mockFile, 1, 1, 1, "Missing required property 'version'", Problem.Severity.WARNING, ProblemCode.JSON_MISSING_PROPERTY);
        List<QuickFix> mpFixes = QuickFixRegistry.getQuickFixes(mpProb, mpDoc);
        assertEquals(1, mpFixes.size());
        TestEditorTarget editorMp = new TestEditorTarget(mpDoc);
        mpFixes.get(0).apply(editorMp, mpProb);
        assertTrue(editorMp.getText().toString().contains("\"version\": \"\""));
    }

    // =========================================================================
    // Multi-Fix Problems (>1 Intentions)
    // =========================================================================

    @Test
    public void testHtmlMultiFix() {
        // HTML_SCRIPT_MISSING_DEFER -> defer vs async
        String scriptDoc = "<html><head><script src=\"app.js\"></script></head><body></body></html>";
        Problem scriptProb = new Problem(mockFile, 1, 13, 6, "Script tag missing defer or async attribute", Problem.Severity.WARNING, ProblemCode.HTML_SCRIPT_MISSING_DEFER);
        List<QuickFix> scriptFixes = QuickFixRegistry.getQuickFixes(scriptProb, scriptDoc);
        assertEquals(2, scriptFixes.size());
        assertEquals("Add 'defer' attribute to <script>", scriptFixes.get(0).getTitle());
        assertEquals("Add 'async' attribute to <script>", scriptFixes.get(1).getTitle());

        // HTML_ANCHOR_AS_BUTTON -> button vs role="button"
        String anchorDoc = "<a href=\"#\" onclick=\"doSomething()\">Click</a>";
        Problem anchorProb = new Problem(mockFile, 1, 1, 2, "Anchor with href='#' should be a button", Problem.Severity.WARNING, ProblemCode.HTML_ANCHOR_AS_BUTTON);
        List<QuickFix> anchorFixes = QuickFixRegistry.getQuickFixes(anchorProb, anchorDoc);
        assertEquals(2, anchorFixes.size());
        assertEquals("Add role=\"button\" to anchor", anchorFixes.get(0).getTitle());
        assertEquals("Convert <a> to <button type=\"button\">", anchorFixes.get(1).getTitle());

        // HTML_DEPRECATED_TAG (strike) -> s vs del
        String strikeDoc = "<strike>old text</strike>";
        Problem strikeProb = new Problem(mockFile, 1, 1, 8, "Deprecated HTML tag '<strike>'", Problem.Severity.WARNING, ProblemCode.HTML_DEPRECATED_TAG);
        List<QuickFix> strikeFixes = QuickFixRegistry.getQuickFixes(strikeProb, strikeDoc);
        assertEquals(2, strikeFixes.size());
        assertEquals("Replace <strike> with <s>", strikeFixes.get(0).getTitle());
        assertEquals("Replace <strike> with <del>", strikeFixes.get(1).getTitle());

        TestEditorTarget editorS = new TestEditorTarget(strikeDoc);
        strikeFixes.get(0).apply(editorS, strikeProb);
        assertEquals("<s>old text</s>", editorS.getText().toString());

        TestEditorTarget editorDel = new TestEditorTarget(strikeDoc);
        strikeFixes.get(1).apply(editorDel, strikeProb);
        assertEquals("<del>old text</del>", editorDel.getText().toString());
    }

    @Test
    public void testCssMultiFix() {
        // CSS_PT_UNIT -> px vs rem
        String ptDoc = ".title { font-size: 12pt; }";
        Problem ptProb = new Problem(mockFile, 1, 21, 4, "Avoid 'pt' unit in screen CSS", Problem.Severity.WARNING, ProblemCode.CSS_PT_UNIT);
        List<QuickFix> ptFixes = QuickFixRegistry.getQuickFixes(ptProb, ptDoc);
        assertEquals(2, ptFixes.size());
        assertEquals("Convert 'pt' to 'px' (16px)", ptFixes.get(0).getTitle());
        assertEquals("Convert 'pt' to 'rem' (1.0rem)", ptFixes.get(1).getTitle());

        // CSS_VAR_NO_FALLBACK -> initial vs inherit
        String varDoc = ".card { color: var(--theme-color); }";
        Problem varProb = new Problem(mockFile, 1, 16, 18, "CSS variable custom property lacks a fallback", Problem.Severity.INFO, ProblemCode.CSS_VAR_NO_FALLBACK);
        List<QuickFix> varFixes = QuickFixRegistry.getQuickFixes(varProb, varDoc);
        assertEquals(2, varFixes.size());
        assertEquals("Add 'initial' fallback to var()", varFixes.get(0).getTitle());
        assertEquals("Add 'inherit' fallback to var()", varFixes.get(1).getTitle());
    }

    @Test
    public void testJsMultiFix() {
        // JS_CONSOLE_STATEMENT -> remove vs comment out
        String consoleDoc = "console.log('debug');";
        Problem consoleProb = new Problem(mockFile, 1, 1, 21, "Unexpected console statement", Problem.Severity.WARNING, ProblemCode.JS_CONSOLE_STATEMENT);
        List<QuickFix> consoleFixes = QuickFixRegistry.getQuickFixes(consoleProb, consoleDoc);
        assertEquals(2, consoleFixes.size());
        assertEquals("Remove console statement", consoleFixes.get(0).getTitle());
        assertEquals("Comment out console statement", consoleFixes.get(1).getTitle());
    }

    @Test
    public void testTsMultiFix() {
        // TS_EXPLICIT_ANY -> unknown vs Record<string, unknown>
        String anyDoc = "function process(data: any): void {}";
        Problem anyProb = new Problem(mockFile, 1, 24, 3, "Explicit 'any' is forbidden", Problem.Severity.WARNING, ProblemCode.TS_EXPLICIT_ANY);
        List<QuickFix> anyFixes = QuickFixRegistry.getQuickFixes(anyProb, anyDoc);
        assertEquals(2, anyFixes.size());
        assertEquals("Replace 'any' with 'unknown'", anyFixes.get(0).getTitle());
        assertEquals("Replace 'any' with 'Record<string, unknown>'", anyFixes.get(1).getTitle());

        // TS_NON_NULL_ASSERTION -> optional chaining vs remove !
        String nonNullDoc = "const name = user!.name;";
        Problem nonNullProb = new Problem(mockFile, 1, 18, 1, "Forbidden non-null assertion", Problem.Severity.WARNING, ProblemCode.TS_NON_NULL_ASSERTION);
        List<QuickFix> nonNullFixes = QuickFixRegistry.getQuickFixes(nonNullProb, nonNullDoc);
        assertEquals(2, nonNullFixes.size());
        assertEquals("Replace '!' with optional chaining '?.'", nonNullFixes.get(0).getTitle());
        assertEquals("Remove non-null assertion '!'", nonNullFixes.get(1).getTitle());
    }

    @Test
    public void testMarkdownMultiFix() {
        // MD_RAW_URL -> angle brackets vs markdown link
        String urlDoc = "Visit https://example.com for info.";
        Problem urlProb = new Problem(mockFile, 1, 7, 19, "Bare URL found", Problem.Severity.INFO, ProblemCode.MD_RAW_URL);
        List<QuickFix> urlFixes = QuickFixRegistry.getQuickFixes(urlProb, urlDoc);
        assertEquals(2, urlFixes.size());
        assertEquals("Enclose URL in angle brackets <https://example.com>", urlFixes.get(0).getTitle());
        assertEquals("Convert URL to Markdown link [https://example.com](https://example.com)", urlFixes.get(1).getTitle());

        // MD_HARD_TAB -> 2 spaces vs 4 spaces
        String tabDoc = "\t# Header";
        Problem tabProb = new Problem(mockFile, 1, 1, 1, "Hard tab characters", Problem.Severity.INFO, ProblemCode.MD_HARD_TAB);
        List<QuickFix> tabFixes = QuickFixRegistry.getQuickFixes(tabProb, tabDoc);
        assertEquals(2, tabFixes.size());
        assertEquals("Convert tab to 2 spaces", tabFixes.get(0).getTitle());
        assertEquals("Convert tab to 4 spaces", tabFixes.get(1).getTitle());
    }

    // =========================================================================
    // LSP CodeAction Mapping & WorkspaceEdit Validation
    // =========================================================================

    @Test
    public void testGetCodeActionsProducesValidWorkspaceEdit() {
        String doc = "const unused = 42;";
        Problem prob = new Problem(mockFile, 1, 7, 6, "Variable 'unused' is declared but never read", Problem.Severity.WARNING, ProblemCode.JS_UNUSED_VARIABLE);

        List<CodeAction> actions = QuickFixRegistry.getCodeActions(prob, mockFile.getAbsolutePath(), doc);
        assertNotNull(actions);
        assertFalse(actions.isEmpty());

        CodeAction action = actions.get(0);
        assertEquals("Remove unused variable", action.title);
        assertNotNull("WorkspaceEdit should be present", action.edit);
        assertNotNull("Changes should include target URI", action.edit.changes.get(mockFile.getAbsolutePath()));

        List<TextEdit> edits = action.edit.changes.get(mockFile.getAbsolutePath());
        assertFalse(edits.isEmpty());
        TextEdit textEdit = edits.get(0);
        assertEquals("", textEdit.newText); // removal
    }

    // =========================================================================
    // CSS ID Selector & Empty Rule Targeted Tests
    // =========================================================================

    @Test
    public void testCssIdSelectorSingle() {
        String doc = "#header { color: red; }";
        Problem prob = new Problem(mockFile, 1, 1, 7, "Avoid using ID selectors ('#id') for styling: prefer class selectors", Problem.Severity.INFO, ProblemCode.CSS_ID_SELECTOR);
        List<QuickFix> fixes = QuickFixRegistry.getQuickFixes(prob, doc);
        assertEquals(1, fixes.size());
        assertEquals("Convert '#header' to '.header' class selector", fixes.get(0).getTitle());

        TestEditorTarget editor = new TestEditorTarget(doc);
        fixes.get(0).apply(editor, prob);
        assertEquals(".header { color: red; }", editor.getText().toString());
    }

    @Test
    public void testCssIdSelectorMultipleNoDuplicateDescriptions() {
        String doc = "#header, #footer { margin: 0; }";
        Problem prob = new Problem(mockFile, 1, 1, 16, "Avoid using ID selectors ('#id') for styling: prefer class selectors", Problem.Severity.INFO, ProblemCode.CSS_ID_SELECTOR);
        List<QuickFix> fixes = QuickFixRegistry.getQuickFixes(prob, doc);
        assertEquals(3, fixes.size());
        assertEquals("Convert '#header' to '.header' class selector", fixes.get(0).getTitle());
        assertEquals("Convert '#footer' to '.footer' class selector", fixes.get(1).getTitle());
        assertEquals("Convert all ID selectors to class selectors", fixes.get(2).getTitle());

        TestEditorTarget editor = new TestEditorTarget(doc);
        fixes.get(2).apply(editor, prob);
        assertEquals(".header, .footer { margin: 0; }", editor.getText().toString());
    }

    @Test
    public void testCssAttributeSelectorWithHashNotFlaggedAsId() {
        String doc = "a[href=\"#top\"] { color: blue; }";
        List<QuickFixRegistry.IdSelectorInfo> ids = QuickFixRegistry.findIdSelectorsInSlice("a[href=\"#top\"]", 0);
        assertTrue(ids.isEmpty());

        Problem prob = new Problem(mockFile, 1, 1, 14, "Avoid using ID selectors ('#id') for styling: prefer class selectors", Problem.Severity.INFO, ProblemCode.CSS_ID_SELECTOR);
        List<QuickFix> fixes = QuickFixRegistry.getQuickFixes(prob, doc);
        assertTrue(fixes.isEmpty());
    }

    @Test
    public void testCssEmptyRuleCleanDeleteSingleLine() {
        String doc = ".empty {}\n.card { color: red; }";
        Problem prob = new Problem(mockFile, 1, 1, 9, "Empty rule for selector '.empty'", Problem.Severity.WARNING, ProblemCode.CSS_EMPTY_RULE);
        List<QuickFix> fixes = QuickFixRegistry.getQuickFixes(prob, doc);
        assertEquals(1, fixes.size());
        assertEquals("Remove empty rule block", fixes.get(0).getTitle());

        TestEditorTarget editor = new TestEditorTarget(doc);
        fixes.get(0).apply(editor, prob);
        assertEquals(".card { color: red; }", editor.getText().toString());
    }

    @Test
    public void testCssEmptyRuleCleanDeleteMultilineIndented() {
        String doc = ".first {\n  color: red;\n}\n\n.empty-box {\n  \n}\n\n.last {\n  color: blue;\n}\n";
        Problem prob = new Problem(mockFile, 5, 1, 10, "Empty rule for selector '.empty-box'", Problem.Severity.WARNING, ProblemCode.CSS_EMPTY_RULE);
        List<QuickFix> fixes = QuickFixRegistry.getQuickFixes(prob, doc);
        assertEquals(1, fixes.size());

        TestEditorTarget editor = new TestEditorTarget(doc);
        fixes.get(0).apply(editor, prob);
        String result = editor.getText().toString();
        assertFalse(result.contains(".empty-box"));
        assertTrue(result.contains(".first {\n  color: red;\n}"));
        assertTrue(result.contains(".last {\n  color: blue;\n}"));
    }

    @Test
    public void testComputeCleanDeleteRangeCrlf() {
        String doc = ".empty {\r\n}\r\n.next {\r\n  color: red;\r\n}\r\n";
        int ruleEnd = doc.indexOf('}') + 1;
        int[] range = QuickFixRegistry.computeCleanDeleteRange(doc, 0, ruleEnd);
        assertEquals(0, range[0]);
        assertEquals(doc.indexOf(".next"), range[1]);
    }

    // =========================================================================
    // In-Memory Test Editor Target Stub
    // =========================================================================

    private static class TestEditorTarget implements CodeEditorTarget {
        private final StringBuilder buffer;

        public TestEditorTarget(String initialText) {
            this.buffer = new StringBuilder(initialText != null ? initialText : "");
        }

        @Override
        public int getLineCount() {
            if (buffer.length() == 0) return 1;
            int count = 1;
            for (int i = 0; i < buffer.length(); i++) {
                if (buffer.charAt(i) == '\n') count++;
            }
            return count;
        }

        @Override
        public String getLineText(int line) {
            int cur = 0;
            int start = 0;
            while (cur < line && start < buffer.length()) {
                int nl = buffer.indexOf("\n", start);
                if (nl == -1) return "";
                start = nl + 1;
                cur++;
            }
            if (cur != line) return "";
            int end = buffer.indexOf("\n", start);
            if (end == -1) end = buffer.length();
            if (end > start && buffer.charAt(end - 1) == '\r') {
                end--;
            }
            return buffer.substring(start, end);
        }

        @Override
        public void replaceRange(int startLine, int startCol, int endLine, int endCol, String text) {
            int startOff = QuickFixRegistry.positionToOffset(buffer.toString(), startLine, startCol);
            int endOff = QuickFixRegistry.positionToOffset(buffer.toString(), endLine, endCol);
            startOff = Math.min(startOff, buffer.length());
            endOff = Math.min(endOff, buffer.length());
            if (startOff > endOff) startOff = endOff;
            buffer.replace(startOff, endOff, text != null ? text : "");
        }

        @Override
        public void insertText(String text) {
            if (text != null) {
                buffer.append(text);
            }
        }

        @Override
        public void beginAtomicGroup() {
        }

        @Override
        public void endAtomicGroup() {
        }

        @Override
        public CharSequence getText() {
            return buffer;
        }

        @Override
        public android.content.Context getContext() {
            return null;
        }
    }
}
