package com.cocode.vcode.ide.views;

import android.content.Context;
import android.view.View;
import android.widget.PopupWindow;

import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.google.android.material.card.MaterialCardView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class CustomPopupMenuTest {

    private Context context;
    private View anchor;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight);
        anchor = new View(context);
    }

    @Test
    public void testDefaultStylingHasSurfaceBackgroundAndZeroCardElevation() {
        CustomPopupMenu popup = new CustomPopupMenu(context, anchor);
        PopupWindow popupWindow = popup.getPopupWindow();
        assertNotNull(popupWindow);

        View contentView = popupWindow.getContentView();
        assertTrue(contentView instanceof MaterialCardView);
        MaterialCardView card = (MaterialCardView) contentView;

        assertEquals(0f, card.getCardElevation(), 0.01f);

        int expectedColor = ContextCompat.getColor(context, R.color.vcode_bg_surface);
        assertNotNull(card.getCardBackgroundColor());
        assertEquals(expectedColor, card.getCardBackgroundColor().getDefaultColor());
        assertEquals(PopupWindow.INPUT_METHOD_NEEDED, popupWindow.getInputMethodMode());
    }

    @Test
    public void testAddItemAndActionExecution() {
        CustomPopupMenu popup = new CustomPopupMenu(context, anchor);
        AtomicBoolean clicked = new AtomicBoolean(false);

        popup.addItem(R.drawable.ic_lightbulb, "Fix Issue", () -> clicked.set(true));

        PopupWindow popupWindow = popup.getPopupWindow();
        View contentView = popupWindow.getContentView();
        android.view.ViewGroup container = contentView.findViewById(R.id.popup_container);
        assertNotNull(container);
        assertEquals(1, container.getChildCount());

        View itemRow = container.getChildAt(0);
        android.widget.TextView tvTitle = itemRow.findViewById(R.id.tv_title);
        assertNotNull(tvTitle);
        assertEquals(ContextCompat.getColor(context, R.color.vcode_text_primary), tvTitle.getCurrentTextColor());
        itemRow.performClick();
        assertTrue(clicked.get());
    }

    @Test
    public void testCustomTextColorOverridesItemTextColor() {
        CustomPopupMenu popup = new CustomPopupMenu(context, anchor);
        int customColor = android.graphics.Color.RED;
        popup.setTextColor(customColor);
        popup.addItem(R.drawable.ic_lightbulb, "Title", null);

        View contentView = popup.getPopupWindow().getContentView();
        android.view.ViewGroup container = contentView.findViewById(R.id.popup_container);
        View itemRow = container.getChildAt(0);
        android.widget.TextView tvTitle = itemRow.findViewById(R.id.tv_title);
        assertNotNull(tvTitle);
        assertEquals(customColor, tvTitle.getCurrentTextColor());
    }

    @Test
    public void testAddDividerInsertsDividerView() {
        CustomPopupMenu popup = new CustomPopupMenu(context, anchor);
        popup.addItem(R.drawable.ic_copy, "Item 1", null);
        popup.addDivider();
        popup.addItem(R.drawable.ic_scissors, "Item 2", null);

        PopupWindow popupWindow = popup.getPopupWindow();
        View contentView = popupWindow.getContentView();
        android.view.ViewGroup container = contentView.findViewById(R.id.popup_container);
        assertNotNull(container);
        assertEquals(3, container.getChildCount());
    }
}
