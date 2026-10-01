package com.cocode.vcode.ide.core.quickfix;

import com.cocode.vcode.ide.core.diagnostic.ProblemCode;
import com.cocode.vcode.ide.core.editor.indent.BracketMatcher;
import com.cocode.vcode.ide.core.language.css.CssLinter;
import com.cocode.vcode.ide.core.language.html.HtmlLinter;
import com.cocode.vcode.ide.core.language.js.JsSemanticLinter;
import com.cocode.vcode.ide.core.language.json.JsonLinter;
import com.cocode.vcode.ide.core.language.md.MarkdownLinter;
import com.cocode.vcode.ide.core.language.ts.TsLinter;
import com.cocode.vcode.ide.core.model.Problem;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies quick fix application across supported diagnostic problems.
 */
public class QuickFixExecutionTest {

    private TestEditorTarget editor;
    private final File htmlFile = new File("test.html");
    private final File cssFile = new File("test.css");
    private final File jsFile = new File("test.js");
    private final File tsFile = new File("test.ts");
    private final File jsonFile = new File("test.json");
    private final File mdFile = new File("test.md");

    @Before
    public void setUp() {
        editor = new TestEditorTarget();
    }

    static class TestEditorTarget implements CodeEditorTarget {
        private final StringBuilder buffer = new StringBuilder();

        public void setText(String text) {
            buffer.setLength(0);
            if (text != null) buffer.append(text);
        }

        @Override
        public int getLineCount() {
            String s = buffer.toString();
            if (s.isEmpty()) return 1;
            int count = 1;
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == '\n') count++;
            }
            return count;
        }

        @Override
        public String getLineText(int line) {
            String[] lines = buffer.toString().split("\n", -1);
            if (line >= 0 && line < lines.length) {
                return lines[line];
            }
            return "";
        }

        @Override
        public void replaceRange(int startLine, int startCol, int endLine, int endCol, String text) {
            String[] lines = buffer.toString().split("\n", -1);
            int startOffset = 0;
            for (int i = 0; i < startLine && i < lines.length; i++) {
                startOffset += lines[i].length() + 1;
            }
            startOffset += startCol;

            int endOffset = 0;
            for (int i = 0; i < endLine && i < lines.length; i++) {
                endOffset += lines[i].length() + 1;
            }
            endOffset += endCol;

            startOffset = Math.max(0, Math.min(startOffset, buffer.length()));
            endOffset = Math.max(startOffset, Math.min(endOffset, buffer.length()));

            buffer.replace(startOffset, endOffset, text != null ? text : "");
        }

        @Override
        public void insertText(String text) {
            if (text != null) buffer.append(text);
        }

        @Override
        public void beginAtomicGroup() {}

        @Override
        public void endAtomicGroup() {}

        @Override
        public CharSequence getText() {
            return buffer.toString();
        }

        @Override
        public android.content.Context getContext() {
            return null;
        }
    }

    private void applyFirstFix(Problem problem) {
        List<QuickFix> fixes = QuickFixRegistry.getFixesForProblem(problem, editor);
        assertNotNull("Fixes list should not be null for " + problem.getProblemCode(), fixes);
        assertFalse("At least one quick fix must be registered for " + problem.getProblemCode() + ": " + problem.getMessage(),
                fixes.isEmpty());
        QuickFix fix = fixes.get(0);
        assertNotNull("QuickFix title must not be null", fix.getTitle());
        fix.apply(editor, problem);
    }

    // ==========================================
    // 1. HTML Quick Fixes
    // ==========================================

    @Test
    public void testHtmlMissingAlt() {
        editor.setText("<img src=\"photo.jpg\">");
        Problem p = new Problem(htmlFile, 1, 1, 4, "'<img>' is missing 'alt' attribute: required for accessibility",
                Problem.Severity.WARNING, ProblemCode.HTML_MISSING_ALT);
        applyFirstFix(p);
        assertEquals("<img src=\"photo.jpg\" alt=\"\">", editor.getText().toString());
    }

    @Test
    public void testHtmlButtonMissingType() {
        editor.setText("<button>Click Me</button>");
        Problem p = new Problem(htmlFile, 1, 1, 8, "'<button>' is missing 'type' attribute (defaults to 'submit')",
                Problem.Severity.WARNING, ProblemCode.HTML_BUTTON_MISSING_TYPE);
        applyFirstFix(p);
        assertEquals("<button type=\"button\">Click Me</button>", editor.getText().toString());
    }

    @Test
    public void testHtmlEmptyTitle() {
        editor.setText("<title></title>");
        Problem p = new Problem(htmlFile, 1, 1, 7, "Empty '<title>' element: page title is required for accessibility and SEO",
                Problem.Severity.WARNING, ProblemCode.HTML_EMPTY_TITLE);
        applyFirstFix(p);
        assertEquals("<title>Document Title</title>", editor.getText().toString());
    }

    @Test
    public void testHtmlMissingLang() {
        editor.setText("<html>\n<body></body>\n</html>");
        Problem p = new Problem(htmlFile, 1, 1, 6, "'<html>' is missing 'lang' attribute: required for screen readers",
                Problem.Severity.WARNING, ProblemCode.HTML_MISSING_LANG);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().startsWith("<html lang=\"en\">"));
    }

    @Test
    public void testHtmlMissingSrc_standard() {
        editor.setText("<img alt=\"photo\">");
        Problem p = new Problem(htmlFile, 1, 1, 4, "'<img>' is missing required attribute 'src'",
                Problem.Severity.ERROR, ProblemCode.HTML_MISSING_SRC);
        applyFirstFix(p);
        assertEquals("<img alt=\"photo\" src=\"\">", editor.getText().toString());
    }

    @Test
    public void testHtmlMissingSrc_selfClosing() {
        editor.setText("<img alt=\"photo\"/>");
        Problem p = new Problem(htmlFile, 1, 1, 4, "'<img>' is missing required attribute 'src'",
                Problem.Severity.ERROR, ProblemCode.HTML_MISSING_SRC);
        applyFirstFix(p);
        assertEquals("<img alt=\"photo\" src=\"\"/>", editor.getText().toString());
    }

    @Test
    public void testHtmlMissingContent() {
        editor.setText("<meta name=\"description\">");
        Problem p = new Problem(htmlFile, 1, 1, 6, "'<meta>' with 'name' is missing 'content' attribute",
                Problem.Severity.WARNING, ProblemCode.HTML_MISSING_CONTENT);
        applyFirstFix(p);
        assertEquals("<meta name=\"description\" content=\"\">", editor.getText().toString());
    }

    @Test
    public void testHtmlMissingFor() {
        editor.setText("<label>Username</label>");
        Problem p = new Problem(htmlFile, 1, 1, 7, "'<label>' is missing 'for' attribute and does not wrap a form control",
                Problem.Severity.WARNING, ProblemCode.HTML_MISSING_FOR);
        applyFirstFix(p);
        assertEquals("<label for=\"\">Username</label>", editor.getText().toString());
    }

    @Test
    public void testHtmlMissingCharset() {
        editor.setText("<html>\n<head>\n</head>\n</html>");
        Problem p = new Problem(htmlFile, 1, 1, 1, "Missing '<meta charset=\"...\">' in <head>: may cause encoding issues",
                Problem.Severity.WARNING, ProblemCode.HTML_MISSING_CHARSET);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("<meta charset=\"UTF-8\">"));
    }

    @Test
    public void testHtmlMissingViewport() {
        editor.setText("<html>\n<head>\n</head>\n</html>");
        Problem p = new Problem(htmlFile, 1, 1, 1, "Missing viewport meta tag: page may not be mobile-responsive",
                Problem.Severity.WARNING, ProblemCode.HTML_MISSING_VIEWPORT);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("name=\"viewport\""));
    }

    @Test
    public void testHtmlMissingTitle() {
        editor.setText("<html>\n<head>\n</head>\n</html>");
        Problem p = new Problem(htmlFile, 1, 1, 1, "Missing '<title>' in <head>",
                Problem.Severity.WARNING, ProblemCode.HTML_MISSING_TITLE);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("<title>Document Title</title>"));
    }

    @Test
    public void testHtmlMissingMetaDescription() {
        editor.setText("<html>\n<head>\n</head>\n</html>");
        Problem p = new Problem(htmlFile, 1, 1, 1, "Consider adding '<meta name=\"description\">' for SEO",
                Problem.Severity.INFO, ProblemCode.HTML_MISSING_META_DESCRIPTION);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("meta name=\"description\""));
    }

    @Test
    public void testHtmlMissingRequiredAttribute() {
        editor.setText("<video width=\"320\">\n</video>");
        Problem p = new Problem(htmlFile, 1, 1, 7, "'<video>' is missing required attribute 'src'",
                Problem.Severity.WARNING, ProblemCode.HTML_MISSING_REQ_ATTR);
        applyFirstFix(p);
        // Ensure it inserts src="", and does NOT insert <video>=""
        assertTrue("Must insert src=\"\" attribute", editor.getText().toString().contains("src=\"\""));
        assertFalse("Must NOT insert tag name as attribute", editor.getText().toString().contains("<video>="));
    }

    @Test
    public void testHtmlDeprecatedTag() {
        editor.setText("<center class=\"main\">Centered content</center>");
        Problem p = new Problem(htmlFile, 1, 1, 8, "'<center>' is deprecated — use CSS text-align instead",
                Problem.Severity.WARNING, ProblemCode.HTML_DEPRECATED_TAG);
        applyFirstFix(p);
        assertEquals("<div class=\"main\">Centered content</div>", editor.getText().toString());
    }

    @Test
    public void testHtmlDeprecatedAttribute() {
        editor.setText("<p align=\"center\">Paragraph</p>");
        Problem p = new Problem(htmlFile, 1, 4, 5, "'align' attribute on '<p>' is deprecated — use CSS instead",
                Problem.Severity.WARNING, ProblemCode.HTML_DEPRECATED_ATTR);
        applyFirstFix(p);
        assertEquals("<p>Paragraph</p>", editor.getText().toString().trim());
    }

    @Test
    public void testHtmlDuplicateAttribute() {
        editor.setText("<div class=\"one\" class=\"two\"></div>");
        Problem p = new Problem(htmlFile, 1, 18, 5, "Duplicate attribute 'class' on '<div>'",
                Problem.Severity.WARNING, ProblemCode.HTML_DUPLICATE_ATTR);
        applyFirstFix(p);
        assertFalse(editor.getText().toString().contains("class=\"two\""));
    }

    @Test
    public void testHtmlDuplicateId() {
        editor.setText("<div id=\"box\"></div>\n<div id=\"box\"></div>");
        Problem p = new Problem(htmlFile, 2, 6, 8, "Duplicate id='box': IDs must be unique in a document",
                Problem.Severity.ERROR, ProblemCode.HTML_DUPLICATE_ID);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("id=\"box-2\""));
    }

    @Test
    public void testHtmlUnclosedTag() {
        editor.setText("<div><p>Hello");
        Problem p = new Problem(htmlFile, 1, 6, 2, "Unclosed tag '<p>'",
                Problem.Severity.ERROR, ProblemCode.HTML_UNCLOSED_TAG);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().endsWith("</p>"));
    }

    @Test
    public void testHtmlVoidElementClosing() {
        editor.setText("<br></br>");
        Problem p = new Problem(htmlFile, 1, 5, 5, "'br' is a void element and cannot have a closing tag",
                Problem.Severity.ERROR, ProblemCode.HTML_VOID_ELEMENT_CLOSING);
        applyFirstFix(p);
        assertEquals("<br>", editor.getText().toString());
    }

    @Test
    public void testHtmlStrayClosingTag() {
        editor.setText("<span>Hello</span></span>");
        Problem p = new Problem(htmlFile, 1, 19, 7, "Stray closing tag '</span>' with no opening tag",
                Problem.Severity.ERROR, ProblemCode.HTML_STRAY_CLOSING_TAG);
        applyFirstFix(p);
        assertEquals("<span>Hello</span>", editor.getText().toString());
    }

    @Test
    public void testHtmlInlineStyle() {
        editor.setText("<p style=\"color: red;\">Text</p>");
        Problem p = new Problem(htmlFile, 1, 4, 5, "Avoid inline styles on '<p>': prefer CSS classes",
                Problem.Severity.INFO, ProblemCode.HTML_INLINE_STYLE);
        applyFirstFix(p);
        assertEquals("<p>Text</p>", editor.getText().toString());
    }

    @Test
    public void testHtmlInlineEvent() {
        editor.setText("<button onclick=\"handleClick()\">Button</button>");
        Problem p = new Problem(htmlFile, 1, 9, 7, "'onclick' inline handler on '<button>': prefer addEventListener() in external JS",
                Problem.Severity.INFO, ProblemCode.HTML_INLINE_EVENT);
        applyFirstFix(p);
        assertEquals("<button>Button</button>", editor.getText().toString());
    }

    @Test
    public void testHtmlRequiredParent() {
        editor.setText("<li>Single item</li>");
        Problem p = new Problem(htmlFile, 1, 1, 4, "'<li>' must be a child of '<ul>' or '<ol>'",
                Problem.Severity.ERROR, ProblemCode.HTML_REQUIRED_PARENT);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().startsWith("<ul>"));
        assertTrue(editor.getText().toString().endsWith("</ul>"));
    }

    @Test
    public void testHtmlNestedAnchor() {
        editor.setText("<a href=\"/one\"><a href=\"/two\">Link</a></a>");
        Problem p = new Problem(htmlFile, 1, 16, 3, "'<a>' cannot be nested inside another '<a>'",
                Problem.Severity.ERROR, ProblemCode.HTML_NESTED_ANCHOR);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("<span"));
    }

    @Test
    public void testHtmlRedundantTypeAttribute() {
        editor.setText("<script type=\"text/javascript\" src=\"main.js\"></script>");
        Problem p = new Problem(htmlFile, 1, 9, 22, "'type=\"text/javascript\"' is redundant in HTML5 — remove it",
                Problem.Severity.WARNING, ProblemCode.HTML_REDUNDANT_TYPE_ATTR);
        applyFirstFix(p);
        assertFalse(editor.getText().toString().contains("type="));
    }

    @Test
    public void testHtmlScriptMissingDefer() {
        editor.setText("<script src=\"app.js\"></script>");
        Problem p = new Problem(htmlFile, 1, 1, 8, "'<script>' without 'defer' or 'async': may block page rendering",
                Problem.Severity.WARNING, ProblemCode.HTML_SCRIPT_MISSING_DEFER);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("defer"));
    }

    @Test
    public void testHtmlTargetBlankSecurity() {
        editor.setText("<a href=\"https://google.com\" target=\"_blank\">Search</a>");
        Problem p = new Problem(htmlFile, 1, 1, 2, "Using target=\"_blank\" without rel=\"noopener\" or rel=\"noreferrer\" is a security risk",
                Problem.Severity.WARNING, ProblemCode.HTML_TARGET_BLANK_SECURITY);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("rel=\"noopener noreferrer\""));
    }

    @Test
    public void testHtmlAnchorAsButton() {
        editor.setText("<a href=\"#\">Action</a>");
        Problem p = new Problem(htmlFile, 1, 1, 2, "'<a href=\"#\">' used as button — use '<button>' for semantic correctness",
                Problem.Severity.WARNING, ProblemCode.HTML_ANCHOR_AS_BUTTON);
        applyFirstFix(p);
        assertEquals("<button type=\"button\">Action</button>", editor.getText().toString());
    }

    @Test
    public void testHtmlSemanticSuggestion() {
        editor.setText("<div class=\"header\">Header content</div>");
        Problem p = new Problem(htmlFile, 1, 1, 5, "'<div class=\"header\">' could be replaced with '<header>'",
                Problem.Severity.INFO, ProblemCode.HTML_SEMANTIC_SUGGESTION);
        applyFirstFix(p);
        assertEquals("<header class=\"header\">Header content</header>", editor.getText().toString());
    }

    @Test
    public void testHtmlEmptyTag() {
        editor.setText("<div>\n<p></p>\n<span>Hi</span>\n</div>");
        Problem p = new Problem(htmlFile, 2, 1, 3, "Empty '<p>': likely unintentional",
                Problem.Severity.INFO, ProblemCode.HTML_EMPTY_TAG);
        applyFirstFix(p);
        assertFalse(editor.getText().toString().contains("<p></p>"));
    }

    @Test
    public void testHtmlLazyLoading() {
        editor.setText("<img src=\"hero.jpg\" alt=\"Hero\">");
        Problem p = new Problem(htmlFile, 1, 1, 4, "Consider adding 'loading=\"lazy\"' on '<img>' for performance",
                Problem.Severity.INFO, ProblemCode.HTML_LAZY_LOADING);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("loading=\"lazy\""));
    }

    @Test
    public void testHtmlMissingThead() {
        editor.setText("<table>\n<tr><td>Data</td></tr>\n</table>");
        Problem p = new Problem(htmlFile, 1, 1, 7, "'<table>' has no header row: add '<thead>' and '<th>' for accessibility",
                Problem.Severity.INFO, ProblemCode.HTML_MISSING_THEAD);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("<thead>"));
    }

    // ==========================================
    // 2. CSS Quick Fixes
    // ==========================================

    @Test
    public void testCssUnknownProperty() {
        editor.setText(".card {\n    colr: red;\n    display: block;\n}");
        Problem p = new Problem(cssFile, 2, 5, 4, "Unknown CSS property 'colr': not a standard CSS property",
                Problem.Severity.WARNING, ProblemCode.CSS_UNKNOWN_PROPERTY);
        applyFirstFix(p);
        assertFalse(editor.getText().toString().contains("colr:"));
        assertTrue(editor.getText().toString().contains("display: block;"));
    }

    @Test
    public void testCssDeprecatedProperty_clip() {
        editor.setText(".box {\n    clip: rect(0, 0, 0, 0);\n}");
        Problem p = new Problem(cssFile, 2, 5, 4, "'clip' is deprecated — use 'clip-path' instead",
                Problem.Severity.WARNING, ProblemCode.CSS_DEPRECATED_PROPERTY);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("clip-path:"));
    }

    @Test
    public void testCssDeprecatedProperty_zoom() {
        editor.setText(".box {\n    zoom: 2;\n}");
        Problem p = new Problem(cssFile, 2, 5, 4, "'zoom' is deprecated — use 'transform: scale()' instead",
                Problem.Severity.WARNING, ProblemCode.CSS_DEPRECATED_PROPERTY);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("transform: scale(2);"));
    }

    @Test
    public void testCssDuplicateProperty() {
        editor.setText(".box {\n    color: red;\n    color: blue;\n}");
        Problem p = new Problem(cssFile, 3, 5, 5, "Duplicate property 'color' in rule '.box'",
                Problem.Severity.WARNING, ProblemCode.CSS_DUPLICATE_PROPERTY);
        applyFirstFix(p);
        assertEquals(".box {\n    color: red;\n}", editor.getText().toString().trim());
    }

    @Test
    public void testCssImportantUsage() {
        editor.setText(".box {\n    color: red !important;\n}");
        Problem p = new Problem(cssFile, 2, 16, 10, "!important used: avoid !important to keep specificity manageable",
                Problem.Severity.INFO, ProblemCode.CSS_IMPORTANT_USAGE);
        applyFirstFix(p);
        assertFalse(editor.getText().toString().contains("!important"));
    }

    @Test
    public void testCssZeroUnit() {
        editor.setText(".box {\n    margin: 0px;\n}");
        Problem p = new Problem(cssFile, 2, 13, 3, "'0px' — units are unnecessary with zero values, use '0'",
                Problem.Severity.INFO, ProblemCode.CSS_ZERO_UNIT);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("margin: 0;"));
    }

    @Test
    public void testCssPtUnit() {
        editor.setText(".box {\n    font-size: 12pt;\n}");
        Problem p = new Problem(cssFile, 2, 16, 4, "Avoid using 'pt' units in web stylesheets — use 'px', 'rem', or 'em'",
                Problem.Severity.WARNING, ProblemCode.CSS_PT_UNIT);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("12px;"));
    }

    @Test
    public void testCssVarNoFallback() {
        editor.setText(".box {\n    color: var(--theme-color);\n}");
        Problem p = new Problem(cssFile, 2, 12, 18, "CSS variable 'var(--theme-color)' used without a fallback value",
                Problem.Severity.INFO, ProblemCode.CSS_VAR_NO_FALLBACK);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("var(--theme-color, inherit)"));
    }

    @Test
    public void testCssVarUnused() {
        editor.setText(":root {\n    --unused-color: #333;\n    color: blue;\n}");
        Problem p = new Problem(cssFile, 2, 5, 14, "CSS variable '--unused-color' is declared but never used",
                Problem.Severity.INFO, ProblemCode.CSS_VAR_UNUSED);
        applyFirstFix(p);
        assertFalse(editor.getText().toString().contains("--unused-color"));
    }

    @Test
    public void testCssHexShortenable() {
        editor.setText(".box {\n    color: #ffffff;\n}");
        Problem p = new Problem(cssFile, 2, 12, 7, "'#ffffff' can be shortened to '#fff'",
                Problem.Severity.INFO, ProblemCode.CSS_HEX_SHORTENABLE);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("#fff"));
    }

    @Test
    public void testCssEmptyRule() {
        editor.setText(".empty {\n}\n.full { color: red; }");
        Problem p = new Problem(cssFile, 1, 1, 6, "Empty rule for selector '.empty'",
                Problem.Severity.WARNING, ProblemCode.CSS_EMPTY_RULE);
        applyFirstFix(p);
        assertFalse(editor.getText().toString().contains(".empty"));
    }

    @Test
    public void testCssVendorPrefixNeeded() {
        editor.setText(".box {\n    user-select: none;\n}");
        Problem p = new Problem(cssFile, 2, 1, 11, "'user-select' may need '-webkit-user-select' for broader browser support",
                Problem.Severity.INFO, ProblemCode.CSS_VENDOR_PREFIX_NEEDED);
        applyFirstFix(p);
        assertTrue("Must insert -webkit-user-select", editor.getText().toString().contains("-webkit-user-select: none;"));
        assertTrue("Must retain user-select", editor.getText().toString().contains("user-select: none;"));
    }

    @Test
    public void testCssMissingBgOrColor_missingBg() {
        editor.setText("body {\n    color: black;\n}");
        Problem p = new Problem(cssFile, 1, 1, 4, "'color' is set without 'background-color' on 'body'",
                Problem.Severity.INFO, ProblemCode.CSS_MISSING_BG_OR_COLOR);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("background-color: transparent;"));
    }

    @Test
    public void testCssMissingBgOrColor_missingColor() {
        editor.setText("body {\n    background-color: white;\n}");
        Problem p = new Problem(cssFile, 1, 1, 4, "'background-color' is set without 'color' on 'body'",
                Problem.Severity.INFO, ProblemCode.CSS_MISSING_BG_OR_COLOR);
        applyFirstFix(p);
        assertTrue("Must add color declaration when missing color", editor.getText().toString().contains("color: inherit;"));
    }

    @Test
    public void testCssMissingGap() {
        editor.setText(".container {\n    display: flex;\n}");
        Problem p = new Problem(cssFile, 1, 1, 10, "No 'gap' property in flex/grid rule '.container'",
                Problem.Severity.INFO, ProblemCode.CSS_MISSING_GAP);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("gap: 1rem;"));
    }

    @Test
    public void testCssMarginAutoShorthand() {
        editor.setText(".center {\n    margin-left: auto;\n}");
        Problem p = new Problem(cssFile, 2, 5, 18, "Consider using 'margin: 0 auto' shorthand for centering block elements",
                Problem.Severity.INFO, ProblemCode.CSS_MARGIN_AUTO_SHORTHAND);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("margin: 0 auto;"));
    }

    @Test
    public void testCssMarginAutoShorthandPreservesSelector() {
        editor.setText(".hero p {\n    margin-left: auto;\n    margin-right: auto;\n}");
        Problem p = new Problem(cssFile, 2, 5, 18,
                "Consider 'margin: 0 auto' or flexbox centering instead of separate margin declarations",
                Problem.Severity.INFO, ProblemCode.CSS_MARGIN_AUTO_SHORTHAND);
        applyFirstFix(p);
        String result = editor.getText().toString();
        assertTrue("Selector line must be preserved", result.contains(".hero p {"));
        assertTrue("Margin shorthand must be present", result.contains("    margin: 0 auto;"));
        assertFalse("Separate margin-right must be removed", result.contains("margin-right"));
        assertFalse("Separate margin-left must be removed", result.contains("margin-left"));
    }

    @Test
    public void testCssMarginAutoShorthandFromSelectorLine() {
        editor.setText(".hero p {\n    margin-left: auto;\n    margin-right: auto;\n}");
        Problem p = new Problem(cssFile, 1, 1, 7,
                "Consider 'margin: 0 auto' or flexbox centering instead of separate margin declarations",
                Problem.Severity.INFO, ProblemCode.CSS_MARGIN_AUTO_SHORTHAND);
        applyFirstFix(p);
        String result = editor.getText().toString();
        assertTrue("Selector line must be preserved", result.contains(".hero p {"));
        assertTrue("Margin shorthand must be present", result.contains("    margin: 0 auto;"));
        assertFalse("Separate margin-right must be removed", result.contains("margin-right"));
    }

    @Test
    public void testCssIdSelector() {
        editor.setText("#nav {\n    background: black;\n}");
        Problem p = new Problem(cssFile, 1, 1, 4, "Avoid using ID selectors ('#id') in CSS rules: use classes instead",
                Problem.Severity.INFO, ProblemCode.CSS_ID_SELECTOR);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().startsWith(".nav"));
    }

    @Test
    public void testCssMediaPxBreakpointToEm() {
        editor.setText("@media (min-width: 768px) {\n    .container { padding: 20px; }\n}");
        Problem p = new Problem(cssFile, 1, 1, 26,
                "'@media' breakpoint in 'px': consider 'em' or 'rem' for accessibility scaling",
                Problem.Severity.INFO, ProblemCode.CSS_MEDIA_PX_UNIT);
        List<QuickFix> fixes = QuickFixRegistry.getFixesForProblem(p, editor);
        assertEquals(2, fixes.size());
        assertEquals("Convert '@media' breakpoint to 'em'", fixes.get(0).getTitle());
        assertEquals("Convert '@media' breakpoint to 'rem'", fixes.get(1).getTitle());

        // Apply 'em' fix
        fixes.get(0).apply(editor, p);
        String result = editor.getText().toString();
        assertTrue("Breakpoint must be converted to 48em", result.contains("@media (min-width: 48em) {"));
        assertTrue("Inner property padding: 20px must NOT be converted", result.contains("padding: 20px;"));
    }

    @Test
    public void testCssMediaPxBreakpointToRem() {
        editor.setText("@media (min-width: 600px) and (max-width: 960px) {\n    body { font-size: 16px; }\n}");
        Problem p = new Problem(cssFile, 1, 1, 50,
                "'@media' breakpoint in 'px': consider 'em' or 'rem' for accessibility scaling",
                Problem.Severity.INFO, ProblemCode.CSS_MEDIA_PX_UNIT);
        List<QuickFix> fixes = QuickFixRegistry.getFixesForProblem(p, editor);
        assertEquals(2, fixes.size());

        // Apply 'rem' fix
        fixes.get(1).apply(editor, p);
        String result = editor.getText().toString();
        assertTrue("Breakpoint min-width must be converted to 37.5rem", result.contains("min-width: 37.5rem"));
        assertTrue("Breakpoint max-width must be converted to 60rem", result.contains("max-width: 60rem"));
        assertTrue("Inner property font-size: 16px must NOT be converted", result.contains("font-size: 16px;"));
    }

    @Test
    public void testCssMediaPxBreakpointMultiLine() {
        editor.setText("@media screen\n  and (min-width: 768px)\n  and (max-width: 1024px) {\n    .box { width: 100px; }\n}");
        Problem p = new Problem(cssFile, 1, 1, 13,
                "'@media' breakpoint in 'px': consider 'em' or 'rem' for accessibility scaling",
                Problem.Severity.INFO, ProblemCode.CSS_MEDIA_PX_UNIT);
        List<QuickFix> fixes = QuickFixRegistry.getFixesForProblem(p, editor);
        fixes.get(0).apply(editor, p);
        String result = editor.getText().toString();
        assertTrue("First breakpoint converted to 48em", result.contains("min-width: 48em"));
        assertTrue("Second breakpoint converted to 64em", result.contains("max-width: 64em"));
        assertTrue("Inner property width: 100px preserved", result.contains("width: 100px;"));
    }

    // ==========================================
    // 3. JavaScript & TypeScript Quick Fixes
    // ==========================================

    @Test
    public void testJsConstReassignment() {
        editor.setText("const counter = 1;\ncounter = 2;");
        Problem p = new Problem(jsFile, 2, 1, 7, "Cannot reassign 'const' variable 'counter'",
                Problem.Severity.ERROR, ProblemCode.JS_CONST_REASSIGNMENT);
        applyFirstFix(p);
        assertTrue("Must convert const to let for variable 'counter'", editor.getText().toString().startsWith("let counter = 1;"));
    }

    @Test
    public void testJsMissingSemicolon() {
        editor.setText("const x = 10\nconsole.log(x);");
        Problem p = new Problem(jsFile, 1, 12, 1, "Missing semicolon at end of statement",
                Problem.Severity.WARNING, ProblemCode.JS_MISSING_SEMICOLON);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().startsWith("const x = 10;"));
    }

    @Test
    public void testJsUnusedVariable() {
        editor.setText("function test(unusedParam) {\n    return 0;\n}");
        Problem p = new Problem(jsFile, 1, 15, 11, "Variable 'unusedParam' is defined but never used",
                Problem.Severity.WARNING, ProblemCode.JS_UNUSED_VARIABLE);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("_unusedParam"));
    }

    @Test
    public void testJsUndefinedVariable() {
        editor.setText("total = 100;");
        Problem p = new Problem(jsFile, 1, 1, 5, "'total' is not defined",
                Problem.Severity.ERROR, ProblemCode.JS_UNDEFINED_VARIABLE);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().startsWith("let total;\n"));
    }

    @Test
    public void testJsDuplicateDeclaration() {
        editor.setText("let item = 1;\nlet item = 2;");
        Problem p = new Problem(jsFile, 2, 5, 4, "Identifier 'item' has already been declared",
                Problem.Severity.ERROR, ProblemCode.JS_DUPLICATE_DECLARATION);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("let item2 = 2;"));
    }

    @Test
    public void testJsConsoleStatement() {
        editor.setText("function run() {\n    console.log(\"debug\");\n    return 1;\n}");
        Problem p = new Problem(jsFile, 2, 5, 21, "Unexpected console statement",
                Problem.Severity.INFO, ProblemCode.JS_CONSOLE_STATEMENT);
        applyFirstFix(p);
        assertFalse(editor.getText().toString().contains("console.log"));
    }

    @Test
    public void testJsDivisionByZero() {
        editor.setText("const result = total / 0;");
        Problem p = new Problem(jsFile, 1, 24, 1, "Division by zero results in Infinity",
                Problem.Severity.WARNING, ProblemCode.JS_DIVISION_BY_ZERO);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("/ 1;"));
    }

    @Test
    public void testJsTypeofUndefinedComparison() {
        editor.setText("if (typeof value === undefined) {}");
        Problem p = new Problem(jsFile, 1, 22, 9, "Comparing typeof to undefined directly: typeof returns a string",
                Problem.Severity.WARNING, ProblemCode.JS_TYPEOF_UNDEFINED_COMPARISON);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("=== 'undefined'"));
    }

    @Test
    public void testTsTypeMismatch() {
        editor.setText("let count: number = \"hello\";");
        Problem p = new Problem(tsFile, 1, 5, 5, "Type mismatch: cannot assign 'string' to 'number'",
                Problem.Severity.ERROR, ProblemCode.TS_TYPE_MISMATCH);
        applyFirstFix(p);
        // Must update type annotation, NOT delete the line
        assertEquals("let count: string = \"hello\";", editor.getText().toString());
    }

    @Test
    public void testTsReturnAny() {
        editor.setText("function getData(): any {\n    return null;\n}");
        Problem p = new Problem(tsFile, 1, 21, 3, "Function 'getData' returns 'any': specify an explicit return type",
                Problem.Severity.WARNING, ProblemCode.TS_RETURN_ANY);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("(): unknown"));
    }

    @Test
    public void testTsMissingReturnType() {
        editor.setText("export function calculate(a: number, b: number) {\n    return;\n}");
        Problem p = new Problem(tsFile, 1, 17, 9, "Exported function 'calculate' is missing a return type annotation",
                Problem.Severity.WARNING, ProblemCode.TS_MISSING_RETURN_TYPE);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("): void"));
    }

    @Test
    public void testTsExplicitAny() {
        editor.setText("let payload: any = {};");
        Problem p = new Problem(tsFile, 1, 14, 3, "Explicit 'any' type: consider using a more specific type or 'unknown'",
                Problem.Severity.WARNING, ProblemCode.TS_EXPLICIT_ANY);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("payload: unknown"));
    }

    @Test
    public void testTsRedundantType() {
        editor.setText("let greeting: string = \"hello\";");
        Problem p = new Problem(tsFile, 1, 13, 8, "Type 'string' is inferred: remove the explicit annotation",
                Problem.Severity.INFO, ProblemCode.TS_REDUNDANT_TYPE);
        applyFirstFix(p);
        assertEquals("let greeting = \"hello\";", editor.getText().toString());
    }

    @Test
    public void testTsUnionUndefined() {
        editor.setText("function parse(opt: string | undefined) {}");
        Problem p = new Problem(tsFile, 1, 16, 22, "'string | undefined' in parameter can be written as 'opt?: string'",
                Problem.Severity.INFO, ProblemCode.TS_UNION_UNDEFINED);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("opt?: string"));
    }

    @Test
    public void testTsReadonlyArray() {
        editor.setText("function sum(values: number[]) {\n    return 0;\n}");
        Problem p = new Problem(tsFile, 1, 22, 8, "Consider 'readonly number[]' to prevent array mutations",
                Problem.Severity.INFO, ProblemCode.TS_READONLY_ARRAY);
        applyFirstFix(p);
        // Must insert readonly before array, not at start of line
        assertTrue("Must insert readonly before array", editor.getText().toString().contains("values: readonly number[]"));
        assertFalse("Must not prepend readonly to function keyword", editor.getText().toString().startsWith("readonly function"));
    }

    @Test
    public void testTsBroadFunctionType() {
        editor.setText("let callback: Function;");
        Problem p = new Problem(tsFile, 1, 15, 8, "'Function' type is too broad: specify parameters and return type",
                Problem.Severity.WARNING, ProblemCode.TS_BROAD_FUNCTION_TYPE);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("callback: () => void;"));
    }

    @Test
    public void testTsNonNullAssertion() {
        editor.setText("const name = response!.data;");
        Problem p = new Problem(tsFile, 1, 23, 1, "Non-null assertion '!': prefer optional chaining '?.' or an explicit check",
                Problem.Severity.WARNING, ProblemCode.TS_NON_NULL_ASSERTION);
        applyFirstFix(p);
        assertEquals("const name = response?.data;", editor.getText().toString());
    }

    // ==========================================
    // 4. JSON Quick Fixes
    // ==========================================

    @Test
    public void testJsonCommentsDisallowed_wholeLine() {
        editor.setText("{\n  // Disallowed comment\n  \"key\": \"value\"\n}");
        Problem p = new Problem(jsonFile, 2, 3, 22, "Comments are not permitted in standard JSON",
                Problem.Severity.WARNING, ProblemCode.JSON_COMMENTS_DISALLOWED);
        applyFirstFix(p);
        assertFalse(editor.getText().toString().contains("//"));
        assertTrue(editor.getText().toString().contains("\"key\": \"value\""));
    }

    @Test
    public void testJsonCommentsDisallowed_inline() {
        editor.setText("{\n  \"key\": \"value\" // inline comment\n}");
        Problem p = new Problem(jsonFile, 2, 19, 18, "Comments are not permitted in standard JSON",
                Problem.Severity.WARNING, ProblemCode.JSON_COMMENTS_DISALLOWED);
        applyFirstFix(p);
        // Inline comment deleted, data retained
        assertFalse(editor.getText().toString().contains("// inline comment"));
        assertTrue(editor.getText().toString().contains("\"key\": \"value\""));
    }

    @Test
    public void testJsonSyntaxError_trailingCommaMultiline() {
        editor.setText("{\n  \"name\": \"App\",\n}");
        Problem p = new Problem(jsonFile, 2, 17, 1, "Trailing comma",
                Problem.Severity.ERROR, ProblemCode.JSON_SYNTAX_ERROR);
        applyFirstFix(p);
        assertEquals("{\n  \"name\": \"App\"\n}", editor.getText().toString());
    }

    @Test
    public void testJsonSyntaxError_singleQuotes() {
        editor.setText("{ 'name': 'App' }");
        Problem p = new Problem(jsonFile, 1, 3, 6, "Syntax error",
                Problem.Severity.ERROR, ProblemCode.JSON_SYNTAX_ERROR);
        applyFirstFix(p);
        assertEquals("{ \"name\": \"App\" }", editor.getText().toString());
    }

    @Test
    public void testJsonDuplicateKey() {
        editor.setText("{\n  \"port\": 3000,\n  \"port\": 8080\n}");
        Problem p = new Problem(jsonFile, 3, 3, 6, "Duplicate object key 'port'",
                Problem.Severity.WARNING, ProblemCode.JSON_DUPLICATE_KEY);
        applyFirstFix(p);
        assertFalse(editor.getText().toString().contains("8080"));
        assertTrue(editor.getText().toString().contains("3000"));
    }

    // ==========================================
    // 5. Markdown Quick Fixes
    // ==========================================

    @Test
    public void testMdHeadingIncrement() {
        editor.setText("# Heading 1\n### Skipped to 3");
        Problem p = new Problem(mdFile, 2, 1, 3, "Heading levels should only increment by one level at a time (e.g. # followed by ##, not ###)",
                Problem.Severity.WARNING, ProblemCode.MD_HEADING_INCREMENT);
        applyFirstFix(p);
        assertEquals("# Heading 1\n## Skipped to 3", editor.getText().toString());
    }

    @Test
    public void testMdEmptyBlockquote() {
        editor.setText("# Notes\n>\nParagraph");
        Problem p = new Problem(mdFile, 2, 1, 1, "Empty blockquote",
                Problem.Severity.WARNING, ProblemCode.MD_EMPTY_BLOCKQUOTE);
        applyFirstFix(p);
        assertFalse(editor.getText().toString().contains(">"));
    }

    @Test
    public void testMdInconsistentListMarker() {
        editor.setText("- Item 1\n* Item 2");
        Problem p = new Problem(mdFile, 2, 1, 1, "Inconsistent list marker: standardize to '-' or '*'",
                Problem.Severity.INFO, ProblemCode.MD_INCONSISTENT_LIST_MARKER);
        applyFirstFix(p);
        assertEquals("- Item 1\n- Item 2", editor.getText().toString());
    }

    @Test
    public void testMdEmptyLinkText() {
        editor.setText("Click [](https://google.com) here");
        Problem p = new Problem(mdFile, 1, 7, 2, "Link has no text: provide descriptive anchor text",
                Problem.Severity.WARNING, ProblemCode.MD_EMPTY_LINK_TEXT);
        applyFirstFix(p);
        assertEquals("Click [Link](https://google.com) here", editor.getText().toString());
    }

    @Test
    public void testMdMissingImageAlt() {
        editor.setText("![](banner.jpg)");
        Problem p = new Problem(mdFile, 1, 1, 2, "Image is missing alt text: required for accessibility",
                Problem.Severity.WARNING, ProblemCode.MD_MISSING_IMAGE_ALT);
        applyFirstFix(p);
        assertEquals("![Image description](banner.jpg)", editor.getText().toString());
    }

    @Test
    public void testMdUselessImageAlt() {
        editor.setText("![image](banner.jpg)");
        Problem p = new Problem(mdFile, 1, 3, 5, "Useless alt text: 'image', 'picture', 'photo' add no value for screen readers",
                Problem.Severity.INFO, ProblemCode.MD_USELESS_IMAGE_ALT);
        applyFirstFix(p);
        assertEquals("![Descriptive image text](banner.jpg)", editor.getText().toString());
    }

    @Test
    public void testMdRawUrl() {
        editor.setText("Visit https://example.com for info");
        Problem p = new Problem(mdFile, 1, 7, 19, "Raw URL detected: enclose in '<...>' for Markdown autolinking",
                Problem.Severity.INFO, ProblemCode.MD_RAW_URL);
        applyFirstFix(p);
        assertEquals("Visit <https://example.com> for info", editor.getText().toString());
    }

    @Test
    public void testMdTrailingWhitespace() {
        editor.setText("Line with whitespace   ");
        Problem p = new Problem(mdFile, 1, 21, 3, "Trailing whitespace detected: clean up unnecessary spaces",
                Problem.Severity.INFO, ProblemCode.MD_TRAILING_WHITESPACE);
        applyFirstFix(p);
        assertEquals("Line with whitespace", editor.getText().toString());
    }

    @Test
    public void testMdHardTab() {
        editor.setText("Indent\twith tab");
        Problem p = new Problem(mdFile, 1, 7, 1, "Hard tab detected: prefer spaces for consistent Markdown rendering",
                Problem.Severity.INFO, ProblemCode.MD_HARD_TAB);
        applyFirstFix(p);
        assertEquals("Indent    with tab", editor.getText().toString());
    }

    // ==========================================
    // 6. Bracket & General Syntax Quick Fixes
    // ==========================================

    @Test
    public void testBracketMismatch_unclosedBrace() {
        editor.setText("function test() {");
        Problem p = new Problem(jsFile, 1, 17, 1, "Unclosed brace '{'",
                Problem.Severity.ERROR, ProblemCode.BRACKET_MISMATCH);
        applyFirstFix(p);
        assertEquals("function test() {}", editor.getText().toString());
    }

    @Test
    public void testBracketMismatch_unclosedBracket() {
        editor.setText("const arr = [1, 2");
        Problem p = new Problem(jsFile, 1, 13, 1, "Unclosed bracket '['",
                Problem.Severity.ERROR, ProblemCode.BRACKET_MISMATCH);
        applyFirstFix(p);
        assertEquals("const arr = [1, 2]", editor.getText().toString());
    }

    @Test
    public void testBracketMismatch_unclosedParenthesis() {
        editor.setText("call(param");
        Problem p = new Problem(jsFile, 1, 5, 1, "Unclosed parenthesis '('",
                Problem.Severity.ERROR, ProblemCode.BRACKET_MISMATCH);
        applyFirstFix(p);
        assertEquals("call(param)", editor.getText().toString());
    }

    @Test
    public void testBracketMismatch_unmatchedClosing() {
        editor.setText("const x = 1; }");
        Problem p = new Problem(jsFile, 1, 14, 1, "Unmatched closing brace '}'",
                Problem.Severity.ERROR, ProblemCode.BRACKET_MISMATCH);
        applyFirstFix(p);
        assertEquals("const x = 1; ", editor.getText().toString());
    }

    @Test
    public void testBracketMismatch_mismatchedClosing() {
        editor.setText("call(param}");
        Problem p = new Problem(jsFile, 1, 11, 1, "Mismatched closing brace '}': expected ')'",
                Problem.Severity.ERROR, ProblemCode.BRACKET_MISMATCH);
        applyFirstFix(p);
        assertEquals("call(param)", editor.getText().toString());
    }

    @Test
    public void testSyntaxError_cleanup() {
        editor.setText("{ a: 1, }");
        Problem p = new Problem(jsFile, 1, 7, 1, "Generic syntax error",
                Problem.Severity.ERROR, ProblemCode.SYNTAX_ERROR);
        applyFirstFix(p);
        assertEquals("{ a: 1 }", editor.getText().toString());
    }

    // ==========================================
    // 7. End-to-End Linter Output to QuickFix Verification
    // ==========================================

    @Test
    public void testLinterEndToEnd_htmlMissingAlt() {
        String code = "<img src=\"logo.png\">";
        List<Problem> problems = HtmlLinter.analyze(htmlFile, code);
        boolean foundAltProblem = false;
        for (Problem prob : problems) {
            if (prob.getProblemCode() == ProblemCode.HTML_MISSING_ALT) {
                foundAltProblem = true;
                editor.setText(code);
                applyFirstFix(prob);
                assertTrue(editor.getText().toString().contains("alt=\"\""));
            }
        }
        assertTrue("HtmlLinter should detect missing alt", foundAltProblem);
    }

    @Test
    public void testLinterEndToEnd_cssUnknownProperty() {
        String code = "body {\n  badprop: 10px;\n}";
        List<Problem> problems = CssLinter.analyze(cssFile, code);
        boolean foundUnknown = false;
        for (Problem prob : problems) {
            if (prob.getProblemCode() == ProblemCode.CSS_UNKNOWN_PROPERTY) {
                foundUnknown = true;
                editor.setText(code);
                applyFirstFix(prob);
                assertFalse(editor.getText().toString().contains("badprop"));
            }
        }
        assertTrue("CssLinter should detect unknown property", foundUnknown);
    }

    @Test
    public void testLinterEndToEnd_bracketMatcher() {
        String code = "function run() {\n";
        List<Problem> problems = BracketMatcher.findMismatches(jsFile, code);
        assertFalse("BracketMatcher should detect unclosed brace", problems.isEmpty());
        editor.setText(code);
        applyFirstFix(problems.get(0));
        assertTrue(editor.getText().toString().contains("}"));
    }

    @Test
    public void testProblemEqualsAndHashCode() {
        Problem p1 = new Problem(htmlFile, 5, 2, 10, "Missing alt", Problem.Severity.WARNING, ProblemCode.HTML_MISSING_ALT);
        Problem p2 = new Problem(htmlFile, 5, 2, 10, "Missing alt", Problem.Severity.WARNING, ProblemCode.HTML_MISSING_ALT);
        Problem p3 = new Problem(htmlFile, 6, 2, 10, "Missing alt", Problem.Severity.WARNING, ProblemCode.HTML_MISSING_ALT);

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
        assertFalse(p1.equals(p3));
    }

    @Test
    public void testDynamicIndentation_htmlHeadTwoSpaces() {
        editor.setText("<html>\n  <head>\n  </head>\n</html>");
        Problem p = new Problem(htmlFile, 1, 1, 1, "Missing '<meta charset=\"...\">' in <head>: may cause encoding issues",
                Problem.Severity.WARNING, ProblemCode.HTML_MISSING_CHARSET);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("    <meta charset=\"UTF-8\">\n"));
    }

    @Test
    public void testDynamicIndentation_htmlHeadTabs() {
        editor.setText("<html>\n\t<head>\n\t</head>\n</html>");
        Problem p = new Problem(htmlFile, 1, 1, 1, "Missing '<title>' in <head>",
                Problem.Severity.WARNING, ProblemCode.HTML_MISSING_TITLE);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("\t\t<title>Document Title</title>\n"));
    }

    @Test
    public void testDynamicIndentation_htmlElementInsideParent() {
        editor.setText("    <li>Indented item</li>");
        Problem p = new Problem(htmlFile, 1, 1, 4, "'<li>' must be a child of '<ul>' or '<ol>'",
                Problem.Severity.ERROR, ProblemCode.HTML_REQUIRED_PARENT);
        applyFirstFix(p);
        String expected = "    <ul>\n        <li>Indented item</li>\n    </ul>";
        assertEquals(expected, editor.getText().toString());
    }

    @Test
    public void testDynamicIndentation_cssVendorPrefix() {
        editor.setText(".box {\n  user-select: none;\n}");
        Problem p = new Problem(cssFile, 2, 1, 11, "'user-select' may need '-webkit-user-select' for broader browser support",
                Problem.Severity.INFO, ProblemCode.CSS_VENDOR_PREFIX_NEEDED);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("  -webkit-user-select: none;\n  user-select: none;"));
    }

    @Test
    public void testDynamicIndentation_cssMarginAutoShorthand() {
        editor.setText(".center {\n  margin-left: auto;\n}");
        Problem p = new Problem(cssFile, 2, 3, 18, "Consider using 'margin: 0 auto' shorthand for centering block elements",
                Problem.Severity.INFO, ProblemCode.CSS_MARGIN_AUTO_SHORTHAND);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("  margin: 0 auto;"));
    }

    @Test
    public void testDynamicIndentation_jsUndefinedVariable() {
        editor.setText("function test() {\n    total = 100;\n}");
        Problem p = new Problem(jsFile, 2, 5, 5, "'total' is not defined",
                Problem.Severity.ERROR, ProblemCode.JS_UNDEFINED_VARIABLE);
        applyFirstFix(p);
        assertTrue(editor.getText().toString().contains("    let total;\n    total = 100;"));
    }
}
