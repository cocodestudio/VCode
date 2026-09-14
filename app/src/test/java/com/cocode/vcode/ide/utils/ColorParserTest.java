package com.cocode.vcode.ide.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ColorParserTest {

    @Test
    public void testNamedColorsFromAssets() {
        Integer aliceblue = ColorParser.parse("aliceblue");
        assertNotNull(aliceblue);
        assertEquals(Integer.valueOf(0xFFF0F8FF), aliceblue);

        Integer deeppink = ColorParser.parse("deeppink");
        assertNotNull(deeppink);
        assertEquals(Integer.valueOf(0xFFFF1493), deeppink);

        Integer transparent = ColorParser.parse("transparent");
        assertNotNull(transparent);
        assertEquals(Integer.valueOf(0), transparent);

        Integer red = ColorParser.parse("red");
        assertNotNull(red);
        assertEquals(Integer.valueOf(0xFFFF0000), red);
    }

    @Test
    public void testHexColors() {
        Integer hex3 = ColorParser.parse("#fff");
        assertNotNull(hex3);
        assertEquals(Integer.valueOf(0xFFFFFFFF), hex3);

        Integer hex6 = ColorParser.parse("#123456");
        assertNotNull(hex6);
        assertEquals(Integer.valueOf(0xFF123456), hex6);
    }
}
