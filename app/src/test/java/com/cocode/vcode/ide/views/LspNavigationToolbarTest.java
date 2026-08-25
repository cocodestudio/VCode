package com.cocode.vcode.ide.views;

import android.content.Context;
import android.widget.PopupWindow;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import com.cocode.vcode.ide.core.lsp.LspCallback;
import com.cocode.vcode.ide.core.lsp.LspEditorBridge;
import com.cocode.vcode.ide.core.lsp.LspLocation;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class LspNavigationToolbarTest {

    private LspNavigationToolbar toolbar;
    private Context context;

    @Mock
    private CodeEditText mockEditor;
    @Mock
    private LspEditorBridge mockBridge;
    @Mock
    private LspNavigationToolbar.NavigationListener mockListener;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        context = RuntimeEnvironment.getApplication();
        context.setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight);

        toolbar = new LspNavigationToolbar(context);
        toolbar.bindEditor(mockEditor);
        toolbar.bindBridge(mockBridge);
        toolbar.setNavigationListener(mockListener);

        when(mockEditor.length()).thenReturn(100);
        when(mockEditor.getSelectionStart()).thenReturn(50);
        when(mockEditor.getSelectionEnd()).thenReturn(50);
        
        // Mock getCursorScreenCoords returning some arbitrary coordinates to avoid NPE in updatePosition
        when(mockEditor.getCursorScreenCoords(anyInt())).thenReturn(new int[]{100, 100, 120});
    }

    @Test
    public void testOnCursorIdle_LspNotActive_HidesToolbar() {
        when(mockBridge.isLspActive()).thenReturn(false);
        toolbar.onCursorIdle(50);
        assertFalse(toolbar.isVisible());
        verify(mockBridge, never()).requestDefinition(any());
    }

    @Test
    public void testOnCursorIdle_OutOfBounds_HidesToolbar() {
        when(mockBridge.isLspActive()).thenReturn(true);
        toolbar.onCursorIdle(-1);
        assertFalse(toolbar.isVisible());
        verify(mockBridge, never()).requestDefinition(any());
        
        toolbar.onCursorIdle(150); // > 100
        assertFalse(toolbar.isVisible());
        verify(mockBridge, never()).requestDefinition(any());
    }

    @Test
    public void testOnCursorIdle_NothingFound_HidesToolbar() {
        when(mockBridge.isLspActive()).thenReturn(true);
        
        toolbar.onCursorIdle(50);
        
        ArgumentCaptor<LspCallback> defCaptor = ArgumentCaptor.forClass(LspCallback.class);
        ArgumentCaptor<LspCallback> refCaptor = ArgumentCaptor.forClass(LspCallback.class);
        
        verify(mockBridge).requestDefinition(defCaptor.capture());
        verify(mockBridge).requestReferences(refCaptor.capture());
        
        // Simulate both returning null/empty
        defCaptor.getValue().onResult(null);
        refCaptor.getValue().onResult(new ArrayList<>());
        
        assertFalse("Toolbar should be hidden if no defs or refs are found", toolbar.isVisible());
    }

    @Test
    public void testStaleCache_GenerationMismatch_IgnoresResult() {
        when(mockBridge.isLspActive()).thenReturn(true);
        
        // First probe
        toolbar.onCursorIdle(50);
        ArgumentCaptor<LspCallback> defCaptor1 = ArgumentCaptor.forClass(LspCallback.class);
        verify(mockBridge).requestDefinition(defCaptor1.capture());
        
        // User moves cursor -> hide increments generation
        toolbar.hide();
        
        // Second probe
        toolbar.onCursorIdle(60);
        ArgumentCaptor<LspCallback> defCaptor2 = ArgumentCaptor.forClass(LspCallback.class);
        verify(mockBridge, times(2)).requestDefinition(defCaptor2.capture());
        
        // First probe callback returns late
        LspLocation staleLocation = new LspLocation("file:///test", 0, 0, 0, 0);
        defCaptor1.getValue().onResult(staleLocation);
        
        // Toolbar should NOT become visible from stale callback
        assertFalse(toolbar.isVisible());
    }
}
