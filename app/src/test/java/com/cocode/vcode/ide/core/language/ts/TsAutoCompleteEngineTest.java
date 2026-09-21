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
}
