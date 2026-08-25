package com.cocode.vcode.ide.core.lsp;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SymbolExtractorTest {

    @Test
    public void testExtractJsSymbols() {
        String js = "function myFunc(a, b) { }\n" +
                    "class MyClass { constructor() {} myMethod() {} }\n" +
                    "const myVar = 10;\n" +
                    "const myArrow = async (c) => {}";
                    
        LspDocument doc = new LspDocument("/test.js", js, "javascript", 1);
        List<SymbolEntry> symbols = SymbolExtractor.extractSymbols(doc);
        
        // We expect at least: myFunc, MyClass, myMethod, myVar, myArrow
        boolean foundFunc = false;
        boolean foundClass = false;
        boolean foundMethod = false;
        boolean foundVar = false;
        boolean foundArrow = false;
        
        for (SymbolEntry se : symbols) {
            if ("myFunc".equals(se.name)) foundFunc = true;
            if ("MyClass".equals(se.name)) foundClass = true;
            if ("myMethod".equals(se.name)) foundMethod = true;
            if ("myVar".equals(se.name)) foundVar = true;
            if ("myArrow".equals(se.name)) foundArrow = true;
        }
        
        assertTrue("Should extract myFunc", foundFunc);
        assertTrue("Should extract MyClass", foundClass);
        assertTrue("Should extract myMethod", foundMethod);
        assertTrue("Should extract myVar", foundVar);
        assertTrue("Should extract myArrow", foundArrow);
    }

    @Test
    public void testExtractCssSymbols() {
        String css = ".my-class { color: red; }\n#my-id { width: 100px; }";
        LspDocument doc = new LspDocument("/test.css", css, "css", 1);
        List<SymbolEntry> symbols = SymbolExtractor.extractSymbols(doc);
        
        boolean foundClass = false;
        boolean foundId = false;
        
        for (SymbolEntry se : symbols) {
            if (".my-class".equals(se.name) && se.kind == SymbolEntry.KIND_CSS_CLASS) foundClass = true;
            if ("#my-id".equals(se.name) && se.kind == SymbolEntry.KIND_CSS_ID) foundId = true;
        }
        
        assertTrue("Should extract .my-class", foundClass);
        assertTrue("Should extract #my-id", foundId);
    }

    @Test
    public void testExtractHtmlSymbols() {
        String html = "<div id=\"container\" class=\"wrapper main\"></div>";
        LspDocument doc = new LspDocument("/test.html", html, "html", 1);
        List<SymbolEntry> symbols = SymbolExtractor.extractSymbols(doc);
        
        boolean foundId = false;
        boolean foundWrapper = false;
        boolean foundMain = false;
        
        for (SymbolEntry se : symbols) {
            if ("container".equals(se.name) && se.kind == SymbolEntry.KIND_HTML_ID) foundId = true;
            if ("wrapper".equals(se.name) && se.kind == SymbolEntry.KIND_CSS_CLASS) foundWrapper = true;
            if ("main".equals(se.name) && se.kind == SymbolEntry.KIND_CSS_CLASS) foundMain = true;
        }
        
        assertTrue("Should extract id container", foundId);
        assertTrue("Should extract class wrapper", foundWrapper);
        assertTrue("Should extract class main", foundMain);
    }
}
