package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JsDotCompletionTest extends BaseJsAstTest {

    @Test
    public void testObjectLiteralShapeResolution_C2() {
        String code = "const obj = { foo: 1, bar: 2 }; obj.";
        
        List<CompletionItem> comps = getCompletions(code, code.length());
        
        boolean hasFoo = false, hasBar = false;
        for (CompletionItem item : comps) {
            if ("foo".equals(item.getLabel())) hasFoo = true;
            if ("bar".equals(item.getLabel())) hasBar = true;
        }
        
        assertTrue("Expected 'foo' in completions", hasFoo);
        assertTrue("Expected 'bar' in completions", hasBar);
        assertEquals("Expected exactly two completions (foo, bar)", 2, comps.size());
    }

    @Test
    public void testOptionalChaining_C10() {
        String code = "const obj = { foo: 1, bar: 2 }; obj?.";
        
        List<CompletionItem> comps = getCompletions(code, code.length());
        
        boolean hasFoo = false, hasBar = false;
        for (CompletionItem item : comps) {
            if ("foo".equals(item.getLabel())) hasFoo = true;
            if ("bar".equals(item.getLabel())) hasBar = true;
        }
        
        assertTrue("Expected 'foo' in completions", hasFoo);
        assertTrue("Expected 'bar' in completions", hasBar);
        assertEquals("Expected exactly two completions (foo, bar)", 2, comps.size());
    }

    @Test
    public void testClassMemberRegistry_C3() {
        String code = "class User { name = ''; constructor() { this.age = 0; } greet() {} }\nconst u = new User(); u.";
        
        List<CompletionItem> comps = getCompletions(code, code.length());
        
        boolean hasName = false, hasAge = false, hasGreet = false;
        for (CompletionItem item : comps) {
            if ("name".equals(item.getLabel())) hasName = true;
            if ("age".equals(item.getLabel())) hasAge = true;
            if ("greet".equals(item.getLabel())) hasGreet = true;
        }
        
        assertTrue("Expected 'name' in completions", hasName);
        assertTrue("Expected 'age' in completions", hasAge);
        assertTrue("Expected 'greet' in completions", hasGreet);
    }

    @Test
    public void testClassInheritance_C4() {
        String code = "class A { foo() {} } class B extends A { bar() {} }\nconst b = new B(); b.";
        
        List<CompletionItem> comps = getCompletions(code, code.length());
        
        boolean hasFoo = false, hasBar = false;
        for (CompletionItem item : comps) {
            if ("foo".equals(item.getLabel())) hasFoo = true;
            if ("bar".equals(item.getLabel())) hasBar = true;
        }
        
        assertTrue("Expected 'foo' in completions (inherited)", hasFoo);
        assertTrue("Expected 'bar' in completions", hasBar);
    }

    @Test
    public void testThisCompletion_C5() {
        String code = "class A { name = ''; age = 0; foo() { this. } }";
        // Find position of 'this.'
        int pos = code.indexOf("this.") + 5;
        
        List<CompletionItem> comps = getCompletions(code, pos);
        
        boolean hasName = false, hasAge = false, hasFoo = false;
        for (CompletionItem item : comps) {
            if ("name".equals(item.getLabel())) hasName = true;
            if ("age".equals(item.getLabel())) hasAge = true;
            if ("foo".equals(item.getLabel())) hasFoo = true;
        }
        
        assertTrue("Expected 'name' in completions", hasName);
        assertTrue("Expected 'age' in completions", hasAge);
        assertTrue("Expected 'foo' in completions", hasFoo);
    }

    @Test
    public void testBaseTypeInference_C6() {
        String baseCode = "const a = []; const s = \"hi\"; const m = new Map(); ";
        
        List<CompletionItem> compsA = getCompletions(baseCode + "a.", baseCode.length() + 2);
        
        boolean hasMap = false, hasPush = false;
        for (CompletionItem item : compsA) {
            if ("map".equals(item.getLabel())) hasMap = true;
            if ("push".equals(item.getLabel())) hasPush = true;
        }
        assertTrue("Expected 'map' in Array completions", hasMap);
        assertTrue("Expected 'push' in Array completions", hasPush);

        List<CompletionItem> compsS = getCompletions(baseCode + "s.", baseCode.length() + 2);
        boolean hasSplit = false;
        for (CompletionItem item : compsS) {
            if ("split".equals(item.getLabel())) hasSplit = true;
        }
        assertTrue("Expected 'split' in String completions", hasSplit);
    }

    @Test
    public void testChainResolution_C7() {
        String code = "const arr = []; arr.map(x=>x).filter(x=>x).";
        List<CompletionItem> comps = getCompletions(code, code.length());
        
        boolean hasPush = false, hasJoin = false;
        for (CompletionItem item : comps) {
            if ("push".equals(item.getLabel())) hasPush = true;
            if ("join".equals(item.getLabel())) hasJoin = true;
        }
        
        assertTrue("Expected Array methods like 'push' after filter()", hasPush);
        assertTrue("Expected Array methods like 'join' after filter()", hasJoin);
    }

    @Test
    public void testLiteralStringDotCompletion() {
        String code = "\"Hello\".";
        List<CompletionItem> comps = getCompletions(code, code.length());
        
        boolean hasToUpperCase = false, hasSlice = false;
        for (CompletionItem item : comps) {
            if ("toUpperCase".equals(item.getLabel())) hasToUpperCase = true;
            if ("slice".equals(item.getLabel())) hasSlice = true;
        }
        
        assertTrue("Expected 'toUpperCase' on string literal", hasToUpperCase);
        assertTrue("Expected 'slice' on string literal", hasSlice);
    }

    @Test
    public void testLiteralArrayDotCompletion() {
        String code = "[1, 2, 3].";
        List<CompletionItem> comps = getCompletions(code, code.length());
        
        boolean hasMap = false, hasFilter = false;
        for (CompletionItem item : comps) {
            if ("map".equals(item.getLabel())) hasMap = true;
            if ("filter".equals(item.getLabel())) hasFilter = true;
        }
        
        assertTrue("Expected 'map' on array literal", hasMap);
        assertTrue("Expected 'filter' on array literal", hasFilter);
    }

    @Test
    public void testNestedObjectAndMethodReturnChaining() {
        String code = "const user = { profile: { address: '123 Main' } }; user.profile.address.toUpperCase().";
        List<CompletionItem> comps = getCompletions(code, code.length());
        
        boolean hasToLowerCase = false, hasTrim = false;
        for (CompletionItem item : comps) {
            if ("toLowerCase".equals(item.getLabel())) hasToLowerCase = true;
            if ("trim".equals(item.getLabel())) hasTrim = true;
        }
        
        assertTrue("Expected 'toLowerCase' on chained string method return", hasToLowerCase);
        assertTrue("Expected 'trim' on chained string method return", hasTrim);
    }

    @Test
    public void testNestedObjectPropertyCompletion() {
        String code = "const user = { address: { city: 'NYC', zip: 10001 } }; user.address.";
        List<CompletionItem> comps = getCompletions(code, code.length());
        
        boolean hasCity = false, hasZip = false;
        for (CompletionItem item : comps) {
            if ("city".equals(item.getLabel())) hasCity = true;
            if ("zip".equals(item.getLabel())) hasZip = true;
        }
        
        assertTrue("Expected 'city' on nested object", hasCity);
        assertTrue("Expected 'zip' on nested object", hasZip);
    }

    @Test
    public void testClassMemberDotCompletion_UserScenario() {
        String code = "class MyClass {\n" +
                "  const x = 1;\n" +
                "  constructor() {\n" +
                "    this.x = 1;\n" +
                "  }\n" +
                "  \n" +
                "  getVal() {\n" +
                "    return x;\n" +
                "  }\n" +
                "}\n" +
                "\n" +
                "const myClass = new MyClass();\n" +
                "myClass.";

        List<CompletionItem> comps = getCompletions(code, code.length());

        int getValCount = 0;
        int xCount = 0;
        boolean hasConst = false;
        boolean hasConstructor = false;
        CompletionItem getValItem = null;
        CompletionItem xItem = null;

        for (CompletionItem item : comps) {
            String label = item.getLabel();
            if ("getVal".equals(label)) {
                getValCount++;
                getValItem = item;
            } else if ("x".equals(label)) {
                xCount++;
                xItem = item;
            } else if ("const".equals(label)) {
                hasConst = true;
            } else if ("constructor".equals(label)) {
                hasConstructor = true;
            }
        }

        assertEquals("getVal should appear exactly once", 1, getValCount);
        org.junit.Assert.assertNotNull("getVal item should exist", getValItem);
        assertEquals("getVal detail should be 'MyClass method'", "MyClass method", getValItem.getDetail());
        assertEquals("getVal type should be FUNCTION", CompletionItem.Type.FUNCTION, getValItem.getType());

        assertEquals("x should appear exactly once", 1, xCount);
        org.junit.Assert.assertNotNull("x item should exist", xItem);
        assertEquals("x detail should be 'MyClass property'", "MyClass property", xItem.getDetail());
        assertEquals("x type should be VALUE", CompletionItem.Type.VALUE, xItem.getType());

        org.junit.Assert.assertFalse("'const' should NOT appear in completions", hasConst);
        org.junit.Assert.assertFalse("'constructor' should NOT appear in completions", hasConstructor);
    }

    @Test
    public void testClassMemberDotCompletion_FunctionKeywordMethod() {
        String code = "class MyClass {\n" +
                "  const x = 1;\n" +
                "  constructor() {\n" +
                "    this.x = 1;\n" +
                "  }\n" +
                "  \n" +
                " function getVal() {\n" +
                "    return x;\n" +
                "  }\n" +
                "\n" +
                "}\n" +
                "\n" +
                "const myClass = new MyClass();\n" +
                "\n" +
                "myClass.";

        List<CompletionItem> comps = getCompletions(code, code.length());

        int getValCount = 0;
        int xCount = 0;
        boolean hasFunction = false;
        boolean hasConst = false;
        boolean hasConstructor = false;
        CompletionItem getValItem = null;
        CompletionItem xItem = null;

        for (CompletionItem item : comps) {
            String label = item.getLabel();
            if ("getVal".equals(label)) {
                getValCount++;
                getValItem = item;
            } else if ("x".equals(label)) {
                xCount++;
                xItem = item;
            } else if ("function".equals(label)) {
                hasFunction = true;
            } else if ("const".equals(label)) {
                hasConst = true;
            } else if ("constructor".equals(label)) {
                hasConstructor = true;
            }
        }

        org.junit.Assert.assertFalse("'function' should NOT appear in completions", hasFunction);
        org.junit.Assert.assertFalse("'const' should NOT appear in completions", hasConst);
        org.junit.Assert.assertFalse("'constructor' should NOT appear in completions", hasConstructor);

        assertEquals("getVal should appear exactly once", 1, getValCount);
        org.junit.Assert.assertNotNull("getVal item should exist", getValItem);
        assertEquals("getVal detail should be 'MyClass method'", "MyClass method", getValItem.getDetail());
        assertEquals("getVal type should be FUNCTION", CompletionItem.Type.FUNCTION, getValItem.getType());

        assertEquals("x should appear exactly once", 1, xCount);
        org.junit.Assert.assertNotNull("x item should exist", xItem);
        assertEquals("x detail should be 'MyClass property'", "MyClass property", xItem.getDetail());
        assertEquals("x type should be VALUE", CompletionItem.Type.VALUE, xItem.getType());
    }

    @Test
    public void testNamespaceAndTopLevelBlockDoNotTriggerObjectLiteralKeyCompletions() {
        String code = "const config = { apiKey: '123', timeout: 5000 };\n" +
                      "namespace Service {\n" +
                      "    {\n" +
                      "";
        List<CompletionItem> comps = getCompletions(code, code.length());
        for (CompletionItem item : comps) {
            org.junit.Assert.assertNotEquals("Should not suggest 'Object key' inside namespace or block",
                    "Object key", item.getDetail());
        }
    }
}
