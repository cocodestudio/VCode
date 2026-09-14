package com.cocode.vcode.ide.core.language.ts;

import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TsLinterTest {

    private final File mockFile = new File("test.ts");

    @Test
    public void testAnalyzeTypeMismatch() {
        String ts = "const x: number = 'hello';";
        List<Problem> problems = TsLinter.analyze(mockFile, ts);
        assertFalse("Linter should find problems", problems.isEmpty());

        boolean foundMismatch = false;
        for (Problem p : problems) {
            if (p.getMessage().toLowerCase().contains("type mismatch")) {
                foundMismatch = true;
                break;
            }
        }
        assertTrue("Should detect type mismatch in TS", foundMismatch);
    }

    @Test
    public void testOptionalBeforeRequiredWithDefaultParam() {
        String ts = "function test(x = 'default', y: number) {}";
        List<Problem> problems = TsLinter.analyze(mockFile, ts);
        boolean foundWarning = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("required params must come first")) {
                foundWarning = true;
                break;
            }
        }
        assertTrue("Default parameter x='default' before y must trigger optional before required warning", foundWarning);
    }

    @Test
    public void testOptionalBeforeRequiredWithQuestionMark() {
        String ts = "function test(a?: string, b: number) {}";
        List<Problem> problems = TsLinter.analyze(mockFile, ts);
        boolean foundWarning = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("required params must come first")) {
                foundWarning = true;
                break;
            }
        }
        assertTrue("Optional parameter a? before b must trigger warning", foundWarning);
    }

    @Test
    public void testReturnAny() {
        String ts = "function compute(): any { return 42; }";
        List<Problem> problems = TsLinter.analyze(mockFile, ts);
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("returns 'any'")) {
                found = true;
                break;
            }
        }
        assertTrue("Should warn on return type 'any'", found);
    }

    @Test
    public void testExportedFunctionMissingReturnType() {
        String ts = "export function doWork() {}";
        List<Problem> problems = TsLinter.analyze(mockFile, ts);
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("missing a return type annotation")) {
                found = true;
                break;
            }
        }
        assertTrue("Should warn on exported function missing return type", found);
    }

    @Test
    public void testAnyParam() {
        String ts = "function processData(item: any) {}";
        List<Problem> problems = TsLinter.analyze(mockFile, ts);
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Explicit 'any' type for 'item'")) {
                found = true;
                break;
            }
        }
        assertTrue("Should warn on explicit 'any' parameter", found);
    }

    @Test
    public void testEnumSuggestion() {
        String ts = "enum Direction { Up, Down }";
        List<Problem> problems = TsLinter.analyze(mockFile, ts);
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Enums add runtime overhead")) {
                found = true;
                break;
            }
        }
        assertTrue("Should suggest const object over enum", found);
    }

    @Test
    public void testRedundantInferredType() {
        String ts = "const title: string = 'Welcome';";
        List<Problem> problems = TsLinter.analyze(mockFile, ts);
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("is inferred: remove the explicit annotation")) {
                found = true;
                break;
            }
        }
        assertTrue("Should warn on redundant inferred string type", found);
    }

    @Test
    public void testUnionUndefinedSuggestion() {
        String ts = "function greet(name: string | undefined) {}";
        List<Problem> problems = TsLinter.analyze(mockFile, ts);
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("'Type | undefined' in parameter can be written as")) {
                found = true;
                break;
            }
        }
        assertTrue("Should suggest optional parameter for 'string | undefined'", found);
    }

    @Test
    public void testReadonlyArraySuggestion() {
        String ts = "interface User { roles: string[]; }";
        List<Problem> problems = TsLinter.analyze(mockFile, ts);
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("readonly roles: string[]")) {
                found = true;
                break;
            }
        }
        assertTrue("Should suggest readonly array in interface", found);
    }

    @Test
    public void testInlineObjectTypeSuggestion() {
        String ts = "let config: { id: string; name: string; active: boolean; };";
        List<Problem> problems = TsLinter.analyze(mockFile, ts);
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Inline object type with 3 properties")) {
                found = true;
                break;
            }
        }
        assertTrue("Should suggest named interface for complex inline object type", found);
    }

    @Test
    public void testNamespaceDiscouraged() {
        String ts = "namespace MyModule { export const x = 1; }";
        List<Problem> problems = TsLinter.analyze(mockFile, ts);
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("'namespace' is discouraged in modern TypeScript")) {
                found = true;
                break;
            }
        }
        assertTrue("Should warn against namespace usage", found);
    }

    @Test
    public void testFunctionTypeTooBroad() {
        String ts = "let handler: Function;";
        List<Problem> problems = TsLinter.analyze(mockFile, ts);
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("'Function' type is too broad")) {
                found = true;
                break;
            }
        }
        assertTrue("Should warn on 'Function' type", found);
    }

    @Test
    public void testAsAssertionWarning() {
        String ts = "const element = raw as HTMLDivElement;";
        List<Problem> problems = TsLinter.analyze(mockFile, ts);
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Type assertion 'as HTMLDivElement'")) {
                found = true;
                break;
            }
        }
        assertTrue("Should warn on type assertion 'as HTMLDivElement'", found);
    }

    @Test
    public void testNonNullAssertionOnNullable() {
        String ts = "let target: string | null = null;\n" +
                    "target!;";
        List<Problem> problems = TsLinter.analyze(mockFile, ts);
        boolean found = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Non-null assertion '!' used on a possibly-null value")) {
                found = true;
                break;
            }
        }
        assertTrue("Should detect non-null assertion on nullable target variable", found);
    }
}
