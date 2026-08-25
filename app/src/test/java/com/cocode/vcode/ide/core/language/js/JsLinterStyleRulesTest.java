package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenMask;
import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class JsLinterStyleRulesTest {

    private final File mockFile = new File("test.js");

    @Test
    public void testCheckTypeofComparison() {
        String text = "if (typeof x === undefined) {}";
        TokenMask mask = TokenMask.build(text, "js"); // Assume no masks for simplicity
        List<Problem> problems = new ArrayList<>();

        JsLinterStyleRules.checkTypeofComparison(mockFile, text, mask, problems);

        assertEquals(1, problems.size());
        assertEquals("WARNING", problems.get(0).getSeverity().name());
        assertEquals("'typeof' always returns a string: compare to '\"undefined\"' not 'undefined'", problems.get(0).getMessage());
    }

    @Test
    public void testCheckTodoFixme() {
        String text = "function test() { // TODO: implement this\n }";
        List<Problem> problems = new ArrayList<>();

        JsLinterStyleRules.checkTodoFixme(mockFile, text, problems);

        assertEquals(1, problems.size());
        assertEquals("INFO", problems.get(0).getSeverity().name());
        assertEquals("TODO/FIXME found: 'TODO : implement this'", problems.get(0).getMessage());
    }

    @Test
    public void testCheckFunctionParams() {
        String text = "function doSomething(a, b, c, d, e) {}";
        TokenMask mask = TokenMask.build(text, "js");
        List<Problem> problems = new ArrayList<>();

        JsLinterStyleRules.checkFunctionParams(mockFile, text, mask, problems);

        assertEquals(1, problems.size());
        assertEquals("WARNING", problems.get(0).getSeverity().name());
        assertEquals("Function 'doSomething' has 5 parameters: consider a config object for readability", problems.get(0).getMessage());
    }

    @Test
    public void testCheckDivisionByZero() {
        String text = "let x = 10 / 0;";
        TokenMask mask = TokenMask.build(text, "js");
        List<Problem> problems = new ArrayList<>();

        JsLinterStyleRules.checkDivisionByZero(mockFile, text, mask, problems);

        assertEquals(1, problems.size());
        assertEquals("ERROR", problems.get(0).getSeverity().name());
    }
}
