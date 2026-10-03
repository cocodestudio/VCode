package com.cocode.vcode.ide.ui.preview;

import android.webkit.WebResourceResponse;

import com.cocode.vcode.ide.core.preview.PreviewServer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.TemporaryFolder;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class PreviewRequestHandlerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testBareImportRewriting() {
        PreviewRequestHandler.NodeModulesRequestHandler handler =
                new PreviewRequestHandler.NodeModulesRequestHandler();

        String originalCode = "import axios from 'axios';\n"
                + "import { useState, useEffect } from \"react\";\n"
                + "import Header from './Header.js';\n"
                + "import utils from '../utils/math.js';\n"
                + "import cdn from 'https://cdn.jsdelivr.net/npm/vue@3';\n";

        String rewritten = handler.rewriteBareImports(originalCode);

        assertTrue(rewritten.contains("import axios from '/__modules__/axios';"));
        assertTrue(rewritten.contains("import { useState, useEffect } from \"/__modules__/react\";"));
        assertTrue(rewritten.contains("import Header from './Header.js';"));
        assertTrue(rewritten.contains("import utils from '../utils/math.js';"));
        assertTrue(rewritten.contains("import cdn from 'https://cdn.jsdelivr.net/npm/vue@3';"));
    }

    @Test
    public void testModuleResolutionFromNodeModules() throws Exception {
        File projectRoot = tempFolder.newFolder("web_project");
        File pkgDir = new File(projectRoot, "node_modules/my-lib");
        pkgDir.mkdirs();

        File pkgJson = new File(pkgDir, "package.json");
        try (FileWriter writer = new FileWriter(pkgJson)) {
            writer.write("{\"name\":\"my-lib\",\"module\":\"dist/index.mjs\"}");
        }

        File distDir = new File(pkgDir, "dist");
        distDir.mkdirs();
        File entryFile = new File(distDir, "index.mjs");
        try (FileWriter writer = new FileWriter(entryFile)) {
            writer.write("export const hello = () => 'world';");
        }

        PreviewRequestHandler.NodeModulesRequestHandler handler =
                new PreviewRequestHandler.NodeModulesRequestHandler();

        WebResourceResponse response = handler.resolveModulePackage("my-lib", projectRoot);
        assertNotNull(response);
        assertEquals("application/javascript", response.getMimeType());

        BufferedReader reader = new BufferedReader(new InputStreamReader(response.getData(), StandardCharsets.UTF_8));
        String content = reader.readLine();
        assertEquals("export const hello = () => 'world';", content);
    }

    @Test
    public void testSyntheticModuleFallbackWhenMissing() throws Exception {
        PreviewRequestHandler.NodeModulesRequestHandler handler =
                new PreviewRequestHandler.NodeModulesRequestHandler();

        WebResourceResponse response = handler.resolveModulePackage("non-existent-pkg", null);
        assertNotNull(response);
        assertEquals("application/javascript", response.getMimeType());

        BufferedReader reader = new BufferedReader(new InputStreamReader(response.getData(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        assertTrue(sb.toString().contains("Synthetic module for: non-existent-pkg"));
        assertTrue(sb.toString().contains("export default syntheticModule;"));
    }

    @Test
    public void testPreviewServerDevUrlManagement() throws Exception {
        File projectRoot = tempFolder.newFolder("vite_project");
        PreviewServer server = PreviewServer.getInstance();
        server.clear();

        assertFalse(server.isDevServerActive(projectRoot));
        assertNull(server.getDevServerUrl(projectRoot));

        server.setDevServerUrl(projectRoot, "http://localhost:5173");
        assertTrue(server.isDevServerActive(projectRoot));
        assertEquals("http://localhost:5173", server.getDevServerUrl(projectRoot));

        server.setDevServerUrl(projectRoot, null);
        assertFalse(server.isDevServerActive(projectRoot));
    }
}
