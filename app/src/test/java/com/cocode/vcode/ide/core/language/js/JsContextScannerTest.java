package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.model.CompletionItem;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for JsContextScanner and Phase 6 JS/TS completion isolation.
 */
public class JsContextScannerTest {

    @Test
    public void testDotMemberAccess() {
        String code = "const obj = { foo: 1 }; obj.f";
        int cursor = code.length();
        JsContextScanner.JsContext ctx = JsContextScanner.scan(code, cursor, false);

        assertEquals(JsContextScanner.ContextType.DOT_MEMBER, ctx.type);
        assertEquals("f", ctx.word);
        assertEquals(code.indexOf('.'), ctx.dotOffset);
    }

    @Test
    public void testOptionalChainingDotMember() {
        String code = "const obj = { foo: 1 }; obj?.bar";
        int cursor = code.length();
        JsContextScanner.JsContext ctx = JsContextScanner.scan(code, cursor, false);

        assertEquals(JsContextScanner.ContextType.DOT_MEMBER, ctx.type);
        assertEquals("bar", ctx.word);
        assertEquals(code.indexOf('.'), ctx.dotOffset);
    }

    @Test
    public void testFloatLiteralNotDotMember() {
        String code = "const x = 3.14";
        int cursor = code.length();
        JsContextScanner.JsContext ctx = JsContextScanner.scan(code, cursor, false);

        // 3.14 is a numeric literal, not an object dot-member access
        assertFalse(ctx.type == JsContextScanner.ContextType.DOT_MEMBER);
    }

    @Test
    public void testStringAndCommentSuppression() {
        String lineComment = "const a = 1; // some comment text here";
        JsContextScanner.JsContext ctxComment = JsContextScanner.scan(lineComment, lineComment.length(), false);
        assertEquals(JsContextScanner.ContextType.STRING_OR_COMMENT, ctxComment.type);

        String blockComment = "/* multi-line comment */ const b = 2;";
        JsContextScanner.JsContext ctxBlock = JsContextScanner.scan(blockComment, 15, false);
        assertEquals(JsContextScanner.ContextType.STRING_OR_COMMENT, ctxBlock.type);

        String regularString = "const msg = 'hello world';";
        JsContextScanner.JsContext ctxStr = JsContextScanner.scan(regularString, 20, false);
        assertEquals(JsContextScanner.ContextType.STRING_OR_COMMENT, ctxStr.type);
    }

    @Test
    public void testTemplateLiteralInterpolationNotSuppressed() {
        String tpl = "const msg = `User: ${name.toUp";
        int cursor = tpl.length();
        JsContextScanner.JsContext ctx = JsContextScanner.scan(tpl, cursor, false);

        // Active expression inside `${ ... }` should NOT be treated as a suppressed string
        assertFalse(ctx.type == JsContextScanner.ContextType.STRING_OR_COMMENT);
        assertEquals(JsContextScanner.ContextType.DOT_MEMBER, ctx.type);
        assertEquals("toUp", ctx.word);
    }

    @Test
    public void testImportPathContext() {
        String relativeImport = "import { something } from './utils/h";
        JsContextScanner.JsContext ctxRel = JsContextScanner.scan(relativeImport, relativeImport.length(), false);
        assertEquals(JsContextScanner.ContextType.IMPORT_PATH, ctxRel.type);
        assertTrue(ctxRel.isRelativeImport);
        assertEquals("./utils/h", ctxRel.importPath);

        String bareImport = "import React from 'rea";
        JsContextScanner.JsContext ctxBare = JsContextScanner.scan(bareImport, bareImport.length(), false);
        assertEquals(JsContextScanner.ContextType.IMPORT_PATH, ctxBare.type);
        assertFalse(ctxBare.isRelativeImport);
        assertEquals("rea", ctxBare.importPath);

        String requireImport = "const fs = require('fs";
        JsContextScanner.JsContext ctxReq = JsContextScanner.scan(requireImport, requireImport.length(), false);
        assertEquals(JsContextScanner.ContextType.IMPORT_PATH, ctxReq.type);
        assertFalse(ctxReq.isRelativeImport);
        assertEquals("fs", ctxReq.importPath);
    }

    @Test
    public void testDomSpecializedContexts() {
        String getById = "document.getElementById('header";
        JsContextScanner.JsContext ctxId = JsContextScanner.scan(getById, getById.length(), false);
        assertEquals(JsContextScanner.ContextType.GET_ELEMENT_BY_ID, ctxId.type);
        assertEquals("header", ctxId.word);

        String qSel = "document.querySelector('.nav-";
        JsContextScanner.JsContext ctxSel = JsContextScanner.scan(qSel, qSel.length(), false);
        assertEquals(JsContextScanner.ContextType.DOM_SELECTOR, ctxSel.type);
        assertEquals(".nav-", ctxSel.word);

        String addEvent = "btn.addEventListener('cli";
        JsContextScanner.JsContext ctxEvt = JsContextScanner.scan(addEvent, addEvent.length(), false);
        assertEquals(JsContextScanner.ContextType.EVENT_NAME, ctxEvt.type);
        assertEquals("cli", ctxEvt.word);
    }

    @Test
    public void testTypeScriptContexts() {
        String typeAnn = "const count: num";
        JsContextScanner.JsContext ctxAnn = JsContextScanner.scan(typeAnn, typeAnn.length(), true);
        assertEquals(JsContextScanner.ContextType.TYPE_ANNOTATION, ctxAnn.type);
        assertEquals("num", ctxAnn.word);

        String genericArg = "const list: Promise<User";
        JsContextScanner.JsContext ctxGen = JsContextScanner.scan(genericArg, genericArg.length(), true);
        assertEquals(JsContextScanner.ContextType.GENERIC_TYPE, ctxGen.type);
        assertEquals("User", ctxGen.word);

        String typeAssert = "const val = raw as Str";
        JsContextScanner.JsContext ctxAssert = JsContextScanner.scan(typeAssert, typeAssert.length(), true);
        assertEquals(JsContextScanner.ContextType.TYPE_ASSERTION, ctxAssert.type);
        assertEquals("Str", ctxAssert.word);
    }

    @Test
    public void testBareNpmImportEngineSuggestions() {
        JsAutoCompleteEngine engine = new JsAutoCompleteEngine(null);
        String code = "import fs from 'f";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        assertNotNull(items);
        assertFalse(items.isEmpty());

        boolean hasFs = false;
        for (CompletionItem ci : items) {
            if ("fs".equals(ci.getLabel())) {
                hasFs = true;
                assertEquals(1, ci.getReplaceLength());
            }
        }
        assertTrue("Node built-in module 'fs' must be suggested", hasFs);
    }

    @Test
    public void testStringSuppressionProducesZeroCompletions() {
        JsAutoCompleteEngine engine = new JsAutoCompleteEngine(null);
        String code = "const message = \"this is some random text ";
        List<CompletionItem> items = engine.getSuggestions(code, code.length());

        assertNotNull(items);
        assertTrue("Completions must be suppressed inside plain strings", items.isEmpty());
    }
}
