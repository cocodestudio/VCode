package com.cocode.vcode.ide.views;

import android.content.Context;
import android.view.View;

import com.cocode.vcode.ide.core.lsp.HoverInfo;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class DocumentationPopupTest {

    private Context context;
    private DocumentationPopup popup;
    private View anchorView;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        popup = new DocumentationPopup(context);
        anchorView = new View(context);
    }

    @Test
    public void testExtractMethodName() {
        assertEquals("getElementById", DocumentationPopup.extractMethodName("document.getElementById"));
        assertEquals("addEventListener", DocumentationPopup.extractMethodName("document.getElementById(\"btn\").addEventListener"));
        assertEquals("map", DocumentationPopup.extractMethodName("items.filter(x => x).map"));
        assertEquals("log", DocumentationPopup.extractMethodName("console.log"));
        assertEquals("doSomething", DocumentationPopup.extractMethodName("a.b.c.doSomething"));
        assertEquals("Student", DocumentationPopup.extractMethodName("new com.example.Student"));
        assertEquals("Map", DocumentationPopup.extractMethodName("new Map<string, number>"));
        assertEquals("bar", DocumentationPopup.extractMethodName("foo?.bar"));
        assertEquals("addEventListener", DocumentationPopup.extractMethodName(".addEventListener"));
        assertEquals("getName", DocumentationPopup.extractMethodName("student.getName"));
        assertEquals("fetch", DocumentationPopup.extractMethodName("fetch"));
        assertEquals("super", DocumentationPopup.extractMethodName("super"));
        assertEquals("", DocumentationPopup.extractMethodName(""));
        assertEquals("", DocumentationPopup.extractMethodName(null));
    }

    @Test
    public void testInitialState() {
        assertNotNull(popup);
        assertFalse(popup.isShowing());
        assertFalse(popup.isSignatureMode());
        assertFalse(popup.isHoverMode());
        assertEquals(DocumentationPopup.PopupMode.NONE, popup.getCurrentMode());
    }

    @Test
    public void testSignatureModeTransition() {
        java.util.List<LspSignatureHelp.LspParameterInformation> params = new java.util.ArrayList<>();
        params.add(new LspSignatureHelp.LspParameterInformation("a", "first operand"));
        params.add(new LspSignatureHelp.LspParameterInformation("b", "second operand"));

        LspSignatureHelp.LspSignatureInformation sig = new LspSignatureHelp.LspSignatureInformation(
                "calculate(a, b)",
                "Performs arithmetic calculation",
                params
        );

        LspSignatureHelp help = new LspSignatureHelp(
                java.util.Collections.singletonList(sig),
                0,
                1
        );

        popup.showSignature(help, anchorView, 0);

        assertTrue(popup.isShowing());
        assertTrue(popup.isSignatureMode());
        assertFalse(popup.isHoverMode());
        assertEquals(DocumentationPopup.PopupMode.SIGNATURE, popup.getCurrentMode());

        popup.dismiss();
        assertFalse(popup.isShowing());
        assertFalse(popup.isSignatureMode());
        assertEquals(DocumentationPopup.PopupMode.NONE, popup.getCurrentMode());
    }

    @Test
    public void testHoverModeTransition() {
        String markdown = "```javascript\nfunction greet(name: string): string\n```\n\nGreets someone.\n\n**Parameters:**\n• `name` — User name";
        HoverInfo info = new HoverInfo(markdown);

        popup.showHover(info, anchorView, 50, 100);

        assertTrue(popup.isShowing());
        assertTrue(popup.isHoverMode());
        assertFalse(popup.isSignatureMode());
        assertEquals(DocumentationPopup.PopupMode.HOVER, popup.getCurrentMode());

        popup.dismiss();
        assertFalse(popup.isShowing());
        assertFalse(popup.isHoverMode());
        assertEquals(DocumentationPopup.PopupMode.NONE, popup.getCurrentMode());
    }

    @Test
    public void testAdaptersShareDocumentationPopup() {
        SignatureHintPopup sigAdapter = new SignatureHintPopup(popup);
        HoverDocPopup hoverAdapter = new HoverDocPopup(popup);

        assertEquals(popup, sigAdapter.getDelegate());
        assertEquals(popup, hoverAdapter.getDelegate());

        String markdown = "### Test Symbol\nDescription of symbol";
        HoverInfo info = new HoverInfo(markdown);

        hoverAdapter.show(info, anchorView, 50, 100);
        assertTrue(hoverAdapter.isShowing());
        assertFalse(sigAdapter.isShowing());
        assertTrue(popup.isHoverMode());

        hoverAdapter.dismiss();
        assertFalse(hoverAdapter.isShowing());
        assertFalse(popup.isShowing());
    }
}
