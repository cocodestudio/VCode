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
}
