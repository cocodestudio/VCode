package com.cocode.vcode.ide.core.events;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IdeEventBusTest {

    private IdeEventBus eventBus;

    @Before
    public void setUp() {
        eventBus = IdeEventBus.getInstance();
        eventBus.clearAllSubscriptions();
    }

    @Test
    public void testSubscribeAndPublishSynchronous() {
        AtomicInteger callCount = new AtomicInteger(0);
        List<DocumentEvent.DocumentOpenEvent> received = new ArrayList<>();

        File testFile = new File("/test/workspace/index.js");
        Disposable disposable = eventBus.subscribe(DocumentEvent.DocumentOpenEvent.class, IdeEventBus.ThreadMode.POSTING, event -> {
            callCount.incrementAndGet();
            received.add(event);
        });

        assertNotNull(disposable);

        eventBus.publish(new DocumentEvent.DocumentOpenEvent(testFile, "file:///test/workspace/index.js"));

        assertEquals(1, callCount.get());
        assertEquals(1, received.size());
        assertEquals(testFile, received.get(0).getFile());
        assertEquals("file:///test/workspace/index.js", received.get(0).getUri());

        // Test unsubscribe via disposable
        disposable.dispose();
        eventBus.publish(new DocumentEvent.DocumentOpenEvent(testFile, "updated"));
        assertEquals(1, callCount.get());
    }

    @Test
    public void testEventInheritancePolymorphism() {
        AtomicInteger baseEventCount = new AtomicInteger(0);
        AtomicInteger specificEventCount = new AtomicInteger(0);

        eventBus.subscribe(DocumentEvent.class, IdeEventBus.ThreadMode.POSTING, event -> baseEventCount.incrementAndGet());
        eventBus.subscribe(DocumentEvent.DocumentSaveEvent.class, IdeEventBus.ThreadMode.POSTING, event -> specificEventCount.incrementAndGet());

        File testFile = new File("/test/workspace/style.css");
        eventBus.publish(new DocumentEvent.DocumentSaveEvent(testFile));

        assertEquals(1, specificEventCount.get());
        assertEquals(1, baseEventCount.get());
    }

    @Test
    public void testProjectEvents() {
        List<ProjectEvent.DependencyChangedEvent> depEvents = new ArrayList<>();
        eventBus.subscribe(ProjectEvent.DependencyChangedEvent.class, IdeEventBus.ThreadMode.POSTING, depEvents::add);

        File root = new File("/test/project");
        eventBus.publish(new ProjectEvent.DependencyChangedEvent(root, "lodash",
                ProjectEvent.DependencyChangedEvent.Action.ADDED));

        assertEquals(1, depEvents.size());
        assertEquals("lodash", depEvents.get(0).getPackageName());
        assertEquals(ProjectEvent.DependencyChangedEvent.Action.ADDED, depEvents.get(0).getAction());
    }
}
