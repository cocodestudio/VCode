package com.cocode.vcode.ide.core.language.json;

import com.cocode.vcode.ide.core.model.Problem;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JsonLinterTest {

    private final File mockFile = new File("test.json");

    @Test
    public void testValidJsonHasNoProblems() {
        String source = "{ \"a\": 1, \"b\": 2 }";
        List<Problem> problems = JsonLinter.analyze(mockFile, source);
        assertEquals(0, problems.size());
    }

    @Test
    public void testDuplicateKeys() {
        String source = "{ \"a\": 1, \"b\": 2, \"a\": 3 }";
        List<Problem> problems = JsonLinter.analyze(mockFile, source);
        
        assertEquals(1, problems.size());
        Problem p = problems.get(0);
        assertEquals(Problem.Severity.WARNING, p.getSeverity());
        assertTrue(p.getMessage().contains("Duplicate"));
        assertTrue(p.getMessage().contains("\"a\""));
    }

    @Test
    public void testSyntaxErrors() {
        String source = "{ \"a\" 1 }";
        List<Problem> problems = JsonLinter.analyze(mockFile, source);
        
        assertEquals(1, problems.size());
        Problem p = problems.get(0);
        assertEquals(Problem.Severity.ERROR, p.getSeverity());
        assertEquals("Missing colon", p.getMessage());
    }

    @Test
    public void testTrailingComma() {
        String source = "{ \"a\": 1, }";
        List<Problem> problems = JsonLinter.analyze(mockFile, source);
        
        boolean foundTrailing = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Trailing comma")) {
                foundTrailing = true;
                break;
            }
        }
        assertTrue("Should report trailing comma error", foundTrailing);
    }

    @Test
    public void testMissingClosingQuote() {
        String source = "{ \"a: 1 }";
        List<Problem> problems = JsonLinter.analyze(mockFile, source);
        
        boolean foundQuoteError = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("quote") || p.getMessage().contains("colon") || p.getMessage().contains("Syntax")) {
                foundQuoteError = true;
                break;
            }
        }
        assertTrue("Should report missing quote or syntax error", foundQuoteError);
    }

    @Test
    public void testKeywordBoundaryError() {
        String source = "{ \"a\": truth }";
        List<Problem> problems = JsonLinter.analyze(mockFile, source);
        
        boolean foundError = false;
        for (Problem p : problems) {
            if (p.getSeverity() == Problem.Severity.ERROR) {
                foundError = true;
                break;
            }
        }
        assertTrue("Should report error for invalid identifier keyword", foundError);
    }

    @Test
    public void testMalformedNumberReportsSyntaxError() {
        String source = "{ \"num\": 1.2.3 }";
        List<Problem> problems = JsonLinter.analyze(mockFile, source);
        boolean foundError = false;
        for (Problem p : problems) {
            if (p.getSeverity() == Problem.Severity.ERROR) {
                foundError = true;
                break;
            }
        }
        assertTrue("Should report syntax error for malformed number 1.2.3", foundError);
    }

    @Test
    public void testNormalizeKey() {
        assertEquals("name", JsonLinter.normalizeKey("\"name\""));
        assertEquals("name", JsonLinter.normalizeKey("'name'"));
        assertEquals("name", JsonLinter.normalizeKey("\"name"));
        assertEquals("name", JsonLinter.normalizeKey("name"));
        assertEquals("name", JsonLinter.normalizeKey("  \"name\"  "));
    }

    @Test
    public void testDuplicateKeysNormalized() {
        String source = "{\n \"name\": \"Alice\",\n \"name\": \"Bob\"\n}";
        List<Problem> problems = JsonLinter.analyze(mockFile, source);
        boolean foundDuplicate = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Duplicate") && p.getMessage().contains("name")) {
                foundDuplicate = true;
                break;
            }
        }
        assertTrue("Should report duplicate key across normalized quotes", foundDuplicate);
    }

    @Test
    public void testCommentsInStandardJsonTriggerWarning() {
        String source = "{\n  // This is a comment\n  \"key\": 123\n}";
        List<Problem> problems = JsonLinter.analyze(mockFile, source);
        boolean foundCommentWarn = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Comments are not permitted in standard JSON")) {
                foundCommentWarn = true;
                assertEquals(Problem.Severity.WARNING, p.getSeverity());
                break;
            }
        }
        assertTrue("Comments in standard .json should trigger warning", foundCommentWarn);
    }

    @Test
    public void testCommentsInJsoncAllowed() {
        File jsoncFile = new File("settings.jsonc");
        String source = "{\n  // Permitted comment\n  \"key\": 123\n}";
        List<Problem> problems = JsonLinter.analyze(jsoncFile, source);
        for (Problem p : problems) {
            assertFalse("Comments in .jsonc should not be warned against: " + p.getMessage(),
                    p.getMessage().contains("Comments are not permitted"));
        }
    }

    @Test
    public void testJsoncConfigFilesAllowCommentsAndTrailingCommas() {
        String[] configNames = {"tsconfig.json", "tsconfig.node.json", "settings.json", ".eslintrc.json"};
        for (String name : configNames) {
            File configFile = new File(name);
            assertTrue("Should be recognized as JSONC: " + name, JsonLinter.isJsoncMode(configFile));

            String source = "{\n  // Permitted comment\n  \"key\": 123,\n}";
            List<Problem> problems = JsonLinter.analyze(configFile, source);
            for (Problem p : problems) {
                assertFalse("No comment or trailing comma errors allowed in " + name + ": " + p.getMessage(),
                        p.getMessage().contains("Comments are not permitted") || p.getMessage().contains("Trailing comma"));
            }
        }
    }

    @Test
    public void testMultipleRootsEmitsError() {
        String source = "{ \"a\": 1 } { \"b\": 2 }";
        List<Problem> problems = JsonLinter.analyze(mockFile, source);
        boolean foundMultipleRoots = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Multiple root values")) {
                foundMultipleRoots = true;
                break;
            }
        }
        assertTrue("Should emit error for multiple root values", foundMultipleRoots);
    }

    @Test
    public void testPackageJsonSchemaValidationMissingRequiredFields() {
        File pkgFile = new File("package.json");
        String source = "{\n  \"description\": \"A package without name or version\"\n}";
        List<Problem> problems = JsonLinter.analyze(pkgFile, source);
        boolean missingName = false;
        boolean missingVersion = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Missing required property 'name'")) missingName = true;
            if (p.getMessage().contains("Missing required property 'version'")) missingVersion = true;
        }
        assertTrue("Should report missing 'name' in package.json", missingName);
        assertTrue("Should report missing 'version' in package.json", missingVersion);
    }

    @Test
    public void testTsConfigSchemaValidationInvalidEnum() {
        File tsconfigFile = new File("tsconfig.json");
        String source = "{\n  \"compilerOptions\": {\n    \"target\": \"INVALID_TARGET\"\n  }\n}";
        List<Problem> problems = JsonLinter.analyze(tsconfigFile, source);
        boolean foundEnumError = false;
        for (Problem p : problems) {
            if (p.getMessage().contains("Value 'INVALID_TARGET' is not accepted for 'target'")) {
                foundEnumError = true;
                break;
            }
        }
        assertTrue("Should report invalid enum value for target", foundEnumError);
    }
}
