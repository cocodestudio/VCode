package com.cocode.vcode.ide.core.language.js;
import org.junit.Test;
public class TestScript {
    @Test
    public void runTest() {
        String code = "const a = []; const s = \"hi\"; const m = new Map(); a.";
        JsAutoCompleteEngine engine = new JsAutoCompleteEngine(null);
        java.util.List<com.cocode.vcode.ide.core.model.CompletionItem> compsA = engine.getSuggestions(code, code.indexOf("a.") + 2);
        for(com.cocode.vcode.ide.core.model.CompletionItem item : compsA) {
            System.out.println(item.getLabel());
        }
    }
}
