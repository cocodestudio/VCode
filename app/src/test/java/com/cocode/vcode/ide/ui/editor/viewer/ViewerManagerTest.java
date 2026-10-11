package com.cocode.vcode.ide.ui.editor.viewer;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.cocode.vcode.ide.core.events.Disposable;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.views.CodeEditText;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ViewerManagerTest {

    private ViewerManager manager;

    @Before
    public void setUp() {
        manager = new ViewerManager();
    }

    @After
    public void tearDown() {
        manager.destroyAll();
    }

    private static class MockCustomViewer implements IFileViewer {
        private final String name;

        public MockCustomViewer(String name) {
            this.name = name;
        }

        @Override
        public View getView(Context context, ViewGroup parent) {
            return null;
        }

        @Override
        public void bindFile(EditorFile file, EditorViewModel viewModel) {}

        @Override
        public void onResume() {}

        @Override
        public void onPause() {}

        @Override
        public void destroy() {}

        @Override
        public CodeEditText getCodeEditor() {
            return null;
        }
    }

    @Test
    public void testInstanceViewerRegistrationAndMatching() {
        EditorFile fontFile = new EditorFile("file_font", new File("/test/font.ttf"), "", FileType.FONT);

        Disposable disposable = manager.registerViewer(
                (file, isPreview) -> "font.ttf".equals(file.getFileName()),
                (ctx, file, isPreview) -> new MockCustomViewer("CustomFontViewer")
        );

        IFileViewer viewer = manager.getOrCreateViewer(null, fontFile, false);
        assertNotNull(viewer);
        assertTrue(viewer instanceof MockCustomViewer);
        assertEquals("CustomFontViewer", ((MockCustomViewer) viewer).name);

        // After disposing the registration, new files should fallback to default built-in viewer
        disposable.dispose();
        manager.destroyViewer(fontFile.getId());

        IFileViewer fallbackViewer = manager.getOrCreateViewer(null, fontFile, false);
        assertNotNull(fallbackViewer);
        assertTrue(fallbackViewer instanceof FontFileViewer);
    }

    @Test
    public void testGlobalViewerRegistrationAndDisposal() {
        EditorFile fontFile = new EditorFile("global_font", new File("/test/custom.ttf"), "", FileType.FONT);

        Disposable globalDisposable = ViewerManager.registerGlobalViewer(
                (file, isPreview) -> file.getFileName().endsWith(".ttf"),
                (ctx, file, isPreview) -> new MockCustomViewer("GlobalFontViewer")
        );

        ViewerManager freshManager = new ViewerManager();
        IFileViewer viewer = freshManager.getOrCreateViewer(null, fontFile, false);
        assertNotNull(viewer);
        assertTrue(viewer instanceof MockCustomViewer);
        assertEquals("GlobalFontViewer", ((MockCustomViewer) viewer).name);

        globalDisposable.dispose();
        freshManager.destroyViewer(fontFile.getId());

        IFileViewer fallback = freshManager.getOrCreateViewer(null, fontFile, false);
        assertNotNull(fallback);
        assertTrue(fallback instanceof FontFileViewer);
    }

    @Test
    public void testMarkdownPreviewModeReturnsWebPreviewViewer() {
        EditorFile mdFile = new EditorFile("md_1", new File("/test/README.md"), "# Hello", FileType.MARKDOWN);

        IFileViewer previewViewer = manager.getOrCreateViewer(null, mdFile, true);
        assertNotNull(previewViewer);
        assertTrue("Preview mode should return WebPreviewViewer", previewViewer instanceof WebPreviewViewer);

        previewViewer.bindFile(mdFile, null);
        assertEquals("WebPreviewViewer must track currentFile after bindFile", mdFile, previewViewer.getCurrentFile());

        previewViewer.destroy();
        org.junit.Assert.assertNull("WebPreviewViewer must clear currentFile after destroy", previewViewer.getCurrentFile());
    }
}
