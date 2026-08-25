package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.lsp.LspCompletionItem;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspPosition;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class MarkdownLspServerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private MarkdownLspServer server;
    private Context context;
    private File projectRoot;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        server = new MarkdownLspServer(context);
        projectRoot = tempFolder.newFolder("project");
        new File(projectRoot, "image.png").createNewFile();
        new File(projectRoot, "folder").mkdirs();
        new File(projectRoot, "folder/doc.md").createNewFile();
    }

    @Test
    public void testFileFolderCompletionsInMarkdown() throws Exception {
        File mdFile = new File(projectRoot, "README.md");
        mdFile.createNewFile();

        LspDocument doc = new LspDocument(mdFile.getAbsolutePath(), "Check out this [link](./", "markdown", 1);
        LspPosition pos = new LspPosition(0, "Check out this [link](./".length());

        List<LspCompletionItem> completions = server.completion(doc, pos);
        
        assertNotNull(completions);
        
        boolean foundImage = false;
        boolean foundFolder = false;
        for (LspCompletionItem item : completions) {
            if (item.label.equals("image.png")) foundImage = true;
            if (item.label.equals("folder/")) foundFolder = true;
        }

        assertTrue("Should suggest image.png", foundImage);
        assertTrue("Should suggest folder/", foundFolder);
    }
}
