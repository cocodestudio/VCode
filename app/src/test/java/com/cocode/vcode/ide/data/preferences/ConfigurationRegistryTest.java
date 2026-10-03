package com.cocode.vcode.ide.data.preferences;

import com.cocode.vcode.ide.core.events.ConfigurationEvent;
import com.cocode.vcode.ide.core.events.IdeEventBus;
import com.cocode.vcode.ide.data.model.AppSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConfigurationRegistryTest {

    private ConfigurationRegistry registry;

    @Before
    public void setUp() {
        registry = ConfigurationRegistry.getInstance();
        registry.clear();
        IdeEventBus.getInstance().clearAllSubscriptions();
    }

    @After
    public void tearDown() {
        registry.clear();
        IdeEventBus.getInstance().clearAllSubscriptions();
    }

    @Test
    public void testDynamicRegistrationAndDefaults() {
        assertFalse(registry.hasKey("editor.minimap.enabled"));

        registry.registerKey("editor.minimap.enabled", Boolean.class, false,
                "Show Minimap", "Renders a code overview minimap", "editor");
        registry.registerKey("npm.autoInstall", Boolean.class, true,
                "Auto Install", "Automatically install missing packages", "npm");
        registry.registerKey("editor.fontFamily", String.class, "Fira Code",
                "Font Family", "Custom code font name", "editor");

        assertTrue(registry.hasKey("editor.minimap.enabled"));
        ConfigurationItem item = registry.getItem("editor.minimap.enabled");
        assertNotNull(item);
        assertEquals("Show Minimap", item.getTitle());
        assertEquals("editor", item.getCategory());
        assertEquals(Boolean.class, item.getType());

        assertFalse(registry.getBoolean("editor.minimap.enabled", true));
        assertTrue(registry.getBoolean("npm.autoInstall", false));
        assertEquals("Fira Code", registry.getString("editor.fontFamily", "Monospace"));

        List<ConfigurationItem> editorItems = registry.getItemsByCategory("editor");
        assertEquals(2, editorItems.size());
    }

    @Test
    public void testMutationAndChangeNotifications() {
        registry.registerKey("editor.tabSize", Integer.class, 2, "Tab Size", "Spaces per tab", "editor");
        assertEquals(2, registry.getInt("editor.tabSize", 4));

        AtomicReference<Object> callbackValue = new AtomicReference<>();
        registry.addChangeListener("editor.tabSize", (key, newVal) -> {
            callbackValue.set(newVal);
        });

        AtomicBoolean eventBusReceived = new AtomicBoolean(false);
        IdeEventBus.getInstance().subscribe(ConfigurationEvent.class, event -> {
            if ("editor.tabSize".equals(event.getKey()) && Integer.valueOf(4).equals(event.getNewValue())) {
                eventBusReceived.set(true);
            }
        });

        // Mutate configuration
        registry.set("editor.tabSize", 4);

        assertEquals(4, registry.getInt("editor.tabSize", 2));
        assertEquals(4, callbackValue.get());
        assertTrue(eventBusReceived.get());
    }

    @Test
    public void testAppSettingsDynamicBridge() {
        registry.registerKey("git.autoPrune", Boolean.class, true, "Auto Prune", "Prune remotes on fetch", "git");

        AppSettings settings = new AppSettings();
        assertTrue(settings.getDynamicSetting("git.autoPrune", false));

        settings.setDynamicSetting("git.autoPrune", false);
        assertFalse(settings.getDynamicSetting("git.autoPrune", true));
        assertFalse(registry.getBoolean("git.autoPrune", true));
    }
}
