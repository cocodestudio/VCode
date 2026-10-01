package com.cocode.vcode.ide.core.diagnostic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.language.css.CssLinter;
import com.cocode.vcode.ide.core.language.html.HtmlLinter;
import com.cocode.vcode.ide.core.language.js.JsLinter;
import com.cocode.vcode.ide.core.language.json.JsonLinter;
import com.cocode.vcode.ide.core.language.md.MarkdownLinter;
import com.cocode.vcode.ide.core.language.ts.TsLinter;
import com.cocode.vcode.ide.core.model.Problem;

import org.junit.Test;

import java.io.File;
import java.util.List;

/**
 * Tests diagnostic error ranges and squiggly line positions across linters
 * (HTML, CSS, JS, TS, JSON, Markdown).
 */
public class DiagnosticPositionTest {

    private final File mockFile = new File("test_position");

    private String getHighlightedText(String text, Problem p) {
        int lineStart = LinterUtils.lineStartOffset(text, p.getLine());
        int charStart = lineStart + p.getColumn() - 1;
        int charEnd = charStart + p.getLength();
        assertTrue("charStart (" + charStart + ") must be within bounds [0, " + text.length() + "] for message: " + p.getMessage(),
                charStart >= 0 && charStart <= text.length());
        assertTrue("charEnd (" + charEnd + ") must be within bounds [" + charStart + ", " + text.length() + "] for message: " + p.getMessage(),
                charEnd >= charStart && charEnd <= text.length());
        return text.substring(charStart, charEnd);
    }

    private Problem findProblemContaining(List<Problem> problems, String snippet) {
        for (Problem p : problems) {
            if (p.getMessage().contains(snippet)) {
                return p;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Expected diagnostic containing '").append(snippet).append("' but got:\n");
        for (Problem p : problems) {
            sb.append(" - [L").append(p.getLine()).append(":C").append(p.getColumn())
                    .append(" len=").append(p.getLength()).append("] ")
                    .append(p.getMessage()).append("\n");
        }
        fail(sb.toString());
        return null;
    }

    private void assertSquigglyMatches(String text, List<Problem> problems, String snippet, String expectedUnderline) {
        Problem p = findProblemContaining(problems, snippet);
        assertNotNull(p);
        String actualUnderline = getHighlightedText(text, p);
        assertEquals("Squiggly underline for '" + snippet + "' did not match expected target text",
                expectedUnderline, actualUnderline);
    }

    // =========================================================================
    // HTML Squiggly Position Tests
    // =========================================================================

    @Test
    public void testHtmlUnclosedTagPosition() {
        String code = "<div>\n    <p>Unclosed paragraph\n</div>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Unclosed tag '<p>'", "<p>");
    }

    @Test
    public void testHtmlStrayClosingTagPosition() {
        String code = "<div>\n    </span>\n</div>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Stray closing tag '</span>'", "</span>");
    }

    @Test
    public void testHtmlVoidElementClosingTagPosition() {
        String code = "<div>\n    <br></br>\n</div>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "is a void element and cannot have a closing tag", "</br>");
    }

    @Test
    public void testHtmlDuplicateAttributePosition() {
        String code = "<div class=\"first\" class=\"second\"></div>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Duplicate attribute 'class'", "class");
    }

    @Test
    public void testHtmlDuplicateIdPosition() {
        String code = "<div id=\"main-card\"></div>\n<span id=\"main-card\"></span>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Duplicate id='main-card'", "id=\"main-card\"");
    }

    @Test
    public void testHtmlUnknownElementPosition() {
        String code = "<unknowncustomtag>Content</unknowncustomtag>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "is not a valid HTML5 element", "<unknowncustomtag>");
    }

    @Test
    public void testHtmlDeprecatedElementPosition() {
        String code = "<marquee>Scrolling banner</marquee>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "is deprecated — use CSS animations instead", "<marquee>");
    }

    @Test
    public void testHtmlRequiredParentPosition() {
        String code = "<div>\n    <li>Orphan item</li>\n</div>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "must be a child of", "<li>");
    }

    @Test
    public void testHtmlNestedInteractiveElementPosition() {
        String code = "<a href=\"#outer\">\n    <a href=\"#inner\">Link</a>\n</a>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "cannot be nested inside another", "<a");
    }

    @Test
    public void testHtmlMissingLangAttributePosition() {
        String code = "<html>\n<head><title>Test</title></head>\n<body></body>\n</html>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "is missing 'lang' attribute", "<html>");
    }

    @Test
    public void testHtmlImageMissingAltPosition() {
        String code = "<img src=\"avatar.png\">";
        List<Problem> problems = HtmlLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "is missing 'alt' attribute", "<img");
    }

    @Test
    public void testHtmlImageMissingSrcPosition() {
        String code = "<img alt=\"avatar\">";
        List<Problem> problems = HtmlLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "is missing required attribute 'src'", "<img");
    }

    @Test
    public void testHtmlButtonMissingTypePosition() {
        String code = "<button>Click Me</button>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "is missing 'type' attribute", "<button>");
    }

    @Test
    public void testHtmlHeadMissingTitlePosition() {
        String code = "<head>\n    <meta charset=\"utf-8\">\n</head>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Missing '<title>' in <head>", "<head>");
    }

    @Test
    public void testHtmlLabelMissingTargetPosition() {
        String code = "<label>Username</label>";
        List<Problem> problems = HtmlLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "is missing 'for' attribute", "<label>");
    }

    // =========================================================================
    // CSS Squiggly Position Tests
    // =========================================================================

    @Test
    public void testCssUnknownPropertyPosition() {
        String code = ".container {\n    colour: red;\n}";
        List<Problem> problems = CssLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Unknown CSS property 'colour'", "colour");
    }

    @Test
    public void testCssDeprecatedPropertyPosition() {
        String code = ".container {\n    clip: rect(0, 0, 0, 0);\n}";
        List<Problem> problems = CssLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "'clip' is deprecated", "clip");
    }

    @Test
    public void testCssDuplicatePropertyPosition() {
        String code = ".container {\n    color: red;\n    color: blue;\n}";
        List<Problem> problems = CssLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Duplicate property 'color'", "color");
    }

    @Test
    public void testCssInvalidColorValuePosition() {
        String code = ".container {\n    color: notacolor;\n}";
        List<Problem> problems = CssLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Invalid color value 'notacolor'", "notacolor");
    }

    @Test
    public void testCssInvalidBorderValuePosition() {
        String code = ".container {\n    border: 2px unknownstyle red;\n}";
        List<Problem> problems = CssLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Invalid border value", "2px unknownstyle red");
    }

    @Test
    public void testCssImportantUsagePosition() {
        String code = ".container {\n    color: red !important;\n}";
        List<Problem> problems = CssLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "'!important' on 'color'", "!important");
    }

    @Test
    public void testCssZeroPxPosition() {
        String code = ".container {\n    margin: 0px;\n}";
        List<Problem> problems = CssLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "'0px' — units are unnecessary", "0px");
    }

    @Test
    public void testCssPtUnitPosition() {
        String code = ".container {\n    font-size: 14pt;\n}";
        List<Problem> problems = CssLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Avoid using 'pt' units", "14pt");
    }

    @Test
    public void testCssUnclosedBlockPosition() {
        String code = ".container {\n    color: red;";
        List<Problem> problems = CssLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Unclosed CSS block '{'", "{");
    }

    @Test
    public void testCssEmptyRulesetPosition() {
        String code = ".empty-class {}";
        List<Problem> problems = CssLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Empty rule for selector", ".empty-class");
    }

    @Test
    public void testCssVariableFallbackPosition() {
        String code = ".container {\n    color: var(--primary-color);\n}";
        List<Problem> problems = CssLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "used without a fallback value", "var(--primary-color)");
    }

    // =========================================================================
    // JavaScript Squiggly Position Tests
    // =========================================================================

    @Test
    public void testJsUndefinedVariablePosition() {
        String code = "function compute() {\n    return missingVariable + 1;\n}";
        List<Problem> problems = JsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "'missingVariable' is not defined", "missingVariable");
    }

    @Test
    public void testJsDuplicateDeclarationPosition() {
        String code = "let count = 1;\nlet count = 2;";
        List<Problem> problems = JsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "has already been declared", "count");
    }

    @Test
    public void testJsConstReassignmentPosition() {
        String code = "const MAX = 100;\nMAX = 200;";
        List<Problem> problems = JsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Cannot reassign 'const' variable", "MAX");
    }

    @Test
    public void testJsFunctionArityTooFewPosition() {
        String code = "function sum(a, b) {\n    return a + b;\n}\nsum(1);";
        List<Problem> problems = JsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Too few arguments: 'sum'", "sum");
    }

    @Test
    public void testJsFunctionArityTooManyPosition() {
        String code = "function greet(name) {\n    return 'Hello ' + name;\n}\ngreet('Alice', 'Bob');";
        List<Problem> problems = JsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Too many arguments: 'greet'", "greet");
    }

    @Test
    public void testJsDivisionByZeroPosition() {
        String code = "let val = 42 / 0;";
        List<Problem> problems = JsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Division by zero", "/");
    }

    @Test
    public void testJsConsoleStatementPosition() {
        String code = "console.log('debug output');";
        List<Problem> problems = JsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Unexpected console statement", "console.log");
    }

    @Test
    public void testJsInvalidTypeofComparisonPosition() {
        String code = "let isOk = typeof val === undefined;";
        List<Problem> problems = JsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Comparing typeof to undefined directly", "===");
    }

    @Test
    public void testJsSyntaxErrorPosition() {
        String code = "let = 42;";
        List<Problem> problems = JsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Syntax error", "=");
    }

    // =========================================================================
    // TypeScript Squiggly Position Tests
    // =========================================================================

    @Test
    public void testTsTypeMismatchPosition() {
        String code = "let age: number = 'twenty';";
        List<Problem> problems = TsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Type mismatch", "age");
    }

    @Test
    public void testTsReturnAnyPosition() {
        String code = "function fetchData(): any {\n    return null;\n}";
        List<Problem> problems = TsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Function 'fetchData' returns 'any'", "fetchData");
    }

    @Test
    public void testTsMissingExportedReturnTypePosition() {
        String code = "export function calculateTotal() {\n    return 42;\n}";
        List<Problem> problems = TsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Exported function 'calculateTotal' is missing a return type", "calculateTotal");
    }

    @Test
    public void testTsOptionalBeforeRequiredPosition() {
        String code = "function query(filter?: string, page: number) {}";
        List<Problem> problems = TsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "before required parameter 'page'", "page");
    }

    @Test
    public void testTsExplicitAnyPosition() {
        String code = "let payload: any = {};";
        List<Problem> problems = TsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Explicit 'any' type for 'payload'", "payload");
    }

    @Test
    public void testTsEnumPosition() {
        String code = "enum Direction {\n    Up,\n    Down\n}";
        List<Problem> problems = TsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Enums add runtime overhead", "Direction");
    }

    @Test
    public void testTsRedundantTypePosition() {
        String code = "let username: string = 'alice';";
        List<Problem> problems = TsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Type 'string' is inferred", "username");
    }

    @Test
    public void testTsNonNullAssertionPosition() {
        String code = "let user: string | null = null;\nconsole.log(user!);";
        List<Problem> problems = TsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Non-null assertion '!'", "user!");
    }

    @Test
    public void testTsTypeAssertionPosition() {
        String code = "let val = (raw as String);";
        List<Problem> problems = TsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Type assertion 'as String'", "String");
    }

    @Test
    public void testTsNamespacePosition() {
        String code = "namespace MyUtilities {\n    export const PI = 3.14;\n}";
        List<Problem> problems = TsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "'namespace' is discouraged", "namespace");
    }

    @Test
    public void testTsFunctionTypePosition() {
        String code = "let handler: Function = () => {};";
        List<Problem> problems = TsLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "'Function' type is too broad", "Function");
    }

    // =========================================================================
    // JSON Squiggly Position Tests
    // =========================================================================

    @Test
    public void testJsonCommentsDisallowedPosition() {
        String code = "{\n    // comment line\n    \"key\": 1\n}";
        List<Problem> problems = JsonLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Comments are not permitted in standard JSON", "// comment line");
    }

    @Test
    public void testJsonDuplicateKeyPosition() {
        String code = "{\n    \"item\": 1,\n    \"item\": 2\n}";
        List<Problem> problems = JsonLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Duplicate object key: \"item\"", "\"item\"");
    }

    @Test
    public void testJsonTrailingCommaPosition() {
        String code = "{\n    \"item\": 1,\n}";
        List<Problem> problems = JsonLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Trailing comma", ",");
    }

    @Test
    public void testJsonMissingColonPosition() {
        String code = "{\n    \"key\" \"val\"\n}";
        List<Problem> problems = JsonLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Missing colon", "\"val\"");
    }

    @Test
    public void testJsonStrayBracketPosition() {
        String code = "{\n    \"key\": 1]\n}";
        List<Problem> problems = JsonLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Expected key or '}'", "]");
    }

    // =========================================================================
    // Markdown Squiggly Position Tests
    // =========================================================================

    @Test
    public void testMarkdownSkippedHeadingLevelPosition() {
        String code = "# Heading 1\n\n### Heading 3";
        List<Problem> problems = MarkdownLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Heading levels should only increment", "### Heading 3");
    }

    @Test
    public void testMarkdownInconsistentListMarkerPosition() {
        String code = "- Item 1\n* Item 2";
        List<Problem> problems = MarkdownLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Inconsistent list marker", "*");
    }

    @Test
    public void testMarkdownEmptyBlockquotePosition() {
        String code = ">\n";
        List<Problem> problems = MarkdownLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Empty blockquote", ">");
    }

    @Test
    public void testMarkdownUnclosedCodeBlockPosition() {
        String code = "```javascript\nconst a = 1;\n";
        List<Problem> problems = MarkdownLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Unclosed code block", "```");
    }

    @Test
    public void testMarkdownEmptyLinkTextPosition() {
        String code = "Click [](https://example.com) here.";
        List<Problem> problems = MarkdownLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Link has no text", "[](https://example.com)");
    }

    @Test
    public void testMarkdownEmptyImageAltPosition() {
        String code = "![](https://example.com/logo.png)";
        List<Problem> problems = MarkdownLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Image is missing alt text", "![](https://example.com/logo.png)");
    }

    @Test
    public void testMarkdownUselessImageAltPosition() {
        String code = "![image](https://example.com/logo.png)";
        List<Problem> problems = MarkdownLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Useless alt text", "![image](https://example.com/logo.png)");
    }

    @Test
    public void testMarkdownRawUrlPosition() {
        String code = "Visit https://example.com today!";
        List<Problem> problems = MarkdownLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Raw URL detected", "https://example.com");
    }

    @Test
    public void testMarkdownTrailingWhitespacePosition() {
        String code = "Trailing spaces   \nNext line";
        List<Problem> problems = MarkdownLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Trailing whitespace detected", "   ");
    }

    @Test
    public void testMarkdownHardTabPosition() {
        String code = "\tTab indented content";
        List<Problem> problems = MarkdownLinter.analyze(mockFile, code);
        assertSquigglyMatches(code, problems, "Hard tab detected", "\t");
    }
}
