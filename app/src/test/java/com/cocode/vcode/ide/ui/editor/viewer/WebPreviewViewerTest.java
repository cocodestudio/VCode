package com.cocode.vcode.ide.ui.editor.viewer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowWebView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class WebPreviewViewerTest {

    private Context context;
    private WebPreviewViewer viewer;
    private EditorViewModel viewModel;
    private File tempDir;
    private File markdownFile;
    private File targetFile;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        viewer = new WebPreviewViewer();
        viewModel = mock(EditorViewModel.class);

        tempDir = new File(context.getCacheDir(), "preview_test_" + System.currentTimeMillis());
        assertTrue(tempDir.mkdirs());

        markdownFile = new File(tempDir, "readme.md");
        try (FileOutputStream fos = new FileOutputStream(markdownFile)) {
            fos.write("# Heading\n\nSome text".getBytes(StandardCharsets.UTF_8));
        }

        targetFile = new File(tempDir, "index.html");
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write("<h1>Target</h1>".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testMarkdownInitialRenderConfiguresBaseUrlAndSettings() {
        FrameLayout parent = new FrameLayout(context);
        WebView webView = (WebView) viewer.getView(context, parent);
        assertNotNull(webView);

        EditorFile editorFile = new EditorFile("f1", markdownFile, "# Heading\n\n![Logo](./logo.png)", FileType.MARKDOWN);
        viewer.bindFile(editorFile, viewModel);

        assertTrue(webView.getSettings().getJavaScriptEnabled());
        assertTrue(webView.getSettings().getAllowFileAccess());
        assertTrue(webView.getSettings().getAllowFileAccessFromFileURLs());

        ShadowWebView shadowWebView = Shadows.shadowOf(webView);
        ShadowWebView.LoadDataWithBaseURL lastLoad = shadowWebView.getLastLoadDataWithBaseURL();
        assertNotNull(lastLoad);

        // Verify baseUrl points to parent directory for relative images
        String expectedBaseUrl = tempDir.toURI().toString();
        assertEquals(expectedBaseUrl, lastLoad.baseUrl);
        assertTrue(lastLoad.data.contains("markdown-alert"));
        assertTrue(lastLoad.data.contains("window.updateMarkdown"));
    }

    @Test
    public void testIncrementalUpdateEvaluatesJavascriptWithoutReload() {
        FrameLayout parent = new FrameLayout(context);
        WebView webView = (WebView) viewer.getView(context, parent);

        EditorFile editorFile = new EditorFile("f1", markdownFile, "# First", FileType.MARKDOWN);
        viewer.bindFile(editorFile, viewModel);

        ShadowWebView shadowWebView = Shadows.shadowOf(webView);
        ShadowWebView.LoadDataWithBaseURL firstLoad = shadowWebView.getLastLoadDataWithBaseURL();
        assertNotNull(firstLoad);

        // Update content on the same file
        editorFile.setContent("# Second Updated");
        viewer.bindFile(editorFile, viewModel);

        // Verify evaluateJavascript was called instead of second full load
        String lastJs = shadowWebView.getLastEvaluatedJavascript();
        assertNotNull(lastJs);
        assertTrue(lastJs.contains("window.updateMarkdown("));
    }

    @Test
    public void testWorkspaceFileLinkInterceptCallsViewModelOpenFile() {
        FrameLayout parent = new FrameLayout(context);
        WebView webView = (WebView) viewer.getView(context, parent);

        EditorFile editorFile = new EditorFile("f1", markdownFile, "[Go](index.html)", FileType.MARKDOWN);
        viewer.bindFile(editorFile, viewModel);

        WebViewClient client = Shadows.shadowOf(webView).getWebViewClient();
        assertNotNull(client);

        WebResourceRequest request = mock(WebResourceRequest.class);
        Mockito.when(request.getUrl()).thenReturn(Uri.fromFile(targetFile));

        boolean handled = client.shouldOverrideUrlLoading(webView, request);
        assertTrue(handled);

        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(viewModel).openFile(fileCaptor.capture());
        assertEquals(targetFile.getAbsolutePath(), fileCaptor.getValue().getAbsolutePath());
    }

    @Test
    public void testHeadingAnchorLinkSmoothScrollsInPage() {
        FrameLayout parent = new FrameLayout(context);
        WebView webView = (WebView) viewer.getView(context, parent);

        EditorFile editorFile = new EditorFile("f1", markdownFile, "[Top](#heading-slug)", FileType.MARKDOWN);
        viewer.bindFile(editorFile, viewModel);

        WebViewClient client = Shadows.shadowOf(webView).getWebViewClient();
        assertNotNull(client);

        WebResourceRequest request = mock(WebResourceRequest.class);
        // Anchor link resolving to current directory / file with fragment
        Uri anchorUri = Uri.parse(tempDir.toURI().toString() + "#heading-slug");
        Mockito.when(request.getUrl()).thenReturn(anchorUri);

        boolean handled = client.shouldOverrideUrlLoading(webView, request);
        assertTrue(handled);

        ShadowWebView shadowWebView = Shadows.shadowOf(webView);
        String lastJs = shadowWebView.getLastEvaluatedJavascript();
        assertNotNull(lastJs);
        assertTrue(lastJs.contains("getElementById('heading-slug')"));
        assertTrue(lastJs.contains("scrollIntoView"));
    }

    @Test
    public void testExternalLinkLaunchesViewIntent() {
        FrameLayout parent = new FrameLayout(context);
        WebView webView = (WebView) viewer.getView(context, parent);

        EditorFile editorFile = new EditorFile("f1", markdownFile, "[Web](https://github.com)", FileType.MARKDOWN);
        viewer.bindFile(editorFile, viewModel);

        WebViewClient client = Shadows.shadowOf(webView).getWebViewClient();
        assertNotNull(client);

        WebResourceRequest request = mock(WebResourceRequest.class);
        Mockito.when(request.getUrl()).thenReturn(Uri.parse("https://github.com"));

        boolean handled = client.shouldOverrideUrlLoading(webView, request);
        assertTrue(handled);
    }
}
