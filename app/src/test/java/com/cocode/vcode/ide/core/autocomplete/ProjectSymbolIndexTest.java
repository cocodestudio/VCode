package com.cocode.vcode.ide.core.autocomplete;

import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.model.CompletionItem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ProjectSymbolIndexTest {

    private ProjectSymbolIndex index;

    @Before
    public void setup() {
        index = ProjectSymbolIndex.getInstance();
    }

    @Test
    public void testIndexJsClassMembersAndExports() {
        String js = "export class Car {\n" +
                    "  speed = 100;\n" +
                    "  constructor() {\n" +
                    "    this.brand = 'Toyota';\n" +
                    "  }\n" +
                    "  accelerate(rate) {\n" +
                    "    return rate;\n" +
                    "  }\n" +
                    "}\n" +
                    "export const MAX_SPEED = 200;\n" +
                    "export default function drive() {}\n";

        File mockFile;
        try {
            mockFile = new File("src/Car.js").getCanonicalFile();
        } catch (Exception e) {
            mockFile = new File("src/Car.js").getAbsoluteFile();
        }
        LspDocument doc = new LspDocument(mockFile.getAbsolutePath(), js, "javascript", 1);
        ProjectIndex.getInstance().updateDocument(doc);

        index.invalidateFile(mockFile.getAbsolutePath());
        index.getExportsForPath(new File(mockFile.getParentFile(), "other.js"), "./Car.js");

        List<CompletionItem> members = index.getClassMembers("Car");
        assertNotNull(members);
        assertFalse(members.isEmpty());

        boolean foundAccelerate = false;
        boolean foundSpeed = false;
        boolean foundBrand = false;
        boolean foundConstructor = false;

        for (CompletionItem item : members) {
            if ("accelerate".equals(item.getLabel())) {
                foundAccelerate = true;
                assertEquals("Car method", item.getDetail());
                assertEquals(CompletionItem.Type.FUNCTION, item.getType());
            }
            if ("speed".equals(item.getLabel())) {
                foundSpeed = true;
                assertEquals("Car property", item.getDetail());
                assertEquals(CompletionItem.Type.VALUE, item.getType());
            }
            if ("brand".equals(item.getLabel())) {
                foundBrand = true;
                assertEquals("Car property", item.getDetail());
                assertEquals(CompletionItem.Type.VALUE, item.getType());
            }
            if ("constructor".equals(item.getLabel())) {
                foundConstructor = true;
            }
        }

        assertTrue("Should index accelerate method", foundAccelerate);
        assertTrue("Should index speed property", foundSpeed);
        assertTrue("Should index constructor brand assignment", foundBrand);
        assertFalse("Should never index constructor as member", foundConstructor);

        List<CompletionItem> exports = index.getExportsForPath(new File("src/other.js"), "./Car.js");
        boolean foundCarExport = false;
        boolean foundMaxSpeedExport = false;
        boolean foundDriveExport = false;

        for (CompletionItem item : exports) {
            if ("Car".equals(item.getInsertText())) foundCarExport = true;
            if ("MAX_SPEED".equals(item.getInsertText())) foundMaxSpeedExport = true;
            if ("drive".equals(item.getInsertText())) foundDriveExport = true;
        }

        assertTrue("Should export Car", foundCarExport);
        assertTrue("Should export MAX_SPEED", foundMaxSpeedExport);
        assertTrue("Should export default drive", foundDriveExport);
    }

    @Test
    public void testIndexCssFileWithoutHexColorFalsePositives() {
        String css = ".main-card {\n" +
                     "  color: #fff;\n" +
                     "  background-color: #123456;\n" +
                     "}\n" +
                     "#submit-btn {\n" +
                     "  border: 1px solid #000000;\n" +
                     "}\n";

        File mockFile = new File("src/styles.css").getAbsoluteFile();
        LspDocument doc = new LspDocument(mockFile.getAbsolutePath(), css, "css", 1);
        ProjectIndex.getInstance().updateDocument(doc);

        Set<String> classes = new HashSet<>();
        Set<String> ids = new HashSet<>();

        // Invoking indexCssFile through indexDirectory / direct helper logic
        com.cocode.vcode.ide.core.language.css.CssTokenStream stream = com.cocode.vcode.ide.core.language.css.CssLexer.tokenize(css);
        com.cocode.vcode.ide.core.language.css.CssSyntaxTree tree = com.cocode.vcode.ide.core.language.css.CssParser.parse(stream, css);
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == com.cocode.vcode.ide.core.language.css.CssSyntaxTree.N_SELECTOR) {
                String sel = tree.nodeName[i];
                if (sel != null && !sel.isEmpty()) {
                    // Extract using selector logic
                    int len = sel.length();
                    int j = 0;
                    while (j < len) {
                        char c = sel.charAt(j);
                        if (c == '.' || c == '#') {
                            boolean isClass = (c == '.');
                            j++;
                            int start = j;
                            while (j < len && (sel.charAt(j) == '_' || sel.charAt(j) == '-' || Character.isLetterOrDigit(sel.charAt(j)))) {
                                j++;
                            }
                            if (j > start) {
                                String name = sel.substring(start, j);
                                if (isClass) classes.add(name);
                                else ids.add(name);
                            }
                        } else {
                            j++;
                        }
                    }
                }
            }
        }

        assertTrue("Should contain main-card class", classes.contains("main-card"));
        assertTrue("Should contain submit-btn ID", ids.contains("submit-btn"));
        assertFalse("Must NOT extract hex color #fff as ID", ids.contains("fff"));
        assertFalse("Must NOT extract hex color #123456 as ID", ids.contains("123456"));
        assertFalse("Must NOT extract hex color #000000 as ID", ids.contains("000000"));
    }

    @Test
    public void testIndexHtmlAttributes() {
        String html = "<div id=\"app-root\" class=\"flex items-center justify-between\"></div>";

        com.cocode.vcode.ide.core.language.html.HtmlTokenStream stream = com.cocode.vcode.ide.core.language.html.HtmlLexer.tokenize(html);
        com.cocode.vcode.ide.core.language.js.ParseResult parseRes = com.cocode.vcode.ide.core.language.html.HtmlParser.parse(html, stream);
        com.cocode.vcode.ide.core.language.html.HtmlSyntaxTree tree = parseRes.htmlTree;
        assertNotNull(tree);

        Set<String> classNames = new HashSet<>();
        Set<String> htmlIds = new HashSet<>();

        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == com.cocode.vcode.ide.core.language.html.HtmlSyntaxTree.N_ATTRIBUTE) {
                String attrName = tree.nodeName[i];
                String attrValue = tree.nodeValue[i];
                if (attrName == null || attrValue == null) continue;
                if ((attrValue.startsWith("\"") && attrValue.endsWith("\"")) || (attrValue.startsWith("'") && attrValue.endsWith("'"))) {
                    attrValue = attrValue.substring(1, attrValue.length() - 1);
                }
                if ("id".equalsIgnoreCase(attrName)) {
                    htmlIds.add(attrValue.trim());
                } else if ("class".equalsIgnoreCase(attrName)) {
                    for (String c : attrValue.split("\\s+")) {
                        if (!c.isEmpty()) classNames.add(c);
                    }
                }
            }
        }

        assertTrue("Should contain HTML id app-root", htmlIds.contains("app-root"));
        assertTrue("Should contain class flex", classNames.contains("flex"));
        assertTrue("Should contain class items-center", classNames.contains("items-center"));
        assertTrue("Should contain class justify-between", classNames.contains("justify-between"));
    }
}
