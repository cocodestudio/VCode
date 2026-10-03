package com.cocode.vcode.ide.data.npm;

import com.cocode.vcode.ide.core.autocomplete.ProjectSymbolIndex;
import com.cocode.vcode.ide.core.autocomplete.types.TypeDefinitionIndexer;
import com.cocode.vcode.ide.core.language.js.JsAutoCompleteEngine;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.data.npm.extractor.NpmTarballExtractor;
import com.cocode.vcode.ide.data.npm.safeguard.StorageSafeguardManager;
import com.cocode.vcode.ide.data.npm.sync.PackageJsonSync;
import com.cocode.vcode.ide.data.npm.sync.PackageLockJsonSync;
import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.js.JsLexer;
import com.cocode.vcode.ide.core.language.js.JsParser;
import com.cocode.vcode.ide.core.language.js.JsSemanticLinter;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.language.js.ScopeTree;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;
import com.cocode.vcode.ide.core.lsp.servers.JsSignatureParser;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.utils.FileUtils;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class NpmPackageManagerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File projectRoot;

    @Before
    public void setUp() throws Exception {
        projectRoot = tempFolder.newFolder("test_project");
    }

    @Test
    public void testPackageJsonSync_AddAndRemoveDependency() throws Exception {
        // 1. Add dependency when package.json does not exist yet
        PackageJsonSync.addDependency(projectRoot, "lodash", "4.17.21", false);
        File pkgJson = new File(projectRoot, "package.json");
        assertTrue("package.json should be created", pkgJson.exists());

        Map<String, String> deps = PackageJsonSync.getDependencies(projectRoot);
        assertEquals(1, deps.size());
        assertEquals("4.17.21", deps.get("lodash"));

        // 2. Add devDependency
        PackageJsonSync.addDependency(projectRoot, "@types/node", "20.10.0", true);
        Map<String, String> devDeps = PackageJsonSync.getDevDependencies(projectRoot);
        assertEquals(1, devDeps.size());
        assertEquals("20.10.0", devDeps.get("@types/node"));

        // 3. Update existing dependency
        PackageJsonSync.addDependency(projectRoot, "lodash", "4.18.0", false);
        deps = PackageJsonSync.getDependencies(projectRoot);
        assertEquals("4.18.0", deps.get("lodash"));

        // 4. Remove dependency
        PackageJsonSync.removeDependency(projectRoot, "lodash");
        deps = PackageJsonSync.getDependencies(projectRoot);
        assertFalse(deps.containsKey("lodash"));

        // 5. Dev dependency remains untouched
        devDeps = PackageJsonSync.getDevDependencies(projectRoot);
        assertTrue(devDeps.containsKey("@types/node"));
    }

    @Test
    public void testPackageJsonSync_ResolvesNameFromProjectMeta() throws Exception {
        File uuidDir = tempFolder.newFolder("e4d3c2b1-0000-4000-8000-123456789abc");
        File metaDir = new File(uuidDir, ".vcode/meta");
        metaDir.mkdirs();
        File metaFile = new File(metaDir, "project.json");
        FileUtils.writeFile(metaFile, "{\"name\": \"My Super App\"}");

        JSONObject json = PackageJsonSync.ensurePackageJson(uuidDir);
        assertEquals("my-super-app", json.optString("name"));

        File pkgJson = new File(uuidDir, "package.json");
        assertTrue(pkgJson.exists());
        JSONObject reRead = PackageJsonSync.readPackageJson(uuidDir);
        assertNotNull(reRead);
        assertEquals("my-super-app", reRead.optString("name"));
    }

    @Test
    public void testPackageLockJsonSync_RecordAndRemovePackage() throws Exception {
        // 1. Record regular package
        boolean recorded = PackageLockJsonSync.recordPackage(
                projectRoot,
                "axios",
                "1.7.9",
                "https://registry.npmjs.org/axios/-/axios-1.7.9.tgz",
                "sha512-mock-integrity-hash",
                "MIT",
                false
        );
        assertTrue("recordPackage should succeed", recorded);

        File lockFile = new File(projectRoot, "package-lock.json");
        assertTrue("package-lock.json should exist", lockFile.exists());

        JSONObject lockJson = PackageLockJsonSync.readPackageLockJson(projectRoot);
        assertNotNull(lockJson);
        assertEquals(3, lockJson.optInt("lockfileVersion"));
        assertTrue(lockJson.optBoolean("requires"));

        JSONObject packages = lockJson.optJSONObject("packages");
        assertNotNull(packages);

        JSONObject rootPkg = packages.optJSONObject("");
        assertNotNull(rootPkg);
        JSONObject rootDeps = rootPkg.optJSONObject("dependencies");
        assertNotNull(rootDeps);
        assertEquals("^1.7.9", rootDeps.optString("axios"));

        JSONObject axiosEntry = packages.optJSONObject("node_modules/axios");
        assertNotNull(axiosEntry);
        assertEquals("1.7.9", axiosEntry.optString("version"));
        assertEquals("https://registry.npmjs.org/axios/-/axios-1.7.9.tgz", axiosEntry.optString("resolved"));
        assertEquals("sha512-mock-integrity-hash", axiosEntry.optString("integrity"));
        assertEquals("MIT", axiosEntry.optString("license"));
        assertFalse(axiosEntry.has("dev"));

        // 2. Record dev dependency
        PackageLockJsonSync.recordPackage(
                projectRoot,
                "@types/node",
                "20.10.0",
                "https://registry.npmjs.org/@types/node/-/node-20.10.0.tgz",
                "sha512-mock-dev-integrity",
                "MIT",
                true
        );

        lockJson = PackageLockJsonSync.readPackageLockJson(projectRoot);
        JSONObject nodeEntry = lockJson.getJSONObject("packages").getJSONObject("node_modules/@types/node");
        assertTrue(nodeEntry.optBoolean("dev"));

        // 3. Remove package
        boolean removed = PackageLockJsonSync.removePackage(projectRoot, "axios");
        assertTrue(removed);

        lockJson = PackageLockJsonSync.readPackageLockJson(projectRoot);
        assertFalse(lockJson.getJSONObject("packages").has("node_modules/axios"));
        assertFalse(lockJson.getJSONObject("packages").getJSONObject("").getJSONObject("dependencies").has("axios"));
        assertTrue(lockJson.getJSONObject("packages").has("node_modules/@types/node"));
    }

    @Test
    public void testStorageSafeguardManager_CreatesNomediaShield() throws Exception {
        StorageSafeguardManager.ensureSafeguards(projectRoot);
        File nodeModules = new File(projectRoot, StorageSafeguardManager.NODE_MODULES);
        assertTrue("node_modules directory should be created", nodeModules.exists());

        File nomedia = new File(nodeModules, StorageSafeguardManager.NOMEDIA);
        assertTrue(".nomedia shield file should be present", nomedia.exists());

        File gitignore = new File(projectRoot, ".gitignore");
        assertTrue(".gitignore file should be present", gitignore.exists());
        String gitignoreContent = FileUtils.readFile(gitignore);
        assertNotNull(gitignoreContent);
        assertTrue(gitignoreContent.contains("node_modules/"));
    }

    @Test
    public void testTypeDefinitionIndexer_ParsesAndRegistersSymbols() throws Exception {
        // Create mock package in node_modules/math-lib
        File nodeModules = new File(projectRoot, "node_modules");
        File pkgDir = new File(nodeModules, "math-lib");
        pkgDir.mkdirs();

        // package.json with types entry
        String pkgJsonContent = "{\n" +
                "  \"name\": \"math-lib\",\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"types\": \"./index.d.ts\"\n" +
                "}";
        FileUtils.writeFile(new File(pkgDir, "package.json"), pkgJsonContent);

        // index.d.ts with declarations
        String dtsContent = "export function add(a: number, b: number): number;\n" +
                "export function multiply(x: number, y: number): number;\n" +
                "export const PI: number;\n" +
                "export interface Calculator {\n" +
                "  calculate(expr: string): number;\n" +
                "  clear(): void;\n" +
                "}";
        FileUtils.writeFile(new File(pkgDir, "index.d.ts"), dtsContent);

        // Index the package
        boolean indexed = TypeDefinitionIndexer.indexDependency(projectRoot, "math-lib");
        assertTrue("math-lib should be indexed successfully", indexed);

        // Verify symbols in ProjectSymbolIndex
        List<CompletionItem> exports = ProjectSymbolIndex.getInstance().getExportsForPath("math-lib");
        assertNotNull(exports);
        assertFalse(exports.isEmpty());

        boolean hasAdd = false;
        boolean hasMultiply = false;
        boolean hasPI = false;
        for (CompletionItem item : exports) {
            if ("add".equals(item.getLabel())) hasAdd = true;
            if ("multiply".equals(item.getLabel())) hasMultiply = true;
            if ("PI".equals(item.getLabel())) hasPI = true;
        }
        assertTrue("Should contain function 'add'", hasAdd);
        assertTrue("Should contain function 'multiply'", hasMultiply);
        assertTrue("Should contain const 'PI'", hasPI);

        // Verify cache file was written
        File cacheFile = new File(projectRoot, ".vcode/cache/deps_symbols.idx");
        assertTrue("deps_symbols.idx cache file should exist", cacheFile.exists());

        // Clear in-memory symbols and test cold-start cache restore
        ProjectSymbolIndex.getInstance().clearInMemorySymbols();
        boolean loaded = TypeDefinitionIndexer.loadCachedDependencySymbols(projectRoot);
        assertTrue("Should reload from cache", loaded);

        List<CompletionItem> reloadedExports = ProjectSymbolIndex.getInstance().getExportsForPath("math-lib");
        assertNotNull(reloadedExports);
        assertFalse(reloadedExports.isEmpty());
    }

    @Test
    public void testNpmTarballExtractor_ExtractsTgzCorrectly() throws Exception {
        // Build a minimal in-memory TAR file
        ByteArrayOutputStream tarBytes = new ByteArrayOutputStream();

        // Entry: package/index.js (size: 24 bytes)
        String entryName = "package/index.js";
        byte[] fileData = "console.log('hello vpm');".getBytes(StandardCharsets.UTF_8);

        byte[] header = createTarHeader(entryName, fileData.length);
        tarBytes.write(header);
        tarBytes.write(fileData);

        // Pad file data to 512-byte boundary
        int pad = 512 - (fileData.length % 512);
        if (pad < 512) {
            tarBytes.write(new byte[pad]);
        }

        // Two 512-byte zero blocks for TAR EOF
        tarBytes.write(new byte[1024]);

        // Compress with GZIP
        ByteArrayOutputStream gzippedBytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(gzippedBytes)) {
            gzipOut.write(tarBytes.toByteArray());
        }

        // Extract to target dir
        File targetDir = tempFolder.newFolder("extracted_pkg");
        ByteArrayInputStream inStream = new ByteArrayInputStream(gzippedBytes.toByteArray());
        NpmTarballExtractor.extractTgzStream(inStream, targetDir);

        // Verify that 'package/' prefix was stripped and index.js exists directly in targetDir
        File extractedFile = new File(targetDir, "index.js");
        assertTrue("index.js should be extracted without package/ prefix", extractedFile.exists());

        String content = FileUtils.readFile(extractedFile);
        assertEquals("console.log('hello vpm');", content);
    }

    @Test
    public void testNpmPackageAutocomplete_NamedImportsAndMemberAccess() throws Exception {
        // Setup package in node_modules/calc-lib
        File nodeModules = new File(projectRoot, "node_modules");
        File pkgDir = new File(nodeModules, "calc-lib");
        pkgDir.mkdirs();

        String pkgJsonContent = "{\n" +
                "  \"name\": \"calc-lib\",\n" +
                "  \"version\": \"2.0.0\",\n" +
                "  \"types\": \"./index.d.ts\"\n" +
                "}";
        FileUtils.writeFile(new File(pkgDir, "package.json"), pkgJsonContent);

        String dtsContent = "export function calculate(expr: string): number;\n" +
                "export const VERSION: string;\n" +
                "export class Engine {\n" +
                "  run(): void;\n" +
                "  reset(): void;\n" +
                "}";
        FileUtils.writeFile(new File(pkgDir, "index.d.ts"), dtsContent);

        // Index the package into ProjectSymbolIndex
        TypeDefinitionIndexer.indexDependency(projectRoot, "calc-lib");

        JsAutoCompleteEngine engine = new JsAutoCompleteEngine(null);
        engine.setCurrentFile(new File(projectRoot, "main.js"));

        // 1. Test named import suggestions inside `import { | } from 'calc-lib'`
        String importCode = "import {  } from 'calc-lib';";
        int cursorInsideBraces = 9; // position between `{ ` and ` }`
        List<CompletionItem> importItems = engine.getSuggestions(importCode, cursorInsideBraces);
        assertNotNull(importItems);
        assertFalse(importItems.isEmpty());

        boolean hasCalculate = false;
        boolean hasVersion = false;
        for (CompletionItem item : importItems) {
            if ("calculate".equals(item.getLabel())) hasCalculate = true;
            if ("VERSION".equals(item.getLabel())) hasVersion = true;
        }
        assertTrue("Named imports should suggest calculate", hasCalculate);
        assertTrue("Named imports should suggest VERSION", hasVersion);

        // 2. Test direct package member completion on `calc-lib.`
        String directMemberCode = "calc-lib.";
        List<CompletionItem> memberItems = engine.getSuggestions(directMemberCode, directMemberCode.length());
        assertNotNull(memberItems);
        assertFalse(memberItems.isEmpty());

        boolean memberHasCalculate = false;
        for (CompletionItem item : memberItems) {
            if ("calculate".equals(item.getLabel())) memberHasCalculate = true;
        }
        assertTrue("calc-lib. should suggest calculate", memberHasCalculate);
    }

    @Test
    public void testLodashImportAndMemberAutocomplete_NoDuplicateDeclaration() throws Exception {
        // Setup mock untyped lodash package in node_modules/lodash
        File nodeModules = new File(projectRoot, "node_modules");
        File lodashDir = new File(nodeModules, "lodash");
        lodashDir.mkdirs();

        String pkgJson = "{\n  \"name\": \"lodash\",\n  \"version\": \"4.17.21\",\n  \"main\": \"lodash.js\"\n}";
        FileUtils.writeFile(new File(lodashDir, "package.json"), pkgJson);
        FileUtils.writeFile(new File(lodashDir, "lodash.js"), "function debounce() {}\nlodash.debounce = debounce;\nmodule.exports = lodash;");
        FileUtils.writeFile(new File(lodashDir, "debounce.js"), "module.exports = function debounce() {};");
        FileUtils.writeFile(new File(lodashDir, "throttle.js"), "module.exports = function throttle() {};");

        // Index lodash via TypeDefinitionIndexer
        boolean indexed = TypeDefinitionIndexer.indexDependency(projectRoot, "lodash");
        assertTrue("lodash should be indexed via JS fallback", indexed);

        // 1. Verify NO "Identifier 'lodash' has already been declared" error from JsSemanticLinter
        String code = "import lodash from 'lodash';\nlodash.";
        TokenStream stream = JsLexer.tokenize(code);
        JsSyntaxTree tree = JsParser.parseFull(code, stream);
        assertNotNull(tree);
        ScopeTree scopeTree = ScopeTree.build(tree);
        assertNotNull(scopeTree);

        java.util.List<Problem> problems = new java.util.ArrayList<>();
        JsSemanticLinter.analyze(new File(projectRoot, "main.js"), code, stream, tree, scopeTree, null, problems);

        for (Problem p : problems) {
            assertFalse("Should NOT report duplicate declaration for imported 'lodash'",
                    p.getMessage().contains("Identifier 'lodash' has already been declared"));
        }

        // 2. Verify `import {  } from 'lodash'` with cursor inside braces suggests debounce
        JsAutoCompleteEngine engine = new JsAutoCompleteEngine(null);
        engine.setCurrentFile(new File(projectRoot, "main.js"));

        String importBracesCode = "import {  } from 'lodash';";
        int cursorInsideBraces = 9;
        List<CompletionItem> namedItems = engine.getSuggestions(importBracesCode, cursorInsideBraces);
        assertNotNull(namedItems);
        assertFalse(namedItems.isEmpty());

        boolean hasDebounceNamed = false;
        for (CompletionItem item : namedItems) {
            if ("debounce".equals(item.getLabel())) {
                hasDebounceNamed = true;
                assertEquals("Should be tagged with lodash (npm)", "lodash (npm)", item.getDetail());
            }
        }
        assertTrue("import { } from 'lodash' should suggest debounce", hasDebounceNamed);

        // 3. Verify `lodash.` triggers completions from lodash
        List<CompletionItem> memberItems = engine.getSuggestions(code, code.length());
        assertNotNull(memberItems);
        assertFalse(memberItems.isEmpty());

        boolean hasDebounceMember = false;
        for (CompletionItem item : memberItems) {
            if ("debounce".equals(item.getLabel())) {
                hasDebounceMember = true;
            }
        }
        assertTrue("lodash. should suggest debounce", hasDebounceMember);
    }

    @Test
    public void testTypeDefinitionIndexer_MultiFileDtsAndComplexGenerics() throws Exception {
        // Setup @types/complex-lib with multi-file /// <reference path="..." /> and complex generics
        File nodeModules = new File(projectRoot, "node_modules");
        File atTypesDir = new File(nodeModules, "@types/complex-lib");
        atTypesDir.mkdirs();

        FileUtils.writeFile(new File(atTypesDir, "package.json"), "{\"name\": \"@types/complex-lib\"}");
        FileUtils.writeFile(new File(atTypesDir, "index.d.ts"),
                "/// <reference path=\"./common.d.ts\" />\nexport = _;\ndeclare const _: _.LoDashStatic;");
        FileUtils.writeFile(new File(atTypesDir, "common.d.ts"),
                "interface LoDashStatic {\n" +
                "  debounce<T extends (...args: any[]) => any>(func: T, wait?: number): T;\n" +
                "  throttle<T>(func: T): T;\n" +
                "  VERSION: string;\n" +
                "}");

        boolean indexed = TypeDefinitionIndexer.indexDependency(projectRoot, "complex-lib");
        assertTrue("complex-lib should be indexed via multi-file @types reference", indexed);

        List<CompletionItem> exports = ProjectSymbolIndex.getInstance().getExportsForPath("complex-lib");
        assertNotNull(exports);
        assertFalse(exports.isEmpty());

        boolean hasDebounce = false;
        boolean hasThrottle = false;
        boolean hasVersion = false;
        for (CompletionItem item : exports) {
            if ("debounce".equals(item.getLabel())) hasDebounce = true;
            if ("throttle".equals(item.getLabel())) hasThrottle = true;
            if ("VERSION".equals(item.getLabel())) hasVersion = true;
        }
        assertTrue("Should index generic debounce", hasDebounce);
        assertTrue("Should index generic throttle", hasThrottle);
        assertTrue("Should index VERSION property", hasVersion);
    }

    @Test
    public void testImportAutoComplete_BareIdentifierWithoutParentheses() throws Exception {
        File nodeModules = new File(projectRoot, "node_modules");
        File lodashDir = new File(nodeModules, "lodash");
        lodashDir.mkdirs();

        FileUtils.writeFile(new File(lodashDir, "package.json"), "{\"name\":\"lodash\",\"version\":\"4.17.21\"}");
        FileUtils.writeFile(new File(lodashDir, "debounce.js"), "module.exports = function debounce(func, wait, options) {};");
        TypeDefinitionIndexer.indexDependency(projectRoot, "lodash");

        JsAutoCompleteEngine engine = new JsAutoCompleteEngine(null);
        engine.setCurrentFile(new File(projectRoot, "main.js"));

        String code = "import { d } from 'lodash';";
        int cursor = 10; // cursor right after 'd' inside braces
        List<CompletionItem> items = engine.getSuggestions(code, cursor);
        assertNotNull(items);
        assertFalse(items.isEmpty());

        CompletionItem debounceItem = null;
        for (CompletionItem item : items) {
            // Must NOT have the built-in "Utility" snippet leaking into import braces
            if ("Utility".equals(item.getDetail())) {
                assertFalse("Utility snippet should not appear in import braces", true);
            }
            if ("debounce".equals(item.getLabel())) {
                debounceItem = item;
            }
        }

        assertNotNull("debounce should be suggested", debounceItem);
        assertEquals("debounce", debounceItem.getInsertText());
        assertEquals("debounce", debounceItem.getEffectiveInsertText());
        assertFalse("insertText must NOT contain parentheses in import clause", debounceItem.getInsertText().contains("("));
    }

    @Test
    public void testAxiosAutocomplete_ShowsHttpMethodsAndPropertiesNotPromiseMethods() throws Exception {
        File nodeModules = new File(projectRoot, "node_modules");
        File axiosDir = new File(nodeModules, "axios");
        axiosDir.mkdirs();

        FileUtils.writeFile(new File(axiosDir, "package.json"), "{\"name\":\"axios\",\"types\":\"index.d.ts\"}");
        String dts = "export class Axios {\n" +
                "  defaults: any;\n" +
                "  interceptors: any;\n" +
                "  get(url: string, config?: any): Promise<any>;\n" +
                "  post(url: string, data?: any, config?: any): Promise<any>;\n" +
                "  put(url: string, data?: any, config?: any): Promise<any>;\n" +
                "  delete(url: string, config?: any): Promise<any>;\n" +
                "}\n" +
                "export interface AxiosStatic extends Axios {\n" +
                "  create(config?: any): Axios;\n" +
                "}\n" +
                "declare const axios: AxiosStatic;\n" +
                "export default axios;\n";
        FileUtils.writeFile(new File(axiosDir, "index.d.ts"), dts);
        TypeDefinitionIndexer.indexDependency(projectRoot, "axios");

        JsAutoCompleteEngine engine = new JsAutoCompleteEngine(null);
        engine.setCurrentFile(new File(projectRoot, "main.js"));

        // 1. Bare `axios.`
        String bareAxios = "axios.";
        List<CompletionItem> bareItems = engine.getSuggestions(bareAxios, bareAxios.length());
        assertNotNull(bareItems);
        assertFalse(bareItems.isEmpty());

        boolean hasGet = false, hasPost = false, hasDefaults = false, hasThen = false;
        for (CompletionItem item : bareItems) {
            if ("get".equals(item.getLabel())) hasGet = true;
            if ("post".equals(item.getLabel())) hasPost = true;
            if ("defaults".equals(item.getLabel())) hasDefaults = true;
            if ("then".equals(item.getLabel())) hasThen = true;
        }
        assertTrue("axios. should suggest get", hasGet);
        assertTrue("axios. should suggest post", hasPost);
        assertTrue("axios. should suggest defaults", hasDefaults);
        assertFalse("axios. should NOT suggest then (not a promise yet)", hasThen);

        // 2. Invoked call `axios().`
        String invokedAxios = "axios().";
        List<CompletionItem> invokedItems = engine.getSuggestions(invokedAxios, invokedAxios.length());
        assertNotNull(invokedItems);
        boolean invokedHasThen = false;
        for (CompletionItem item : invokedItems) {
            if ("then".equals(item.getLabel())) invokedHasThen = true;
        }
        assertTrue("axios(). should suggest then", invokedHasThen);
    }

    @Test
    public void testNpmSignatureHelp_AxiosAndLodash() throws Exception {
        File nodeModules = new File(projectRoot, "node_modules");
        File axiosDir = new File(nodeModules, "axios");
        axiosDir.mkdirs();

        FileUtils.writeFile(new File(axiosDir, "package.json"), "{\"name\":\"axios\",\"types\":\"index.d.ts\"}");
        String dts = "export class Axios {\n" +
                "  get(url: string, config?: any): Promise<any>;\n" +
                "}\n" +
                "declare const axios: Axios;\n" +
                "export default axios;\n";
        FileUtils.writeFile(new File(axiosDir, "index.d.ts"), dts);
        TypeDefinitionIndexer.indexDependency(projectRoot, "axios");

        // 1. Test signature help for axios.get(|)
        String code1 = "import axios from 'axios';\naxios.get(";
        File mainFile = new File(projectRoot, "main.js");
        FileUtils.writeFile(mainFile, code1);

        LspDocument doc1 = new LspDocument(mainFile.getAbsolutePath(), code1, "javascript", 1);
        LspPosition pos1 = new LspPosition(1, 10); // inside `axios.get(`
        LspSignatureHelp sigHelp1 = JsSignatureParser.parse(doc1, pos1);

        assertNotNull("Signature help should not be null for axios.get(", sigHelp1);
        assertFalse(sigHelp1.signatures.isEmpty());
        LspSignatureHelp.LspSignatureInformation sigInfo1 = sigHelp1.signatures.get(0);
        assertTrue("display label should contain get(url: string, ...): " + sigInfo1.label,
                sigInfo1.label.contains("get(url: string"));
        assertFalse("display label should NOT have double parens: " + sigInfo1.label,
                sigInfo1.label.contains("(( "));
        assertEquals("Should have 2 parameters", 2, sigInfo1.parameters.size());
        assertEquals("First parameter name", "url: string", sigInfo1.parameters.get(0).label);
        assertEquals("Active parameter index should be 0", 0, sigHelp1.activeParameter);

        // 2. Test signature help for active parameter 1: axios.get('/api', |)
        String code2 = "import axios from 'axios';\naxios.get('/api', ";
        FileUtils.writeFile(mainFile, code2);
        LspDocument doc2 = new LspDocument(mainFile.getAbsolutePath(), code2, "javascript", 1);
        LspPosition pos2 = new LspPosition(1, 18);
        LspSignatureHelp sigHelp2 = JsSignatureParser.parse(doc2, pos2);

        assertNotNull(sigHelp2);
        assertEquals("Active parameter index should be 1", 1, sigHelp2.activeParameter);
    }

    private byte[] createTarHeader(String name, int size) {
        byte[] header = new byte[512];
        // Name (100 bytes)
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 100));

        // Mode (8 bytes): 0000644
        System.arraycopy("0000644\0".getBytes(StandardCharsets.US_ASCII), 0, header, 100, 8);
        // UID (8 bytes)
        System.arraycopy("0000000\0".getBytes(StandardCharsets.US_ASCII), 0, header, 108, 8);
        // GID (8 bytes)
        System.arraycopy("0000000\0".getBytes(StandardCharsets.US_ASCII), 0, header, 116, 8);

        // Size in octal (12 bytes)
        String sizeOctal = String.format(java.util.Locale.US, "%011o ", size);
        System.arraycopy(sizeOctal.getBytes(StandardCharsets.US_ASCII), 0, header, 124, 12);

        // Mtime (12 bytes)
        System.arraycopy("00000000000 ".getBytes(StandardCharsets.US_ASCII), 0, header, 136, 12);

        // Checksum placeholder (8 spaces)
        for (int i = 148; i < 156; i++) {
            header[i] = ' ';
        }

        // Typeflag (1 byte): '0' for normal file
        header[156] = '0';

        // Magic "ustar\0" (6 bytes)
        System.arraycopy("ustar\0".getBytes(StandardCharsets.US_ASCII), 0, header, 257, 6);
        // Version "00" (2 bytes)
        System.arraycopy("00".getBytes(StandardCharsets.US_ASCII), 0, header, 263, 2);

        // Compute checksum
        long chk = 0;
        for (byte b : header) {
            chk += (b & 0xFF);
        }

        String chkOctal = String.format(java.util.Locale.US, "%06o\0 ", chk);
        System.arraycopy(chkOctal.getBytes(StandardCharsets.US_ASCII), 0, header, 148, 8);

        return header;
    }
}
