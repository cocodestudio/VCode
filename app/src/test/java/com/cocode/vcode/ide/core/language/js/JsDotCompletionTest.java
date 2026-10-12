package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
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

    @Test
    public void testForOfLoopArrayElementInference() {
        String code = "const fruits = ['Apple', 'Banana', 'Cherry'];\n" +
                      "for (const fruit of fruits) {\n" +
                      "  fruit.\n" +
                      "}\n";
        int pos = code.indexOf("fruit.") + "fruit.".length();
        List<CompletionItem> comps = getCompletions(code, pos);
        boolean hasToUpperCase = false, hasTrim = false, hasCharAt = false;
        for (CompletionItem item : comps) {
            if ("toUpperCase".equals(item.getLabel())) hasToUpperCase = true;
            if ("trim".equals(item.getLabel())) hasTrim = true;
            if ("charAt".equals(item.getLabel())) hasCharAt = true;
        }
        assertTrue("Expected 'toUpperCase' for fruit.", hasToUpperCase);
        assertTrue("Expected 'trim' for fruit.", hasTrim);
        assertTrue("Expected 'charAt' for fruit.", hasCharAt);
    }

    @Test
    public void testForInLoopKeyInference() {
        String code = "const user = { name: 'Alice', age: 25, role: 'Developer' };\n" +
                      "for (const key in user) {\n" +
                      "  key.\n" +
                      "}\n";
        int pos = code.indexOf("key.") + "key.".length();
        List<CompletionItem> comps = getCompletions(code, pos);
        boolean hasToUpperCase = false, hasTrim = false;
        for (CompletionItem item : comps) {
            if ("toUpperCase".equals(item.getLabel())) hasToUpperCase = true;
            if ("trim".equals(item.getLabel())) hasTrim = true;
        }
        assertTrue("Expected 'toUpperCase' for key.", hasToUpperCase);
        assertTrue("Expected 'trim' for key.", hasTrim);
    }

    @Test
    public void testBracketDynamicIndexing_UnionCompletions_NotObjectKeys() {
        String code = "const user = { name: 'Alice', age: 25, role: 'Developer' };\n" +
                      "for (const key in user) {\n" +
                      "  user[key].\n" +
                      "}\n";
        int pos = code.indexOf("user[key].") + "user[key].".length();
        List<CompletionItem> comps = getCompletions(code, pos);
        boolean hasToUpperCase = false, hasToFixed = false, hasName = false, hasAge = false, hasRole = false;
        for (CompletionItem item : comps) {
            if ("toUpperCase".equals(item.getLabel())) hasToUpperCase = true;
            if ("toFixed".equals(item.getLabel())) hasToFixed = true;
            if ("name".equals(item.getLabel())) hasName = true;
            if ("age".equals(item.getLabel())) hasAge = true;
            if ("role".equals(item.getLabel())) hasRole = true;
        }
        assertTrue("Expected string methods like 'toUpperCase' for user[key].", hasToUpperCase);
        assertTrue("Expected number methods like 'toFixed' for user[key].", hasToFixed);
        org.junit.Assert.assertFalse("Should NOT suggest object property 'name' on user[key].", hasName);
        org.junit.Assert.assertFalse("Should NOT suggest object property 'age' on user[key].", hasAge);
        org.junit.Assert.assertFalse("Should NOT suggest object property 'role' on user[key].", hasRole);
    }

    @Test
    public void testBracketLiteralIndexing_SpecificType() {
        String code = "const user = { name: 'Alice', age: 25 };\n" +
                      "user['name'].\n" +
                      "user['age'].\n";
        int posName = code.indexOf("user['name'].") + "user['name'].".length();
        List<CompletionItem> nameComps = getCompletions(code, posName);
        boolean nameHasToUpperCase = false, nameHasToFixed = false;
        for (CompletionItem item : nameComps) {
            if ("toUpperCase".equals(item.getLabel())) nameHasToUpperCase = true;
            if ("toFixed".equals(item.getLabel())) nameHasToFixed = true;
        }
        assertTrue("Expected 'toUpperCase' for user['name'].", nameHasToUpperCase);
        org.junit.Assert.assertFalse("Should NOT suggest 'toFixed' for user['name'].", nameHasToFixed);

        int posAge = code.indexOf("user['age'].") + "user['age'].".length();
        List<CompletionItem> ageComps = getCompletions(code, posAge);
        boolean ageHasToFixed = false, ageHasToUpperCase = false;
        for (CompletionItem item : ageComps) {
            if ("toFixed".equals(item.getLabel())) ageHasToFixed = true;
            if ("toUpperCase".equals(item.getLabel())) ageHasToUpperCase = true;
        }
        assertTrue("Expected 'toFixed' for user['age'].", ageHasToFixed);
        org.junit.Assert.assertFalse("Should NOT suggest 'toUpperCase' for user['age'].", ageHasToUpperCase);
    }

    @Test
    public void testForEachCallbackParamsInference() {
        String code = "const numbers = [10, 20, 30];\n" +
                      "numbers.forEach((num, index) => {\n" +
                      "  num.\n" +
                      "  index.\n" +
                      "});\n";
        int posNum = code.indexOf("num.") + "num.".length();
        List<CompletionItem> numComps = getCompletions(code, posNum);
        boolean numHasToFixed = false;
        for (CompletionItem item : numComps) {
            if ("toFixed".equals(item.getLabel())) numHasToFixed = true;
        }
        assertTrue("Expected 'toFixed' for num. in forEach callback", numHasToFixed);

        int posIndex = code.indexOf("index.") + "index.".length();
        List<CompletionItem> indexComps = getCompletions(code, posIndex);
        boolean indexHasToFixed = false;
        for (CompletionItem item : indexComps) {
            if ("toFixed".equals(item.getLabel())) indexHasToFixed = true;
        }
        assertTrue("Expected 'toFixed' for index. in forEach callback", indexHasToFixed);
    }

    @Test
    public void testForAwaitOfPromiseArray() {
        String code = "async function demonstrateAsyncLoop() {\n" +
                      "  const asyncTasks = [\n" +
                      "    Promise.resolve('Task 1 complete'),\n" +
                      "    Promise.resolve('Task 2 complete'),\n" +
                      "    Promise.resolve('Task 3 complete')\n" +
                      "  ];\n" +
                      "  for await (const result of asyncTasks) {\n" +
                      "    result.\n" +
                      "  }\n" +
                      "}\n";
        int pos = code.indexOf("result.") + "result.".length();
        List<CompletionItem> comps = getCompletions(code, pos);
        boolean hasToUpperCase = false, hasTrim = false;
        for (CompletionItem item : comps) {
            if ("toUpperCase".equals(item.getLabel())) hasToUpperCase = true;
            if ("trim".equals(item.getLabel())) hasTrim = true;
        }
        assertTrue("Expected 'toUpperCase' for result. in for await loop", hasToUpperCase);
        assertTrue("Expected 'trim' for result. in for await loop", hasTrim);
    }

    @Test
    public void testTypeScriptLoopAndCallbackInference() {
        String code = "const fruits: string[] = ['Apple', 'Banana'];\n" +
                      "for (const fruit of fruits) {\n" +
                      "  fruit.\n" +
                      "}\n";
        int pos = code.indexOf("fruit.") + "fruit.".length();
        com.cocode.vcode.ide.core.language.ts.TsAutoCompleteEngine tsEngine =
                new com.cocode.vcode.ide.core.language.ts.TsAutoCompleteEngine(null);
        List<CompletionItem> comps = tsEngine.getSuggestions(code, pos);
        boolean hasToUpperCase = false;
        for (CompletionItem item : comps) {
            if ("toUpperCase".equals(item.getLabel())) hasToUpperCase = true;
        }
        assertTrue("Expected 'toUpperCase' for TypeScript fruit.", hasToUpperCase);
    }

    @Test
    public void testParenthesizedArithmeticInference() {
        String code = "let result = ((10 - 6) * 3); result.";
        List<CompletionItem> comps = getCompletions(code, code.length());
        assertTrue("Expected 'toFixed' for result.", hasCompletion(comps, "toFixed"));
        assertTrue("Expected 'toPrecision' for result.", hasCompletion(comps, "toPrecision"));
        assertTrue("Expected 'toExponential' for result.", hasCompletion(comps, "toExponential"));
    }

    @Test
    public void testBinarySubtractionInference() {
        String code = "let diff = a - b; diff.";
        List<CompletionItem> comps = getCompletions(code, code.length());
        assertTrue("Expected 'toFixed' for diff.", hasCompletion(comps, "toFixed"));
    }

    @Test
    public void testUnaryPlusInference() {
        String code = "let n = +str; n.";
        List<CompletionItem> comps = getCompletions(code, code.length());
        assertTrue("Expected 'toFixed' for n.", hasCompletion(comps, "toFixed"));
    }

    @Test
    public void testRelationalOperatorBooleanInference() {
        String code = "let flag = count > 0; flag.";
        List<CompletionItem> comps = getCompletions(code, code.length());
        assertTrue("Expected 'valueOf' for flag.", hasCompletion(comps, "valueOf"));
        assertTrue("Expected 'toString' for flag.", hasCompletion(comps, "toString"));
    }

    @Test
    public void testTemplateLiteralStringInference() {
        String code = "let msg = `Hello ${name}`; msg.";
        List<CompletionItem> comps = getCompletions(code, code.length());
        assertTrue("Expected 'toUpperCase' for msg.", hasCompletion(comps, "toUpperCase"));
        assertTrue("Expected 'trim' for msg.", hasCompletion(comps, "trim"));
        assertTrue("Expected 'slice' for msg.", hasCompletion(comps, "slice"));
    }

    @Test
    public void testRegexLiteralInference() {
        String code = "let re = /^[a-z]+$/; re.";
        List<CompletionItem> comps = getCompletions(code, code.length());
        assertTrue("Expected 'test' for re.", hasCompletion(comps, "test"));
        assertTrue("Expected 'exec' for re.", hasCompletion(comps, "exec"));
    }

    @Test
    public void testMathBuiltinCallInference() {
        String code = "let rounded = Math.round(x); rounded.";
        List<CompletionItem> comps = getCompletions(code, code.length());
        assertTrue("Expected 'toFixed' for rounded.", hasCompletion(comps, "toFixed"));
    }

    @Test
    public void testDateConstructorInference() {
        String code = "let d = new Date(); d.";
        List<CompletionItem> comps = getCompletions(code, code.length());
        assertTrue("Expected 'getTime' for d.", hasCompletion(comps, "getTime"));
        assertTrue("Expected 'getFullYear' for d.", hasCompletion(comps, "getFullYear"));
    }

    @Test
    public void testVariableAliasingInference() {
        String code = "let a = 10; let b = a; b.";
        List<CompletionItem> comps = getCompletions(code, code.length());
        assertTrue("Expected 'toFixed' for b.", hasCompletion(comps, "toFixed"));
    }

    @Test
    public void testDirectParenthesizedArithmeticDotAccess() {
        String code = "((10 - 6) * 3).";
        List<CompletionItem> comps = getCompletions(code, code.length());
        assertTrue("Expected 'toFixed' for ((10 - 6) * 3).", hasCompletion(comps, "toFixed"));
    }

    @Test
    public void testMultilineDotChaining_LeadingDots() {
        String code = "let result = 10;\nresult\n    .toFixed(2)\n    .";
        List<CompletionItem> comps = getCompletions(code, code.length());
        boolean hasToUpperCase = false, hasCharAt = false;
        for (CompletionItem item : comps) {
            if ("toUpperCase".equals(item.getLabel())) hasToUpperCase = true;
            if ("charAt".equals(item.getLabel())) hasCharAt = true;
        }
        assertTrue("Expected toUpperCase in string completions", hasToUpperCase);
        assertTrue("Expected charAt in string completions", hasCharAt);
    }

    @Test
    public void testMultilineDotChaining_3Level() {
        String code = "let result = 10;\nresult\n    .toFixed(2)\n    .charAt(0)\n    .";
        List<CompletionItem> comps = getCompletions(code, code.length());
        boolean hasToUpperCase = false;
        for (CompletionItem item : comps) {
            if ("toUpperCase".equals(item.getLabel())) hasToUpperCase = true;
        }
        assertTrue("Expected toUpperCase after 3-level chain", hasToUpperCase);
    }

    @Test
    public void testMultilineDotChaining_TrailingDots() {
        String code = "let result = 10;\nresult.\n    toFixed(2).\n    ";
        List<CompletionItem> comps = getCompletions(code, code.length());
        boolean hasToUpperCase = false;
        for (CompletionItem item : comps) {
            if ("toUpperCase".equals(item.getLabel())) hasToUpperCase = true;
        }
        assertTrue("Expected toUpperCase with trailing dots", hasToUpperCase);
    }

    @Test
    public void testMultilineDotChaining_WithComments() {
        String code = "let result = 10;\nresult\n    // format decimal\n    .toFixed(2)\n    /* get char */\n    .";
        List<CompletionItem> comps = getCompletions(code, code.length());
        boolean hasToUpperCase = false;
        for (CompletionItem item : comps) {
            if ("toUpperCase".equals(item.getLabel())) hasToUpperCase = true;
        }
        assertTrue("Expected toUpperCase with comments in chain", hasToUpperCase);
    }

    private boolean hasCompletion(List<CompletionItem> items, String label) {
        if (items == null) return false;
        for (CompletionItem ci : items) {
            if (label.equals(ci.getLabel())) return true;
        }
        return false;
    }
}
