package com.cocode.vcode.ide.core.autocomplete;

import com.cocode.vcode.ide.core.events.IdeEventBus;
import com.cocode.vcode.ide.core.events.ProjectEvent;
import com.cocode.vcode.ide.core.model.CompletionItem;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PartitionedSymbolIndexTest {

    private ProjectSymbolIndex symbolIndex;

    @Before
    public void setUp() {
        symbolIndex = ProjectSymbolIndex.getInstance();
        symbolIndex.clearInMemorySymbols();
    }

    @Test
    public void testScopedSymbolsPartitioning() {
        CompletionItem projItem = new CompletionItem("myProjectHelper", "myProjectHelper()", "function", "Project");
        CompletionItem libItem = new CompletionItem("axiosGet", "get()", "method", "Axios");
        CompletionItem extItem = new CompletionItem("extensionCmd", "cmd", "command", "Extension");

        symbolIndex.putScopedSymbols(SymbolScope.PROJECT, "helpers", Collections.singletonList(projItem));
        symbolIndex.putScopedSymbols(SymbolScope.LIBRARIES, "axios", Collections.singletonList(libItem));
        symbolIndex.putScopedSymbols(SymbolScope.EXTENSIONS, "tools", Collections.singletonList(extItem));

        assertEquals(1, symbolIndex.getScopedSymbols(SymbolScope.PROJECT, "helpers").size());
        assertEquals(1, symbolIndex.getScopedSymbols(SymbolScope.LIBRARIES, "axios").size());
        assertEquals(1, symbolIndex.getScopedSymbols(SymbolScope.EXTENSIONS, "tools").size());

        // Invalidate only LIBRARIES scope
        symbolIndex.invalidateScope(SymbolScope.LIBRARIES);

        assertTrue("LIBRARIES scope symbols must be cleared", symbolIndex.getScopedSymbols(SymbolScope.LIBRARIES, "axios").isEmpty());
        assertFalse("PROJECT scope symbols must remain intact", symbolIndex.getScopedSymbols(SymbolScope.PROJECT, "helpers").isEmpty());
        assertFalse("EXTENSIONS scope symbols must remain intact", symbolIndex.getScopedSymbols(SymbolScope.EXTENSIONS, "tools").isEmpty());
    }

    @Test
    public void testEventBusDependencyChangedTriggersLibraryScopeInvalidation() {
        CompletionItem libItem = new CompletionItem("lodashMap", "map()", "method", "Lodash");
        symbolIndex.putScopedSymbols(SymbolScope.LIBRARIES, "lodash", Collections.singletonList(libItem));
        assertEquals(1, symbolIndex.getScopedSymbols(SymbolScope.LIBRARIES, "lodash").size());

        // Publish dependency event via IdeEventBus
        IdeEventBus.getInstance().publish(new ProjectEvent.DependencyChangedEvent(
                new File("/test/project"), "lodash", ProjectEvent.DependencyChangedEvent.Action.REMOVED
        ));

        assertTrue("Publishing DependencyChangedEvent must invalidate LIBRARIES scope automatically",
                symbolIndex.getScopedSymbols(SymbolScope.LIBRARIES, "lodash").isEmpty());
    }
}
