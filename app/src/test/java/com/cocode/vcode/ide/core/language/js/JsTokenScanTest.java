package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class JsTokenScanTest extends BaseJsAstTest {

    private static final String SOURCE =
            "const x = 1; // Global x\n" +
            "function foo() {\n" +
            "    const x = 2; // Local x\n" +
            "    console.log(x); // Usage A\n" +
            "}\n" +
            "console.log(x); // Usage B";

    @Test
    public void tokensCoverAtLeastFirstNode() {
        setupEngine(SOURCE);

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
}
