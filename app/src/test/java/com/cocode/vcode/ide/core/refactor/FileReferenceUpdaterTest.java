package com.cocode.vcode.ide.core.refactor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cocode.vcode.ide.utils.FileUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class FileReferenceUpdaterTest {

    private File tempProjectDir;

    @Before
    public void setUp() throws IOException {
        tempProjectDir = File.createTempFile("vcode_refactor_test_", "");
        tempProjectDir.delete();
        tempProjectDir.mkdirs();
    }

    @After
    public void tearDown() {
        if (tempProjectDir != null && tempProjectDir.exists()) {
            FileUtils.deleteRecursive(tempProjectDir);
        }
    }

    private File createProjectFile(String relativePath, String content) throws IOException {
        File file = new File(tempProjectDir, relativePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileUtils.writeFile(file, content);
        return file;
    }

    @Test
    public void testJsEsmImportUpdatedOnFileMove() throws IOException {
        File appJs = createProjectFile("app.js", "export const appName = 'VCode';");
        File mainJs = createProjectFile("main.js", "import { appName } from './app.js';\nconsole.log(appName);");

        File utilsDir = new File(tempProjectDir, "utils");
        utilsDir.mkdirs();
        File newAppJs = new File(utilsDir, "app.js");
        appJs.renameTo(newAppJs);

        FileReferenceUpdater.RefactorResult result = FileReferenceUpdater.updateReferences(
                tempProjectDir, appJs, newAppJs, null
        );

        assertTrue(result.referencesUpdatedCount > 0);
        String updatedMain = FileUtils.readFile(mainJs);
        assertEquals("import { appName } from './utils/app.js';\nconsole.log(appName);", updatedMain);
    }

    @Test
    public void testJsExtensionlessImportUpdated() throws IOException {
        File appJs = createProjectFile("app.js", "export const appName = 'VCode';");
        File mainJs = createProjectFile("main.js", "import App from './app';\nconsole.log(App);");

        File utilsDir = new File(tempProjectDir, "utils");
        utilsDir.mkdirs();
        File newAppJs = new File(utilsDir, "app.js");
        appJs.renameTo(newAppJs);

        FileReferenceUpdater.RefactorResult result = FileReferenceUpdater.updateReferences(
                tempProjectDir, appJs, newAppJs, null
        );

        assertTrue(result.referencesUpdatedCount > 0);
        String updatedMain = FileUtils.readFile(mainJs);
        assertEquals("import App from './utils/app';\nconsole.log(App);", updatedMain);
    }

    @Test
    public void testJsDynamicImportAndRequire() throws IOException {
        File appJs = createProjectFile("app.js", "module.exports = { version: 5 };");
        File mainJs = createProjectFile("main.js",
                "const a = require('./app');\nconst b = import('./app.js');");

        File utilsDir = new File(tempProjectDir, "utils");
        utilsDir.mkdirs();
        File newAppJs = new File(utilsDir, "app.js");
        appJs.renameTo(newAppJs);

        FileReferenceUpdater.updateReferences(tempProjectDir, appJs, newAppJs, null);

        String updatedMain = FileUtils.readFile(mainJs);
        assertEquals("const a = require('./utils/app');\nconst b = import('./utils/app.js');", updatedMain);
    }

    @Test
    public void testOutboundImportsUpdatedWhenMoved() throws IOException {
        File configJs = createProjectFile("config.js", "export const port = 3000;");
        File appJs = createProjectFile("app.js", "import { port } from './config.js';");

        File utilsDir = new File(tempProjectDir, "utils");
        utilsDir.mkdirs();
        File newAppJs = new File(utilsDir, "app.js");
        appJs.renameTo(newAppJs);

        FileReferenceUpdater.updateReferences(tempProjectDir, appJs, newAppJs, null);

        String updatedApp = FileUtils.readFile(newAppJs);
        assertEquals("import { port } from '../config.js';", updatedApp);
    }

    @Test
    public void testHtmlScriptAndLinkTagUpdated() throws IOException {
        File appJs = createProjectFile("app.js", "console.log('App');");
        File styleCss = createProjectFile("style.css", "body { color: red; }");
        File indexHtml = createProjectFile("index.html",
                "<!DOCTYPE html>\n<html>\n<head>\n" +
                        "  <link rel=\"stylesheet\" href=\"style.css\">\n" +
                        "</head>\n<body>\n" +
                        "  <script src=\"app.js\"></script>\n" +
                        "</body>\n</html>");

        File themeCss = new File(tempProjectDir, "theme.css");
        styleCss.renameTo(themeCss);
        FileReferenceUpdater.updateReferences(tempProjectDir, styleCss, themeCss, null);

        File scriptsDir = new File(tempProjectDir, "scripts");
        scriptsDir.mkdirs();
        File newAppJs = new File(scriptsDir, "app.js");
        appJs.renameTo(newAppJs);
        FileReferenceUpdater.updateReferences(tempProjectDir, appJs, newAppJs, null);

        String updatedHtml = FileUtils.readFile(indexHtml);
        assertTrue(updatedHtml.contains("<link rel=\"stylesheet\" href=\"theme.css\">"));
        assertTrue(updatedHtml.contains("<script src=\"scripts/app.js\"></script>"));
    }

    @Test
    public void testCssImportAndUrlUpdated() throws IOException {
        File colorsCss = createProjectFile("colors.css", ":root { --primary: #007acc; }");
        File logoImg = createProjectFile("images/logo.png", "fake_png");
        File styleCss = createProjectFile("styles/main.css",
                "@import \"../colors.css\";\n.logo { background: url('../images/logo.png'); }");

        File paletteCss = new File(tempProjectDir, "palette.css");
        colorsCss.renameTo(paletteCss);
        FileReferenceUpdater.updateReferences(tempProjectDir, colorsCss, paletteCss, null);

        File assetsImg = new File(tempProjectDir, "assets/logo.png");
        assetsImg.getParentFile().mkdirs();
        logoImg.renameTo(assetsImg);
        FileReferenceUpdater.updateReferences(tempProjectDir, logoImg, assetsImg, null);

        String updatedCss = FileUtils.readFile(styleCss);
        assertTrue(updatedCss.contains("@import \"../palette.css\";"));
        assertTrue(updatedCss.contains("url('../assets/logo.png')"));
    }

    @Test
    public void testMarkdownLinkUpdated() throws IOException {
        File guideMd = createProjectFile("docs/guide.md", "# Guide");
        File readmeMd = createProjectFile("README.md", "See [Guide](docs/guide.md) for details.");

        File manualMd = new File(tempProjectDir, "docs/manual.md");
        guideMd.renameTo(manualMd);
        FileReferenceUpdater.updateReferences(tempProjectDir, guideMd, manualMd, null);

        String updatedReadme = FileUtils.readFile(readmeMd);
        assertEquals("See [Guide](docs/manual.md) for details.", updatedReadme);
    }

    @Test
    public void testDuplicateNameDisambiguationZeroCollision() throws IOException {
        // Two files named format.js in different directories
        File utilsFormat = createProjectFile("utils/format.js", "export function formatUtil() {}");
        File servicesFormat = createProjectFile("services/format.js", "export function formatService() {}");

        // services/network.js imports ./format.js (points to services/format.js)
        File networkJs = createProjectFile("services/network.js",
                "import { formatService } from './format.js';\nformatService();");

        // views/home.js imports ../utils/format.js (points to utils/format.js)
        File homeJs = createProjectFile("views/home.js",
                "import { formatUtil } from '../utils/format.js';\nformatUtil();");

        // Move ONLY utils/format.js to common/format.js
        File commonDir = new File(tempProjectDir, "common");
        commonDir.mkdirs();
        File commonFormat = new File(commonDir, "format.js");
        utilsFormat.renameTo(commonFormat);

        FileReferenceUpdater.RefactorResult result = FileReferenceUpdater.updateReferences(
                tempProjectDir, utilsFormat, commonFormat, null
        );

        // views/home.js must be updated to ../common/format.js
        String updatedHome = FileUtils.readFile(homeJs);
        assertEquals("import { formatUtil } from '../common/format.js';\nformatUtil();", updatedHome);

        // services/network.js must remain COMPLETELY UNCHANGED
        String networkContent = FileUtils.readFile(networkJs);
        assertEquals("import { formatService } from './format.js';\nformatService();", networkContent);
        assertFalse(networkContent.contains("common"));
    }

    @Test
    public void testIdenticalSubdirectoryNamesIsolation() throws IOException {
        // Two directories named utils in different modules
        File coreHelper = createProjectFile("core/utils/helper.js", "export const h1 = 1;");
        File clientHelper = createProjectFile("client/utils/helper.js", "export const h2 = 2;");

        File coreMain = createProjectFile("core/main.js", "import { h1 } from './utils/helper.js';");
        File clientApp = createProjectFile("client/app.js", "import { h2 } from './utils/helper.js';");

        File oldCoreUtils = new File(tempProjectDir, "core/utils");
        File newCoreLib = new File(tempProjectDir, "core/lib");
        oldCoreUtils.renameTo(newCoreLib);
        File newCoreHelper = new File(newCoreLib, "helper.js");

        FileReferenceUpdater.updateReferences(tempProjectDir, oldCoreUtils, newCoreLib, null);

        // core/main.js should update to ./lib/helper.js
        String updatedCoreMain = FileUtils.readFile(coreMain);
        assertEquals("import { h1 } from './lib/helper.js';", updatedCoreMain);

        // client/app.js MUST be completely untouched!
        String clientAppContent = FileUtils.readFile(clientApp);
        assertEquals("import { h2 } from './utils/helper.js';", clientAppContent);
    }

    @Test
    public void testUnrelatedFilesAndExternalUrlsUntouched() throws IOException {
        File appJs = createProjectFile("app.js", "export const a = 1;");
        File vendorJs = createProjectFile("vendor.js",
                "import React from 'react';\nimport { map } from 'lodash';\nconst cdn = 'https://cdn.example.com/app.js';");

        File newAppJs = new File(tempProjectDir, "dist/app.js");
        newAppJs.getParentFile().mkdirs();
        appJs.renameTo(newAppJs);

        FileReferenceUpdater.updateReferences(tempProjectDir, appJs, newAppJs, null);

        String updatedVendor = FileUtils.readFile(vendorJs);
        assertEquals("import React from 'react';\nimport { map } from 'lodash';\nconst cdn = 'https://cdn.example.com/app.js';", updatedVendor);
    }
}
