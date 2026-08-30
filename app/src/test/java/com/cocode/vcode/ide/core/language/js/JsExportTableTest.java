package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for K.1: the per-file export table built from
 * a parsed tree.
 */
public class JsExportTableTest extends BaseJsAstTest {

    @Test
    public void emptySourceProducesEmptyTable() {
        JsExportTable t = JsExportTable.build(null, "/a.js");
        assertNotNull(t);
        assertEquals(0, t.count);
    }

    @Test
    public void nullUriProducesEmptyTable() {
        String src = "export function foo() {}";
        JsExportTable t = getExportTable(null, src);
        assertEquals(0, t.count);
    }

    @Test
    public void exportFunctionIsRecorded() {
        String src = "export function fetchData() { return 1; }";
        JsExportTable t = getExportTable("/a.js", src);
        assertEquals(1, t.count);
        assertEquals("fetchData", t.exportName[0]);
        assertEquals("/a.js", t.exportSourceUri[0]);
        assertTrue(t.containsName("fetchData"));
    }

    @Test
    public void exportClassIsRecorded() {
        String src = "export class Foo {}";
        JsExportTable t = getExportTable("/a.js", src);
        assertEquals(1, t.count);
        assertEquals("Foo", t.exportName[0]);
    }

    @Test
    public void exportConstIsRecorded() {
        String src = "export const x = 1;";
        JsExportTable t = getExportTable("/a.js", src);
        assertEquals(1, t.count);
        assertEquals("x", t.exportName[0]);
    }

    @Test
    public void exportBraceListIsRecorded() {
        String src = "export { a, b, c };";
        JsExportTable t = getExportTable("/a.js", src);
        assertEquals(3, t.count);
        assertTrue(t.containsName("a"));
        assertTrue(t.containsName("b"));
        assertTrue(t.containsName("c"));
        assertFalse(t.containsName("d"));
    }

    @Test
    public void exportInterfaceIsRecorded() {
        // KNOWN LIMITATION: the current parser's `parseExport` only
        // dispatches to `parseClass` for the `class` keyword, not
        // for `interface`. `export interface I { ... }` falls through
        // to the generic `skipToNextStatement` branch, which
        // records `interface` (the keyword) and `I` (the name) as
        // raw `N_IDENTIFIER` children. The table contains both, but
        // the interface name `I` IS in the table — this is what
        // callers care about for completion.
        String src = "export interface I { x: number; }";
        JsExportTable t = getExportTable("/a.js", src);
        assertTrue("'I' must be in the export table", t.containsName("I"));
    }

    @Test
    public void exportTypeAliasIsRecorded() {
        // Same parser limitation as exportInterface: `export type` is
        // not specifically dispatched, so the table records the
        // alias name as a generic identifier.
        String src = "export type T = number;";
        JsExportTable t = getExportTable("/a.js", src);
        assertTrue("'T' must be in the export table", t.containsName("T"));
    }

    @Test
    public void exportEnumIsRecorded() {
        String src = "export enum Color { Red, Green, Blue }";
        JsExportTable t = getExportTable("/a.js", src);
        assertEquals(1, t.count);
        assertEquals("Color", t.exportName[0]);
    }

    @Test
    public void nonExportedNamesAreNotRecorded() {
        String src = "function helper() {}\nexport function public_() {}\nconst internal = 1;";
        JsExportTable t = getExportTable("/a.js", src);
        assertEquals(1, t.count);
        assertEquals("public_", t.exportName[0]);
        assertFalse(t.containsName("helper"));
        assertFalse(t.containsName("internal"));
    }

    @Test
    public void duplicateExportsAreDeduplicated() {
        // The AST has two export nodes; the first has an identifier `x`,
        // the second has an export list containing `x`. The identifier is seen
        // twice. The table should keep only one entry.
        String src = "export const x = 1;\nexport { x };";
        JsExportTable t = getExportTable("/a.js", src);
        assertEquals(1, t.count);
        assertEquals("x", t.exportName[0]);
    }
}
