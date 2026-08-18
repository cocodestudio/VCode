package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.diagnostic.util.TokenMask;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JsLinter {

    // ── Patterns compiled once ──────────────────────────────────────────────
    // ── Entry point ─────────────────────────────────────────────────────────
    public static List<Problem> analyze(File file, String text) {
        if (text == null || text.trim().isEmpty()) return java.util.Collections.emptyList();

        List<Problem> problems = new ArrayList<>();
        TokenMask mask = TokenMask.build(text, "js");
        String[] lines = LinterUtils.splitLines(text);

        JsLinterCoreRules.checkVarUsage(file, text, mask, problems);
        JsLinterCoreRules.checkConsole(file, text, mask, problems);
        JsLinterCoreRules.checkDebugger(file, text, mask, problems);
        JsLinterCoreRules.checkLooseEquality(file, text, mask, problems);
        JsLinterCoreRules.checkEval(file, text, mask, problems);
        JsLinterCoreRules.checkSetTimeoutString(file, text, mask, problems);
        JsLinterCoreRules.checkNewObjectArray(file, text, mask, problems);
        JsLinterCoreRules.checkWith(file, text, mask, problems);
        JsLinterCoreRules.checkNaNComparison(file, text, mask, problems);
        JsLinterCoreRules.checkEmptyCatch(file, text, mask, problems);
        JsLinterCoreRules.checkInfiniteLoop(file, text, mask, problems);
        JsLinterCoreRules.checkSwitchDefault(file, text, mask, problems);
        JsLinterCoreRules.checkPromiseChain(file, text, mask, problems);
        JsLinterCoreRules.checkMissingAwait(file, text, lines, mask, problems);
        JsLinterCoreRules.checkAsyncNoAwait(file, text, mask, problems);
        JsLinterStyleRules.checkTypeofComparison(file, text, mask, problems);
        JsLinterStyleRules.checkTodoFixme(file, text, problems);
        JsLinterStyleRules.checkFunctionParams(file, text, mask, problems);
        JsLinterStyleRules.checkDivisionByZero(file, text, mask, problems);
        JsLinterStyleRules.checkUnreachableCode(file, text, lines, mask, problems);
        JsLinterStyleRules.checkConstReassign(file, text, mask, problems);
        JsLinterStyleRules.checkUnclosedString(file, text, lines, mask, problems);
        JsLinterStyleRules.checkReturnOutsideFunction(file, text, mask, problems);
        JsLinterStyleRules.checkBreakContinue(file, text, mask, problems);
        JsLinterStyleRules.checkUnusedVars(file, text, mask, problems);
        JsLinterStyleRules.checkArrowSimplification(file, text, mask, problems);
        JsLinterStyleRules.checkStringConcat(file, text, mask, problems);
        JsLinterStyleRules.checkOptionalChaining(file, text, mask, problems);
        JsLinterStyleRules.checkNullishCoalescing(file, text, mask, problems);
        JsLinterStyleRules.checkStringConcatInLoop(file, text, mask, problems);

        return problems;
    }


    // ── Rule implementations ────────────────────────────────────────────────

}
