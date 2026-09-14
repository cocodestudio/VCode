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
}
