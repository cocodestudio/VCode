package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JsScopeAndDiagnosticsFixTest extends BaseJsAstTest {

    @Test
    public void testClassMethodsAndPropertiesNotReportedAsUndefined() {
        String js = "class Student {\n" +
                "    constructor(name, rollNo) {\n" +
                "        this.name = name;\n" +
                "        this.rollNo = rollNo;\n" +
                "    }\n" +
                "    getDetails() {\n" +
                "        return this.name + \" \" + this.rollNo;\n" +
                "    }\n" +
                "}\n";

        setupEngine(js);
        List<Problem> result = JsLinter.analyze(mockFile, js, mockIndex);

        for (Problem p : result) {
            String msg = p.getMessage();
            assertFalse("Diagnostic should not report 'name' as undefined: " + msg, msg.contains("'name' is not defined"));
            assertFalse("Diagnostic should not report 'rollNo' as undefined: " + msg, msg.contains("'rollNo' is not defined"));
            assertFalse("Diagnostic should not report 'getDetails' as undefined: " + msg, msg.contains("'getDetails' is not defined"));
        }
    }

    @Test
    public void testArrowFunctionParametersNotReportedAsUndefined() {
        String js = "const add = (a, b) => {\n" +
                "    return a + b;\n" +
                "};\n";

        setupEngine(js);
        List<Problem> result = JsLinter.analyze(mockFile, js, mockIndex);

        for (Problem p : result) {
            String msg = p.getMessage();
            assertFalse("Diagnostic should not report 'a' as undefined: " + msg, msg.contains("'a' is not defined"));
            assertFalse("Diagnostic should not report 'b' as undefined: " + msg, msg.contains("'b' is not defined"));
        }
    }

    @Test
    public void testTypeScriptConstructorParamProperties() {
        String ts = "class User {\n" +
                "    constructor(public username: string, private role: string) {}\n" +
                "    getRole() {\n" +
                "        return this.role;\n" +
                "    }\n" +
                "}\n";

        setupEngine(ts);
        List<Problem> result = JsLinter.analyze(mockFile, ts, mockIndex);

        for (Problem p : result) {
            String msg = p.getMessage();
            assertFalse("Diagnostic should not report 'username' as undefined: " + msg, msg.contains("'username' is not defined"));
            assertFalse("Diagnostic should not report 'role' as undefined: " + msg, msg.contains("'role' is not defined"));
        }
    }

    @Test
    public void testAutoCompleteDoesNotLeakClassPropertiesToGlobalScope() {
        String code = "class Student {\n" +
                "    constructor(name, rollNo) {\n" +
                "        this.name = name;\n" +
                "        this.rollNo = rollNo;\n" +
                "    }\n" +
                "    getDetails() {\n" +
                "        return this.name + \" \" + this.rollNo;\n" +
                "    }\n" +
                "}\n\n" +
                "const x = 1;\n" +
                "r"; // cursor at 'r' outside class

        List<CompletionItem> items = getCompletions(code, code.length());

        boolean hasRollNoWord = false;
        for (CompletionItem item : items) {
            if ("rollNo".equals(item.getLabel()) && "Word".equals(item.getDetail())) {
                hasRollNoWord = true;
                break;
            }
        }
        assertFalse("Class property 'rollNo' must NOT leak as a generic Word completion outside class", hasRollNoWord);
    }

    @Test
    public void testAutoCompleteSuggestsClassPropertiesOnThis() {
        String code = "class Student {\n" +
                "    constructor(name, rollNo) {\n" +
                "        this.name = name;\n" +
                "        this.rollNo = rollNo;\n" +
                "    }\n" +
                "    getDetails() {\n" +
                "        return this.";
        int dotPos = code.length();

        List<CompletionItem> items = getCompletions(code, dotPos);

        boolean hasName = false;
        boolean hasRollNo = false;
        boolean hasGetDetails = false;

        for (CompletionItem item : items) {
            if ("name".equals(item.getLabel())) hasName = true;
            if ("rollNo".equals(item.getLabel())) hasRollNo = true;
            if ("getDetails".equals(item.getLabel())) hasGetDetails = true;
        }

        assertTrue("this. should autocomplete 'name'", hasName);
        assertTrue("this. should autocomplete 'rollNo'", hasRollNo);
        assertTrue("this. should autocomplete 'getDetails'", hasGetDetails);
    }

    @Test
    public void testAutoCompleteSuggestsClassPropertiesOnInstance() {
        String code = "class Student {\n" +
                "    constructor(name, rollNo) {\n" +
                "        this.name = name;\n" +
                "        this.rollNo = rollNo;\n" +
                "    }\n" +
                "    getDetails() {\n" +
                "        return this.name;\n" +
                "    }\n" +
                "}\n" +
                "const s = new Student();\n" +
                "s.";
        int dotPos = code.length();

        List<CompletionItem> items = getCompletions(code, dotPos);

        boolean hasName = false;
        boolean hasRollNo = false;
        boolean hasGetDetails = false;

        for (CompletionItem item : items) {
            if ("name".equals(item.getLabel())) hasName = true;
            if ("rollNo".equals(item.getLabel())) hasRollNo = true;
            if ("getDetails".equals(item.getLabel())) hasGetDetails = true;
        }

        assertTrue("s. should autocomplete 'name'", hasName);
        assertTrue("s. should autocomplete 'rollNo'", hasRollNo);
        assertTrue("s. should autocomplete 'getDetails'", hasGetDetails);
    }

    @Test
    public void testEnumDeclarationAndUsageNotReportedAsUndefined() {
        String ts = "enum Direction {\n" +
                "    UP = \"UP\",\n" +
                "    DOWN = \"DOWN\",\n" +
                "}\n" +
                "const current = Direction.UP;\n";

        setupEngine(ts);
        List<Problem> result = JsLinter.analyze(mockFile, ts, mockIndex);

        for (Problem p : result) {
            String msg = p.getMessage();
            assertFalse("Diagnostic should not report Direction as undefined: " + msg, msg.contains("'Direction' is not defined"));
            assertFalse("Diagnostic should not report UP as undefined: " + msg, msg.contains("'UP' is not defined"));
            assertFalse("Diagnostic should not report DOWN as undefined: " + msg, msg.contains("'DOWN' is not defined"));
        }
    }

    @Test
    public void testEnumComputedMemberReferencesInScope() {
        String ts = "enum Numbers {\n" +
                "    A = 1,\n" +
                "    B = A + 1,\n" +
                "}\n";

        setupEngine(ts);
        List<Problem> result = JsLinter.analyze(mockFile, ts, mockIndex);

        for (Problem p : result) {
            String msg = p.getMessage();
            assertFalse("Diagnostic should not report 'A' as undefined inside enum: " + msg, msg.contains("'A' is not defined"));
        }
    }

    @Test
    public void testTypeScriptTopLevelConstructsNotReportedAsUndefined() {
        String ts = "declare const API_KEY: string;\n" +
                "export enum Status { ACTIVE, INACTIVE }\n" +
                "export interface User {\n" +
                "    id: string;\n" +
                "    name: string;\n" +
                "}\n" +
                "export type UserID = string;\n" +
                "const s = Status.ACTIVE;\n" +
                "const key = API_KEY;\n";

        setupEngine(ts);
        List<Problem> result = JsLinter.analyze(mockFile, ts, mockIndex);

        for (Problem p : result) {
            String msg = p.getMessage();
            assertFalse("Diagnostic should not report Status as undefined: " + msg, msg.contains("'Status' is not defined"));
            assertFalse("Diagnostic should not report API_KEY as undefined: " + msg, msg.contains("'API_KEY' is not defined"));
            assertFalse("Diagnostic should not report User as undefined: " + msg, msg.contains("'User' is not defined"));
            assertFalse("Diagnostic should not report UserID as undefined: " + msg, msg.contains("'UserID' is not defined"));
        }
    }

    @Test
    public void testTypeOnlyImportsNotFalselyFlagged() {
        String ts = "import type { UserProfile } from './types';\n" +
                "import { type Config, createServer } from './server';\n" +
                "const s = createServer();\n";

        setupEngine(ts);
        List<Problem> result = JsLinter.analyze(mockFile, ts, mockIndex);

        for (Problem p : result) {
            String msg = p.getMessage();
            assertFalse("Diagnostic should not report 'type' as undefined: " + msg, msg.contains("'type' is not defined"));
        }
    }

    @Test
    public void testStandardWebApisNotReportedAsUndefined() {
        String js = "const blob = new Blob([\"hello\"], { type: \"text/plain\" });\n" +
                "const reader = new FileReader();\n" +
                "const res = new Response();\n" +
                "const evt = new CustomEvent(\"test\");\n";

        setupEngine(js);
        List<Problem> result = JsLinter.analyze(mockFile, js, mockIndex);

        for (Problem p : result) {
            String msg = p.getMessage();
            assertFalse("Diagnostic should not report Blob as undefined: " + msg, msg.contains("'Blob' is not defined"));
            assertFalse("Diagnostic should not report FileReader as undefined: " + msg, msg.contains("'FileReader' is not defined"));
            assertFalse("Diagnostic should not report Response as undefined: " + msg, msg.contains("'Response' is not defined"));
            assertFalse("Diagnostic should not report CustomEvent as undefined: " + msg, msg.contains("'CustomEvent' is not defined"));
        }
    }

    @Test
    public void testTsLinterEnumSeverityIsInfo() {
        String ts = "enum Direction {\n" +
                "    UP,\n" +
                "    DOWN\n" +
                "}\n";

        List<Problem> problems = com.cocode.vcode.ide.core.language.ts.TsLinter.analyze(mockFile, ts, mockIndex);
        boolean hasEnumWarning = false;
        boolean hasEnumInfo = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Enums add runtime overhead")) {
                if (p.getSeverity() == Problem.Severity.WARNING) hasEnumWarning = true;
                if (p.getSeverity() == Problem.Severity.INFO) hasEnumInfo = true;
            }
        }
        assertFalse("TsLinter enum check should not be WARNING", hasEnumWarning);
        assertTrue("TsLinter enum check should be INFO", hasEnumInfo);
    }

    @Test
    public void testCrossFileImportedFunctionArity_noFalseZeroParamError() {
        File baseDir = new File(".").getAbsoluteFile();
        File sourceFile = new File(baseDir, "app.js");
        File mathFile = new File(baseDir, "math.js");

        String mathCode = "export function add(a, b) {\n" +
                "    return a + b;\n" +
                "}\n";
        mockIndex.updateDocumentSnapshot(mathFile.getAbsolutePath(), mathCode, "javascript");

        String appCode = "import { add } from './math';\n" +
                "add(10, 20);\n";

        List<Problem> problems = JsLinter.analyze(sourceFile, appCode, mockIndex);
        for (Problem p : problems) {
            String msg = p.getMessage();
            assertFalse("Should not report expects 0 arguments: " + msg, msg.contains("expects 0 argument(s)"));
        }
    }

    @Test
    public void testCrossFileImportedClassConstructorArity_noFalseZeroParamError() {
        File baseDir = new File(".").getAbsoluteFile();
        File sourceFile = new File(baseDir, "main.js");
        File personFile = new File(baseDir, "person.js");

        String personCode = "export class Person {\n" +
                "    constructor(firstName, lastName) {\n" +
                "        this.firstName = firstName;\n" +
                "        this.lastName = lastName;\n" +
                "    }\n" +
                "}\n";
        mockIndex.updateDocumentSnapshot(personFile.getAbsolutePath(), personCode, "javascript");

        String mainCode = "import { Person } from './person';\n" +
                "const p = new Person(\"John\", \"Doe\");\n";

        List<Problem> problems = JsLinter.analyze(sourceFile, mainCode, mockIndex);
        for (Problem p : problems) {
            String msg = p.getMessage();
            assertFalse("Should not report Person constructor expects 0 arguments: " + msg, msg.contains("expects 0 argument(s)"));
        }
    }

    @Test
    public void testCallbackAndExternalModule_noFalseZeroParamError() {
        String code = "import { useState } from 'react';\n" +
                "const [state, setState] = useState(0);\n" +
                "function run(callback) {\n" +
                "    callback(1, 2, 3);\n" +
                "}\n";
        List<Problem> problems = JsLinter.analyze(mockFile, code, mockIndex);
        for (Problem p : problems) {
            String msg = p.getMessage();
            assertFalse("Unresolved external module or callback parameter call should not report expects 0 arguments: " + msg,
                    msg.contains("expects 0 argument(s)"));
        }
    }

    @Test
    public void testFunctionSignatureChange_updatesIndexParseResultAndSymbols() {
        File file = new File("component.js");
        String v1 = "function calculate(x) { return x; }";
        JsLinter.analyze(file, v1, mockIndex);

        List<com.cocode.vcode.ide.core.lsp.SymbolEntry> syms1 = mockIndex.getFileSymbols(file.getAbsolutePath());
        org.junit.Assert.assertNotNull(syms1);
        boolean foundCalc1 = false;
        for (com.cocode.vcode.ide.core.lsp.SymbolEntry s : syms1) {
            if ("calculate".equals(s.name)) {
                assertEquals("x", s.detail);
                foundCalc1 = true;
                break;
            }
        }
        assertTrue("calculate(x) should be indexed", foundCalc1);

        String v2 = "function calculate(x, y, z) { return x + y + z; }";
        JsLinter.analyze(file, v2, mockIndex);

        List<com.cocode.vcode.ide.core.lsp.SymbolEntry> syms2 = mockIndex.getFileSymbols(file.getAbsolutePath());
        org.junit.Assert.assertNotNull(syms2);
        boolean foundCalc2 = false;
        for (com.cocode.vcode.ide.core.lsp.SymbolEntry s : syms2) {
            if ("calculate".equals(s.name)) {
                assertEquals("x, y, z", s.detail);
                foundCalc2 = true;
                break;
            }
        }
        assertTrue("calculate(x, y, z) should be updated in index immediately", foundCalc2);
    }

    @Test
    public void testClassInstanceMethodArity_detectsTooFewAndTooManyArguments() {
        String js = "class Person {\n" +
                "    getName(prefix) {\n" +
                "        return prefix + ' John';\n" +
                "    }\n" +
                "}\n" +
                "const p = new Person();\n" +
                "p.getName();\n" +
                "p.getName('Sir');\n" +
                "p.getName('Sir', 'Extra');\n";

        List<Problem> problems = JsLinter.analyze(mockFile, js, mockIndex);

        boolean foundTooFew = false;
        boolean foundTooMany = false;
        for (Problem p : problems) {
            String msg = p.getMessage();
            if (msg.contains("Too few arguments: 'p.getName' expects 1 argument(s), but got 0")) {
                foundTooFew = true;
            }
            if (msg.contains("Too many arguments: 'p.getName' expects 1 argument(s), but got 2")) {
                foundTooMany = true;
            }
        }
        assertTrue("Should report too few arguments when calling p.getName() with 0 arguments", foundTooFew);
        assertTrue("Should report too many arguments when calling p.getName() with 2 arguments", foundTooMany);
    }

    @Test
    public void testClassInstanceMethodArity_signatureChange_dynamicallyUpdatesDiagnostics() {
        // Version 1: getName takes NO params. Calling p.getName() has 0 errors.
        String v1 = "class Person {\n" +
                "    getName() {\n" +
                "        return 'John';\n" +
                "    }\n" +
                "}\n" +
                "const p = new Person();\n" +
                "p.getName();\n";

        List<Problem> problemsV1 = JsLinter.analyze(mockFile, v1, mockIndex);
        for (Problem p : problemsV1) {
            assertFalse("V1 should have no arity error for p.getName(): " + p.getMessage(),
                    p.getMessage().contains("Too few arguments: 'p.getName'"));
        }

        // Version 2: User changes signature: getName now takes 1 param (prefix).
        // Calling p.getName() with 0 args should now immediately report error!
        String v2 = "class Person {\n" +
                "    getName(prefix) {\n" +
                "        return prefix + ' John';\n" +
                "    }\n" +
                "}\n" +
                "const p = new Person();\n" +
                "p.getName();\n";

        List<Problem> problemsV2 = JsLinter.analyze(mockFile, v2, mockIndex);
        boolean foundError = false;
        for (Problem p : problemsV2) {
            if (p.getMessage().contains("Too few arguments: 'p.getName' expects 1 argument(s), but got 0")) {
                foundError = true;
                break;
            }
        }
        assertTrue("V2 must report 'Too few arguments' after signature was changed to take 1 param", foundError);
    }

    @Test
    public void testCrossFileClassInstanceMethodArity_detectsArityMismatch() {
        File personFile = new File("Person.js");
        String personCode = "export class Person {\n" +
                "    getName(prefix) {\n" +
                "        return prefix + ' John';\n" +
                "    }\n" +
                "}\n";
        JsLinter.analyze(personFile, personCode, mockIndex);

        File mainFile = new File("main.js");
        String mainCode = "import { Person } from './Person';\n" +
                "const p = new Person();\n" +
                "p.getName();\n" +
                "p.getName('Dr.');\n";

        List<Problem> problems = JsLinter.analyze(mainFile, mainCode, mockIndex);
        boolean foundTooFew = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Too few arguments: 'p.getName' expects 1 argument(s), but got 0")) {
                foundTooFew = true;
                break;
            }
        }
        assertTrue("Cross-file class instance method arity check must report too few arguments", foundTooFew);
    }

    @Test
    public void testClassInstanceInheritedMethodArity() {
        String js = "class Animal {\n" +
                "    speak(sound) {\n" +
                "        return sound;\n" +
                "    }\n" +
                "}\n" +
                "class Dog extends Animal {}\n" +
                "const d = new Dog();\n" +
                "d.speak();\n";
        List<Problem> problems = JsLinter.analyze(mockFile, js, mockIndex);
        boolean foundTooFew = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Too few arguments: 'd.speak' expects 1 argument(s), but got 0")) {
                foundTooFew = true;
                break;
            }
        }
        assertTrue("Inherited method arity check must report too few arguments", foundTooFew);
    }
}

