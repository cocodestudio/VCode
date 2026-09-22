package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.SymbolExtractor;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JsSymbolRenamerTest {

    private static final String STUDENT_CLASS_CODE =
            "class Student {\n" +
            "  constructor(name, role) {\n" +
            "    this.name = name;\n" +
            "    this.role = role;\n" +
            "  }\n" +
            "  \n" +
            "  getProfile() {\n" +
            "    return `Name: ${this.name}\\nRole: ${this.role}`;\n" +
            "  }\n" +
            "}\n" +
            "\n" +
            "const myClass = new Student(\"Adeel\", \"admin\");\n" +
            "\n" +
            "myClass.getProfile();\n";

    @Test
    public void testRenameThisPropertyInConstructorDoesNotChangeConstructorParam() {
        LspDocument doc = new LspDocument("/test.js", STUDENT_CLASS_CODE, "javascript", 1);
        int offset = STUDENT_CLASS_CODE.indexOf("this.name") + 6; // Cursor on 'name' in 'this.name'
        LspPosition pos = SymbolExtractor.offsetToPosition(STUDENT_CLASS_CODE, offset);

        List<LspLocation> locs = JsSymbolRenamer.rename(doc, pos);

        assertNotNull("Rename locations should not be null", locs);
        assertEquals("Should rename this.name in constructor and in getProfile template", 2, locs.size());

        // Verify locations correspond to this.name and not param name
        int paramNameOffset = STUDENT_CLASS_CODE.indexOf("(name,");
        int rhsNameOffset = STUDENT_CLASS_CODE.indexOf("= name;");

        for (LspLocation loc : locs) {
            int start = doc.toOffset(loc.range.start);
            int end = doc.toOffset(loc.range.end);
            assertEquals("name", STUDENT_CLASS_CODE.substring(start, end));

            // Must NOT be the constructor param
            assertTrue("Must not be constructor parameter declaration",
                    start > paramNameOffset + 10 || start < paramNameOffset);
            // Must NOT be the RHS `= name;`
            assertTrue("Must not be the RHS assignment identifier",
                    start != rhsNameOffset + 2);
        }
    }

    @Test
    public void testRenameConstructorParamDoesNotAffectThisProperty() {
        LspDocument doc = new LspDocument("/test.js", STUDENT_CLASS_CODE, "javascript", 1);
        int offset = STUDENT_CLASS_CODE.indexOf("(name,") + 2; // Cursor on param 'name'
        LspPosition pos = SymbolExtractor.offsetToPosition(STUDENT_CLASS_CODE, offset);

        List<LspLocation> locs = JsSymbolRenamer.rename(doc, pos);

        assertNotNull("Rename locations should not be null", locs);
        assertEquals("Should rename constructor param and RHS assignment only", 2, locs.size());

        int thisNameConstructor = STUDENT_CLASS_CODE.indexOf("this.name") + 5;
        int thisNameTemplate = STUDENT_CLASS_CODE.lastIndexOf("this.name") + 5;

        for (LspLocation loc : locs) {
            int start = doc.toOffset(loc.range.start);
            int end = doc.toOffset(loc.range.end);
            assertEquals("name", STUDENT_CLASS_CODE.substring(start, end));

            // Must NOT be this.name
            assertTrue("Must not rename this.name in constructor", start != thisNameConstructor);
            assertTrue("Must not rename this.name in template literal", start != thisNameTemplate);
        }
    }

    @Test
    public void testRenameClassMethodFromDeclarationRenamesDeclarationAndCallSites() {
        LspDocument doc = new LspDocument("/test.js", STUDENT_CLASS_CODE, "javascript", 1);
        int offset = STUDENT_CLASS_CODE.indexOf("getProfile()") + 2; // Cursor on declaration
        LspPosition pos = SymbolExtractor.offsetToPosition(STUDENT_CLASS_CODE, offset);

        List<LspLocation> locs = JsSymbolRenamer.rename(doc, pos);

        assertNotNull("Rename locations should not be null", locs);
        assertEquals("Should rename declaration and calling place", 2, locs.size());

        int declOffset = STUDENT_CLASS_CODE.indexOf("getProfile()");
        int callOffset = STUDENT_CLASS_CODE.indexOf("myClass.getProfile()") + 8;

        boolean foundDecl = false;
        boolean foundCall = false;

        for (LspLocation loc : locs) {
            int start = doc.toOffset(loc.range.start);
            int end = doc.toOffset(loc.range.end);
            assertEquals("getProfile", STUDENT_CLASS_CODE.substring(start, end));

            if (start == declOffset) foundDecl = true;
            if (start == callOffset) foundCall = true;
        }

        assertTrue("Declaration must be included", foundDecl);
        assertTrue("Calling site must be included", foundCall);
    }

    @Test
    public void testRenameClassMethodFromCallSiteRenamesDeclarationAndCallSites() {
        LspDocument doc = new LspDocument("/test.js", STUDENT_CLASS_CODE, "javascript", 1);
        int offset = STUDENT_CLASS_CODE.indexOf("myClass.getProfile()") + 10; // Cursor on call site
        LspPosition pos = SymbolExtractor.offsetToPosition(STUDENT_CLASS_CODE, offset);

        List<LspLocation> locs = JsSymbolRenamer.rename(doc, pos);

        assertNotNull("Rename locations should not be null", locs);
        assertEquals("Should rename declaration and calling place from call site", 2, locs.size());

        int declOffset = STUDENT_CLASS_CODE.indexOf("getProfile()");
        int callOffset = STUDENT_CLASS_CODE.indexOf("myClass.getProfile()") + 8;

        boolean foundDecl = false;
        boolean foundCall = false;

        for (LspLocation loc : locs) {
            int start = doc.toOffset(loc.range.start);
            int end = doc.toOffset(loc.range.end);
            assertEquals("getProfile", STUDENT_CLASS_CODE.substring(start, end));

            if (start == declOffset) foundDecl = true;
            if (start == callOffset) foundCall = true;
        }

        assertTrue("Declaration must be included", foundDecl);
        assertTrue("Calling site must be included", foundCall);
    }

    @Test
    public void testRenameLexicalVariable() {
        String code = "let counter = 0;\ncounter++;\nconsole.log(counter);\n";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        LspPosition pos = new LspPosition(0, 5); // on 'counter'

        List<LspLocation> locs = JsSymbolRenamer.rename(doc, pos);

        assertNotNull(locs);
        assertEquals("Should rename all 3 occurrences of lexical counter", 3, locs.size());
        for (LspLocation loc : locs) {
            int start = doc.toOffset(loc.range.start);
            int end = doc.toOffset(loc.range.end);
            assertEquals("counter", code.substring(start, end));
        }
    }

    @Test
    public void testRenameThisPropertyDoesNotBleedToAnotherClass() {
        String code =
                "class Student {\n" +
                "  constructor(name) {\n" +
                "    this.name = name;\n" +
                "  }\n" +
                "}\n" +
                "class Teacher {\n" +
                "  constructor(name) {\n" +
                "    this.name = name;\n" +
                "  }\n" +
                "}\n";
        LspDocument doc = new LspDocument("/test.js", code, "javascript", 1);
        int offset = code.indexOf("this.name") + 6; // Inside Student
        LspPosition pos = SymbolExtractor.offsetToPosition(code, offset);

        List<LspLocation> locs = JsSymbolRenamer.rename(doc, pos);

        assertNotNull(locs);
        assertEquals("Should only rename Student's this.name", 1, locs.size());

        int teacherThisName = code.lastIndexOf("this.name") + 5;
        for (LspLocation loc : locs) {
            int start = doc.toOffset(loc.range.start);
            assertTrue("Must not be Teacher's this.name", start != teacherThisName);
        }
    }

    @Test
    public void testTypeScriptSupport() {
        String tsCode =
                "class User {\n" +
                "  constructor(public id: number, public role: string) {\n" +
                "    this.role = role;\n" +
                "  }\n" +
                "}\n";
        LspDocument doc = new LspDocument("/test.ts", tsCode, "typescript", 1);
        int offset = tsCode.indexOf("this.role") + 6;
        LspPosition pos = SymbolExtractor.offsetToPosition(tsCode, offset);

        TsLspServer server = new TsLspServer();
        List<LspLocation> locs = server.rename(doc, pos);

        assertNotNull(locs);
        assertFalse(locs.isEmpty());
    }

    @Test
    public void testRenameSymbolInTypeScriptConditionAndMethodCall() {
        String code =
                "let safeValue: unknown = \"hello\";\n" +
                "if (typeof safeValue === \"string\") {\n" +
                "    console.log(safeValue.toUpperCase());\n" +
                "}\n";
        LspDocument doc = new LspDocument("/test.ts", code, "typescript", 1);
        int offset = code.indexOf("let safeValue") + 5; // On safeValue
        LspPosition pos = SymbolExtractor.offsetToPosition(code, offset);

        List<LspLocation> locs = JsSymbolRenamer.rename(doc, pos);

        assertNotNull("Locations should not be null", locs);
        assertEquals("Should rename safeValue at declaration, in if-condition, and in method call", 3, locs.size());

        for (LspLocation loc : locs) {
            int start = doc.toOffset(loc.range.start);
            int end = doc.toOffset(loc.range.end);
            assertEquals("safeValue", code.substring(start, end));
        }
    }

    @Test
    public void testRenameSymbolInTypeScriptTypeAnnotation() {
        String code =
                "enum Role {\n" +
                "  User,\n" +
                "  Admin,\n" +
                "}\n" +
                "\n" +
                "let currRole: Role = Role.Admin;\n";
        LspDocument doc = new LspDocument("/test.ts", code, "typescript", 1);
        int offset = code.indexOf("enum Role") + 6; // On Role in enum
        LspPosition pos = SymbolExtractor.offsetToPosition(code, offset);

        List<LspLocation> locs = JsSymbolRenamer.rename(doc, pos);

        assertNotNull("Locations should not be null", locs);
        assertEquals("Should rename enum declaration, type annotation, and enum member receiver", 3, locs.size());

        for (LspLocation loc : locs) {
            int start = doc.toOffset(loc.range.start);
            int end = doc.toOffset(loc.range.end);
            assertEquals("Role", code.substring(start, end));
        }
    }

    @Test
    public void testRenameFromTypeAnnotationSite() {
        String code =
                "enum Role {\n" +
                "  User,\n" +
                "  Admin,\n" +
                "}\n" +
                "\n" +
                "let currRole: Role = Role.Admin;\n";
        LspDocument doc = new LspDocument("/test.ts", code, "typescript", 1);
        int offset = code.indexOf(": Role") + 3; // Cursor directly on Role in : Role
        LspPosition pos = SymbolExtractor.offsetToPosition(code, offset);

        List<LspLocation> locs = JsSymbolRenamer.rename(doc, pos);

        assertNotNull("Locations should not be null", locs);
        assertEquals("Should rename all occurrences when invoked directly from type annotation", 3, locs.size());

        for (LspLocation loc : locs) {
            int start = doc.toOffset(loc.range.start);
            int end = doc.toOffset(loc.range.end);
            assertEquals("Role", code.substring(start, end));
        }
    }

    @Test
    public void testRenameInterfaceAndTypeAliasInTypeAnnotations() {
        String code =
                "interface User {\n" +
                "  name: string;\n" +
                "}\n" +
                "type UserList = User[];\n" +
                "function printUser(u: User): User {\n" +
                "  return u;\n" +
                "}\n";
        LspDocument doc = new LspDocument("/test.ts", code, "typescript", 1);
        int offset = code.indexOf("interface User") + 11;
        LspPosition pos = SymbolExtractor.offsetToPosition(code, offset);

        List<LspLocation> locs = JsSymbolRenamer.rename(doc, pos);

        assertNotNull("Locations should not be null", locs);
        // User appears in: interface User, User[], u: User, : User
        assertEquals("Should rename User in interface decl, type alias, parameter, and return type", 4, locs.size());

        for (LspLocation loc : locs) {
            int start = doc.toOffset(loc.range.start);
            int end = doc.toOffset(loc.range.end);
            assertEquals("User", code.substring(start, end));
        }
    }

    @Test
    public void testRenameInWhileSwitchAndForLoops() {
        String code =
                "let count = 10;\n" +
                "while (count > 0) {\n" +
                "  count--;\n" +
                "}\n" +
                "switch (count) {\n" +
                "  case 0: break;\n" +
                "}\n" +
                "for (let i = count; i < 20; i++) {}\n";
        LspDocument doc = new LspDocument("/test.ts", code, "typescript", 1);
        int offset = code.indexOf("let count") + 5;
        LspPosition pos = SymbolExtractor.offsetToPosition(code, offset);

        List<LspLocation> locs = JsSymbolRenamer.rename(doc, pos);

        assertNotNull("Locations should not be null", locs);
        // count appears in: let count, while (count), count--, switch (count), for (let i = count)
        assertEquals("Should rename count across all control loop headers", 5, locs.size());

        for (LspLocation loc : locs) {
            int start = doc.toOffset(loc.range.start);
            int end = doc.toOffset(loc.range.end);
            assertEquals("count", code.substring(start, end));
        }
    }
}
