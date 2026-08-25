package com.cocode.vcode.ide.core.lsp;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

public class ModuleResolverTest {

    @Test
    public void testResolveModulePath_ExactFile() throws IOException {
        File dir = new File(System.getProperty("java.io.tmpdir"), "vcode_test_resolver");
        dir.mkdirs();
        File doc = new File(dir, "main.js");
        doc.createNewFile();
        File target = new File(dir, "utils.js");
        target.createNewFile();

        LspLocation loc = ModuleResolver.resolveModulePath(doc.getCanonicalPath(), "./utils.js");
        assertNotNull(loc);
        assertEquals(target.getCanonicalPath(), new File(loc.uri).getCanonicalPath());
        
        target.delete();
        doc.delete();
        dir.delete();
    }

    @Test
    public void testResolveModulePath_NoExtension() throws IOException {
        File dir = new File(System.getProperty("java.io.tmpdir"), "vcode_test_resolver2");
        dir.mkdirs();
        File doc = new File(dir, "main.js");
        doc.createNewFile();
        File target = new File(dir, "api.ts");
        target.createNewFile();

        LspLocation loc = ModuleResolver.resolveModulePath(doc.getCanonicalPath(), "./api");
        assertNotNull(loc);
        assertEquals(target.getCanonicalPath(), new File(loc.uri).getCanonicalPath());
        
        target.delete();
        doc.delete();
        dir.delete();
    }
    
    @Test
    public void testResolveModulePath_IndexFile() throws IOException {
        File dir = new File(System.getProperty("java.io.tmpdir"), "vcode_test_resolver3");
        dir.mkdirs();
        File doc = new File(dir, "main.js");
        doc.createNewFile();
        
        File componentDir = new File(dir, "components");
        componentDir.mkdirs();
        File indexFile = new File(componentDir, "index.tsx");
        indexFile.createNewFile();

        LspLocation loc = ModuleResolver.resolveModulePath(doc.getCanonicalPath(), "./components");
        assertNotNull(loc);
        assertEquals(indexFile.getCanonicalPath(), new File(loc.uri).getCanonicalPath());
        
        indexFile.delete();
        componentDir.delete();
        doc.delete();
        dir.delete();
    }
}
