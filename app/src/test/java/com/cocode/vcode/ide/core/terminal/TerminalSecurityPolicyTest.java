package com.cocode.vcode.ide.core.terminal;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying terminal security boundaries:
 * 1. Restricting deletion and modification of .vcode and its contents.
 * 2. Restricting deletion of out-of-scope files and directories.
 * 3. Permitting safe project-internal file operations.
 */
public class TerminalSecurityPolicyTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File projectRoot;
    private File vcodeDir;
    private File srcDir;

    @Before
    public void setUp() throws IOException {
        projectRoot = tempFolder.newFolder("MyProject");
        vcodeDir = new File(projectRoot, ".vcode");
        assertTrue(vcodeDir.mkdir());

        File vcodeState = new File(vcodeDir, "state");
        assertTrue(vcodeState.mkdir());
        File sessionFile = new File(vcodeState, "session.json");
        assertTrue(sessionFile.createNewFile());

        srcDir = new File(projectRoot, "src");
        assertTrue(srcDir.mkdir());
        File indexHtml = new File(projectRoot, "index.html");
        assertTrue(indexHtml.createNewFile());
        File appJs = new File(srcDir, "app.js");
        assertTrue(appJs.createNewFile());
    }

    // ==========================================
    // 1. .vcode Direct Deletion Attempts (Must be DENIED)
    // ==========================================

    @Test
    public void testRmRfVCodeDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("rm -rf .vcode", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode is restricted"));
    }

    @Test
    public void testRmRfDotSlashVCodeDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("rm -rf ./.vcode", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode is restricted"));
    }

    @Test
    public void testRmRfVCodeTrailingSlashDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("rm -rf .vcode/", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode is restricted"));
    }

    @Test
    public void testRmSubfileInsideVCodeDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("rm .vcode/state/session.json", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode is restricted"));
    }

    @Test
    public void testRmRmdirUnlinkVCodeDenied() {
        assertFalse(TerminalSecurityPolicy.validateCommand("rmdir .vcode", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("unlink .vcode", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("shred .vcode/state/session.json", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testRmVCodeFromSubdirectoryDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("rm -rf ../.vcode", projectRoot, srcDir);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode is restricted"));
    }

    @Test
    public void testRmVCodeWithHomeExpansionDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("rm -rf ~/.vcode", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode is restricted"));
    }

    @Test
    public void testRmVCodeWithPwdExpansionDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("rm -rf $PWD/.vcode", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode is restricted"));
    }

    // ==========================================
    // 2. Wildcards Targeting .vcode (Must be DENIED)
    // ==========================================

    @Test
    public void testWildcardDotStarDeniedInRoot() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("rm -rf .*", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode is restricted"));
    }

    @Test
    public void testWildcardDotVDeniedInRoot() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("rm -rf .v*", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode is restricted"));
    }

    @Test
    public void testWildcardDotVCodeStarDeniedInRoot() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("rm -rf .vcode*", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode is restricted"));
    }

    @Test
    public void testWildcardInsideVCodeDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("rm -rf .vcode/*", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode is restricted"));
    }

    // ==========================================
    // 3. Out of Scope Deletion Attempts (Must be DENIED)
    // ==========================================

    @Test
    public void testRmSdcardDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("rm -rf /sdcard/DCIM", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains("outside workspace scope"));
    }

    @Test
    public void testRmParentDirectoryDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("rm -rf ..", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains("outside workspace scope"));
    }

    @Test
    public void testRmRootDirectoryDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("rm -rf /", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains("outside workspace scope"));
    }

    @Test
    public void testRmProjectRootItselfDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("rm -rf .", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains("cannot delete project root"));
    }

    // ==========================================
    // 4. Move (mv) Operations
    // ==========================================

    @Test
    public void testMoveVCodeDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("mv .vcode .vcode_bak", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode is restricted"));
    }

    @Test
    public void testMoveToVCodeDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("mv index.html .vcode/", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode is restricted"));
    }

    @Test
    public void testMoveOutOfScopeDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("mv index.html /sdcard/", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains("outside workspace scope"));
    }

    @Test
    public void testMoveFromOutOfScopeDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("mv /sdcard/pic.jpg .", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains("outside workspace scope"));
    }

    @Test
    public void testMoveWithinScopeAllowed() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("mv index.html index.backup.html", projectRoot, projectRoot);
        assertTrue(result.isAllowed());
    }

    // ==========================================
    // 5. Output Redirection Security
    // ==========================================

    @Test
    public void testRedirectTruncateVCodeDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("echo 'hacked' > .vcode", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode is restricted"));
    }

    @Test
    public void testRedirectIntoVCodeDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("echo '{}' > .vcode/state/session.json", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode is restricted"));
    }

    @Test
    public void testRedirectOutOfScopeDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("echo 'foo' > /sdcard/evil.txt", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains("outside workspace scope"));
    }

    @Test
    public void testRedirectInsideScopeAllowed() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("echo 'const a = 1;' > src/app.js", projectRoot, projectRoot);
        assertTrue(result.isAllowed());
    }

    // ==========================================
    // 6. Wrappers, Subshells, and Chaining
    // ==========================================

    @Test
    public void testSubshellShCPrevented() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("sh -c \"rm -rf .vcode\"", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode is restricted"));
    }

    @Test
    public void testSubshellBashCPrevented() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("bash -c \"rm -rf /sdcard\"", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains("outside workspace scope"));
    }

    @Test
    public void testCompoundCommandsPrevented() {
        assertFalse(TerminalSecurityPolicy.validateCommand("echo hello && rm -rf .vcode", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("rm -rf .vcode || echo failed", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("echo 1; rm -rf /sdcard; echo 2", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testCommandWrappersPrevented() {
        assertFalse(TerminalSecurityPolicy.validateCommand("toybox rm -rf .vcode", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("sudo rm -rf .vcode", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("env FOO=BAR rm -rf .vcode", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testQuotedTargetPrevented() {
        assertFalse(TerminalSecurityPolicy.validateCommand("rm -rf \".vcode\"", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("rm -rf '.vcode'", projectRoot, projectRoot).isAllowed());
    }

    // ==========================================
    // 7. Find Command Security
    // ==========================================

    @Test
    public void testFindDeleteOnProjectRootDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("find . -name \"*.tmp\" -delete", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains("find: -delete on project root is restricted to protect .vcode"));
    }

    @Test
    public void testFindDeleteInSafeSubdirAllowed() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("find src -name \"*.tmp\" -delete", projectRoot, projectRoot);
        assertTrue(result.isAllowed());
    }

    @Test
    public void testFindWithoutDeleteAllowed() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("find . -name \"*.js\"", projectRoot, projectRoot);
        assertTrue(result.isAllowed());
    }

    // ==========================================
    // 8. Navigation (cd) Security
    // ==========================================

    @Test
    public void testCdIntoVCodeDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("cd .vcode", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains(".vcode directory is restricted"));
    }

    @Test
    public void testCdOutOfScopeDenied() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("cd /sdcard", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains("restricted to project workspace"));
    }

    @Test
    public void testCdInsideScopeAllowed() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("cd src", projectRoot, projectRoot);
        assertTrue(result.isAllowed());
    }

    // ==========================================
    // 9. Legitimate Workspace Deletions (Must be ALLOWED)
    // ==========================================

    @Test
    public void testSafeProjectFileDeletionAllowed() {
        assertTrue(TerminalSecurityPolicy.validateCommand("rm index.html", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("rm -rf src", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("rm -f src/app.js", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("rm -rf dist", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testSafeProjectWildcardAllowed() {
        // Star without leading dot does not match .vcode
        assertTrue(TerminalSecurityPolicy.validateCommand("rm *.js", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("rm -rf *", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testEchoNotTreatedAsDelete() {
        assertTrue(TerminalSecurityPolicy.validateCommand("echo 'rm -rf .vcode'", projectRoot, projectRoot).isAllowed());
    }
}
