package com.cocode.vcode.ide.core.extension;

import com.cocode.vcode.ide.core.command.CommandRegistry;
import com.cocode.vcode.ide.core.events.Disposable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ExtensionHostTest {

    private ExtensionHost host;

    @Before
    public void setUp() {
        host = ExtensionHost.getInstance();
        host.shutdown();
    }

    @After
    public void tearDown() {
        host.shutdown();
    }

    @Test
    public void testManifestParsing() throws Exception {
        String json = "{\n" +
                "  \"id\": \"vcode.plugin.prettier\",\n" +
                "  \"name\": \"prettier-formatter\",\n" +
                "  \"displayName\": \"Prettier Formatter\",\n" +
                "  \"version\": \"2.5.0\",\n" +
                "  \"description\": \"Opinionated code formatter for web languages\",\n" +
                "  \"entryPoint\": \"com.vcode.plugins.prettier.PrettierPlugin\"\n" +
                "}";

        ExtensionManifest manifest = ExtensionManifest.fromJson(json);
        assertEquals("vcode.plugin.prettier", manifest.getId());
        assertEquals("prettier-formatter", manifest.getName());
        assertEquals("Prettier Formatter", manifest.getDisplayName());
        assertEquals("2.5.0", manifest.getVersion());
        assertEquals("Opinionated code formatter for web languages", manifest.getDescription());
        assertEquals("com.vcode.plugins.prettier.PrettierPlugin", manifest.getEntryPoint());
    }

    @Test
    public void testExtensionContextDisposables() {
        ExtensionContext context = new ExtensionContext("test.ext", new File("/ext"), new File("/workspace"));
        assertEquals("test.ext", context.getExtensionId());
        assertEquals(new File("/ext"), context.getExtensionDir());
        assertEquals(new File("/workspace"), context.getWorkspaceDir());

        List<Integer> order = new ArrayList<>();
        context.addDisposable(() -> order.add(1));
        context.addDisposable(() -> order.add(2));
        context.addDisposable(() -> order.add(3));

        // Disposes in reverse order (LIFO)
        context.dispose();
        assertEquals(3, order.size());
        assertEquals(Integer.valueOf(3), order.get(0));
        assertEquals(Integer.valueOf(2), order.get(1));
        assertEquals(Integer.valueOf(1), order.get(2));
    }

    @Test
    public void testPluginActivationAndDeactivation() {
        AtomicBoolean initialized = new AtomicBoolean(false);
        AtomicBoolean deactivated = new AtomicBoolean(false);
        AtomicBoolean commandDisposed = new AtomicBoolean(false);

        ExtensionManifest manifest = new ExtensionManifest(
                "vcode.test.plugin",
                "test-plugin",
                "1.0.0",
                "Test Plugin",
                "Description",
                "com.test.Plugin"
        );

        VCodePlugin plugin = new VCodePlugin() {
            @Override
            public void initialize(ExtensionContext context) {
                initialized.set(true);
                Disposable cmdDisposable = context.getCommandRegistry().registerCommand(
                        "vcode.test.action", "Test Action", args -> {}
                );
                context.addDisposable(() -> {
                    cmdDisposable.dispose();
                    commandDisposed.set(true);
                });
            }

            @Override
            public void deactivate() {
                deactivated.set(true);
            }
        };

        host.activatePlugin(manifest, plugin, new File("/workspace"));

        assertTrue(initialized.get());
        assertTrue(host.isExtensionActive("vcode.test.plugin"));
        assertEquals(1, host.getActiveExtensions().size());
        assertTrue(CommandRegistry.getInstance().hasCommand("vcode.test.action"));

        host.unloadExtension("vcode.test.plugin");

        assertTrue(deactivated.get());
        assertTrue(commandDisposed.get());
        assertFalse(host.isExtensionActive("vcode.test.plugin"));
        assertEquals(0, host.getActiveExtensions().size());
        assertFalse(CommandRegistry.getInstance().hasCommand("vcode.test.action"));
    }

    @Test
    public void testShutdownCleansAllExtensions() {
        ExtensionManifest m1 = new ExtensionManifest("ext1", "e1", "1.0", null, null, null);
        ExtensionManifest m2 = new ExtensionManifest("ext2", "e2", "1.0", null, null, null);

        AtomicBoolean m1Deactivated = new AtomicBoolean(false);
        AtomicBoolean m2Deactivated = new AtomicBoolean(false);

        host.activatePlugin(m1, new VCodePlugin() {
            @Override
            public void initialize(ExtensionContext context) {}
            @Override
            public void deactivate() { m1Deactivated.set(true); }
        }, null);

        host.activatePlugin(m2, new VCodePlugin() {
            @Override
            public void initialize(ExtensionContext context) {}
            @Override
            public void deactivate() { m2Deactivated.set(true); }
        }, null);

        assertEquals(2, host.getActiveExtensions().size());
        host.shutdown();

        assertEquals(0, host.getActiveExtensions().size());
        assertTrue(m1Deactivated.get());
        assertTrue(m2Deactivated.get());
    }
}
