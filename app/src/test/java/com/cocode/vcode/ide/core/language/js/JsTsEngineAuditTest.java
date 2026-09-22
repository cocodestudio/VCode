package com.cocode.vcode.ide.core.language.js;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class JsTsEngineAuditTest {

    private JsSyntaxTree parse(String code) {
        TokenStream tokens = JsLexer.tokenize(code);
        JsSyntaxTree tree = JsParser.parseFull(code, tokens);
        if (tree != null) {
            tree.buildNodesByOffset();
        }
        return tree;
    }

    private List<Integer> findNodesByType(JsSyntaxTree tree, int type) {
        List<Integer> result = new ArrayList<>();
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == type) {
                result.add(i);
            }
        }
        return result;
    }

    private int findFirstNodeByName(JsSyntaxTree tree, int type, String name) {
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == type && name.equals(tree.nodeName[i])) {
                return i;
            }
        }
        return 0;
    }

    // ISSUE-1: Arrow function return type annotation must not become a fake N_PARAM
    @Test
    public void testArrowFunctionReturnTypeNotParam() {
        String code = "const fn = (x: number): string => x.toString();";
        JsSyntaxTree tree = parse(code);
        ScopeTree scope = ScopeTree.build(tree);

        List<Integer> arrowNodes = findNodesByType(tree, JsSyntaxTree.N_ARROW_FUNC);
        assertEquals(1, arrowNodes.size());
        int arrowNode = arrowNodes.get(0);

        assertEquals("string", tree.nodeTypeAnn[arrowNode]);

        // Count params under arrowNode
        List<String> paramNames = new ArrayList<>();
        int child = tree.nodeChild[arrowNode];
        while (child > 0) {
            if (tree.nodeType[child] == JsSyntaxTree.N_PARAM) {
                paramNames.add(tree.nodeName[child]);
            }
            child = tree.nodeSibling[child];
        }

        assertEquals(1, paramNames.size());
        assertEquals("x", paramNames.get(0));

        // Ensure "string" is not in arrow scope as a parameter
        int arrowScope = scope.nodeToScope[arrowNode];
        assertNull("Return type 'string' should not be registered as a symbol in arrow scope",
                scope.lookupSymbol("string", arrowScope));
    }

    // ISSUE-2: Interface and abstract methods with semicolons
    @Test
    public void testInterfaceMethodSemicolonParsing() {
        String code = "interface Service {\n" +
                "    fetch(id: string): Promise<Data>;\n" +
                "    save(data: Data): boolean;\n" +
                "    clear(): void;\n" +
                "}";
        JsSyntaxTree tree = parse(code);

        List<Integer> ifaceNodes = findNodesByType(tree, JsSyntaxTree.N_INTERFACE);
        assertEquals(1, ifaceNodes.size());
        int iface = ifaceNodes.get(0);

        List<String> methodNames = new ArrayList<>();
        int child = tree.nodeChild[iface];
        while (child > 0) {
            if (tree.nodeType[child] == JsSyntaxTree.N_METHOD) {
                methodNames.add(tree.nodeName[child]);
            }
            child = tree.nodeSibling[child];
        }

        assertEquals(3, methodNames.size());
        assertTrue(methodNames.contains("fetch"));
        assertTrue(methodNames.contains("save"));
        assertTrue(methodNames.contains("clear"));
    }

    // ISSUE-3: Function type annotation in type alias with '=>'
    @Test
    public void testFunctionTypeAnnotationInTypeAlias() {
        String code = "type Callback = (err: Error | null, result: string) => void;";
        JsSyntaxTree tree = parse(code);

        List<Integer> aliasNodes = findNodesByType(tree, JsSyntaxTree.N_TYPE_ALIAS);
        assertEquals(1, aliasNodes.size());
        int alias = aliasNodes.get(0);
        assertEquals("Callback", tree.nodeName[alias]);
        assertNotNull(tree.nodeTypeAnn[alias]);
        assertTrue("Type annotation must include return type: " + tree.nodeTypeAnn[alias],
                tree.nodeTypeAnn[alias].contains("=> void"));
    }

    // ISSUE-4: Automatic Semicolon Insertion before TypeScript keywords
    @Test
    public void testAsiBeforeTypeAndInterface() {
        String code = "const a = 1\n" +
                "type B = string\n" +
                "interface C {\n" +
                "    val: number;\n" +
                "}";
        JsSyntaxTree tree = parse(code);

        int aNode = findFirstNodeByName(tree, JsSyntaxTree.N_VAR_DECL, "a");
        assertTrue("Node 'a' must exist", aNode > 0);

        int bNode = findFirstNodeByName(tree, JsSyntaxTree.N_TYPE_ALIAS, "B");
        assertTrue("Type alias 'B' must exist", bNode > 0);

        int cNode = findFirstNodeByName(tree, JsSyntaxTree.N_INTERFACE, "C");
        assertTrue("Interface 'C' must exist", cNode > 0);
    }

    // ISSUE-5: for await (const x of items)
    @Test
    public void testForAwaitOfLoop() {
        String code = "async function run() {\n" +
                "    for await (const x of items) {\n" +
                "        console.log(x);\n" +
                "    }\n" +
                "}";
        JsSyntaxTree tree = parse(code);

        List<Integer> forNodes = findNodesByType(tree, JsSyntaxTree.N_FOR_STMT);
        assertEquals(1, forNodes.size());

        int xNode = findFirstNodeByName(tree, JsSyntaxTree.N_VAR_DECL, "x");
        assertTrue("Var decl 'x' must exist inside for await loop", xNode > 0);
    }

    // ISSUE-6: const enum and export const enum
    @Test
    public void testConstEnumParsing() {
        String code = "const enum Direction { Up, Down }\n" +
                "export const enum Color { Red, Green }";
        JsSyntaxTree tree = parse(code);

        List<Integer> enumNodes = findNodesByType(tree, JsSyntaxTree.N_ENUM);
        assertEquals(2, enumNodes.size());

        int dirNode = findFirstNodeByName(tree, JsSyntaxTree.N_ENUM, "Direction");
        assertTrue("Enum Direction must exist", dirNode > 0);

        int colorNode = findFirstNodeByName(tree, JsSyntaxTree.N_ENUM, "Color");
        assertTrue("Enum Color must exist", colorNode > 0);
    }

    // ISSUE-7: Private class members retain '#' prefix
    @Test
    public void testPrivateClassMembers() {
        String code = "class Counter {\n" +
                "    #count = 0;\n" +
                "    #increment() { this.#count++; }\n" +
                "    getCount() { return this.#count; }\n" +
                "}";
        JsSyntaxTree tree = parse(code);

        int classNode = findFirstNodeByName(tree, JsSyntaxTree.N_CLASS_DECL, "Counter");
        assertTrue("Class Counter must exist", classNode > 0);

        List<String> memberNames = new ArrayList<>();
        int child = tree.nodeChild[classNode];
        while (child > 0) {
            memberNames.add(tree.nodeName[child]);
            child = tree.nodeSibling[child];
        }

        assertTrue("Class must have '#count'", memberNames.contains("#count"));
        assertTrue("Class must have '#increment'", memberNames.contains("#increment"));
        assertTrue("Class must have 'getCount'", memberNames.contains("getCount"));
    }

    // ISSUE-8: Arrow destructuring parameter aliases
    @Test
    public void testArrowDestructuringParameterAliases() {
        String code = "const fn = ({ x: a, y: b }) => a + b;";
        JsSyntaxTree tree = parse(code);
        ScopeTree scope = ScopeTree.build(tree);

        List<Integer> arrowNodes = findNodesByType(tree, JsSyntaxTree.N_ARROW_FUNC);
        assertEquals(1, arrowNodes.size());
        int arrowNode = arrowNodes.get(0);
        int arrowScope = scope.nodeToScope[arrowNode];

        assertNotNull("Param alias 'a' must be in arrow scope", scope.lookupSymbol("a", arrowScope));
        assertNotNull("Param alias 'b' must be in arrow scope", scope.lookupSymbol("b", arrowScope));
    }

    // ISSUE-9: as const and complex type assertions
    @Test
    public void testAsConstAndTypeAssertions() {
        String code = "const config = { mode: 'dark' } as const;\n" +
                "const list = items as string[];";
        JsSyntaxTree tree = parse(code);

        List<Integer> typeRefNodes = findNodesByType(tree, JsSyntaxTree.N_TYPE_REF);
        List<String> refNames = new ArrayList<>();
        for (int node : typeRefNodes) {
            refNames.add(tree.nodeName[node]);
        }

        assertTrue("Must contain 'const' type ref", refNames.contains("const"));
        assertTrue("Must contain 'string[]' type ref", refNames.contains("string[]"));
    }

    // ISSUE-10: import type DefaultType from './foo'
    // ISSUE-10: import type DefaultType from './foo'
    @Test
    public void testImportTypeDefault() {
        String code = "import type Foo from './foo';\n" +
                "import type { Bar } from './bar';";
        JsSyntaxTree tree = parse(code);

        List<Integer> importNodes = findNodesByType(tree, JsSyntaxTree.N_IMPORT);
        assertEquals(2, importNodes.size());

        List<String> modulePaths = new ArrayList<>();
        for (int node : importNodes) {
            modulePaths.add(tree.nodeName[node]);
        }
        assertTrue(modulePaths.contains("./foo"));
        assertTrue(modulePaths.contains("./bar"));

        // Imported variables are stored as N_VAR_DECL under the import nodes
        List<Integer> declNodes = findNodesByType(tree, JsSyntaxTree.N_VAR_DECL);
        List<String> importedVars = new ArrayList<>();
        for (int node : declNodes) {
            importedVars.add(tree.nodeName[node]);
        }

        assertTrue("Foo must be imported", importedVars.contains("Foo"));
        assertTrue("Bar must be imported", importedVars.contains("Bar"));
        assertFalse("'type' modifier keyword must not be imported as a variable", importedVars.contains("type"));
    }

    // ISSUE-11: Contextual keywords as variable names in parseVarDecl
    @Test
    public void testContextualKeywordsAsVariableNames() {
        String code = "let type = 1;\n" +
                "const from = 'here';\n" +
                "const of = 2;";
        JsSyntaxTree tree = parse(code);

        int typeNode = findFirstNodeByName(tree, JsSyntaxTree.N_VAR_DECL, "type");
        assertTrue("Variable 'type' must exist", typeNode > 0);

        int fromNode = findFirstNodeByName(tree, JsSyntaxTree.N_VAR_DECL, "from");
        assertTrue("Variable 'from' must exist", fromNode > 0);

        int ofNode = findFirstNodeByName(tree, JsSyntaxTree.N_VAR_DECL, "of");
        assertTrue("Variable 'of' must exist", ofNode > 0);
    }

    // ISSUE-12: Class and member decorators skipped cleanly
    @Test
    public void testDecoratorsSkipped() {
        String code = "@Component({ selector: 'app' })\n" +
                "class MyComponent {\n" +
                "    @observable count = 0;\n" +
                "    @action increment() {}\n" +
                "}";
        JsSyntaxTree tree = parse(code);

        int compNode = findFirstNodeByName(tree, JsSyntaxTree.N_CLASS_DECL, "MyComponent");
        assertTrue("Class MyComponent must exist", compNode > 0);

        List<String> members = new ArrayList<>();
        int child = tree.nodeChild[compNode];
        while (child > 0) {
            members.add(tree.nodeName[child]);
            child = tree.nodeSibling[child];
        }

        assertTrue("Class must have 'count'", members.contains("count"));
        assertTrue("Class must have 'increment'", members.contains("increment"));
        assertFalse("Decorators must not be recorded as class members", members.contains("observable"));
        assertFalse("Decorators must not be recorded as class members", members.contains("action"));
    }

    // Edge case: TypeScript index signature
    @Test
    public void testIndexSignature() {
        String code = "interface Dict {\n" +
                "    [key: string]: number;\n" +
                "    title: string;\n" +
                "}";
        JsSyntaxTree tree = parse(code);

        int iface = findFirstNodeByName(tree, JsSyntaxTree.N_INTERFACE, "Dict");
        assertTrue("Dict must exist", iface > 0);

        List<String> members = new ArrayList<>();
        int child = tree.nodeChild[iface];
        while (child > 0) {
            members.add(tree.nodeName[child]);
            child = tree.nodeSibling[child];
        }

        assertTrue("Must have title", members.contains("title"));
        assertFalse("'key' should not be emitted as regular property", members.contains("key"));
    }
}
