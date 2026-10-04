package com.cocode.vcode.ide.core.command;

import com.cocode.vcode.ide.core.events.Disposable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CommandRegistryTest {

    private CommandRegistry registry;

    @Before
    public void setUp() {
        registry = CommandRegistry.getInstance();
        registry.clear();
    }

    @After
    public void tearDown() {
        registry.clear();
    }

    @Test
    public void testRegisterAndExecuteCommand() {
        AtomicBoolean executed = new AtomicBoolean(false);
        AtomicReference<Object[]> receivedArgs = new AtomicReference<>();

        Disposable disposable = registry.registerCommand("vcode.test.hello", "Hello Command", args -> {
            executed.set(true);
            receivedArgs.set(args);
        });

        assertTrue(registry.hasCommand("vcode.test.hello"));
        assertNotNull(registry.getCommand("vcode.test.hello"));
        assertEquals("Hello Command", registry.getCommand("vcode.test.hello").getTitle());

        boolean result = registry.executeCommand("vcode.test.hello", "arg1", 42);
        assertTrue(result);
        assertTrue(executed.get());
        assertNotNull(receivedArgs.get());
        assertEquals(2, receivedArgs.get().length);
        assertEquals("arg1", receivedArgs.get()[0]);
        assertEquals(42, receivedArgs.get()[1]);

        // Test disposable unregisters the command
        disposable.dispose();
        assertFalse(registry.hasCommand("vcode.test.hello"));
        assertNull(registry.getCommand("vcode.test.hello"));
        assertFalse(registry.executeCommand("vcode.test.hello"));
    }

    @Test
    public void testCommandItemWithCategoryAndIcon() {
        CommandItem item = new CommandItem("vcode.git.commit", "Commit Staged Changes", "Git", 1234, args -> {});
        registry.registerCommand(item);

        CommandItem retrieved = registry.getCommand("vcode.git.commit");
        assertNotNull(retrieved);
        assertEquals("Git", retrieved.getCategory());
        assertEquals(1234, retrieved.getIconResId());
    }

    @Test
    public void testGetAllCommands() {
        registry.registerCommand("cmd1", "Command 1", args -> {});
        registry.registerCommand("cmd2", "Command 2", args -> {});
        registry.registerCommand("cmd3", "Command 3", args -> {});

        List<CommandItem> all = registry.getAllCommands();
        assertEquals(3, all.size());
    }

    @Test
    public void testExecuteNonExistentCommandReturnsFalse() {
        assertFalse(registry.executeCommand("vcode.does.not.exist"));
    }
}
