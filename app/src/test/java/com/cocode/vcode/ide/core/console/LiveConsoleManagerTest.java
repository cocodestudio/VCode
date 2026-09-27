package com.cocode.vcode.ide.core.console;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LiveConsoleManagerTest {

    private LiveConsoleManager manager;

    @Before
    public void setUp() {
        manager = LiveConsoleManager.getInstance();
        manager.clearLogs();
    }

    @Test
    public void testAddLogAndClear() {
        manager.addLog(new ConsoleLogEntry(ConsoleLogEntry.Level.INFO, "Server started", System.currentTimeMillis(), "app.js:10"));
        manager.addLog(new ConsoleLogEntry(ConsoleLogEntry.Level.ERROR, "Null reference", System.currentTimeMillis(), "app.js:20"));

        List<ConsoleLogEntry> snapshot = manager.getSnapshot();
        assertEquals(2, snapshot.size());
        assertEquals(ConsoleLogEntry.Level.INFO, snapshot.get(0).getLevel());
        assertEquals("Server started", snapshot.get(0).getMessage());
        assertEquals(ConsoleLogEntry.Level.ERROR, snapshot.get(1).getLevel());

        manager.clearLogs();
        assertTrue(manager.getSnapshot().isEmpty());
    }

    @Test
    public void testCapacityLimit() {
        for (int i = 0; i < 550; i++) {
            manager.addLog(new ConsoleLogEntry(ConsoleLogEntry.Level.LOG, "Log " + i, System.currentTimeMillis(), ""));
        }

        List<ConsoleLogEntry> snapshot = manager.getSnapshot();
        assertEquals(500, snapshot.size());
        assertEquals("Log 50", snapshot.get(0).getMessage());
        assertEquals("Log 549", snapshot.get(499).getMessage());
    }
}
