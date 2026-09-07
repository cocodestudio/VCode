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

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class JsExportAliasTest extends BaseJsAstTest {

    private static final String MAIN_FILE = "/project/main.js";
    private static final String USER_FILE = "/project/user.js";

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
    public void testExportAliasDotCompletion() {
        publish(USER_FILE, "const obj = { name: 'Alice', age: 30, greet() {} };\nexport { obj as user };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "import { user } from './user';\nuser.";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'name' in user completions with export alias", s.contains("name"));
        assertTrue("Expected 'age' in user completions with export alias", s.contains("age"));
        assertTrue("Expected 'greet' in user completions with export alias", s.contains("greet"));
    }

    @Test
    public void testMultipleExportAliases() {
        publish(USER_FILE, "const a = { xProp: 1 };\nconst b = { yProp: 2 };\nexport { a as first, b as second };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);

        String code1 = "import { first } from './user';\nfirst.";
        List<CompletionItem> items1 = engine.getSuggestions(code1, code1.length());
        assertTrue("Expected 'xProp' in first completions", labels(items1).contains("xProp"));

        String code2 = "import { second } from './user';\nsecond.";
        List<CompletionItem> items2 = engine.getSuggestions(code2, code2.length());
        assertTrue("Expected 'yProp' in second completions", labels(items2).contains("yProp"));
    }

    @Test
    public void testExportAliasChainedMethodCompletion() {
        publish(USER_FILE, "const obj = { getProfile() { return { bio: 'dev', avatar: 'img.png' }; } };\nexport { obj as user };");

        JsAutoCompleteEngine engine = newEngine(MAIN_FILE);
        String code = "import { user } from './user';\nuser.getProfile().";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        Set<String> s = labels(items);
        assertTrue("Expected 'bio' on user.getProfile()", s.contains("bio"));
        assertTrue("Expected 'avatar' on user.getProfile()", s.contains("avatar"));
    }
}
