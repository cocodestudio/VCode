package com.cocode.vcode.ide.views;

import com.cocode.vcode.ide.core.editor.text.SpanPool;
import com.cocode.vcode.ide.views.span.SyntaxHighlightSpan;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SpanPoolAndDecorationTest {

    @Test
    public void testSpanPoolRecyclingAndReconfiguration() {
        SpanPool pool = new SpanPool(10);
        assertEquals(0, pool.size());

        SyntaxHighlightSpan span1 = pool.obtain(0xFF0000, true);
        assertNotNull(span1);
        assertEquals(0xFF0000, span1.getForegroundColor());
        assertTrue(span1.isUnderline());

        // Recycle span back into pool
        pool.recycle(span1);
        assertEquals(1, pool.size());

        // Next obtain should reuse the same instance with new configuration
        SyntaxHighlightSpan span2 = pool.obtain(0x00FF00, false);
        assertSame(span1, span2);
        assertEquals(0x00FF00, span2.getForegroundColor());
        assertFalse(span2.isUnderline());
        assertEquals(0, pool.size());
    }

    @Test
    public void testTextDecorationManagerFiltersAndRanges() {
        TextDecorationManager manager = new TextDecorationManager();
        AtomicInteger changeNotifications = new AtomicInteger(0);
        manager.addListener(changeNotifications::incrementAndGet);

        TextDecoration squiggly1 = TextDecoration.builder(TextDecoration.Type.SQUIGGLY_UNDERLINE)
                .id("sq-1")
                .range(10, 0, 10, 15)
                .color(0xFFFF0000)
                .tag("lint")
                .build();

        TextDecoration bg1 = TextDecoration.builder(TextDecoration.Type.BACKGROUND_HIGHLIGHT)
                .id("bg-1")
                .range(8, 2, 12, 10)
                .color(0x3300FF00)
                .tag("search")
                .build();

        manager.addDecorations(Arrays.asList(squiggly1, bg1));
        assertEquals(1, changeNotifications.get());
        assertEquals(2, manager.getDecorations().size());

        // Range querying
        List<TextDecoration> inRange = manager.getDecorationsInRange(9, 11);
        assertEquals(2, inRange.size());

        List<TextDecoration> squiggliesOnly = manager.getDecorationsInRangeAndType(
                9, 11, TextDecoration.Type.SQUIGGLY_UNDERLINE);
        assertEquals(1, squiggliesOnly.size());
        assertEquals("sq-1", squiggliesOnly.get(0).getId());

        // Filter removals
        manager.removeDecorationsByTag("lint");
        assertEquals(1, manager.getDecorations().size());
        assertEquals("bg-1", manager.getDecorations().get(0).getId());

        manager.clear();
        assertEquals(0, manager.getDecorations().size());
    }
}
