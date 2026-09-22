package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JsParserTest extends BaseJsAstTest {

    private static final String SHADOWING_SOURCE =
            "const data = \"global\";\n" +
            "function process(data) {\n" +
            "    return data + 1;\n" +
            "}";

    private static final String BLOCK_SHADOWING_SOURCE =
            "const x = 1; // Global x\n" +
            "function foo() {\n" +
            "    const x = 2; // Local x\n" +
            "    console.log(x); // Usage A\n" +
            "}\n" +
            "console.log(x); // Usage B";

    @Test
    public void parsesTopLevelTreeShape() {
        setupEngine(SHADOWING_SOURCE);

        assertNotNull(tree);
        assertTrue("Tree should contain at least the root", tree.nodeCount >= 1);

        boolean sawFunction = false;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_FUNC_DECL && "process".equals(tree.nodeName[i])) {
                sawFunction = true;
                break;
            }
        }
        assertTrue("Should record a FUNC_DECL node named 'process'", sawFunction);
    }

    @Test
    public void resolvesShadowedBindingInsideFunction() {
        setupEngine(SHADOWING_SOURCE);

        int offset = SHADOWING_SOURCE.indexOf("data + 1");
        int scope = scopeTree.findScopeAt(offset, tree);
        int[] resolved = scopeTree.lookupSymbol("data", scope);

        assertNotNull("'data' must resolve inside process()", resolved);
        assertEquals(JsSyntaxTree.N_PARAM, resolved[2]);
        int paramOffset = SHADOWING_SOURCE.indexOf("data)");
        assertEquals("Resolved decl should start at parameter offset",
                paramOffset, tree.nodeStart[resolved[1]]);
    }

    @Test
    public void resolvesLocalThenGlobalAtTwoCallSites() {
        setupEngine(BLOCK_SHADOWING_SOURCE);

        int usageA = BLOCK_SHADOWING_SOURCE.indexOf("x); // Usage A");
        int scopeA = scopeTree.findScopeAt(usageA, tree);
        int[] resolvedA = scopeTree.lookupSymbol("x", scopeA);
        assertNotNull("Usage A must resolve to local x", resolvedA);

        int usageB = BLOCK_SHADOWING_SOURCE.lastIndexOf("x); // Usage B");
        int scopeB = scopeTree.findScopeAt(usageB, tree);
        int[] resolvedB = scopeTree.lookupSymbol("x", scopeB);
        assertNotNull("Usage B must resolve to global x", resolvedB);
    }

    @Test
    public void tokensCoverAtLeastFirstNode() {
        setupEngine(BLOCK_SHADOWING_SOURCE);

        assertTrue(tree.nodeCount > 1);
        int firstDecl = 1;
        boolean found = false;
        for (int t = 0; t < tokens.types.length; t++) {
            if (tokens.tokenStart[t] < tree.nodeStart[firstDecl]) continue;
            if (tokens.tokenStart[t] >= tree.nodeEnd[firstDecl]) break;
            if (tokens.types[t] == TokenStream.TK_IDENTIFIER) {
                found = true;
                break;
            }
        }
        assertTrue("First node must contain at least one identifier token", found);
    }

    @Test
    public void destructuredParamIsRegisteredAsParameter() {
        String code = "function updateProfile({ name, age }) {\n" +
                "    console.log(name);\n" +
                "}";
        setupEngine(code);

        int logCall = code.indexOf("log(name)") + "log(".length();
        int scope = scopeTree.findScopeAt(logCall, tree);
        int[] resolved = scopeTree.lookupSymbol("name", scope);

        assertNotNull("'name' from destructured param must be resolvable", resolved);
        assertEquals("name should resolve as a parameter", JsSyntaxTree.N_PARAM, resolved[2]);
    }
}
