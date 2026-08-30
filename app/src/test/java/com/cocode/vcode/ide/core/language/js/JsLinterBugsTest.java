package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JsLinterBugsTest extends BaseJsAstTest {

    private static final String CONST_REASSIGN_SOURCE = "const x = 1;\nx = 2;";

    @Test
    public void lexerAndParserProduceAVarDeclNode() {
        setupEngine(CONST_REASSIGN_SOURCE);

        assertNotNull(tree);
        assertTrue("Parser must produce at least one node beyond the root", tree.nodeCount > 1);

        boolean sawVarDecl = false;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsSyntaxTree.N_VAR_DECL) {
                sawVarDecl = true;
                break;
            }
        }
        assertTrue("Parser must register a VAR_DECL for 'const x = 1;'", sawVarDecl);
    }
}
