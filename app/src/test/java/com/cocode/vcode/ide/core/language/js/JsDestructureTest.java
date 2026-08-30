package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class JsDestructureTest extends BaseJsAstTest {

    private static final String DESTRUCTURE_SOURCE =
            "function updateProfile({ name, age }) {\n" +
            "    console.log(name);\n" +
            "}";

    @Test
    public void destructuredParamIsRegisteredAsParameter() {
        setupEngine(DESTRUCTURE_SOURCE);

        int logCall = DESTRUCTURE_SOURCE.indexOf("log(name)") + "log(".length();
        int scope = scopeTree.findScopeAt(logCall, tree);
        int[] resolved = scopeTree.lookupSymbol("name", scope);

        assertNotNull("'name' from destructured param must be resolvable", resolved);
        assertEquals("name should resolve as a parameter", JsSyntaxTree.N_PARAM, resolved[2]);
    }
}
