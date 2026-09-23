package com.cocode.vcode.ide.utils;

import com.cocode.vcode.ide.core.model.FileType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class CodeFormatterTest {

    @Test
    public void testIsFormatSupported() {
        assertTrue(CodeFormatter.isFormatSupported(FileType.JSON));
        assertTrue(CodeFormatter.isFormatSupported(FileType.HTML));
        assertTrue(CodeFormatter.isFormatSupported(FileType.CSS));
        assertTrue(CodeFormatter.isFormatSupported(FileType.JAVASCRIPT));
        assertTrue(CodeFormatter.isFormatSupported(FileType.TYPESCRIPT));
        
        // Check for unsupported type (assuming TEXT is unsupported)
        assertFalse(CodeFormatter.isFormatSupported(FileType.TEXT));
    }

    @Test
    public void testFormatNullOrEmpty() {
        assertEquals(null, CodeFormatter.format(null, FileType.JSON));
        assertEquals("", CodeFormatter.format("", FileType.JSON));
        assertEquals("   ", CodeFormatter.format("   ", FileType.JSON));
    }

    @Test
    public void testFormatUnsupportedType() {
        String code = "just some text";
        assertEquals(code, CodeFormatter.format(code, FileType.TEXT));
    }

    @Test
    public void testFormatJavaScriptForLoop() {
        String input = "for (let i = 0;\ni < 3;\ni ++ ) {\n}";
        String formatted = CodeFormatter.format(input, FileType.JAVASCRIPT);
        assertEquals("for (let i = 0; i < 3; i++) {\n}\n", formatted);

        String singleLine = "for (let i = 0; i < 5; i++) {\n  console.log(i);\n}";
        String formattedSingleLine = CodeFormatter.format(singleLine, FileType.JAVASCRIPT);
        assertEquals("for (let i = 0; i < 5; i++) {\n  console.log(i);\n}\n", formattedSingleLine);

        String prefixStep = "for (let i = 0; i < 5; ++i) {\n}";
        assertEquals("for (let i = 0; i < 5; ++i) {\n}\n", CodeFormatter.format(prefixStep, FileType.JAVASCRIPT));

        String emptyFor = "for (;;) {\n}";
        assertEquals("for (;;) {\n}\n", CodeFormatter.format(emptyFor, FileType.JAVASCRIPT));
    }

    @Test
    public void testFormatJavaScriptIncrementDecrement() {
        String input = "count ++ ;\ni -- ;\n++ count ;\n-- count ;";
        String expected = "count++;\ni--;\n++count;\n--count;\n";
        assertEquals(expected, CodeFormatter.format(input, FileType.JAVASCRIPT));

        String expr = "let x = count ++ ;";
        assertEquals("let x = count++;\n", CodeFormatter.format(expr, FileType.JAVASCRIPT));
    }

    @Test
    public void testFormatTypeScriptForLoop() {
        String input = "for (let i: number = 0; i < 5; i++) {\n  console.log(i);\n}";
        String formatted = CodeFormatter.format(input, FileType.TYPESCRIPT);
        assertEquals("for (let i: number = 0; i < 5; i++) {\n  console.log(i);\n}\n", formatted);
    }

    @Test
    public void testFormatBracketIndexAndOperators() {
        String input = "arr[i] = matrix[i][j];\narr[i] ++ ;";
        String expected = "arr[i] = matrix[i][j];\narr[i]++;\n";
        assertEquals(expected, CodeFormatter.format(input, FileType.JAVASCRIPT));

        String eq = "if (a === b && c !== d) {\n}";
        assertEquals("if (a === b && c !== d) {\n}\n", CodeFormatter.format(eq, FileType.JAVASCRIPT));
    }
}
