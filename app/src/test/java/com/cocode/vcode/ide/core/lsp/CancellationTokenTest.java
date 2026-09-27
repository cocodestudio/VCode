package com.cocode.vcode.ide.core.lsp;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link CancellationToken} and {@link OperationCancelledException}.
 */
public class CancellationTokenTest {

    @Test
    public void testInitialStateIsNotCancelled() {
        CancellationToken token = new CancellationToken();
        assertFalse(token.isCancelled());
        // Should not throw
        token.throwIfCancelled();
    }

    @Test
    public void testCancelFlipsState() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        assertTrue(token.isCancelled());
    }

    @Test(expected = OperationCancelledException.class)
    public void testThrowIfCancelledThrowsWhenCancelled() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        token.throwIfCancelled();
    }

    @Test
    public void testCancellationAcrossThreads() throws InterruptedException {
        CancellationToken token = new CancellationToken();
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(50);
                token.cancel();
            } catch (InterruptedException ignored) {
            }
        });
        thread.start();

        long start = System.currentTimeMillis();
        while (!token.isCancelled() && (System.currentTimeMillis() - start) < 1000) {
            Thread.sleep(5);
        }
        thread.join();

        assertTrue(token.isCancelled());
    }
}
