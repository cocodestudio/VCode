package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenMask;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.LspRange;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.lsp.SymbolEntry;
import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

public class JsSemanticLinterTest {

    private ProjectIndex mockIndex;
    private File file;

    @Before
    public void setUp() {
        mockIndex = ProjectIndex.getInstance();
        file = new File("test.js");
        
        try {
            Field fsField = ProjectIndex.class.getDeclaredField("fileSymbols");
            fsField.setAccessible(true);
            ConcurrentHashMap<String, List<SymbolEntry>> fileSymbols = (ConcurrentHashMap<String, List<SymbolEntry>>) fsField.get(mockIndex);
            
            SymbolEntry f1 = new SymbolEntry("utilFunc", "other.js", new LspRange(0, 0, 0, 0), SymbolEntry.KIND_FUNCTION, "a, b");
            SymbolEntry f2 = new SymbolEntry("utilFuncDefaults", "other.js", new LspRange(0, 0, 0, 0), SymbolEntry.KIND_FUNCTION, "a, b = 1");
            SymbolEntry f3 = new SymbolEntry("utilFuncRest", "other.js", new LspRange(0, 0, 0, 0), SymbolEntry.KIND_FUNCTION, "a, ...rest");
            
            List<SymbolEntry> syms = new ArrayList<>();
            syms.add(f1);
            syms.add(f2);
            syms.add(f3);
            
            fileSymbols.put("other.js", syms);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testArityChecking() {
        String code = "let utilFunc = null; utilFunc(1);";
        TokenMask mask = TokenMask.build(code, "js");
        List<Problem> problems = new ArrayList<>();
        JsSemanticLinter.analyze(file, code, mask, mockIndex, problems);


        assertEquals(1, problems.size());
        assertTrue(problems.get(0).getMessage().contains("Too few arguments"));
    }
    
    @Test
    public void testArityChecking_Defaults() {
        String code = "let utilFuncDefaults = null; utilFuncDefaults(1);";
        TokenMask mask = TokenMask.build(code, "js");
        List<Problem> problems = new ArrayList<>();
        JsSemanticLinter.analyze(file, code, mask, mockIndex, problems);

        assertEquals(0, problems.size()); // Allowed because b has default
        
        String code2 = "let utilFuncDefaults = null; utilFuncDefaults(1, 2, 3);";
        mask = TokenMask.build(code2, "js");
        problems.clear();
        JsSemanticLinter.analyze(file, code2, mask, mockIndex, problems);

        assertEquals(1, problems.size()); // Not allowed because total params is 2
        assertTrue(problems.get(0).getMessage().contains("Too many arguments"));
    }
    
    @Test
    public void testArityChecking_RestParamIgnored() {
        String code = "let utilFuncRest = null; utilFuncRest(1, 2, 3, 4);";
        TokenMask mask = TokenMask.build(code, "js");
        List<Problem> problems = new ArrayList<>();
        JsSemanticLinter.analyze(file, code, mask, mockIndex, problems);

        assertEquals(0, problems.size()); // Allowed because rest param skips checking
    }

    @Test
    public void testUndefinedVariable() {
        String code = "console.log(unknownVar);";
        TokenMask mask = TokenMask.build(code, "js");
        List<Problem> problems = new ArrayList<>();
        JsSemanticLinter.analyze(file, code, mask, mockIndex, problems);

        assertEquals(1, problems.size());
        assertEquals("'unknownVar' is not defined", problems.get(0).getMessage());
    }

    @Test
    public void testDefinedVariableNotFlagged() {
        String code = "const knownVar = 1; console.log(knownVar);";
        TokenMask mask = TokenMask.build(code, "js");
        List<Problem> problems = new ArrayList<>();
        JsSemanticLinter.analyze(file, code, mask, mockIndex, problems);

        assertEquals(0, problems.size());
    }
    
    @Test
    public void testPropertyAccessNotFlagged() {
        String code = "const obj = {}; console.log(obj.someProp);";
        TokenMask mask = TokenMask.build(code, "js");
        List<Problem> problems = new ArrayList<>();
        JsSemanticLinter.analyze(file, code, mask, mockIndex, problems);

        assertEquals(0, problems.size());
    }
}
