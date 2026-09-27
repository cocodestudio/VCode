package com.cocode.vcode.ide.core.lsp;

import com.cocode.vcode.ide.core.language.js.ParseResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Unit tests verifying memory-bounded AST cache lifecycle (LRU eviction) in {@link ProjectIndex}.
 */
public class ProjectIndexOptimizationTest {

    private ProjectIndex projectIndex;

    @Before
    public void setUp() {
        projectIndex = ProjectIndex.getInstance();
        projectIndex.clear();
    }

    @After
    public void tearDown() {
        projectIndex.clear();
    }

    @Test
    public void testLruAstEvictionBoundsCacheToMaxLimit() {
        // Insert 12 dummy ParseResults into ProjectIndex
        for (int i = 1; i <= 12; i++) {
            File dummyFile = new File("/workspace/test_" + i + ".js");
            ParseResult pr = new ParseResult(dummyFile, "const x = " + i + ";", null, null, null, ParseResult.MODE_FULL);
            projectIndex.updateParseResult(dummyFile.getAbsolutePath(), pr);
        }

        // ProjectIndex has MAX_CACHED_AST_COUNT = 8.
        // Therefore, the first 4 files (test_1 to test_4) should have been evicted,
        // while the most recent 8 files (test_5 to test_12) should remain cached.
        for (int i = 1; i <= 4; i++) {
            File dummyFile = new File("/workspace/test_" + i + ".js");
            assertNull("Old entry should be evicted: " + dummyFile.getAbsolutePath(),
                    projectIndex.getParseResult(dummyFile.getAbsolutePath()));
        }

        for (int i = 5; i <= 12; i++) {
            File dummyFile = new File("/workspace/test_" + i + ".js");
            assertNotNull("Recent entry should remain cached: " + dummyFile.getAbsolutePath(),
                    projectIndex.getParseResult(dummyFile.getAbsolutePath()));
        }
    }
}
