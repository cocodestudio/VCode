package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.language.ts.TsFormatter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying Prettier-style formatting, import/export statement normalization,
 * multiline dot chaining, and verbatim span preservation across JsFormatter and TsFormatter.
 */
public class JsFormatterTest {

    private final JsFormatter formatter = new JsFormatter(JsFormatter.DEFAULT_INDENT);
    private final TsFormatter tsFormatter = new TsFormatter(JsFormatter.DEFAULT_INDENT);

    @Test
    public void testImportStatementFormatting() {
        String input = "import { getAmm }\nfrom\n'app.js'";
        String expected = "import { getAmm } from 'app.js';\n";
        assertEquals(expected, formatter.format(input));

        // Unspaced and multi-identifier imports
        String inputUnspaced = "import {foo,bar} from 'app.js';";
        String expectedUnspaced = "import { foo, bar } from 'app.js';\n";
        assertEquals(expectedUnspaced, formatter.format(inputUnspaced));
    }

    @Test
    public void testExportStatementFormatting() {
        String input = "export { foo, bar }\nfrom\n'app.js'";
        String expected = "export { foo, bar } from 'app.js';\n";
        assertEquals(expected, formatter.format(input));

        String inputLocal = "export { foo, bar }";
        String expectedLocal = "export { foo, bar };\n";
        assertEquals(expectedLocal, formatter.format(inputLocal));
    }

    @Test
    public void testMultipleDotChainingFormatting() {
        String input = "result.toFixed(2).toChar().toUpperCase();";
        String expected = "result\n    .toFixed(2)\n    .toChar()\n    .toUpperCase();\n";
        assertEquals(expected, formatter.format(input));
    }

    @Test
    public void testSingleDotAccessPreserved() {
        String input = "console.log(\"hello world\");";
        String expected = "console.log(\"hello world\");\n";
        assertEquals(expected, formatter.format(input));

        String memberAccess = "let len = arr.length;";
        assertEquals("let len = arr.length;\n", formatter.format(memberAccess));
    }

    @Test
    public void testChainedDotsInsideFunction() {
        String input = "function calc() {\n    let result = 10;\n    result.toFixed(2).toChar().toUpperCase();\n}";
        String expected = "function calc() {\n    let result = 10;\n    result\n        .toFixed(2)\n        .toChar()\n        .toUpperCase();\n}\n";
        assertEquals(expected, formatter.format(input));
    }

    @Test
    public void testTypeScriptImportAndChaining() {
        String tsImport = "import type { User }\nfrom\n'./user'";
        String expectedImport = "import type { User } from './user';\n";
        assertEquals(expectedImport, new TsFormatter().format(tsImport));

        String tsChain = "result.toFixed(2).toChar().toUpperCase();";
        String expectedChain = "result\n    .toFixed(2)\n    .toChar()\n    .toUpperCase();\n";
        assertEquals(expectedChain, tsFormatter.format(tsChain));
    }

    @Test
    public void testOptionalChainingBreak() {
        String input = "user?.profile?.address?.zipCode;";
        String expected = "user\n    ?.profile\n    ?.address\n    ?.zipCode;\n";
        assertEquals(expected, formatter.format(input));
    }

    @Test
    public void testPreserveVerbatimSpansWithDots() {
        // Dots inside string literals or template literals must never trigger chain breaks
        String input = "console.log(\"a.b.c.d\");";
        String expected = "console.log(\"a.b.c.d\");\n";
        assertEquals(expected, formatter.format(input));

        String templateInput = "console.log(`value is ${a.b.c}`);";
        // Only one top-level call on console.log
        assertTrue(formatter.format(templateInput).startsWith("console.log"));
    }

    @Test
    public void testDotsInsideArgumentsDoNotBreakOuterCall() {
        String input = "console.log(user.address.street);";
        String expected = "console.log(user.address.street);\n";
        assertEquals(expected, formatter.format(input));
    }

    @Test
    public void testLongImportStatementBreaksToMultiline() {
        String input = "import { a, b, c, d, e, f, g, h } from 'app.js'";
        String expected = "import {\n" +
                "    a,\n" +
                "    b,\n" +
                "    c,\n" +
                "    d,\n" +
                "    e,\n" +
                "    f,\n" +
                "    g,\n" +
                "    h\n" +
                "} from 'app.js';\n";
        assertEquals(expected, formatter.format(input));
    }

    @Test
    public void testLongObjectLiteralBreaksToMultiline() {
        String input = "let obj = {name: \"Adeel\", lang: \"JS\", app: \"VCode\", versionName: 6.0.0}";
        String expected = "let obj = {\n" +
                "    name: \"Adeel\",\n" +
                "    lang: \"JS\",\n" +
                "    app: \"VCode\",\n" +
                "    versionName: 6.0.0\n" +
                "};\n";
        assertEquals(expected, formatter.format(input));
    }

    @Test
    public void testShortObjectLiteralPreservedOnSingleLine() {
        String input = "let point = { x: 1, y: 2 };";
        String expected = "let point = { x: 1, y: 2 };\n";
        assertEquals(expected, formatter.format(input));
    }

    @Test
    public void testLongExportStatementBreaksToMultiline() {
        String input = "export { a, b, c, d, e, f, g, h } from 'app.js'";
        String expected = "export {\n" +
                "    a,\n" +
                "    b,\n" +
                "    c,\n" +
                "    d,\n" +
                "    e,\n" +
                "    f,\n" +
                "    g,\n" +
                "    h\n" +
                "} from 'app.js';\n";
        assertEquals(expected, formatter.format(input));
    }
}
