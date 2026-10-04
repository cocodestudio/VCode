package com.cocode.vcode.ide.core.diagnostic;

import com.cocode.vcode.ide.core.lsp.CancellationToken;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.model.Problem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiagnosticCancellationTest {

    private DiagnosticProvider slowProvider1;
    private DiagnosticProvider slowProvider2;

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
        if (slowProvider1 != null) DiagnosticEngine.unregisterProvider(slowProvider1);
        if (slowProvider2 != null) DiagnosticEngine.unregisterProvider(slowProvider2);
    }

    @Test
    public void testCancellationTokenState() {
        CancellationToken token = new CancellationToken();
        assertFalse(token.isCancelled());

        token.cancel();
        assertTrue(token.isCancelled());
    }

    @Test
    public void testPreCancelledTokenSkipsAnalysis() {
        AtomicBoolean providerExecuted = new AtomicBoolean(false);

        slowProvider1 = new DiagnosticProvider() {
            @Override
            public String getId() {
                return "test-slow-linter";
            }

            @Override
            public boolean canLint(FileType fileType, String path) {
                return true;
            }

            @Override
            public List<Problem> lint(File file, String content, FileType fileType, ProjectIndex index, CancellationToken token) {
                providerExecuted.set(true);
                return Collections.emptyList();
            }

            @Override
            public List<Problem> lint(File file, String content, FileType fileType, ProjectIndex index) {
                return lint(file, content, fileType, index, null);
            }
        };

        DiagnosticEngine.registerProvider(slowProvider1);

        CancellationToken cancelledToken = new CancellationToken();
        cancelledToken.cancel();

        List<Problem> problems = DiagnosticEngine.analyze(
                new File("/test/test.js"),
                "console.log('hi');",
                FileType.JAVASCRIPT,
                cancelledToken
        );

        assertTrue(problems.isEmpty());
        assertFalse("Provider should not have been executed when token was pre-cancelled", providerExecuted.get());
    }

    @Test
    public void testCooperativeCancellationBetweenProviders() {
        AtomicInteger runs = new AtomicInteger(0);
        CancellationToken token = new CancellationToken();

        slowProvider1 = new DiagnosticProvider() {
            @Override
            public String getId() {
                return "test-cancelling-linter-1";
            }

            @Override
            public boolean canLint(FileType fileType, String path) {
                return true;
            }

            @Override
            public List<Problem> lint(File file, String content, FileType fileType, ProjectIndex index, CancellationToken t) {
                runs.incrementAndGet();
                // Simulate detecting a keystroke / abort request mid-lint
                if (t != null) {
                    t.cancel();
                }
                return Collections.emptyList();
            }

            @Override
            public List<Problem> lint(File file, String content, FileType fileType, ProjectIndex index) {
                return lint(file, content, fileType, index, null);
            }
        };

        slowProvider2 = new DiagnosticProvider() {
            @Override
            public String getId() {
                return "test-cancelling-linter-2";
            }

            @Override
            public boolean canLint(FileType fileType, String path) {
                return true;
            }

            @Override
            public List<Problem> lint(File file, String content, FileType fileType, ProjectIndex index, CancellationToken t) {
                runs.incrementAndGet();
                return Collections.emptyList();
            }

            @Override
            public List<Problem> lint(File file, String content, FileType fileType, ProjectIndex index) {
                return lint(file, content, fileType, index, null);
            }
        };

        DiagnosticEngine.registerProvider(slowProvider1);
        DiagnosticEngine.registerProvider(slowProvider2);

        DiagnosticEngine.analyze(
                new File("/test/test.js"),
                "var a = 1;",
                FileType.JAVASCRIPT,
                token
        );

        // slowProvider1 ran and cancelled token; subsequent custom providers must be skipped
        assertTrue(runs.get() < 2);
    }
}
