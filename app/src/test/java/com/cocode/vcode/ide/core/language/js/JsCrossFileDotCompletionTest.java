package com.cocode.vcode.ide.core.language.js;

import android.content.Context;

import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class JsCrossFileDotCompletionTest extends BaseJsAstTest {

    private static final String DIR = "/project";
    private static final String MAIN_FILE = "/project/main.js";
    private static final String USER_FILE = "/project/user.js";
    private static final String CONFIG_FILE = "/project/config.js";
    private static final String API_FILE = "/project/api.js";

    @Before
    public void setup() {
        ProjectIndex.getInstance().clear();
    }

    @After
    public void cleanup() {
        ProjectIndex.getInstance().clear();
    }

    private JsAutoCompleteEngine newEngine(String currentFileUri) {
        Context ctx = RuntimeEnvironment.getApplication();
        JsAutoCompleteEngine engine = new JsAutoCompleteEngine(ctx);
        engine.setCurrentFile(new File(currentFileUri));
        return engine;
    }

    private void publish(String uri, String source) {
        ProjectIndex.getInstance().updateParseResult(uri, parseFile(uri, source));
    }

    private static Set<String> labels(List<CompletionItem> items) {
        Set<String> s = new HashSet<>();
        if (items == null) return s;
        for (CompletionItem c : items) s.add(c.getLabel());
        return s;
    }

    @Test
    public void testNamedImportObjectMemberCompletion() {
        publish(USER_FILE, "export const user = { name: 'Alice', age: 30 };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "import { user } from './user';\nuser.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'name' in user completions", s.contains("name"));
        assertTrue("Expected 'age' in user completions", s.contains("age"));
    }

    @Test
    public void testNestedObjectDotCompletion() {
        publish(USER_FILE, "export const user = { address: { city: 'Wonderland', zip: 12345 } };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "import { user } from './user';\nuser.address.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'city' in user.address completions", s.contains("city"));
        assertTrue("Expected 'zip' in user.address completions", s.contains("zip"));
    }

    @Test
    public void testDeepChainingDotCompletion() {
        publish(USER_FILE, "export const user = { address: { geo: { lat: 10, lng: 20 } } };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "import { user } from './user';\nuser.address.geo.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'lat' in user.address.geo completions", s.contains("lat"));
        assertTrue("Expected 'lng' in user.address.geo completions", s.contains("lng"));
    }

    @Test
    public void testDeepChainingPrimitiveNumberMethods() {
        publish(USER_FILE, "export const user = { address: { geo: { lat: 10.5 } } };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "import { user } from './user';\nuser.address.geo.lat.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'toFixed' for number lat", s.contains("toFixed"));
        assertTrue("Expected 'toPrecision' for number lat", s.contains("toPrecision"));
    }

    @Test
    public void testArrayMemberCompletion() {
        publish(USER_FILE, "export const user = { scores: [10, 20, 30] };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "import { user } from './user';\nuser.scores.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'map' on user.scores", s.contains("map"));
        assertTrue("Expected 'filter' on user.scores", s.contains("filter"));
        assertTrue("Expected 'length' on user.scores", s.contains("length"));
        assertTrue("Expected 'push' on user.scores", s.contains("push"));
    }

    @Test
    public void testArrayIndexingNumberMethods() {
        publish(USER_FILE, "export const user = { scores: [10, 20, 30] };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "import { user } from './user';\nuser.scores[0].";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'toFixed' on user.scores[0]", s.contains("toFixed"));
    }

    @Test
    public void testArrayOfObjectsIndexingCompletion() {
        publish(USER_FILE, "export const user = { items: [{ id: 1, title: 'Book' }] };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "import { user } from './user';\nuser.items[0].";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'id' on user.items[0]", s.contains("id"));
        assertTrue("Expected 'title' on user.items[0]", s.contains("title"));
    }

    @Test
    public void testStringMethodCompletion() {
        publish(USER_FILE, "export const user = { name: 'Alice' };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "import { user } from './user';\nuser.name.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'toUpperCase' on user.name", s.contains("toUpperCase"));
        assertTrue("Expected 'trim' on user.name", s.contains("trim"));
        assertTrue("Expected 'toLowerCase' on user.name", s.contains("toLowerCase"));
    }

    @Test
    public void testBooleanMethodCompletion() {
        publish(USER_FILE, "export const user = { isActive: true };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "import { user } from './user';\nuser.isActive.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'toString' on user.isActive", s.contains("toString"));
        assertTrue("Expected 'valueOf' on user.isActive", s.contains("valueOf"));
    }

    @Test
    public void testMethodReturnShapeCompletion() {
        publish(USER_FILE, "export const user = { getProfile() { return { bio: 'dev', avatar: 'img.png' }; } };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "import { user } from './user';\nuser.getProfile().";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'bio' on user.getProfile()", s.contains("bio"));
        assertTrue("Expected 'avatar' on user.getProfile()", s.contains("avatar"));
    }

    @Test
    public void testFunctionReturnShapeCompletion() {
        publish(USER_FILE, "export function getUser() { return { profile: { theme: 'dark', lang: 'en' } }; }");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "import { getUser } from './user';\ngetUser().profile.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'theme' on getUser().profile", s.contains("theme"));
        assertTrue("Expected 'lang' on getUser().profile", s.contains("lang"));
    }

    @Test
    public void testDefaultExportAndImportCompletion() {
        publish(CONFIG_FILE, "export default { apiKey: 'secret', endpoint: 'https://api.example.com' };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "import config from './config';\nconfig.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'apiKey' on config", s.contains("apiKey"));
        assertTrue("Expected 'endpoint' on config", s.contains("endpoint"));
    }

    @Test
    public void testAliasedImportCompletion() {
        publish(USER_FILE, "export const user = { id: 42, role: 'admin' };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "import { user as u } from './user';\nu.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'id' on u", s.contains("id"));
        assertTrue("Expected 'role' on u", s.contains("role"));
    }

    @Test
    public void testNamespaceImportCompletion() {
        publish(API_FILE, "export const user = { name: 'Alice', address: { city: 'Paris' } };\nexport const version = '1.0';");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code1 = "import * as api from './api';\napi.";
        List<CompletionItem> items1 = engine.getSuggestions(code1, code1.length());

        Set<String> s1 = labels(items1);
        assertTrue("Expected 'user' in api namespace", s1.contains("user"));
        assertTrue("Expected 'version' in api namespace", s1.contains("version"));

        String code2 = "import * as api from './api';\napi.user.address.";
        List<CompletionItem> items2 = engine.getSuggestions(code2, code2.length());
        Set<String> s2 = labels(items2);
        assertTrue("Expected 'city' in api.user.address", s2.contains("city"));
    }

    @Test
    public void testCommonJsDestructuredRequireCompletion() {
        publish(USER_FILE, "export const user = { details: { email: 'user@test.com' } };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "const { user } = require('./user');\nuser.details.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'email' on user.details with require", s.contains("email"));
    }

    @Test
    public void testCommonJsDefaultRequireCompletion() {
        publish(CONFIG_FILE, "export default { host: 'localhost', port: 8080 };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "const config = require('./config');\nconfig.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'host' on config with require", s.contains("host"));
        assertTrue("Expected 'port' on config with require", s.contains("port"));
    }
}
