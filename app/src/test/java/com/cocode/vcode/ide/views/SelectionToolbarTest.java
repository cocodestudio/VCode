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
import static org.junit.Assert.assertNotNull;
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
        View btnMore = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnMore);

        assertEquals(View.VISIBLE, btnCut.getVisibility());
        assertEquals(View.VISIBLE, btnCopy.getVisibility());
        assertEquals(View.VISIBLE, btnPaste.getVisibility());
        assertEquals(View.VISIBLE, btnSelectAll.getVisibility());
        assertEquals(View.VISIBLE, btnMore.getVisibility());
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
        View btnMore = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnMore);

        assertEquals(View.VISIBLE, btnCut.getVisibility());
        assertEquals(View.VISIBLE, btnCopy.getVisibility());
        assertEquals(View.VISIBLE, btnPaste.getVisibility());
        assertEquals(View.GONE, btnSelectAll.getVisibility());
        assertEquals(View.VISIBLE, btnMore.getVisibility());
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
        View btnMore = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnMore);

        assertEquals(View.GONE, btnCut.getVisibility());
        assertEquals(View.GONE, btnCopy.getVisibility());
        assertEquals(View.VISIBLE, btnPaste.getVisibility());
        assertEquals(View.VISIBLE, btnSelectAll.getVisibility());
        assertEquals(View.VISIBLE, btnMore.getVisibility());
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
        View btnMore = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnMore);
        assertEquals(View.VISIBLE, btnCut.getVisibility());
        assertEquals(View.VISIBLE, btnCopy.getVisibility());
        assertEquals(View.VISIBLE, btnMore.getVisibility());
    }

    @Test
    public void testMoreMenuOpensOnClickAndToggles() {
        editor.setText("line 1\nline 2\nline 3");
        editor.setSelection(0, 6);

        toolbar.show();
        assertTrue(toolbar.isVisible());
        assertFalse(toolbar.isMoreMenuShowing());

        View btnMore = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnMore);
        assertEquals(View.VISIBLE, btnMore.getVisibility());

        btnMore.performClick();
        assertTrue("More menu should be showing after click", toolbar.isMoreMenuShowing());
        assertNotNull(toolbar.getMoreMenuPopup());

        btnMore.performClick();
        assertFalse("More menu should be dismissed after second click", toolbar.isMoreMenuShowing());
    }

    @Test
    public void testMoreMenuHidesWhenToolbarHides() {
        editor.setText("hello world");
        editor.setSelection(0, 5);

        toolbar.show();
        View btnMore = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnMore);
        btnMore.performClick();
        assertTrue(toolbar.isMoreMenuShowing());

        toolbar.hide();
        assertFalse(toolbar.isVisible());
        assertFalse(toolbar.isMoreMenuShowing());
    }

    @Test
    public void testMoreMenuCopyLineDownAction() {
        editor.setText("line 1\nline 2");
        editor.setSelection(0);

        toolbar.show();
        View btnMore = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnMore);
        btnMore.performClick();
        assertTrue(toolbar.isMoreMenuShowing());

        android.widget.PopupWindow popup = toolbar.getMoreMenuPopup();
        assertNotNull(popup);
        View popupView = popup.getContentView();
        assertNotNull(popupView);

        android.view.ViewGroup container = popupView.findViewById(com.cocode.vcode.ide.R.id.popup_container);
        assertNotNull(container);
        View copyLineDownItem = container.getChildAt(1);
        assertNotNull(copyLineDownItem);
        copyLineDownItem.performClick();

        assertEquals("line 1\nline 1\nline 2", editor.getText().toString());
        assertFalse(toolbar.isMoreMenuShowing());
        assertFalse(toolbar.isVisible());
    }

    @Test
    public void testMoreMenuToggleCommentAction() {
        editor.setText("const a = 1;");
        editor.setSelection(0);

        toolbar.show();
        View btnMore = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnMore);
        btnMore.performClick();
        assertTrue(toolbar.isMoreMenuShowing());

        android.widget.PopupWindow popup = toolbar.getMoreMenuPopup();
        assertNotNull(popup);
        android.view.ViewGroup container = popup.getContentView().findViewById(com.cocode.vcode.ide.R.id.popup_container);
        assertNotNull(container);

        View commentItem = container.getChildAt(container.getChildCount() - 1);
        assertNotNull(commentItem);
        commentItem.performClick();

        assertEquals("// const a = 1;", editor.getText().toString());
        assertFalse(toolbar.isMoreMenuShowing());
        assertFalse(toolbar.isVisible());
    }

    @Test
    public void testMoreMenuHasSameBackgroundColorAsToolbar() {
        editor.setText("line 1\nline 2");
        editor.setSelection(0);

        toolbar.show();
        View btnMore = toolbar.findViewById(com.cocode.vcode.ide.R.id.btnMore);
        btnMore.performClick();
        assertTrue(toolbar.isMoreMenuShowing());

        android.widget.PopupWindow popup = toolbar.getMoreMenuPopup();
        assertNotNull(popup);
        View popupView = popup.getContentView();
        assertTrue(popupView instanceof com.google.android.material.card.MaterialCardView);
        com.google.android.material.card.MaterialCardView popupCard = (com.google.android.material.card.MaterialCardView) popupView;

        View toolbarCardView = toolbar.findViewById(com.cocode.vcode.ide.R.id.cardToolbar);
        assertTrue(toolbarCardView instanceof com.google.android.material.card.MaterialCardView);
        com.google.android.material.card.MaterialCardView toolbarCard = (com.google.android.material.card.MaterialCardView) toolbarCardView;

        assertNotNull(toolbarCard.getCardBackgroundColor());
        assertNotNull(popupCard.getCardBackgroundColor());
        assertEquals(toolbarCard.getCardBackgroundColor().getDefaultColor(),
                popupCard.getCardBackgroundColor().getDefaultColor());
    }
}