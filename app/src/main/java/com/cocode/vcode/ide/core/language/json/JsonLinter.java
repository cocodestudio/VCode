package com.cocode.vcode.ide.core.language.json;

import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Linter for JSON using the flat-array AST.
 * Flags syntax errors (N_ERROR) and duplicate keys within the same object.
 */
public class JsonLinter {

    public static List<Problem> analyze(File file, String text) {
        List<Problem> problems = new ArrayList<>();
        if (text == null || text.isEmpty()) return problems;
        
        JsonTokenStream stream = JsonLexer.tokenize(text);
        
        int mode = com.cocode.vcode.ide.core.language.base.ParseModeGate.select(
                com.cocode.vcode.ide.core.language.base.ParseModeGate.SizeMetric.NODES, 
                stream.length, 1000, 5000, 15000);
                
        if (mode == com.cocode.vcode.ide.core.language.js.ParseResult.MODE_TOKENIZE_ONLY) {
            return problems;
        }
        
        JsonSyntaxTree tree = JsonParser.parse(stream, text);

        for (int i = 1; i < tree.nodeCount; i++) {
            byte type = tree.nodeType[i];
            
            if (type == JsonSyntaxTree.N_ERROR) {
                int start = tree.nodeStart[i];
                int end = tree.nodeEnd[i];
                int len = Math.max(1, end - start);
                
                int line = LinterUtils.getLine(text, start);
                int col = LinterUtils.getColumn(text, start);
                
                String msg = tree.nodeName[i];
                if (msg == null || msg.isEmpty()) msg = "Syntax error";
                
                problems.add(new Problem(file, line, col, len, msg, Problem.Severity.ERROR));
            } else if (type == JsonSyntaxTree.N_OBJECT) {
                Set<String> seenKeys = new HashSet<>();
                int child = tree.nodeChild[i];
                int childLoop = 0;
                while (child > 0 && child < tree.nodeCount && ++childLoop <= tree.nodeCount) {
                    if (tree.nodeType[child] == JsonSyntaxTree.N_KEY) {
                        String keyName = tree.nodeName[child];
                        if (keyName != null && !keyName.isEmpty()) {
                            String normalized = normalizeKey(keyName);
                            if (!seenKeys.add(normalized)) {
                                int start = tree.nodeStart[child];
                                int end = tree.nodeEnd[child];
                                int len = Math.max(1, end - start);
                                int line = LinterUtils.getLine(text, start);
                                int col = LinterUtils.getColumn(text, start);
                                
                                problems.add(new Problem(file, line, col, len, "Duplicate object key: " + keyName, Problem.Severity.WARNING));
                            }
                        }
                    }
                    child = tree.nodeSibling[child];
                }
            }
        }
        
        return problems;
    }

    static String normalizeKey(String key) {
        if (key == null) return "";
        key = key.trim();
        int start = 0;
        int end = key.length();
        if (start < end && (key.charAt(start) == '"' || key.charAt(start) == '\'')) {
            start++;
        }
        if (end > start && (key.charAt(end - 1) == '"' || key.charAt(end - 1) == '\'')) {
            end--;
        }
        return key.substring(start, end).trim();
    }
}
