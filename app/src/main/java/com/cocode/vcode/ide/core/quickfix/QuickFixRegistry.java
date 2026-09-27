package com.cocode.vcode.ide.core.quickfix;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.diagnostic.ProblemCode;
import com.cocode.vcode.ide.core.model.Problem;
import com.cocode.vcode.ide.views.CodeEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry resolving actionable QuickFix intentions for diagnostic issues across HTML, JS, CSS, and JSON.
 */
public final class QuickFixRegistry {

    private QuickFixRegistry() {
    }

    /**
     * Resolves all applicable quick-fix intentions for the given diagnostic problem.
     *
     * @param problem diagnostic problem at cursor/gutter
     * @param editor  active code editor view
     * @return non-null list of available quick fixes
     */
    public static List<QuickFix> getFixesForProblem(Problem problem, CodeEditText editor) {
        if (problem == null || editor == null) return Collections.emptyList();

        List<QuickFix> fixes = new ArrayList<>();
        ProblemCode code = problem.getProblemCode();
        String msg = problem.getMessage() != null ? problem.getMessage() : "";

        // 1. HTML: Missing alt attribute on <img>
        if (code == ProblemCode.HTML_MISSING_ALT || (code == null && msg.contains("missing 'alt' attribute"))) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_alt, "Add alt=\"\" attribute");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    String lineText = ed.getLineText(pLine);
                    int imgIdx = lineText.toLowerCase().indexOf("<img");
                    if (imgIdx >= 0) {
                        int closeTagIdx = lineText.indexOf('>', imgIdx);
                        if (closeTagIdx > 0) {
                            int insertCol = closeTagIdx;
                            if (lineText.charAt(closeTagIdx - 1) == '/') {
                                insertCol = closeTagIdx - 1;
                            }
                            ed.beginAtomicGroup();
                            try {
                                ed.replaceRange(pLine, insertCol, pLine, insertCol, " alt=\"\"");
                            } finally {
                                ed.endAtomicGroup();
                            }
                        }
                    }
                }
            });
        }

        // 2. HTML: Missing type attribute on <button>
        if (code == ProblemCode.HTML_BUTTON_MISSING_TYPE || (code == null && msg.contains("missing 'type' attribute") && msg.contains("button"))) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_button_type, "Add type=\"button\" attribute");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    String lineText = ed.getLineText(pLine);
                    int btnIdx = lineText.toLowerCase().indexOf("<button");
                    if (btnIdx >= 0) {
                        int closeTagIdx = lineText.indexOf('>', btnIdx);
                        if (closeTagIdx > 0) {
                            int insertCol = closeTagIdx;
                            if (lineText.charAt(closeTagIdx - 1) == '/') {
                                insertCol = closeTagIdx - 1;
                            }
                            ed.beginAtomicGroup();
                            try {
                                ed.replaceRange(pLine, insertCol, pLine, insertCol, " type=\"button\"");
                            } finally {
                                ed.endAtomicGroup();
                            }
                        }
                    }
                }
            });
        }

        // 3. HTML: Empty <title> element
        if (code == ProblemCode.HTML_EMPTY_TITLE || (code == null && msg.contains("Empty '<title>'"))) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_add_title, "Add title text");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    String lineText = ed.getLineText(pLine);
                    int titleIdx = lineText.toLowerCase().indexOf("<title>");
                    if (titleIdx >= 0) {
                        int insertCol = titleIdx + 7;
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, insertCol, pLine, insertCol, "Document Title");
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // 4. JS/TS: Const reassignment -> convert const to let
        if (code == ProblemCode.JS_CONST_REASSIGNMENT || (code == null && msg.contains("reassign 'const' variable"))) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_const_to_let, "Convert 'const' declaration to 'let'");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    String varName = "";
                    int q1 = prob.getMessage().lastIndexOf('\'');
                    if (q1 > 0) {
                        int q0 = prob.getMessage().lastIndexOf('\'', q1 - 1);
                        if (q0 >= 0) {
                            varName = prob.getMessage().substring(q0 + 1, q1);
                        }
                    }
                    if (varName.isEmpty()) return;

                    int curLine = Math.max(0, prob.getLine() - 1);
                    for (int l = curLine; l >= 0; l--) {
                        String lt = ed.getLineText(l);
                        int constIdx = lt.indexOf("const ");
                        if (constIdx >= 0 && lt.contains(varName)) {
                            ed.beginAtomicGroup();
                            try {
                                ed.replaceRange(l, constIdx, l, constIdx + 5, "let");
                            } finally {
                                ed.endAtomicGroup();
                            }
                            break;
                        }
                    }
                }
            });
        }

        // 5. JSON: Disallowed comment -> remove comment line
        if (code == ProblemCode.JSON_COMMENTS_DISALLOWED || (code == null && msg.contains("Comments are not permitted in standard JSON"))) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_remove_json_comment, "Remove disallowed comment");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine >= 0 && pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        ed.beginAtomicGroup();
                        try {
                            if (pLine + 1 < ed.getLineCount()) {
                                ed.replaceRange(pLine, 0, pLine + 1, 0, "");
                            } else {
                                ed.replaceRange(pLine, 0, pLine, lt.length(), "");
                            }
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // 6. JS/TS: Missing semicolon -> insert semicolon
        if (code == ProblemCode.JS_MISSING_SEMICOLON || (code == null && msg.toLowerCase().contains("missing semicolon"))) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_insert_semicolon, "Insert missing ';'");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine >= 0 && pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        int col = prob.getColumn() - 1;
                        int insertCol = (col >= 0 && col <= lt.length()) ? col : lt.length();
                        ed.beginAtomicGroup();
                        try {
                            ed.replaceRange(pLine, insertCol, pLine, insertCol, ";");
                        } finally {
                            ed.endAtomicGroup();
                        }
                    }
                }
            });
        }

        // 7. JS/TS: Unused variable -> prefix with '_'
        if (code == ProblemCode.JS_UNUSED_VARIABLE || (code == null && msg.contains("never used"))) {
            fixes.add(new QuickFix() {
                @Override
                public String getTitle() {
                    return getFixTitle(editor, R.string.vcode_quick_fix_prefix_unused, "Prefix unused identifier with '_'");
                }

                @Override
                public void apply(CodeEditText ed, Problem prob) {
                    String varName = "";
                    int q1 = prob.getMessage().lastIndexOf('\'');
                    if (q1 > 0) {
                        int q0 = prob.getMessage().lastIndexOf('\'', q1 - 1);
                        if (q0 >= 0) {
                            varName = prob.getMessage().substring(q0 + 1, q1);
                        }
                    }
                    int pLine = Math.max(0, prob.getLine() - 1);
                    if (pLine >= 0 && pLine < ed.getLineCount()) {
                        String lt = ed.getLineText(pLine);
                        int vIdx = !varName.isEmpty() ? lt.indexOf(varName) : -1;
                        if (vIdx < 0 && prob.getColumn() > 0) {
                            vIdx = prob.getColumn() - 1;
                        }
                        if (vIdx >= 0 && vIdx <= lt.length()) {
                            ed.beginAtomicGroup();
                            try {
                                ed.replaceRange(pLine, vIdx, pLine, vIdx, "_");
                            } finally {
                                ed.endAtomicGroup();
                            }
                        }
                    }
                }
            });
        }

        return fixes;
    }

    private static String getFixTitle(CodeEditText ed, int resId, String defaultTitle) {
        if (ed != null && ed.getContext() != null) {
            try {
                return ed.getContext().getString(resId);
            } catch (Throwable ignored) {
            }
        }
        return defaultTitle;
    }

    /**
     * Quickly checks whether the problem has any registered quick fix intentions.
     */
    public static boolean hasFixes(Problem problem) {
        if (problem == null) return false;
        ProblemCode code = problem.getProblemCode();
        String msg = problem.getMessage() != null ? problem.getMessage() : "";
        if (code == ProblemCode.HTML_MISSING_ALT || (code == null && msg.contains("missing 'alt' attribute"))) return true;
        if (code == ProblemCode.HTML_BUTTON_MISSING_TYPE || (code == null && msg.contains("missing 'type' attribute") && msg.contains("button"))) return true;
        if (code == ProblemCode.HTML_EMPTY_TITLE || (code == null && msg.contains("Empty '<title>'"))) return true;
        if (code == ProblemCode.JS_CONST_REASSIGNMENT || (code == null && msg.contains("reassign 'const' variable"))) return true;
        if (code == ProblemCode.JSON_COMMENTS_DISALLOWED || (code == null && msg.contains("Comments are not permitted in standard JSON"))) return true;
        if (code == ProblemCode.JS_MISSING_SEMICOLON || (code == null && msg.toLowerCase().contains("missing semicolon"))) return true;
        if (code == ProblemCode.JS_UNUSED_VARIABLE || (code == null && msg.contains("never used"))) return true;
        return false;
    }
}
