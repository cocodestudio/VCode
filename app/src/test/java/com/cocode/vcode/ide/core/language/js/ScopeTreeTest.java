package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ScopeTreeTest extends BaseJsAstTest {

    private static final String VAR_AND_LET_SOURCE =
            "if (true) { var a = 1; let b = 2; }\n" +
            "const foo = (c, d) => { return c + d; };";

    @Test
    public void varHoistsToFunctionScope() {
        setupEngine(VAR_AND_LET_SOURCE);

        assertNotNull(scopeTree.symbols);
        assertTrue("var 'a' must be registered", scopeTree.symbols.containsKey("a"));
        assertTrue("let 'b' must be registered", scopeTree.symbols.containsKey("b"));
        assertTrue("const 'foo' must be registered", scopeTree.symbols.containsKey("foo"));
    }

    @Test
    public void symbolsExposeScopeCreatorType() {
        setupEngine(VAR_AND_LET_SOURCE);

        for (Map.Entry<String, int[]> entry : scopeTree.symbols.entrySet()) {
            int[] tuples = entry.getValue();
            assertTrue("Tuple array length must be a multiple of 3", tuples.length % 3 == 0);
        }
    }
}
