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
}
