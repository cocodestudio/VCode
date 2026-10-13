package com.cocode.vcode.ide.views;

import android.content.Context;
import android.view.View;

import com.cocode.vcode.ide.core.lsp.HoverInfo;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

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

    @Test
    public void testHighlightDeclarationCodeFunction() {
        SpannableStringBuilder sb = popup.highlightDeclarationCode("function myFunc(a: string, b: number): boolean");
        assertNotNull(sb);
        assertEquals("function myFunc(a: string, b: number): boolean", sb.toString());

        ForegroundColorSpan[] colorSpans = sb.getSpans(0, sb.length(), ForegroundColorSpan.class);
        assertTrue("Should have multiple foreground color spans", colorSpans.length >= 5);

        StyleSpan[] styleSpans = sb.getSpans(0, sb.length(), StyleSpan.class);
        assertTrue("Function name should have style span", styleSpans.length >= 1);
    }

    @Test
    public void testHighlightDeclarationCodeMethod() {
        SpannableStringBuilder sb = popup.highlightDeclarationCode("(method) Console.log(...data: any[]): void");
        assertNotNull(sb);
        assertEquals("(method) Console.log(...data: any[]): void", sb.toString());

        ForegroundColorSpan[] colorSpans = sb.getSpans(0, sb.length(), ForegroundColorSpan.class);
        assertTrue("Method declaration should have color spans", colorSpans.length >= 6);

        StyleSpan[] styleSpans = sb.getSpans(0, sb.length(), StyleSpan.class);
        assertTrue("Method name log should have bold style span", styleSpans.length >= 1);
    }

    @Test
    public void testHighlightDeclarationCodeVariable() {
        SpannableStringBuilder sb1 = popup.highlightDeclarationCode("const myVar: string");
        assertNotNull(sb1);
        assertEquals("const myVar: string", sb1.toString());
        assertTrue(sb1.getSpans(0, sb1.length(), ForegroundColorSpan.class).length >= 3);

        SpannableStringBuilder sb2 = popup.highlightDeclarationCode("(variable) name: string");
        assertNotNull(sb2);
        assertEquals("(variable) name: string", sb2.toString());
        assertTrue(sb2.getSpans(0, sb2.length(), ForegroundColorSpan.class).length >= 3);
    }

    @Test
    public void testHighlightDeclarationCodeClassAndInterface() {
        SpannableStringBuilder sb1 = popup.highlightDeclarationCode("class MyClass extends BaseClass");
        assertNotNull(sb1);
        assertEquals("class MyClass extends BaseClass", sb1.toString());
        assertTrue(sb1.getSpans(0, sb1.length(), ForegroundColorSpan.class).length >= 4);

        SpannableStringBuilder sb2 = popup.highlightDeclarationCode("interface User<T>");
        assertNotNull(sb2);
        assertEquals("interface User<T>", sb2.toString());
        assertTrue(sb2.getSpans(0, sb2.length(), ForegroundColorSpan.class).length >= 3);
    }

    @Test
    public void testHoverHeaderHasSyntaxHighlighting() {
        String markdown = "```typescript\nfunction greet(name: string): string\n```\n\nGreets someone.";
        HoverInfo info = new HoverInfo(markdown);

        popup.showHover(info, anchorView, 50, 100);
        CharSequence header = popup.getHeaderText();
        assertNotNull(header);
        assertTrue(header instanceof Spanned);

        Spanned spannedHeader = (Spanned) header;
        ForegroundColorSpan[] spans = spannedHeader.getSpans(0, spannedHeader.length(), ForegroundColorSpan.class);
        assertTrue("Hover header must contain syntax highlighting spans", spans.length >= 4);
    }
}
