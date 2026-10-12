package com.cocode.vcode.ide.core.autocomplete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.cocode.vcode.ide.core.model.CompletionItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class PathAutoCompleteEngineTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File projectRoot;
    private File srcDir;
    private File componentsDir;
    private File assetsDir;
    private File gitignoreFile;
    private File indexHtml;
    private File appJs;
    private PathAutoCompleteEngine engine;

    @Before
    public void setUp() throws IOException {
        projectRoot = tempFolder.newFolder("my_project");
        srcDir = new File(projectRoot, "src");
        srcDir.mkdir();
        componentsDir = new File(srcDir, "components");
        componentsDir.mkdir();
        assetsDir = new File(projectRoot, "assets");
        assetsDir.mkdir();

        // Files
        gitignoreFile = new File(projectRoot, ".gitignore");
        gitignoreFile.createNewFile();
        indexHtml = new File(projectRoot, "index.html");
        indexHtml.createNewFile();
        appJs = new File(srcDir, "app.js");
        appJs.createNewFile();
        new File(componentsDir, "Button.js").createNewFile();
        new File(componentsDir, "Header.jsx").createNewFile();
        new File(assetsDir, "logo.png").createNewFile();
        new File(projectRoot, ".env").createNewFile();
        new File(projectRoot, "package.json").createNewFile();
        new File(projectRoot, ".git").mkdir();
        new File(projectRoot, ".vcode").mkdir();

        // Invalidate VFS cache
        VFSManager.getInstance().clear();

        engine = new PathAutoCompleteEngine(null);
        engine.setWorkspaceRoot(projectRoot);
    }

    @After
    public void tearDown() {
        VFSManager.getInstance().clear();
    }

    @Test
    public void testRelativeDirectoryTraversalAndTrailingSlash() {
        engine.setCurrentFile(appJs); // inside src/

        // Querying current directory (src/)
        List<CompletionItem> items = engine.getSuggestionsForPath("./");
        assertNotNull(items);
        assertFalse(items.isEmpty());

        boolean hasComponentsFolder = false;
        for (CompletionItem item : items) {
            if ("components/".equals(item.getLabel())) {
                hasComponentsFolder = true;
                assertEquals(CompletionItem.Type.FOLDER, item.getType());
                assertEquals("components/", item.getEffectiveInsertText());
                assertEquals(0, item.getReplaceLength());
            }
        }
        assertTrue("Should suggest components/ folder", hasComponentsFolder);
    }

    @Test
    public void testNestedSubdirectoryNavigation() {
        engine.setCurrentFile(appJs); // inside src/

        List<CompletionItem> items = engine.getSuggestionsForPath("./components/");
        assertNotNull(items);

        boolean hasButton = false;
        boolean hasHeader = false;
        for (CompletionItem item : items) {
            if ("Button.js".equals(item.getLabel())) {
                hasButton = true;
                assertEquals(0, item.getReplaceLength());
            }
            if ("Header.jsx".equals(item.getLabel())) {
                hasHeader = true;
                assertEquals(0, item.getReplaceLength());
            }
        }
        assertTrue("Should suggest Button.js inside components", hasButton);
        assertTrue("Should suggest Header.jsx inside components", hasHeader);
    }

    @Test
    public void testParentDirectoryTraversal() {
        engine.setCurrentFile(appJs); // inside src/

        List<CompletionItem> items = engine.getSuggestionsForPath("../");
        assertNotNull(items);

        boolean hasAssets = false;
        boolean hasIndexHtml = false;
        for (CompletionItem item : items) {
            if ("assets/".equals(item.getLabel())) {
                hasAssets = true;
                assertEquals(CompletionItem.Type.FOLDER, item.getType());
            }
            if ("index.html".equals(item.getLabel())) {
                hasIndexHtml = true;
                assertEquals(CompletionItem.Type.FILE, item.getType());
            }
        }
        assertTrue("Should navigate up to root and suggest assets/", hasAssets);
        assertTrue("Should navigate up to root and suggest index.html", hasIndexHtml);
    }

    @Test
    public void testWorkspaceRootRelativePath() {
        engine.setCurrentFile(appJs); // inside src/

        List<CompletionItem> items = engine.getSuggestionsForPath("/assets/");
        assertNotNull(items);

        boolean hasLogo = false;
        for (CompletionItem item : items) {
            if ("logo.png".equals(item.getLabel())) {
                hasLogo = true;
                assertEquals(0, item.getReplaceLength());
            }
        }
        assertTrue("Should resolve /assets/ from workspace root and find logo.png", hasLogo);
    }

    @Test
    public void testReplaceLengthOnFilterPrefix() {
        engine.setCurrentFile(appJs); // inside src/

        // Typing ./components/But
        List<CompletionItem> items = engine.getSuggestionsForPath("./components/But");
        assertNotNull(items);
        assertFalse(items.isEmpty());

        CompletionItem buttonItem = null;
        for (CompletionItem item : items) {
            if ("Button.js".equals(item.getLabel())) {
                buttonItem = item;
                break;
            }
        }
        assertNotNull("Should match Button.js", buttonItem);
        assertEquals("Replace length must match prefix 'But' length (3)", 3, buttonItem.getReplaceLength());
    }

    @Test
    public void testGitIgnoreUnquotedCompletionsAndHiddenFiles() {
        engine.setCurrentFile(gitignoreFile);
        assertTrue("Should identify gitignore file", engine.isGitIgnoreFile());

        // Line with prefix ".e"
        String text = "# Ignore rules\n.e";
        int cursor = text.length();

        List<CompletionItem> items = engine.getSuggestions(text, cursor);
        assertNotNull(items);
        assertFalse(items.isEmpty());

        boolean hasEnv = false;
        for (CompletionItem item : items) {
            if (".env".equals(item.getLabel())) {
                hasEnv = true;
                assertEquals(2, item.getReplaceLength()); // ".e".length() == 2
            }
        }
        assertTrue("Should suggest .env hidden file in gitignore", hasEnv);
    }

    @Test
    public void testGitIgnoreCommentLineSuppression() {
        engine.setCurrentFile(gitignoreFile);

        String text = "# .env should not suggest";
        int cursor = text.length();

        List<CompletionItem> items = engine.getSuggestions(text, cursor);
        assertNotNull(items);
        assertTrue("Comment line starting with # must not trigger completions", items.isEmpty());
    }

    @Test
    public void testGitIgnoreNegationRule() {
        engine.setCurrentFile(gitignoreFile);

        String text = "!src/";
        int cursor = text.length();

        List<CompletionItem> items = engine.getSuggestions(text, cursor);
        assertNotNull(items);
        assertFalse(items.isEmpty());

        boolean hasComponents = false;
        for (CompletionItem item : items) {
            if ("components/".equals(item.getLabel())) {
                hasComponents = true;
                break;
            }
        }
        assertTrue("Negated path !src/ should list contents of src/", hasComponents);
    }

    @Test
    public void testRelativePathHelper() {
        File fileA = new File(srcDir, "app.js");
        File fileB = new File(assetsDir, "logo.png");

        String rel = PathAutoCompleteEngine.getRelativePath(srcDir, fileB);
        assertEquals("../assets/logo.png", rel.replace('\\', '/'));
    }

    @Test
    public void testNoDotOrDotDotInjectionAndGitVcodeFiltered() {
        engine.setCurrentFile(indexHtml);

        // When typing single dot
        List<CompletionItem> dotItems = engine.getSuggestionsForPath(".");
        assertNotNull(dotItems);
        for (CompletionItem item : dotItems) {
            assertFalse("Typing . must not suggest ./", "./".equals(item.getLabel()));
            assertFalse("Typing . must not suggest ../", "../".equals(item.getLabel()));
            assertFalse("Typing . must not suggest .git", ".git".equalsIgnoreCase(item.getLabel()) || ".git/".equalsIgnoreCase(item.getLabel()));
        }
        boolean hasEnv = false;
        for (CompletionItem item : dotItems) {
            if (".env".equals(item.getLabel())) {
                hasEnv = true;
                break;
            }
        }
        assertTrue("Typing . must still suggest valid hidden file .env", hasEnv);

        // When typing double dot
        List<CompletionItem> dotDotItems = engine.getSuggestionsForPath("..");
        assertNotNull(dotDotItems);
        assertTrue("Typing .. must not inject ./ or ../ and must return empty when no file matches", dotDotItems.isEmpty());
    }

    @Test
    public void testBoundaryEnforcementRootParentTraversal() {
        engine.setCurrentFile(indexHtml); // at projectRoot

        // From project root, ../ traverses above boundary
        List<CompletionItem> items = engine.getSuggestionsForPath("../");
        assertNotNull(items);
        assertTrue("Cannot traverse above project root with ../", items.isEmpty());
    }

    @Test
    public void testBoundaryEnforcementNestedFolderTraversal() {
        File buttonJs = new File(componentsDir, "Button.js");
        engine.setCurrentFile(buttonJs); // in src/components

        // ../ should navigate to src/
        List<CompletionItem> parentItems = engine.getSuggestionsForPath("../");
        assertNotNull(parentItems);
        assertFalse(parentItems.isEmpty());
        boolean hasAppJs = false;
        for (CompletionItem item : parentItems) {
            if ("app.js".equals(item.getLabel())) {
                hasAppJs = true;
                break;
            }
        }
        assertTrue("Should navigate to src/ and find app.js", hasAppJs);

        // ../../ should navigate to projectRoot
        List<CompletionItem> rootItems = engine.getSuggestionsForPath("../../");
        assertNotNull(rootItems);
        assertFalse(rootItems.isEmpty());
        boolean hasIndexHtml = false;
        for (CompletionItem item : rootItems) {
            if ("index.html".equals(item.getLabel())) {
                hasIndexHtml = true;
                break;
            }
        }
        assertTrue("Should navigate to project root and find index.html", hasIndexHtml);

        // ../../../ exceeds projectRoot boundary
        List<CompletionItem> outOfBoundsItems = engine.getSuggestionsForPath("../../../");
        assertNotNull(outOfBoundsItems);
        assertTrue("Exceeding project boundary with ../../../ must return 0 suggestions", outOfBoundsItems.isEmpty());
    }

    @Test
    public void testBoundaryEnforcementRootRelative() {
        engine.setCurrentFile(appJs);

        // Root relative / should stay in projectRoot
        List<CompletionItem> rootItems = engine.getSuggestionsForPath("/");
        assertNotNull(rootItems);
        assertFalse(rootItems.isEmpty());

        // Root relative /../ attempts to escape projectRoot
        List<CompletionItem> escapedItems = engine.getSuggestionsForPath("/../");
        assertNotNull(escapedItems);
        assertTrue("Root-relative /../ escaping boundary must return 0 suggestions", escapedItems.isEmpty());
    }

    @Test
    public void testGitIgnoreNeverSuggestsGitOrVcode() {
        engine.setCurrentFile(gitignoreFile);

        List<CompletionItem> itemsDot = engine.getSuggestions(".g", 2);
        assertNotNull(itemsDot);
        for (CompletionItem item : itemsDot) {
            assertFalse("Gitignore must never suggest .git", ".git".equalsIgnoreCase(item.getLabel()) || ".git/".equalsIgnoreCase(item.getLabel()));
        }
    }

    @Test
    public void testCssUrlExtsIncludeCssAndScss() {
        assertTrue("CSS_URL_EXTS must support css", PathAutoCompleteEngine.CSS_URL_EXTS.contains("css"));
        assertTrue("CSS_URL_EXTS must support scss", PathAutoCompleteEngine.CSS_URL_EXTS.contains("scss"));
    }

    @Test
    public void testTokenExtractionBeforePunctuationAndQuotes() {
        engine.setCurrentFile(appJs);

        // url("...") with parenthesis before quote
        String urlText = "background: url(\"../assets/";
        List<CompletionItem> urlItems = engine.getSuggestions(urlText, urlText.length());
        assertNotNull(urlItems);
        boolean hasLogoInUrl = false;
        for (CompletionItem item : urlItems) {
            if ("logo.png".equals(item.getLabel())) {
                hasLogoInUrl = true;
                break;
            }
        }
        assertTrue("url(\"...\") must extract url token and allow images", hasLogoInUrl);

        // src="..." with equals before quote
        String srcText = "<img src=\"../assets/";
        List<CompletionItem> srcItems = engine.getSuggestions(srcText, srcText.length());
        assertNotNull(srcItems);
        boolean hasLogoInSrc = false;
        for (CompletionItem item : srcItems) {
            if ("logo.png".equals(item.getLabel())) {
                hasLogoInSrc = true;
                break;
            }
        }
        assertTrue("src=\"...\" must extract src token and allow images", hasLogoInSrc);
    }
}
