package com.cocode.vcode.ide.core.language.ts;

import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TsAutoCompleteEngineTest {

    private TsAutoCompleteEngine engine;

    @Before
    public void setUp() {
        engine = new TsAutoCompleteEngine(null);
    }

    private boolean hasItem(List<CompletionItem> items, String label) {
        if (items == null) return false;
        for (CompletionItem item : items) {
            if (label.equals(item.getLabel())) return true;
        }
        return false;
    }

    @Test
    public void testTypeAnnotationPositionDetection() {
        assertTrue(TsAutoCompleteEngine.isTypeAnnotationPosition("let safeValue: ", 15, ""));
        assertTrue(TsAutoCompleteEngine.isTypeAnnotationPosition("let safeValue: u", 16, "u"));
        assertTrue(TsAutoCompleteEngine.isTypeAnnotationPosition("const user: ", 12, ""));
        assertTrue(TsAutoCompleteEngine.isTypeAnnotationPosition("function foo(a: ", 16, ""));
        assertTrue(TsAutoCompleteEngine.isTypeAnnotationPosition("val as ", 7, ""));
        assertTrue(TsAutoCompleteEngine.isTypeAnnotationPosition("Promise<", 8, ""));
        assertTrue(TsAutoCompleteEngine.isTypeAnnotationPosition("string | ", 9, ""));
        assertTrue(TsAutoCompleteEngine.isTypeAnnotationPosition("type User = ", 12, ""));

        assertFalse(TsAutoCompleteEngine.isTypeAnnotationPosition("let x = ", 8, ""));
        assertFalse(TsAutoCompleteEngine.isTypeAnnotationPosition("if (x == ", 9, ""));
        assertFalse(TsAutoCompleteEngine.isTypeAnnotationPosition("case 1: ", 8, ""));
    }

    @Test
    public void testTypeAnnotationSuggestionsAndStatementFiltering() {
        String code = "enum Role { User, Admin }\nlet currRole: ";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        assertTrue("Expected 'Role' in type completions", hasItem(items, "Role"));
        assertTrue("Expected 'string' in type completions", hasItem(items, "string"));
        assertTrue("Expected 'number' in type completions", hasItem(items, "number"));
        assertTrue("Expected 'boolean' in type completions", hasItem(items, "boolean"));
        assertTrue("Expected 'unknown' in type completions", hasItem(items, "unknown"));

        // Ensure statement keywords are filtered out in type position
        assertFalse("Should not suggest 'if' in type position", hasItem(items, "if"));
        assertFalse("Should not suggest 'while' in type position", hasItem(items, "while"));
        assertFalse("Should not suggest 'return' in type position", hasItem(items, "return"));
        assertFalse("Should not suggest 'for' in type position", hasItem(items, "for"));
        assertFalse("Should not suggest 'switch' in type position", hasItem(items, "switch"));
    }

    @Test
    public void testEnumMemberCompletion() {
        String code = "enum Role {\n  User,\n  Admin,\n}\nRole.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        assertTrue("Expected 'User' on Role.", hasItem(items, "User"));
        assertTrue("Expected 'Admin' on Role.", hasItem(items, "Admin"));
    }

    @Test
    public void testInlineObjectTypeCompletion() {
        String code = "let user: { name: string; age: number; address: { city: string } };\nuser.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        assertTrue("Expected 'name' on user.", hasItem(items, "name"));
        assertTrue("Expected 'age' on user.", hasItem(items, "age"));
        assertTrue("Expected 'address' on user.", hasItem(items, "address"));
    }

    @Test
    public void testNestedInlineObjectTypeChaining() {
        String code = "let user: { name: string; age: number; address: { city: string } };\nuser.address.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        assertTrue("Expected 'city' on user.address.", hasItem(items, "city"));
    }

    @Test
    public void testTypeAliasMemberCompletion() {
        String code = "type User = { id: number; name: string };\nlet u: User;\nu.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        assertTrue("Expected 'id' on u.", hasItem(items, "id"));
        assertTrue("Expected 'name' on u.", hasItem(items, "name"));
    }

    @Test
    public void testInterfaceInheritanceCompletion() {
        String code = "interface Base {\n  id: number;\n}\ninterface User extends Base {\n  name: string;\n}\nlet u: User;\nu.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        assertTrue("Expected 'name' on u.", hasItem(items, "name"));
        assertTrue("Expected 'id' inherited from Base on u.", hasItem(items, "id"));
    }

    @Test
    public void testArrayMemberAndIndexCompletion() {
        String codeArray = "type User = { id: number; name: string };\nlet users: User[];\nusers.";
        List<CompletionItem> arrayItems = engine.getSuggestions(codeArray, codeArray.length());

        assertTrue("Expected 'push' on users.", hasItem(arrayItems, "push"));
        assertTrue("Expected 'map' on users.", hasItem(arrayItems, "map"));
        assertTrue("Expected 'filter' on users.", hasItem(arrayItems, "filter"));

        String codeIndex = "type User = { id: number; name: string };\nlet users: User[];\nusers[0].";
        List<CompletionItem> elementItems = engine.getSuggestions(codeIndex, codeIndex.length());

        assertTrue("Expected 'id' on users[0].", hasItem(elementItems, "id"));
        assertTrue("Expected 'name' on users[0].", hasItem(elementItems, "name"));
    }

    @Test
    public void testPromiseMemberAndAwaitCompletion() {
        String codePromise = "type User = { id: number; name: string };\nlet task: Promise<User>;\ntask.";
        List<CompletionItem> promiseItems = engine.getSuggestions(codePromise, codePromise.length());

        assertTrue("Expected 'then' on task.", hasItem(promiseItems, "then"));
        assertTrue("Expected 'catch' on task.", hasItem(promiseItems, "catch"));

        String codeAwait = "type User = { id: number; name: string };\nlet task: Promise<User>;\n(await task).";
        List<CompletionItem> resolvedItems = engine.getSuggestions(codeAwait, codeAwait.length());
        assertTrue("Expected 'id' on (await task).", hasItem(resolvedItems, "id"));
        assertTrue("Expected 'name' on (await task).", hasItem(resolvedItems, "name"));
    }

    @Test
    public void testMouseEventMemberCompletion() {
        String code = "function handleClick(e: MouseEvent) {\n  e.\n}";
        int offset = code.indexOf("e.") + 2;
        List<CompletionItem> items = engine.getSuggestions(code, offset);

        assertTrue("Expected 'clientX' on MouseEvent", hasItem(items, "clientX"));
        assertTrue("Expected 'clientY' on MouseEvent", hasItem(items, "clientY"));
        assertTrue("Expected 'preventDefault' on MouseEvent", hasItem(items, "preventDefault"));
        assertTrue("Expected 'target' on MouseEvent", hasItem(items, "target"));
    }

    @Test
    public void testUserSnippetTupleMemberAndIndexing() {
        String code = "let tuple: [\n  string, number\n]\n= [\n  \"hello\", 10\n];\ntuple.";
        List<CompletionItem> tupleItems = engine.getSuggestions(code, code.length());
        assertTrue("Expected 'push' on tuple.", hasItem(tupleItems, "push"));
        assertTrue("Expected 'map' on tuple.", hasItem(tupleItems, "map"));
        assertTrue("Expected 'slice' on tuple.", hasItem(tupleItems, "slice"));

        String codeElem0 = "let tuple: [\n  string, number\n]\n= [\n  \"hello\", 10\n];\ntuple[0].";
        List<CompletionItem> elem0Items = engine.getSuggestions(codeElem0, codeElem0.length());
        assertTrue("Expected 'toUpperCase' on tuple[0].", hasItem(elem0Items, "toUpperCase"));
        assertTrue("Expected 'toLowerCase' on tuple[0].", hasItem(elem0Items, "toLowerCase"));

        String codeElem1 = "let tuple: [\n  string, number\n]\n= [\n  \"hello\", 10\n];\ntuple[1].";
        List<CompletionItem> elem1Items = engine.getSuggestions(codeElem1, codeElem1.length());
        assertTrue("Expected 'toFixed' on tuple[1].", hasItem(elem1Items, "toFixed"));
    }

    @Test
    public void testMultilineArrayCompletion() {
        String code = "let list: number [\n\n]\n\n= [\n  1, 2, 3\n];\nlist.";
        List<CompletionItem> listItems = engine.getSuggestions(code, code.length());
        assertTrue("Expected 'push' on multiline array list.", hasItem(listItems, "push"));
        assertTrue("Expected 'map' on multiline array list.", hasItem(listItems, "map"));

        String codeElem = "let list: number [\n\n]\n\n= [\n  1, 2, 3\n];\nlist[0].";
        List<CompletionItem> elemItems = engine.getSuggestions(codeElem, codeElem.length());
        assertTrue("Expected 'toFixed' on list[0].", hasItem(elemItems, "toFixed"));
    }

    @Test
    public void testControlFlowTypeNarrowingWithTypeOf() {
        String code = "let safeValue: unknown = \"hello\";\nif (typeof safeValue === \"string\") {\n  safeValue.\n}";
        int offset = code.indexOf("safeValue.") + 10;
        List<CompletionItem> narrowedItems = engine.getSuggestions(code, offset);
        assertTrue("Expected 'toUpperCase' on safeValue inside typeof check.", hasItem(narrowedItems, "toUpperCase"));
        assertTrue("Expected 'toLowerCase' on safeValue inside typeof check.", hasItem(narrowedItems, "toLowerCase"));
        assertTrue("Expected 'trim' on safeValue inside typeof check.", hasItem(narrowedItems, "trim"));
    }

    @Test
    public void testAnyTypeCompletions() {
        String code = "let randomValue: any = 10;\nrandomValue.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());
        assertTrue("Expected 'toString' on randomValue.", hasItem(items, "toString"));
        assertTrue("Expected 'valueOf' on randomValue.", hasItem(items, "valueOf"));
    }

    @Test
    public void testScopeVariablesPriority() {
        String code = "// Primitive Types\n" +
                "let isDone: boolean = false;\n" +
                "let age: number = 30;\n" +
                "let firstName = \"Alice\";\n" +
                "let u: undefined = undefined;\n" +
                "let n: null = null;\n" +
                "let list: number [] = [ 1, 2, 3 ];\n" +
                "let tuple: [ string, number ] = [ \"hello\", 10 ];\n" +
                "let randomValue: any = 10;\n" +
                "let safeValue: unknown = \"hello\";\n";

        // Querying 'u' should have variable 'u' at top or near top ahead of keywords
        List<CompletionItem> uItems = engine.getSuggestions(code + "u", (code + "u").length());
        assertTrue("Expected 'u' in completions", hasItem(uItems, "u"));
        // 'u' exact match should be at the very top
        assertTrue("Expected 'u' at index 0", !uItems.isEmpty() && "u".equals(uItems.get(0).getLabel()));

        // Querying 'n' should have variable 'n' at top
        List<CompletionItem> nItems = engine.getSuggestions(code + "n", (code + "n").length());
        assertTrue("Expected 'n' in completions", hasItem(nItems, "n"));
        assertTrue("Expected 'n' at index 0", !nItems.isEmpty() && "n".equals(nItems.get(0).getLabel()));

        // Querying 'tup' should have 'tuple'
        List<CompletionItem> tupItems = engine.getSuggestions(code + "tup", (code + "tup").length());
        assertTrue("Expected 'tuple' in completions", hasItem(tupItems, "tuple"));

        // Querying 'rand' should have 'randomValue'
        List<CompletionItem> randItems = engine.getSuggestions(code + "rand", (code + "rand").length());
        assertTrue("Expected 'randomValue' in completions", hasItem(randItems, "randomValue"));

        // Querying 'safe' should have 'safeValue'
        List<CompletionItem> safeItems = engine.getSuggestions(code + "safe", (code + "safe").length());
        assertTrue("Expected 'safeValue' in completions", hasItem(safeItems, "safeValue"));
    }

    @Test
    public void testTypeAnnotationInsideTupleBracket() {
        String code1 = "let tuple: [ ";
        List<CompletionItem> items1 = engine.getSuggestions(code1, code1.length());
        assertTrue("Expected 'string' in tuple type position", hasItem(items1, "string"));
        assertTrue("Expected 'number' in tuple type position", hasItem(items1, "number"));

        String code2 = "let tuple: [ string, ";
        List<CompletionItem> items2 = engine.getSuggestions(code2, code2.length());
        assertTrue("Expected 'number' after comma in tuple", hasItem(items2, "number"));
        assertTrue("Expected 'boolean' after comma in tuple", hasItem(items2, "boolean"));
    }

    @Test
    public void testDotCompletionsOnAllVariablesInUserSnippet() {
        String snippet = "// Primitive Types\n" +
                "\n" +
                "let isDone: boolean = false;\n" +
                "\n" +
                "let age: number = 30;\n" +
                "\n" +
                "let firstName = \"Alice\";\n" +
                "\n" +
                "let u: undefined = undefined;\n" +
                "\n" +
                "let n: null = null;\n" +
                "// Arrays & Tuples\n" +
                "\n" +
                "let list: number [\n" +
                "\n" +
                "]\n" +
                "\n" +
                "= [\n" +
                "  1, 2, 3\n" +
                "];\n" +
                "\n" +
                "\n" +
                "let tuple: [\n" +
                "  string, number\n" +
                "]\n" +
                "\n" +
                "= [\n" +
                "  \"hello\", 10\n" +
                "];\n" +
                "\n" +
                "\n" +
                "// Fixed length and type order\n" +
                "// Enums\n" +
                "enum Role {\n" +
                "  User = \"USER\",\n" +
                "  Admin = \"ADMIN\",\n" +
                "\n" +
                "}\n" +
                "\n" +
                "let currentRole: Role = Role.Admin;\n" +
                "// Any, Unknown, and Never\n" +
                "\n" +
                "let randomValue: any = 10;\n" +
                "// Disables type checking (avoid when possible)\n" +
                "\n" +
                "let safeValue: unknown = \"hello\";\n" +
                "// Requires type checking before use\n" +
                "if (typeof safeValue === \"string\") {\n" +
                "  console.log(safeValue.toUpperCase());\n" +
                "\n" +
                "}\n" +
                "\n" +
                "function throwError(message: string): never {\n" +
                "  throw new Error(message);\n" +
                "\n" +
                "}\n";

        List<CompletionItem> randItems = engine.getSuggestions(snippet + "\nrandomValue.", (snippet + "\nrandomValue.").length());
        assertTrue("Expected 'toFixed' on randomValue.", hasItem(randItems, "toFixed"));
        assertTrue("Expected 'toString' on randomValue.", hasItem(randItems, "toString"));

        List<CompletionItem> safeItems = engine.getSuggestions(snippet + "\nsafeValue.", (snippet + "\nsafeValue.").length());
        assertTrue("Expected 'toUpperCase' on safeValue.", hasItem(safeItems, "toUpperCase"));
        assertTrue("Expected 'charAt' on safeValue.", hasItem(safeItems, "charAt"));

        List<CompletionItem> uItems = engine.getSuggestions(snippet + "\nu.", (snippet + "\nu.").length());
        assertTrue("Expected 'toString' on u.", hasItem(uItems, "toString"));
        assertTrue("Expected 'valueOf' on u.", hasItem(uItems, "valueOf"));

        List<CompletionItem> nItems = engine.getSuggestions(snippet + "\nn.", (snippet + "\nn.").length());
        assertTrue("Expected 'toString' on n.", hasItem(nItems, "toString"));
        assertTrue("Expected 'valueOf' on n.", hasItem(nItems, "valueOf"));

        String inLog = snippet.replace("console.log(safeValue.toUpperCase());", "console.log(safeValue.");
        int offset = inLog.indexOf("console.log(safeValue.") + "console.log(safeValue.".length();
        List<CompletionItem> inLogItems = engine.getSuggestions(inLog, offset);
        assertTrue("Expected 'toUpperCase' inside console.log(safeValue.", hasItem(inLogItems, "toUpperCase"));
    }
}
