package com.cocode.vcode.ide.views;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class SelectionToolbarTest {

    private Context context;
    private CodeEditText editor;
    private SelectionToolbar toolbar;
    private ClipboardManager clipboardManager;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight);

        clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("test", "clipboard content"));
        }

        editor = new CodeEditText(context);
        toolbar = new SelectionToolbar(context);
        toolbar.bindEditor(editor);
    }

    @Test
    public void testHasSelectionAndIsAllSelected() {
        editor.setText("const x = 42;\nconst y = 100;");

        // No selection
        assertFalse(editor.hasSelection());
        assertFalse(editor.isAllSelected());

        // Partial selection
        editor.setSelection(0, 5);
        assertTrue(editor.hasSelection());
        assertFalse(editor.isAllSelected());

        // Full selection
        editor.selectAll();
        assertTrue(editor.hasSelection());
        assertTrue(editor.isAllSelected());
    }

    @Test
    public void testToolbarOptionsForPartialSelection() {
        editor.setText("hello world");
        editor.setSelection(0, 5);

        toolbar.show();
        assertTrue(toolbar.isVisible());

        View btnCut = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnCut);
        View btnCopy = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnCopy);
        View btnPaste = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnPaste);
        View btnSelectAll = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnSelectAll);

        assertEquals(View.VISIBLE, btnCut.getVisibility());
        assertEquals(View.VISIBLE, btnCopy.getVisibility());
        assertEquals(View.VISIBLE, btnPaste.getVisibility());
        assertEquals(View.VISIBLE, btnSelectAll.getVisibility());
    }

    @Test
    public void testToolbarOptionsForFullSelectionHidesSelectAll() {
        editor.setText("hello world");
        editor.selectAll();

        toolbar.show();
        assertTrue(toolbar.isVisible());

        View btnCut = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnCut);
        View btnCopy = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnCopy);
        View btnPaste = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnPaste);
        View btnSelectAll = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnSelectAll);

        assertEquals(View.VISIBLE, btnCut.getVisibility());
        assertEquals(View.VISIBLE, btnCopy.getVisibility());
        assertEquals(View.VISIBLE, btnPaste.getVisibility());
        assertEquals(View.GONE, btnSelectAll.getVisibility());
    }

    @Test
    public void testToolbarOptionsForEmptyLineCursorMode() {
        editor.setText("line 1\n\nline 3");
        editor.setSelection(7);

        assertFalse(editor.hasSelection());

        toolbar.show();
        assertTrue(toolbar.isVisible());

        View btnCut = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnCut);
        View btnCopy = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnCopy);
        View btnPaste = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnPaste);
        View btnSelectAll = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnSelectAll);

        assertEquals(View.GONE, btnCut.getVisibility());
        assertEquals(View.GONE, btnCopy.getVisibility());
        assertEquals(View.VISIBLE, btnPaste.getVisibility());
        assertEquals(View.VISIBLE, btnSelectAll.getVisibility());
    }

    @Test
    public void testSelectAllButtonDoesNotDismissToolbar() {
        editor.setText("const alpha = 'beta';");
        editor.setSelection(0, 5);

        toolbar.show();
        assertTrue(toolbar.isVisible());

        View btnSelectAll = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnSelectAll);
        assertEquals(View.VISIBLE, btnSelectAll.getVisibility());

        btnSelectAll.performClick();

        assertTrue("Toolbar should stay visible after tapping Select All", toolbar.isVisible());
        assertTrue(editor.isAllSelected());
        assertEquals(View.GONE, btnSelectAll.getVisibility());

        View btnCut = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnCut);
        View btnCopy = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnCopy);
        assertEquals(View.VISIBLE, btnCut.getVisibility());
        assertEquals(View.VISIBLE, btnCopy.getVisibility());
    }
}