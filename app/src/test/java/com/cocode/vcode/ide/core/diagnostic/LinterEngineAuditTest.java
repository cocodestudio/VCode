package com.cocode.vcode.ide.core.diagnostic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cocode.vcode.ide.core.language.css.CssLinter;
import com.cocode.vcode.ide.core.language.js.JsLinter;
import com.cocode.vcode.ide.core.language.ts.TsLinter;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.model.Problem;

import org.junit.Test;

import java.io.File;
import java.util.List;

public class LinterEngineAuditTest {

    private final File mockJsFile = new File("test.js");
    private final File mockTsFile = new File("test.ts");
    private final File mockCssFile = new File("test.css");
    private final File mockHtmlFile = new File("test.html");

    private List<Problem> lintJs(String code) {
        return JsLinter.analyze(mockJsFile, code);
    }

    private List<Problem> lintTs(String code) {
        return TsLinter.analyze(mockTsFile, code);
    }

    private boolean hasErrorMatching(List<Problem> problems, String snippet) {
        for (Problem p : problems) {
            if (p.getSeverity() == Problem.Severity.ERROR && p.getMessage().contains(snippet)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUndefinedVariableError(List<Problem> problems, String varName) {
        return hasErrorMatching(problems, "'" + varName + "' is not defined");
    }

    // AUDIT ISSUE-1: for..of loop variable and contextual keyword 'of'
    @Test
    public void testForOfLoopVariableAndKeyword() {
        String code = "const list = [1, 2, 3];\n" +
                      "for (const item of list) {\n" +
                      "    console.log(item);\n" +
                      "}\n";
        List<Problem> problems = lintJs(code);
        assertFalse("Should not flag 'of' as undefined", hasUndefinedVariableError(problems, "of"));
        assertFalse("Should not flag 'item' as undefined", hasUndefinedVariableError(problems, "item"));
    }

    @Test
    public void testForOfSingleStatementNoBraces() {
        String code = "const arr = ['a', 'b'];\n" +
                      "for (let x of arr)\n" +
                      "    console.log(x);\n";
        List<Problem> problems = lintJs(code);
        assertFalse("Should not flag 'of' as undefined", hasUndefinedVariableError(problems, "of"));
        assertFalse("Should not flag 'x' as undefined", hasUndefinedVariableError(problems, "x"));
    }

    // AUDIT ISSUE-2: for..of and forEach destructuring with renames and defaults
    @Test
    public void testForOfDestructuringWithRenames() {
        String code = "const users = [{ oldName: 'Alice', age: 30 }];\n" +
                      "for (const { oldName: newName, age } of users) {\n" +
                      "    console.log(newName, age);\n" +
                      "}\n";
        List<Problem> problems = lintJs(code);
        assertFalse("Should not flag 'newName' as undefined", hasUndefinedVariableError(problems, "newName"));
        assertFalse("Should not flag 'age' as undefined", hasUndefinedVariableError(problems, "age"));
    }

    @Test
    public void testForEachWithDefaultParamsInDestructuring() {
        String code = "const items = [{ a: 1 }];\n" +
                      "items.forEach(({ a, b = 2, c = 'default' }) => {\n" +
                      "    console.log(a, b, c);\n" +
                      "});\n";
        List<Problem> problems = lintJs(code);
        assertFalse("Should not flag 'a' as undefined", hasUndefinedVariableError(problems, "a"));
        assertFalse("Should not flag 'b' as undefined", hasUndefinedVariableError(problems, "b"));
        assertFalse("Should not flag 'c' as undefined", hasUndefinedVariableError(problems, "c"));
    }

    @Test
    public void testForEachArrayDestructuringRest() {
        String code = "const rows = [[1, 2, 3]];\n" +
                      "rows.forEach(([head, ...tail]) => {\n" +
                      "    console.log(head, tail);\n" +
                      "});\n";
        List<Problem> problems = lintJs(code);
        assertFalse("Should not flag 'head' as undefined", hasUndefinedVariableError(problems, "head"));
        assertFalse("Should not flag 'tail' as undefined", hasUndefinedVariableError(problems, "tail"));
    }

    // AUDIT ISSUE-3: try..catch destructuring parameter
    @Test
    public void testCatchClauseDestructuring() {
        String code = "try {\n" +
                      "    doWork();\n" +
                      "} catch ({ message, stack }) {\n" +
                      "    console.log(message, stack);\n" +
                      "}\n";
        List<Problem> problems = lintJs(code);
        assertFalse("Should not flag 'message' as undefined", hasUndefinedVariableError(problems, "message"));
        assertFalse("Should not flag 'stack' as undefined", hasUndefinedVariableError(problems, "stack"));
    }

    // AUDIT ISSUE-4: Loop labels in break and continue
    @Test
    public void testLoopLabelsInBreakAndContinue() {
        String code = "outerLoop: for (let i = 0; i < 5; i++) {\n" +
                      "    innerLoop: for (let j = 0; j < 5; j++) {\n" +
                      "        if (j === 2) continue innerLoop;\n" +
                      "        if (i === 3) break outerLoop;\n" +
                      "    }\n" +
                      "}\n";
        List<Problem> problems = lintJs(code);
        assertFalse("Should not flag 'outerLoop' as undefined", hasUndefinedVariableError(problems, "outerLoop"));
        assertFalse("Should not flag 'innerLoop' as undefined", hasUndefinedVariableError(problems, "innerLoop"));
    }

    // AUDIT ISSUE-5: typeof on undeclared variable (ECMA-262 compliance)
    @Test
    public void testTypeofUndeclaredVariableNotFlagged() {
        String code = "if (typeof customFeatureFlag !== 'undefined') {\n" +
                      "    console.log('Feature active');\n" +
                      "}\n";
        List<Problem> problems = lintJs(code);
        assertFalse("Should not flag 'customFeatureFlag' inside typeof as undefined",
                hasUndefinedVariableError(problems, "customFeatureFlag"));
    }

    // AUDIT ISSUE-6: CSS variable unused warning accurate line/column
    @Test
    public void testCssVariableAccurateLineAndColumn() {
        String css = ":root {\n" +
                     "  --my-unused-color: #ff0000;\n" +
                     "}\n" +
                     "body {\n" +
                     "  color: blue;\n" +
                     "}\n";
        List<Problem> problems = CssLinter.analyze(mockCssFile, css);
        Problem unusedProp = null;
        for (Problem p : problems) {
            if (p.getMessage().contains("--my-unused-color") && p.getMessage().contains("never used")) {
                unusedProp = p;
                break;
            }
        }
        assertTrue("Should report unused CSS variable", unusedProp != null);
        assertEquals("Should report line of the declaration", 2, unusedProp.getLine());
        assertEquals("Should report column of the declaration", 3, unusedProp.getColumn());
    }

    // AUDIT ISSUE-7: Embedded <script> linting in HTML via DiagnosticEngine
    @Test
    public void testHtmlEmbeddedScriptLinting() {
        String html = "<!DOCTYPE html>\n" +
                      "<html>\n" +
                      "<head><title>Test</title></head>\n" +
                      "<body>\n" +
                      "  <script>\n" +
                      "    const list = [1, 2];\n" +
                      "    for (const item of list) {\n" +
                      "        console.log(item);\n" +
                      "    }\n" +
                      "  </script>\n" +
                      "</body>\n" +
                      "</html>";
        List<Problem> problems = DiagnosticEngine.analyze(mockHtmlFile, html, FileType.HTML);
        assertFalse("Should not flag 'of' in embedded script", hasUndefinedVariableError(problems, "of"));
        assertFalse("Should not flag 'item' in embedded script", hasUndefinedVariableError(problems, "item"));
    }

    @Test
    public void testHtmlEmbeddedScriptFlagsUndefinedVariableAtExactLine() {
        String html = "<!DOCTYPE html>\n" +
                      "<html>\n" +
                      "<body>\n" +
                      "  <script>\n" +
                      "    undeclaredBadFunctionCall();\n" +
                      "  </script>\n" +
                      "</body>\n" +
                      "</html>";
        List<Problem> problems = DiagnosticEngine.analyze(mockHtmlFile, html, FileType.HTML);
        Problem badFunc = null;
        for (Problem p : problems) {
            if (p.getMessage().contains("undeclaredBadFunctionCall")) {
                badFunc = p;
                break;
            }
        }
        if (badFunc == null) {
            System.out.println("DEBUG: problems size=" + problems.size());
            for (Problem p : problems) {
                System.out.println("DEBUG: " + p.getLine() + ":" + p.getColumn() + " " + p.getMessage());
            }
        }
        assertTrue("Should flag undeclared variable in embedded script", badFunc != null);
        assertEquals("Line should be mapped to the enclosing HTML line 5", 5, badFunc.getLine());
    }

    // AUDIT ISSUE-8: Undeclared function calls, class instantiations, and member calls
    @Test
    public void testUndeclaredFunctionCallAndNewClass() {
        String code = "undeclaredFunctionCall();\n" +
                      "const x = new UndeclaredClass();\n" +
                      "undeclaredObj.doWork();\n";
        List<Problem> problems = lintJs(code);
        assertTrue("Should flag undeclared function call", hasUndefinedVariableError(problems, "undeclaredFunctionCall"));
        assertTrue("Should flag undeclared class instantiation", hasUndefinedVariableError(problems, "UndeclaredClass"));
        assertTrue("Should flag undeclared base object", hasUndefinedVariableError(problems, "undeclaredObj"));
    }

    @Test
    public void testDeclaredFunctionAndClassCallPass() {
        String code = "function myFunc() {}\n" +
                      "myFunc();\n" +
                      "class MyClass {}\n" +
                      "const x = new MyClass();\n" +
                      "const obj = { doWork() {} };\n" +
                      "obj.doWork();\n";
        List<Problem> problems = lintJs(code);
        assertFalse("Should not flag declared function", hasUndefinedVariableError(problems, "myFunc"));
        assertFalse("Should not flag declared class", hasUndefinedVariableError(problems, "MyClass"));
        assertFalse("Should not flag declared obj", hasUndefinedVariableError(problems, "obj"));
    }

    // AUDIT ISSUE-9: Private fields and methods (#field)
    @Test
    public void testPrivateIdentifierNotFlagged() {
        String code = "class BankAccount {\n" +
                      "    #balance = 0;\n" +
                      "    deposit(amount) {\n" +
                      "        this.#balance += amount;\n" +
                      "    }\n" +
                      "}\n";
        List<Problem> problems = lintJs(code);
        assertFalse("Should not flag private field '#balance' as undefined", hasUndefinedVariableError(problems, "balance"));
    }

    // AUDIT ISSUE-10: Shorthand object properties
    @Test
    public void testShorthandObjectPropertyUndeclaredAndDeclared() {
        String badCode = "const obj = { undeclaredVar };\n";
        List<Problem> badProblems = lintJs(badCode);
        assertTrue("Should flag undeclared shorthand property", hasUndefinedVariableError(badProblems, "undeclaredVar"));

        String goodCode = "const declaredVar = 1;\n" +
                          "const obj = { declaredVar };\n";
        List<Problem> goodProblems = lintJs(goodCode);
        assertFalse("Should not flag declared shorthand property", hasUndefinedVariableError(goodProblems, "declaredVar"));
    }

    // AUDIT ISSUE-11: TypeScript namespace keyword on variables vs declaration
    @Test
    public void testTsNamespaceKeywordNotFlaggedOnVariables() {
        String code = "const namespace = 'my-namespace';\n" +
                      "const config = { namespace: 'default' };\n";
        List<Problem> problems = lintTs(code);
        boolean hasNamespaceWarning = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("'namespace' is discouraged")) {
                hasNamespaceWarning = true;
                break;
            }
        }
        assertFalse("Should not flag variable or property named 'namespace'", hasNamespaceWarning);

        String realNamespaceCode = "namespace MyUtils {\n" +
                                   "    export const version = '1.0';\n" +
                                   "}\n";
        List<Problem> realProblems = lintTs(realNamespaceCode);
        boolean hasRealNamespaceWarning = false;
        for (Problem p : realProblems) {
            if (p.getMessage().contains("'namespace' is discouraged")) {
                hasRealNamespaceWarning = true;
                break;
            }
        }
        assertTrue("Should flag real TypeScript namespace declaration", hasRealNamespaceWarning);
    }

    // AUDIT ISSUE-12: TypeScript 'as' in import/export aliases
    @Test
    public void testTsAsAssertionImportExportNotFlagged() {
        String code = "import { Component as MyComponent } from 'react';\n" +
                      "export { MyComponent as DefaultComp };\n";
        List<Problem> problems = lintTs(code);
        boolean hasAsAssertionWarning = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Type assertion 'as")) {
                hasAsAssertionWarning = true;
                break;
            }
        }
        assertFalse("Should not flag 'as' alias in import/export as type assertion", hasAsAssertionWarning);
    }

    // AUDIT ISSUE-13: TypeScript interface and type hoisting
    @Test
    public void testTsInterfaceHoisting() {
        String code = "function getUser(): UserProfile {\n" +
                      "    return { name: 'Alice' };\n" +
                      "}\n" +
                      "interface UserProfile {\n" +
                      "    name: string;\n" +
                      "}\n";
        List<Problem> problems = lintTs(code);
        assertFalse("Should not flag hoisted interface 'UserProfile' as undefined",
                hasUndefinedVariableError(problems, "UserProfile"));
    }

    // AUDIT ISSUE-14: Exported functions and variables must check undefined variables
    @Test
    public void testExportedFunctionUndeclaredVariableFlagged() {
        String code = "export function testFunc() {\n" +
                      "    undeclaredInsideExport();\n" +
                      "}\n" +
                      "export const x = undeclaredConstInit;\n" +
                      "export { existingVar as myAlias };\n" +
                      "export function okExport() {\n" +
                      "    const ok = 1;\n" +
                      "    return ok;\n" +
                      "}\n";
        List<Problem> problems = lintJs(code);
        assertTrue("Should flag undeclared variable inside export function",
                hasUndefinedVariableError(problems, "undeclaredInsideExport"));
        assertTrue("Should flag undeclared variable in export const initializer",
                hasUndefinedVariableError(problems, "undeclaredConstInit"));
        assertFalse("Should not flag declared local variable inside exported function",
                hasUndefinedVariableError(problems, "ok"));
    }

    @Test
    public void testLoopsCleanSnippet() {
        String code = "console.log(\"--- 1. Standard For Loop ---\");\n" +
                "for (let i = 0; i < 3; i++) {\n" +
                "  console.log(`Index: ${i}`);\n" +
                "}\n" +
                "\n" +
                "console.log(\"\\n--- 2. While Loop ---\");\n" +
                "let count = 0;\n" +
                "while (count < 3) {\n" +
                "  console.log(`Count: ${count}`);\n" +
                "  count++;\n" +
                "}\n" +
                "\n" +
                "console.log(\"\\n--- 3. Do...While Loop ---\");\n" +
                "let step = 0;\n" +
                "do {\n" +
                "  console.log(`Step: ${step}`);\n" +
                "  step++;\n" +
                "} while (step < 3);\n" +
                "\n" +
                "console.log(\"\\n--- 4. For...Of Loop (Arrays & Strings) ---\");\n" +
                "const fruits = ['Apple', 'Banana', 'Cherry'];\n" +
                "for (const fruit of fruits) {\n" +
                "  console.log(`Fruit: ${fruit}`);\n" +
                "}\n" +
                "\n" +
                "const word = 'JS';\n" +
                "for (const char of word) {\n" +
                "  console.log(`Char: ${char}`);\n" +
                "}\n" +
                "\n" +
                "console.log(\"\\n--- 5. For...In Loop (Objects) ---\");\n" +
                "const user = { name: 'Alice', age: 25, role: 'Developer' };\n" +
                "for (const key in user) {\n" +
                "  console.log(`${key}: ${user[key]}`);\n" +
                "}\n" +
                "\n" +
                "console.log(\"\\n--- 6. Array .forEach() ---\");\n" +
                "const numbers = [10, 20, 30];\n" +
                "numbers.forEach((num, index) => {\n" +
                "  console.log(`Element at index ${index}: ${num}`);\n" +
                "});\n" +
                "\n" +
                "console.log(\"\\n--- 7. For Await...Of Loop (Async) ---\");\n" +
                "async function demonstrateAsyncLoop() {\n" +
                "  const asyncTasks = [\n" +
                "    Promise.resolve('Task 1 complete'),\n" +
                "    Promise.resolve('Task 2 complete'),\n" +
                "    Promise.resolve('Task 3 complete')\n" +
                "  ];\n" +
                "\n" +
                "  for await (const result of asyncTasks) {\n" +
                "    console.log(result);\n" +
                "  }\n" +
                "}\n" +
                "demonstrateAsyncLoop();\n";

        List<Problem> problems = lintJs(code);
        assertFalse("Should not flag 'i' as undefined", hasUndefinedVariableError(problems, "i"));
        assertFalse("Should not flag 'count' as undefined", hasUndefinedVariableError(problems, "count"));
        assertFalse("Should not flag 'step' as undefined", hasUndefinedVariableError(problems, "step"));
        assertFalse("Should not flag 'fruits' as undefined", hasUndefinedVariableError(problems, "fruits"));
        assertFalse("Should not flag 'fruit' as undefined", hasUndefinedVariableError(problems, "fruit"));
        assertFalse("Should not flag 'word' as undefined", hasUndefinedVariableError(problems, "word"));
        assertFalse("Should not flag 'char' as undefined", hasUndefinedVariableError(problems, "char"));
        assertFalse("Should not flag 'user' as undefined", hasUndefinedVariableError(problems, "user"));
        assertFalse("Should not flag 'key' as undefined", hasUndefinedVariableError(problems, "key"));
        assertFalse("Should not flag 'numbers' as undefined", hasUndefinedVariableError(problems, "numbers"));
        assertFalse("Should not flag 'num' as undefined", hasUndefinedVariableError(problems, "num"));
        assertFalse("Should not flag 'index' as undefined", hasUndefinedVariableError(problems, "index"));
        assertFalse("Should not flag 'asyncTasks' as undefined", hasUndefinedVariableError(problems, "asyncTasks"));
        assertFalse("Should not flag 'result' as undefined", hasUndefinedVariableError(problems, "result"));
    }

    @Test
    public void testLoopsSimulatedKeystrokes() {
        String code = "console.log(\"--- 1. Standard For Loop ---\");\n" +
                "for (let i = 0; i < 3; i++) {\n" +
                "  console.log(`Index: ${i}`);\n" +
                "}\n" +
                "\n" +
                "console.log(\"\\n--- 2. While Loop ---\");\n" +
                "let count = 0;\n" +
                "while (count < 3) {\n" +
                "  console.log(`Count: ${count}`);\n" +
                "  count++;\n" +
                "}\n" +
                "\n" +
                "console.log(\"\\n--- 3. Do...While Loop ---\");\n" +
                "let step = 0;\n" +
                "do {\n" +
                "  console.log(`Step: ${step}`);\n" +
                "  step++;\n" +
                "} while (step < 3);\n" +
                "\n" +
                "console.log(\"\\n--- 4. For...Of Loop (Arrays & Strings) ---\");\n" +
                "const fruits = ['Apple', 'Banana', 'Cherry'];\n" +
                "for (const fruit of fruits) {\n" +
                "  console.log(`Fruit: ${fruit}`);\n" +
                "}\n" +
                "\n" +
                "const word = 'JS';\n" +
                "for (const char of word) {\n" +
                "  console.log(`Char: ${char}`);\n" +
                "}\n" +
                "\n" +
                "console.log(\"\\n--- 5. For...In Loop (Objects) ---\");\n" +
                "const user = { name: 'Alice', age: 25, role: 'Developer' };\n" +
                "for (const key in user) {\n" +
                "  console.log(`${key}: ${user[key]}`);\n" +
                "}\n" +
                "\n" +
                "console.log(\"\\n--- 6. Array .forEach() ---\");\n" +
                "const numbers = [10, 20, 30];\n" +
                "numbers.forEach((num, index) => {\n" +
                "  console.log(`Element at index ${index}: ${num}`);\n" +
                "});\n" +
                "\n" +
                "console.log(\"\\n--- 7. For Await...Of Loop (Async) ---\");\n" +
                "async function demonstrateAsyncLoop() {\n" +
                "  const asyncTasks = [\n" +
                "    Promise.resolve('Task 1 complete'),\n" +
                "    Promise.resolve('Task 2 complete'),\n" +
                "    Promise.resolve('Task 3 complete')\n" +
                "  ];\n" +
                "\n" +
                "  for await (const result of asyncTasks) {\n" +
                "    console.log(result);\n" +
                "  }\n" +
                "}\n" +
                "demonstrateAsyncLoop();\n";

        String[] editTargets = new String[]{
                "Fruit: ${fruit}",
                "Char: ${char}",
                "user[key]",
                "Element at index",
                "console.log(result);"
        };

        for (String target : editTargets) {
            int pos = code.indexOf(target);
            assertTrue("Target must exist in snippet: " + target, pos >= 0);

            // 1. Space inserted
            String withSpace = code.substring(0, pos) + " " + code.substring(pos);
            List<Problem> probs1 = lintJs(withSpace);
            assertFalse("Space edit in [" + target + "] must not flag fruit", hasUndefinedVariableError(probs1, "fruit"));
            assertFalse("Space edit in [" + target + "] must not flag char", hasUndefinedVariableError(probs1, "char"));
            assertFalse("Space edit in [" + target + "] must not flag key", hasUndefinedVariableError(probs1, "key"));
            assertFalse("Space edit in [" + target + "] must not flag index", hasUndefinedVariableError(probs1, "index"));
            assertFalse("Space edit in [" + target + "] must not flag num", hasUndefinedVariableError(probs1, "num"));
            assertFalse("Space edit in [" + target + "] must not flag result", hasUndefinedVariableError(probs1, "result"));

            // 2. Space removed (backspace simulation)
            List<Problem> probs2 = lintJs(code);
            assertFalse("Backspace in [" + target + "] must not flag fruit", hasUndefinedVariableError(probs2, "fruit"));
            assertFalse("Backspace in [" + target + "] must not flag char", hasUndefinedVariableError(probs2, "char"));
            assertFalse("Backspace in [" + target + "] must not flag key", hasUndefinedVariableError(probs2, "key"));
            assertFalse("Backspace in [" + target + "] must not flag index", hasUndefinedVariableError(probs2, "index"));
            assertFalse("Backspace in [" + target + "] must not flag num", hasUndefinedVariableError(probs2, "num"));
            assertFalse("Backspace in [" + target + "] must not flag result", hasUndefinedVariableError(probs2, "result"));
        }

        // 3. Typing incomplete expression inside loop bodies
        String[] incompleteEdits = new String[]{
                "for (const fruit of fruits) {\n  let temp = \n  console.log(`Fruit: ${fruit}`);\n}",
                "for (const key in user) {\n  const x = \n  console.log(`${key}: ${user[key]}`);\n}",
                "numbers.forEach((num, index) => {\n  let y = \n  console.log(`Element at index ${index}: ${num}`);\n});"
        };
        for (String incCode : incompleteEdits) {
            List<Problem> probs = lintJs(incCode);
            assertFalse("Incomplete edit must not flag fruit", hasUndefinedVariableError(probs, "fruit"));
            assertFalse("Incomplete edit must not flag key", hasUndefinedVariableError(probs, "key"));
            assertFalse("Incomplete edit must not flag num", hasUndefinedVariableError(probs, "num"));
            assertFalse("Incomplete edit must not flag index", hasUndefinedVariableError(probs, "index"));
        }
    }
}
