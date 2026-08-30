package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JsShadowingTest extends BaseJsAstTest {

    private static final String SHADOWING_SOURCE =
            "const data = \"global\";\n" +
            "function process(data) {\n" +
            "    return data + 1;\n" +
            "}";

    @Test
    public void parameterShadowsOuterConst() {
        setupEngine(SHADOWING_SOURCE);

        int offset = SHADOWING_SOURCE.indexOf("data + 1");
        int scope = scopeTree.findScopeAt(offset, tree);
        int[] resolved = scopeTree.lookupSymbol("data", scope);

        assertNotNull(resolved);
        int paramOffset = SHADOWING_SOURCE.indexOf("data)");
        assertTrue("Resolved decl should be the parameter",
                tree.nodeStart[resolved[1]] == paramOffset);
    }
}
