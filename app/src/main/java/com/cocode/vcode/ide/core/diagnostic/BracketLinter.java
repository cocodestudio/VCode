package com.cocode.vcode.ide.core.diagnostic;

import com.cocode.vcode.ide.core.editor.indent.BracketMatcher;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.List;

public class BracketLinter {
    public static List<Problem> analyze(File file, String text) {
        return BracketMatcher.findMismatches(file, text);
    }
}
