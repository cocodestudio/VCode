package com.cocode.vcode.ide.core.language.js;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DocCommentParserTest {

    @Test
    public void testJsDocExtractionWithTags() {
        String code = "/**\n"
                + " * Calculates the sum of two numbers.\n"
                + " * @param {number} a First number\n"
                + " * @param {number} b Second number\n"
                + " * @returns {number} The sum\n"
                + " */\n"
                + "function add(a, b) { return a + b; }";

        int funcOffset = code.indexOf("function");
        DocCommentParser.DocCommentInfo info = DocCommentParser.extractDoc(code, funcOffset);

        assertNotNull(info);
        assertEquals("Calculates the sum of two numbers.", info.description);
        assertNotNull(info.paramDocs);
        assertEquals("First number", info.paramDocs.get("a"));
        assertEquals("Second number", info.paramDocs.get("b"));
        assertEquals("The sum", info.returnDoc);

        String md = info.toMarkdown("function add(a, b): number", "javascript");
        assertTrue(md.contains("```javascript\nfunction add(a, b): number\n```"));
        assertTrue(md.contains("Calculates the sum of two numbers."));
        assertTrue(md.contains("• `a: number` — First number"));
        assertTrue(md.contains("• `b: number` — Second number"));
        assertTrue(md.contains("**Returns:**\n`number` — The sum"));
    }

    @Test
    public void testJsDocWithoutTypeAnnotations() {
        String code = "/**\n"
                + " * Greets a user.\n"
                + " * @param name The user's name\n"
                + " * @return Greeting string\n"
                + " */\n"
                + "function greet(name) { return 'Hello ' + name; }";

        int funcOffset = code.indexOf("function");
        DocCommentParser.DocCommentInfo info = DocCommentParser.extractDoc(code, funcOffset);

        assertNotNull(info);
        assertEquals("Greets a user.", info.description);
        assertEquals("The user's name", info.paramDocs.get("name"));
        assertEquals("Greeting string", info.returnDoc);
    }

    @Test
    public void testMultiLineBlockComment() {
        String code = "/*\n"
                + " * Utility method to validate email address.\n"
                + " * Handles modern top-level domains.\n"
                + " */\n"
                + "function validateEmail(email) { return true; }";

        int funcOffset = code.indexOf("function");
        DocCommentParser.DocCommentInfo info = DocCommentParser.extractDoc(code, funcOffset);

        assertNotNull(info);
        assertTrue(info.description.contains("Utility method to validate email address."));
        assertTrue(info.description.contains("Handles modern top-level domains."));
    }

    @Test
    public void testSingleLineComments() {
        String code = "// Helper function to format prices\n"
                + "// Adds currency symbol\n"
                + "function formatPrice(cents) { return '$' + cents; }";

        int funcOffset = code.indexOf("function formatPrice");
        DocCommentParser.DocCommentInfo info = DocCommentParser.extractDoc(code, funcOffset);

        assertNotNull(info);
        assertTrue(info.description.contains("Helper function to format prices"));
        assertTrue(info.description.contains("Adds currency symbol"));
    }

    @Test
    public void testNoComment() {
        String code = "let count = 0;\nfunction increment() { count++; }";
        int funcOffset = code.indexOf("function");
        DocCommentParser.DocCommentInfo info = DocCommentParser.extractDoc(code, funcOffset);
        assertNull(info);
    }

    @Test
    public void testSingleLineCommentSeparatedByBlankLinesNotExtracted() {
        String code = "// Old comment\n\n\nfunction calculate() {}";
        int funcOffset = code.indexOf("function");
        DocCommentParser.DocCommentInfo info = DocCommentParser.extractDoc(code, funcOffset);
        assertNull(info);
    }
}
