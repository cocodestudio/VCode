package com.cocode.vcode.ide.core.language.json;

import org.junit.Test;
import static org.junit.Assert.*;

public class JsonParserTest {

    @Test
    public void testValidJson() {
        String source = "{ \"str\": \"val\", \"num\": 123, \"arr\": [ true, false, null ] }";
        JsonTokenStream stream = JsonLexer.tokenize(source);
        JsonSyntaxTree tree = JsonParser.parse(stream, source);
        
        assertEquals(JsonSyntaxTree.N_OBJECT, tree.nodeType[1]); // root obj
        
        int keyStr = tree.nodeChild[1];
        assertEquals(JsonSyntaxTree.N_KEY, tree.nodeType[keyStr]);
        assertEquals("\"str\"", tree.nodeName[keyStr]);
        int valStr = tree.nodeChild[keyStr];
        assertEquals(JsonSyntaxTree.N_VALUE_STRING, tree.nodeType[valStr]);
        assertEquals("\"val\"", tree.nodeName[valStr]);
        
        int keyNum = tree.nodeSibling[keyStr];
        assertEquals(JsonSyntaxTree.N_KEY, tree.nodeType[keyNum]);
        assertEquals("\"num\"", tree.nodeName[keyNum]);
        int valNum = tree.nodeChild[keyNum];
        assertEquals(JsonSyntaxTree.N_VALUE_NUMBER, tree.nodeType[valNum]);
        assertEquals("123", tree.nodeName[valNum]);
        
        int keyArr = tree.nodeSibling[keyNum];
        assertEquals(JsonSyntaxTree.N_KEY, tree.nodeType[keyArr]);
        assertEquals("\"arr\"", tree.nodeName[keyArr]);
        int arr = tree.nodeChild[keyArr];
        assertEquals(JsonSyntaxTree.N_ARRAY, tree.nodeType[arr]);
        
        int boolT = tree.nodeChild[arr];
        assertEquals(JsonSyntaxTree.N_VALUE_BOOL, tree.nodeType[boolT]);
        assertEquals("true", tree.nodeName[boolT]);
        
        int boolF = tree.nodeSibling[boolT];
        assertEquals(JsonSyntaxTree.N_VALUE_BOOL, tree.nodeType[boolF]);
        assertEquals("false", tree.nodeName[boolF]);
        
        int valNull = tree.nodeSibling[boolF];
        assertEquals(JsonSyntaxTree.N_VALUE_NULL, tree.nodeType[valNull]);
        assertEquals("null", tree.nodeName[valNull]);
    }

    @Test
    public void testTrailingCommaAndMissingColon() {
        String source = "{ \"key\" \"val\", }";
        JsonTokenStream stream = JsonLexer.tokenize(source);
        JsonSyntaxTree tree = JsonParser.parse(stream, source);
        
        int obj = 1;
        assertEquals(JsonSyntaxTree.N_OBJECT, tree.nodeType[obj]);
        
        int key = tree.nodeChild[obj];
        assertEquals(JsonSyntaxTree.N_KEY, tree.nodeType[key]);
        assertEquals("\"key\"", tree.nodeName[key]);
        
        // Next is missing colon error
        int err1 = tree.nodeChild[key];
        assertEquals(JsonSyntaxTree.N_ERROR, tree.nodeType[err1]);
        
        int val = tree.nodeSibling[err1];
        assertEquals(JsonSyntaxTree.N_VALUE_STRING, tree.nodeType[val]);
        assertEquals("\"val\"", tree.nodeName[val]);
        
        // Then trailing comma error under obj (since key was popped after val)
        int err2 = tree.nodeSibling[key];
        assertEquals(JsonSyntaxTree.N_ERROR, tree.nodeType[err2]);
        assertEquals("Trailing comma", tree.nodeName[err2] != null ? tree.nodeName[err2] : "Trailing comma"); 
        // wait, I don't set nodeName for errors! The error message is passed to `addNode` as name!
        assertEquals("Trailing comma", tree.nodeName[err2]);
    }

    @Test
    public void testMissingQuote() {
        String source = "{ \"key: 123 }";
        JsonTokenStream stream = JsonLexer.tokenize(source);
        JsonSyntaxTree tree = JsonParser.parse(stream, source);
        
        int obj = 1;
        int err = tree.nodeChild[obj];
        assertEquals(JsonSyntaxTree.N_ERROR, tree.nodeType[err]);
        assertEquals("Missing closing quote", tree.nodeName[err]);
        
        int key = tree.nodeSibling[err];
        assertEquals(JsonSyntaxTree.N_KEY, tree.nodeType[key]);
        assertEquals("\"key: 123 }", tree.nodeName[key]);
    }
}
