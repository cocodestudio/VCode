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

    // ==========================================
    // 10. Fork Bomb Detection (Must be BLOCKED)
    // ==========================================

    @Test
    public void testForkBombClassicBlocked() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand(":(){ :|:& };:", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains("fork bomb"));
    }

    @Test
    public void testForkBombCompactBlocked() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand(":(){:|:&};:", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains("fork bomb"));
    }

    @Test
    public void testForkBombGenericFunctionBlocked() {
        TerminalSecurityPolicy.ValidationResult result =
                TerminalSecurityPolicy.validateCommand("bomb() { bomb | bomb & }; bomb", projectRoot, projectRoot);
        assertFalse(result.isAllowed());
        assertTrue(result.getErrorMessage().contains("fork bomb"));
    }

    // ==========================================
    // 11. Forbidden System & Hardware Commands (Must be BLOCKED)
    // ==========================================

    @Test
    public void testForbiddenSystemCommandsBlocked() {
        assertFalse(TerminalSecurityPolicy.validateCommand("su", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("reboot", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("shutdown -h now", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("poweroff", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("mkfs.ext4 /dev/block/sda", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("fdisk /dev/block/mmcblk0", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("setenforce 0", projectRoot, projectRoot).isAllowed());
    }

    // ==========================================
    // 12. Raw Device and Kernel Node Access (Must be BLOCKED)
    // ==========================================

    @Test
    public void testRawDevicePathArgumentsBlocked() {
        assertFalse(TerminalSecurityPolicy.validateCommand("cat /dev/block/bootdevice", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("hexdump /dev/mem", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("cat /proc/kmsg", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testRawDeviceRedirectionBlocked() {
        assertFalse(TerminalSecurityPolicy.validateCommand("echo 'malicious' > /dev/block/mmcblk0", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("echo 'payload' >> /dev/mem", projectRoot, projectRoot).isAllowed());
    }

    // ==========================================
    // 13. Specialized Commands (dd, truncate, cp, tee, chmod) Security
    // ==========================================

    @Test
    public void testDdDestinationsRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("dd if=/dev/zero of=/dev/block/sda", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("dd if=/dev/zero of=.vcode/config", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("dd if=/dev/zero of=/sdcard/hack", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("dd if=/dev/zero of=test.bin bs=1024 count=1", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testTruncateDestinationsRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("truncate -s 0 .vcode/state/session.json", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("truncate -s 0 /sdcard/file.txt", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("truncate -s 0 /dev/block/sda", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("truncate -s 0 index.html", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testCpDestinationsRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("cp index.html .vcode/", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("cp index.html /sdcard/", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("cp index.html /dev/block/sda", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("cp index.html index.backup.html", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("cp -r src dist", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testTeeDestinationsRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("echo 123 | tee .vcode/state/session.json", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("echo 123 | tee /sdcard/out.txt", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("echo 123 | tee /dev/mem", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("echo 123 | tee src/output.txt", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testChmodDestinationsRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("chmod -R 000 /", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("chmod 777 .vcode", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("chmod 644 /dev/block/sda", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("chmod 644 /sdcard/file.txt", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("chmod 755 src/app.js", projectRoot, projectRoot).isAllowed());
    }

    // ==========================================
    // 14. Command Substitution & Subshell Security
    // ==========================================

    @Test
    public void testCommandSubstitutionRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("echo $(rm -rf .vcode)", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("echo $(reboot)", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("echo `su`", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("VAR=\"$(rm -rf .vcode)\"", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("cat <(rm -rf .vcode)", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("echo 'safe $(substitution)'", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testAndroidFrameworkAndDeviceCommandsRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("pm uninstall com.example.app", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("am start -a android.intent.action.VIEW", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("content query --uri content://sms/", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("screencap /sdcard/screen.png", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("logcat -d", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("svc wifi disable", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("setprop persist.sys.evil 1", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testEnvFlagObfuscationRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("env -i rm -rf .vcode", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("env -u FOO rm -rf .vcode", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("env --null rm -rf .vcode", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testXargsWrappingRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("xargs rm -rf .vcode", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("xargs reboot", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("xargs -I {} rm -rf .vcode", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testSubshellFlagsRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("bash -lc \"rm -rf .vcode\"", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("sh -cl \"reboot\"", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("bash --command \"rm -rf .vcode\"", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testAdvancedRedirectionRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("echo 1 >| .vcode/state.json", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("echo 1 &> /dev/mem", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("cat < /dev/block/bootdevice", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testExpandedDangerousCommandsRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("mount -o remount,rw /system", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("umount /data", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("chroot /", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("dmesg", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("locksettings clear", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("telecom call 911", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("uiautomator dump", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("monkey -p com.android.settings 100", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("ime enable evil.ime", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("bu backup", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("bmgr restore", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("run-as com.other.app", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("magisk --daemon", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("tcpdump -i any", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testCommandWrappersRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("nice -n 10 rm -rf .vcode", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("timeout 5 reboot", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("timeout -s 9 5s rm -rf .vcode", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("watch -n 1 rm -rf .vcode", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("eval \"rm -rf .vcode\"", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("eval reboot", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("eval \"echo hello\"", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testTouchAndMkdirAndLnRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("touch .vcode/state.json", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("touch /dev/block/sda", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("touch /sdcard/evil.txt", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("touch app.js", projectRoot, projectRoot).isAllowed());

        assertFalse(TerminalSecurityPolicy.validateCommand("mkdir .vcode/backdoor", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("mkdir /sdcard/bad_folder", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("mkdir src", projectRoot, projectRoot).isAllowed());

        assertFalse(TerminalSecurityPolicy.validateCommand("ln -s /etc .vcode/link", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("ln -s /dev/block link_dev", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("ln -s /dev/mem link_mem", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("ln -s index.html main.html", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testTargetDirectoryOptionRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("mv -t .vcode file.txt", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("mv -t /sdcard file.txt", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("mv --target-directory=.vcode file.txt", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("mv -t dist file.txt", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testFindExecSubcommandRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("find . -exec reboot \\;", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("find . -exec rm -rf .vcode \\;", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("find . -exec pm uninstall com.example \\;", projectRoot, projectRoot).isAllowed());
        assertTrue(TerminalSecurityPolicy.validateCommand("find . -name \"*.js\" -exec grep -H \"foo\" {} \\;", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testDevicePathTraversalRestricted() {
        assertFalse(TerminalSecurityPolicy.validateCommand("cat ../../dev/block/bootdevice", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("cat /workspace/../../dev/mem", projectRoot, projectRoot).isAllowed());
        assertFalse(TerminalSecurityPolicy.validateCommand("dd if=/dev/zero of=../../dev/block/sda", projectRoot, projectRoot).isAllowed());
    }

    @Test
    public void testIsWithinScopeValidation() {
        assertTrue(TerminalSecurityPolicy.isWithinScope(projectRoot, projectRoot));
        File subDir = new File(projectRoot, "src/components");
        assertTrue(TerminalSecurityPolicy.isWithinScope(subDir, projectRoot));

        File parentDir = projectRoot.getParentFile();
        assertFalse(TerminalSecurityPolicy.isWithinScope(parentDir, projectRoot));

        File rootTraversal = new File(projectRoot, "../../..");
        assertFalse(TerminalSecurityPolicy.isWithinScope(rootTraversal, projectRoot));

        assertFalse(TerminalSecurityPolicy.isWithinScope(null, projectRoot));
        assertFalse(TerminalSecurityPolicy.isWithinScope(subDir, null));
    }
}
