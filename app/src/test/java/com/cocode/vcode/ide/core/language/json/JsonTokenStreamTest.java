package com.cocode.vcode.ide.core.language.json;

import org.junit.Test;
import static org.junit.Assert.*;

public class JsonTokenStreamTest {

    @Test
    public void testJsonTokenStream() {
        byte[] types = new byte[10];
        int[] starts = new int[10];

        types[0] = JsonTokenStream.TK_BRACE_OPEN; starts[0] = 0;
        types[1] = JsonTokenStream.TK_STRING;     starts[1] = 1;
        types[2] = JsonTokenStream.TK_STRING;     starts[2] = 1;
        types[3] = JsonTokenStream.TK_COLON;      starts[3] = 3;
        
        JsonTokenStream stream = new JsonTokenStream(types, starts);
        assertFalse(stream.isString(0));
        assertTrue(stream.isString(1));
        assertTrue(stream.isString(2));
        assertFalse(stream.isNumber(3));
        assertEquals(10, stream.length);
        
        stream.reset(20);
        assertEquals(20, stream.length);
        assertNotNull(stream.types);
        assertTrue(stream.types.length >= 20);
    }
}
