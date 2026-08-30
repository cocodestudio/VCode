package com.cocode.vcode.ide.core.language.js;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.lsp.ModuleResolver;
import com.cocode.vcode.ide.core.lsp.LspLocation;

public class RegressionMatrixTest extends BaseJsAstTest {

    private List<Problem> analyze(String code) {
        setupEngine(code);
        return problems;
    }

    @Test
    public void testBlockLineCommentEdits() {
        String code = "/* block */ let x = 1; // line\n x = 2;";
        List<Problem> p = analyze(code);
        if (!p.isEmpty()) { for (Problem p1 : p) { System.out.println("PROBLEM: " + p1.getMessage()); } } assertTrue(p.isEmpty());
    }

    @Test
    public void testTemplateLiteralNested() {
        String code = "let a = `hello ${`world ${1}`}`;";
        List<Problem> p = analyze(code);
        if (!p.isEmpty()) { for (Problem p1 : p) { System.out.println("PROBLEM: " + p1.getMessage()); } } assertTrue(p.isEmpty());
    }

    @Test
    public void testGeneratorFunction() {
        String code = "function* gen() { yield 1; }";
        List<Problem> p = analyze(code);
        if (!p.isEmpty()) { for (Problem p1 : p) { System.out.println("PROBLEM: " + p1.getMessage()); } } assertTrue(p.isEmpty());
    }

    @Test
    public void testMemberAccess() {
        String code = "let obj = { a: 1 }; obj.a;";
        List<Problem> p = analyze(code);
        if (!p.isEmpty()) { for (Problem p1 : p) { System.out.println("PROBLEM: " + p1.getMessage()); } } assertTrue(p.isEmpty());
    }

    @Test
    public void testArrowWithDefaultParam() {
        String code = "const f = (a = 1) => a;";
        List<Problem> p = analyze(code);
        if (!p.isEmpty()) { for (Problem p1 : p) { System.out.println("PROBLEM: " + p1.getMessage()); } } assertTrue(p.isEmpty());
    }

    @Test
    public void testRestParams() {
        String code = "function f(a, ...rest) { rest; }";
        List<Problem> p = analyze(code);
        if (!p.isEmpty()) { for (Problem p1 : p) { System.out.println("PROBLEM: " + p1.getMessage()); } } assertTrue(p.isEmpty());
    }

    @Test
    public void testDestructuredTypedParams() {
        String code = "function f({ id, name }: User) { id; }";
        List<Problem> p = analyze(code);
        for(int i = 1; i < tree.nodeCount; i++) {
            System.out.println("Node " + i + ": type=" + tree.nodeType[i] + " start=" + tree.nodeStart[i] + " end=" + tree.nodeEnd[i] + " name=" + tree.nodeName[i]);
        }
        if (!p.isEmpty()) { for (Problem p1 : p) { System.out.println("PROBLEM: " + p1.getMessage()); } } assertTrue(p.isEmpty());
    }

    @Test
    public void testTdzViolation() {
        String code = "console.log(x); let x = 1;";
        List<Problem> p = analyze(code);
        assertFalse("Expected TDZ violation to be flagged", p.isEmpty());
    }

    @Test
    public void testConstReassignment() {
        String code = "const x = 1; x == 1; x += 1;";
        List<Problem> p = analyze(code);
        assertFalse("Expected const reassignment to be flagged", p.isEmpty());
    }
    
    @Test
    public void testRapidTypingStress() {
        for(int i=0; i<100; i++) {
            analyze("let x = " + i + "; console.log(x);");
        }
    }

    @Test
    public void testCaseMismatchedImport() {
        LspLocation loc = ModuleResolver.resolveModulePath("file:///dummy/path.js", "./DoesntExist");
        assertNull(loc);
    }
}
