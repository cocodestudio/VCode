package com.cocode.vcode.ide.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.LayoutSnackbarBinding;
import com.cocode.vcode.ide.utils.UiUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit test suite for {@link VCodeSnackbar} ensuring proper view hierarchy inflation,
 * FontManager typography application, semantic type configuration, action callbacks,
 * and dismissal behaviors.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class VCodeSnackbarTest {

    private Context context;
    private FrameLayout containerView;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.setTheme(R.style.Theme_VCode);

        containerView = new FrameLayout(context);
    }

    @Test
    public void testMakeNormalSnackbar() {
        CharSequence testMessage = "Project loaded successfully";
        VCodeSnackbar snackbar = VCodeSnackbar.make(containerView, testMessage, VCodeSnackbar.LENGTH_SHORT);

        assertNotNull(snackbar);
        assertNotNull(snackbar.getSnackbar());
        assertNotNull(snackbar.getBinding());
        assertEquals(VCodeSnackbar.Type.NORMAL, snackbar.getType());

        LayoutSnackbarBinding binding = snackbar.getBinding();
        assertEquals(testMessage, binding.tvSnackbarMessage.getText().toString());
        assertEquals(View.GONE, binding.ivSnackbarIcon.getVisibility());
        assertEquals(View.GONE, binding.btnSnackbarAction.getVisibility());
        assertEquals(View.GONE, binding.btnSnackbarDismiss.getVisibility());
    }

    @Test
    public void testSemanticTypes() {
        // INFO
        VCodeSnackbar infoBar = VCodeSnackbar.info(containerView, "Indexing workspace");
        assertEquals(VCodeSnackbar.Type.INFO, infoBar.getType());
        assertEquals(View.VISIBLE, infoBar.getBinding().ivSnackbarIcon.getVisibility());

        // SUCCESS
        VCodeSnackbar successBar = VCodeSnackbar.success(containerView, "Branch merged");
        assertEquals(VCodeSnackbar.Type.SUCCESS, successBar.getType());
        assertEquals(View.VISIBLE, successBar.getBinding().ivSnackbarIcon.getVisibility());

        // WARNING
        VCodeSnackbar warningBar = VCodeSnackbar.warning(containerView, "File has unstaged changes");
        assertEquals(VCodeSnackbar.Type.WARNING, warningBar.getType());
        assertEquals(View.VISIBLE, warningBar.getBinding().ivSnackbarIcon.getVisibility());

        // ERROR
        VCodeSnackbar errorBar = VCodeSnackbar.error(containerView, "Failed to resolve conflict");
        assertEquals(VCodeSnackbar.Type.ERROR, errorBar.getType());
        assertEquals(View.VISIBLE, errorBar.getBinding().ivSnackbarIcon.getVisibility());
    }

    @Test
    public void testStringResourceOverloads() {
        VCodeSnackbar makeRes = VCodeSnackbar.make(containerView, R.string.vcode_retry, VCodeSnackbar.LENGTH_SHORT);
        assertEquals(context.getString(R.string.vcode_retry), makeRes.getBinding().tvSnackbarMessage.getText().toString());

        VCodeSnackbar infoRes = VCodeSnackbar.info(containerView, R.string.vcode_dismiss);
        assertEquals(context.getString(R.string.vcode_dismiss), infoRes.getBinding().tvSnackbarMessage.getText().toString());
        assertEquals(VCodeSnackbar.Type.INFO, infoRes.getType());

        VCodeSnackbar successRes = VCodeSnackbar.success(containerView, R.string.vcode_retry);
        assertEquals(VCodeSnackbar.Type.SUCCESS, successRes.getType());

        VCodeSnackbar warningRes = VCodeSnackbar.warning(containerView, R.string.vcode_retry);
        assertEquals(VCodeSnackbar.Type.WARNING, warningRes.getType());

        VCodeSnackbar errorRes = VCodeSnackbar.error(containerView, R.string.vcode_retry);
        assertEquals(VCodeSnackbar.Type.ERROR, errorRes.getType());
    }

    @Test
    public void testSetActionWithClickListener() {
        VCodeSnackbar snackbar = VCodeSnackbar.make(containerView, "Failed to fetch remote", VCodeSnackbar.LENGTH_LONG);
        AtomicBoolean actionTriggered = new AtomicBoolean(false);

        snackbar.setAction(R.string.vcode_retry, v -> actionTriggered.set(true));

        LayoutSnackbarBinding binding = snackbar.getBinding();
        assertEquals(View.VISIBLE, binding.btnSnackbarAction.getVisibility());
        assertEquals(context.getString(R.string.vcode_retry), binding.btnSnackbarAction.getText().toString());

        binding.btnSnackbarAction.performClick();
        assertTrue(actionTriggered.get());
    }

    @Test
    public void testSetDismissible() {
        VCodeSnackbar snackbar = VCodeSnackbar.make(containerView, "Dismissible notification", VCodeSnackbar.LENGTH_LONG);
        LayoutSnackbarBinding binding = snackbar.getBinding();

        assertEquals(View.GONE, binding.btnSnackbarDismiss.getVisibility());

        snackbar.setDismissible(true);
        assertEquals(View.VISIBLE, binding.btnSnackbarDismiss.getVisibility());

        snackbar.setDismissible(false);
        assertEquals(View.GONE, binding.btnSnackbarDismiss.getVisibility());
    }

    @Test
    public void testSetCustomIconAndTint() {
        VCodeSnackbar snackbar = VCodeSnackbar.make(containerView, "Custom icon message", VCodeSnackbar.LENGTH_SHORT);
        snackbar.setIcon(R.drawable.ic_circle_check);
        snackbar.setIconTint(Color.CYAN);

        LayoutSnackbarBinding binding = snackbar.getBinding();
        assertEquals(View.VISIBLE, binding.ivSnackbarIcon.getVisibility());
        assertNotNull(binding.ivSnackbarIcon.getImageTintList());
        assertEquals(Color.CYAN, binding.ivSnackbarIcon.getImageTintList().getDefaultColor());
    }

    @Test
    public void testSetActionTextColor() {
        VCodeSnackbar snackbar = VCodeSnackbar.make(containerView, "Action color test", VCodeSnackbar.LENGTH_SHORT);
        snackbar.setAction("Action", null);
        snackbar.setActionTextColor(Color.MAGENTA);

        LayoutSnackbarBinding binding = snackbar.getBinding();
        assertEquals(Color.MAGENTA, binding.btnSnackbarAction.getCurrentTextColor());

        ColorStateList csl = ColorStateList.valueOf(Color.YELLOW);
        snackbar.setActionTextColor(csl);
        assertEquals(Color.YELLOW, binding.btnSnackbarAction.getCurrentTextColor());
    }

    @Test
    public void testTypographyTypefacesApplied() {
        VCodeSnackbar snackbar = VCodeSnackbar.make(containerView, "Typography test", VCodeSnackbar.LENGTH_SHORT);
        LayoutSnackbarBinding binding = snackbar.getBinding();

        assertNotNull(binding.tvSnackbarMessage.getTypeface());
        assertNotNull(binding.btnSnackbarAction.getTypeface());
    }

    @Test
    public void testUiUtilsIntegration() {
        // Ensure static utility wrappers in UiUtils delegate cleanly without exceptions
        UiUtils.showSnackbar(containerView, "Standard message", VCodeSnackbar.LENGTH_SHORT);
        UiUtils.showErrorSnackbar(containerView, "Error message");
        UiUtils.showSuccessSnackbar(containerView, "Success message");
        UiUtils.showWarningSnackbar(containerView, "Warning message");
    }
}
