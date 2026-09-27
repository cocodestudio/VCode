package com.cocode.vcode.ide.core.language.base;

import com.cocode.vcode.ide.core.lsp.LspClientManager;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.LspServerRegistry;
import com.cocode.vcode.ide.core.lsp.ServerCapabilities;
import com.cocode.vcode.ide.core.lsp.middleware.LspMiddleware;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.utils.CodeFormatter;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests verifying modular language architecture:
 * 1. Pluggable LanguageBackend SPI and LanguageRegistry
 * 2. Neutral DomNode and DomFile tree representations
 * 3. Dynamic LspServerRegistry and ServerCapabilities
 * 4. Backward-compatible delegation in LspClientManager and CodeFormatter
 */
public class LanguageArchitectureTest {

    @Test
    public void testLanguageRegistryStandardBackends() {
        LanguageRegistry registry = LanguageRegistry.getInstance();
        assertNotNull(registry);

        // Verify lookup by FileType
        assertNotNull("HTML backend registered", registry.getBackend(FileType.HTML));
        assertNotNull("CSS backend registered", registry.getBackend(FileType.CSS));
        assertNotNull("JS backend registered", registry.getBackend(FileType.JAVASCRIPT));
        assertNotNull("TS backend registered", registry.getBackend(FileType.TYPESCRIPT));
        assertNotNull("JSON backend registered", registry.getBackend(FileType.JSON));
        assertNotNull("Markdown backend registered", registry.getBackend(FileType.MARKDOWN));
        assertNotNull("SVG backend registered", registry.getBackend(FileType.SVG));

        // Verify lookup by extension
        assertEquals(FileType.HTML, registry.getBackendForExtension("html").getFileType());
        assertEquals(FileType.CSS, registry.getBackendForExtension("css").getFileType());
        assertEquals(FileType.JAVASCRIPT, registry.getBackendForExtension("js").getFileType());
        assertEquals(FileType.JAVASCRIPT, registry.getBackendForExtension("mjs").getFileType());
        assertEquals(FileType.TYPESCRIPT, registry.getBackendForExtension("ts").getFileType());
        assertEquals(FileType.JSON, registry.getBackendForExtension("json").getFileType());
        assertEquals(FileType.MARKDOWN, registry.getBackendForExtension("md").getFileType());
        assertEquals(FileType.SVG, registry.getBackendForExtension("svg").getFileType());

        // Verify lookup by languageId
        assertEquals(FileType.HTML, registry.getBackendForLanguageId("html").getFileType());
        assertEquals(FileType.JAVASCRIPT, registry.getBackendForLanguageId("javascript").getFileType());
        assertEquals(FileType.TYPESCRIPT, registry.getBackendForLanguageId("typescript").getFileType());
        assertEquals(FileType.CSS, registry.getBackendForLanguageId("css").getFileType());
        assertEquals(FileType.JSON, registry.getBackendForLanguageId("json").getFileType());
        assertEquals(FileType.MARKDOWN, registry.getBackendForLanguageId("markdown").getFileType());
    }

    @Test
    public void testHtmlDomNodeParsing() {
        LanguageBackend htmlBackend = LanguageRegistry.getInstance().getBackend(FileType.HTML);
        assertNotNull(htmlBackend);

        String html = "<div class=\"container\"><p>Hello World</p></div>";
        DomNode root = htmlBackend.parseToDom(html);
        assertNotNull(root);
        assertTrue(root instanceof DomFile);
        DomFile domFile = (DomFile) root;
        assertEquals(FileType.HTML, domFile.getFileType());
        assertTrue(domFile.getNodeCount() > 1);

        List<? extends DomNode> children = root.getChildren();
        assertFalse("Root should have children", children.isEmpty());
        DomNode divNode = children.get(0);
        assertEquals("div", divNode.getName());

        // Test deepest node lookup
        int pOffset = html.indexOf("<p>");
        DomNode deepest = root.findDeepestNodeAt(pOffset + 1);
        assertNotNull(deepest);
        assertEquals("p", deepest.getName());
    }

    @Test
    public void testJsDomNodeParsing() {
        LanguageBackend jsBackend = LanguageRegistry.getInstance().getBackend(FileType.JAVASCRIPT);
        assertNotNull(jsBackend);

        String js = "function calculateTotal(price, tax) { return price + tax; }";
        DomNode root = jsBackend.parseToDom(js);
        assertNotNull(root);
        assertTrue(root instanceof DomFile);
        DomFile domFile = (DomFile) root;
        assertEquals(FileType.JAVASCRIPT, domFile.getFileType());

        List<? extends DomNode> children = root.getChildren();
        assertFalse("Root should have children", children.isEmpty());
        DomNode funcNode = children.get(0);
        assertEquals("calculateTotal", funcNode.getName());

        // Test offset lookup inside function
        int returnOffset = js.indexOf("return");
        DomNode deepest = root.findDeepestNodeAt(returnOffset);
        assertNotNull(deepest);
    }

    @Test
    public void testCssDomNodeParsing() {
        LanguageBackend cssBackend = LanguageRegistry.getInstance().getBackend(FileType.CSS);
        assertNotNull(cssBackend);

        String css = ".card { margin: 16px; color: #333; }";
        DomNode root = cssBackend.parseToDom(css);
        assertNotNull(root);
        assertTrue(root instanceof DomFile);

        List<? extends DomNode> rules = root.getChildren();
        assertFalse("CSS should have rules", rules.isEmpty());
        DomNode rule = rules.get(0);
        assertNotNull(rule);
    }

    @Test
    public void testJsonDomNodeParsing() {
        LanguageBackend jsonBackend = LanguageRegistry.getInstance().getBackend(FileType.JSON);
        assertNotNull(jsonBackend);

        String json = "{\n  \"appName\": \"VCode\",\n  \"version\": 1\n}";
        DomNode root = jsonBackend.parseToDom(json);
        assertNotNull(root);
        assertTrue(root instanceof DomFile);
    }

    @Test
    public void testLspServerRegistryAndCapabilities() {
        LspServerRegistry registry = LspServerRegistry.getInstance();
        assertNotNull(registry);

        assertTrue(registry.hasServer("html"));
        assertTrue(registry.hasServer("css"));
        assertTrue(registry.hasServer("scss"));
        assertTrue(registry.hasServer("javascript"));
        assertTrue(registry.hasServer("typescript"));
        assertTrue(registry.hasServer("json"));
        assertTrue(registry.hasServer("markdown"));
        assertFalse(registry.hasServer("plaintext"));
        assertFalse(registry.hasServer("unsupported"));

        ServerCapabilities jsCaps = registry.getCapabilities("javascript");
        assertNotNull(jsCaps);
        assertTrue(jsCaps.hasCompletion());
        assertTrue(jsCaps.hasHover());
        assertTrue(jsCaps.hasDefinition());
        assertTrue(jsCaps.hasReferences());
        assertTrue(jsCaps.hasRename());
        assertTrue(jsCaps.hasFormatting());
        assertTrue(jsCaps.hasSignatureHelp());

        ServerCapabilities jsonCaps = registry.getCapabilities("json");
        assertTrue(jsonCaps.hasCompletion());
        assertTrue(jsonCaps.hasFormatting());
        assertFalse(jsonCaps.hasHover());
    }

    @Test
    public void testLspMiddlewareRegistration() {
        LspServerRegistry registry = LspServerRegistry.getInstance();
        int initialCount = registry.getMiddlewares().size();

        LspMiddleware testMiddleware = new LspMiddleware() {};
        registry.addMiddleware(testMiddleware);

        assertEquals(initialCount + 1, registry.getMiddlewares().size());
        assertTrue(registry.getMiddlewares().contains(testMiddleware));
    }

    @Test
    public void testLspClientManagerDelegation() {
        LspClientManager manager = LspClientManager.getInstance();
        assertNotNull(manager);

        assertTrue(manager.hasServerForLanguage("html"));
        assertTrue(manager.hasServerForLanguage("css"));
        assertTrue(manager.hasServerForLanguage("javascript"));
        assertTrue(manager.hasServerForLanguage("typescript"));
        assertTrue(manager.hasServerForLanguage("json"));
        assertTrue(manager.hasServerForLanguage("markdown"));

        assertFalse(manager.hasServerForLanguage("plaintext"));
        assertFalse(manager.hasServerForLanguage(null));
        assertFalse(manager.hasServerForLanguage("unknown"));
    }

    @Test
    public void testCodeFormatterDelegation() {
        assertTrue(CodeFormatter.isFormatSupported(FileType.HTML));
        assertTrue(CodeFormatter.isFormatSupported(FileType.CSS));
        assertTrue(CodeFormatter.isFormatSupported(FileType.JAVASCRIPT));
        assertTrue(CodeFormatter.isFormatSupported(FileType.TYPESCRIPT));
        assertTrue(CodeFormatter.isFormatSupported(FileType.JSON));
    }
}
