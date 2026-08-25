package com.cocode.vcode.ide.views;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.appcompat.widget.TooltipCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.lsp.LspCallback;
import com.cocode.vcode.ide.core.lsp.LspEditorBridge;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.databinding.ViewLspNavigationToolbarBinding;

import java.util.List;

/**
 * Floating action bar shown when the cursor is idle on a navigable symbol.
 *
 * <p>Displays as a native-style floating PopupWindow above/below the cursor line.
 * It queries the LSP bridge to determine if Go to Definition or Find References
 * are available, and only shows the corresponding icons if they are.
 */
public class LspNavigationToolbar {

    private final ViewLspNavigationToolbarBinding binding;
    private final Context context;
    private final PopupWindow popupWindow;
    private CodeEditText editor;
    private LspEditorBridge bridge;

    public interface NavigationListener {
        void onNavigate(LspLocation loc);
        void onShowReferences(List<LspLocation> refs);
    }
    private NavigationListener navigationListener;

    private LspLocation cachedDefinition = null;
    private List<LspLocation> cachedReferences = null;
    
    private int probeGeneration = 0;

    // Track the offset that the current popup is displaying for
    private int currentOffset = -1;

    public LspNavigationToolbar(Context context) {
        this.context = context;
        binding = ViewLspNavigationToolbarBinding.inflate(LayoutInflater.from(context));

        float density = context.getResources().getDisplayMetrics().density;

        setupListeners();

        popupWindow = new PopupWindow(
                binding.getRoot(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false /* not focusable */);

        popupWindow.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setClippingEnabled(false);
        popupWindow.setElevation(12 * density);
        popupWindow.setAnimationStyle(R.style.VCodePopupMenuAnimation);
    }

    public void bindEditor(CodeEditText editor) {
        this.editor = editor;
    }

    public void bindBridge(LspEditorBridge bridge) {
        this.bridge = bridge;
    }

    public void setNavigationListener(NavigationListener listener) {
        this.navigationListener = listener;
    }

    /**
     * Called when the cursor has been idle. Triggers a background check for LSP navigation options.
     */
    public void onCursorIdle(int flatOffset) {
        if (editor == null || bridge == null) return;
        if (!bridge.isLspActive()) {
            hide();
            return;
        }
        if (flatOffset < 0 || flatOffset > editor.length() || !editor.hasFocus()) {
            hide();
            return;
        }

        final int myGeneration = ++probeGeneration;

        // We run both checks asynchronously and wait for both to complete before showing.
        final boolean[] defChecked = {false};
        final boolean[] refChecked = {false};
        final boolean[] hasDef = {false};
        final boolean[] hasRef = {false};

        LspCallback<LspLocation> defCallback = new LspCallback<LspLocation>() {
            @Override
            public void onResult(LspLocation result) {
                if (myGeneration != probeGeneration) return;
                if (editor.getSelectionStart() != flatOffset) return;
                cachedDefinition = result;
                hasDef[0] = result != null;
                defChecked[0] = true;
                if (refChecked[0]) showIfAvailable(flatOffset, hasDef[0], hasRef[0]);
            }
            @Override
            public void onError(String errorMessage) {
                if (myGeneration != probeGeneration) return;
                if (editor.getSelectionStart() != flatOffset) return;
                cachedDefinition = null;
                defChecked[0] = true;
                if (refChecked[0]) showIfAvailable(flatOffset, hasDef[0], hasRef[0]);
            }
        };

        LspCallback<List<LspLocation>> refCallback = new LspCallback<List<LspLocation>>() {
            @Override
            public void onResult(List<LspLocation> result) {
                if (myGeneration != probeGeneration) return;
                if (editor.getSelectionStart() != flatOffset) return;
                cachedReferences = result;
                hasRef[0] = result != null && !result.isEmpty();
                refChecked[0] = true;
                if (defChecked[0]) showIfAvailable(flatOffset, hasDef[0], hasRef[0]);
            }
            @Override
            public void onError(String errorMessage) {
                if (myGeneration != probeGeneration) return;
                if (editor.getSelectionStart() != flatOffset) return;
                cachedReferences = null;
                refChecked[0] = true;
                if (defChecked[0]) showIfAvailable(flatOffset, hasDef[0], hasRef[0]);
            }
        };

        bridge.requestDefinition(defCallback);
        bridge.requestReferences(refCallback);
    }

    private void showIfAvailable(int flatOffset, boolean hasDef, boolean hasRef) {
        if (!hasDef && !hasRef) {
            dismissAndClear();
            return;
        }

        binding.btnDefinition.setVisibility(hasDef ? View.VISIBLE : View.GONE);
        binding.btnReferences.setVisibility(hasRef ? View.VISIBLE : View.GONE);
        
        currentOffset = flatOffset;

        if (!popupWindow.isShowing()) {
            popupWindow.showAtLocation(editor, Gravity.NO_GRAVITY, -10000, -10000);
        }
        updatePosition();
    }

    private void dismissAndClear() {
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
        currentOffset = -1;
        cachedDefinition = null;
        cachedReferences = null;
    }

    public void hide() {
        dismissAndClear();
        probeGeneration++;
    }

    public boolean isVisible() {
        return popupWindow.isShowing();
    }

    public void updatePositionIfVisible() {
        if (isVisible()) {
            updatePosition();
        }
    }

    private void updatePosition() {
        if (editor == null || !popupWindow.isShowing() || currentOffset == -1) return;

        // Check if cursor actually moved away from what we're displaying.
        // In that case, hide it.
        if (editor.getSelectionStart() != currentOffset || editor.getSelectionStart() != editor.getSelectionEnd()) {
            hide();
            return;
        }

        binding.getRoot().measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupWidth = binding.getRoot().getMeasuredWidth();
        int popupHeight = binding.getRoot().getMeasuredHeight();

        float density = context.getResources().getDisplayMetrics().density;
        int screenW = context.getResources().getDisplayMetrics().widthPixels;
        int screenH = context.getResources().getDisplayMetrics().heightPixels;
        int maxWidth = context.getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
        int maxAllowedWidth = Math.min((int) (screenW * 0.92f), maxWidth);
        if (popupWidth > maxAllowedWidth) {
            popupWidth = maxAllowedWidth;
        }
        int margin = (int) (0.2 * density);
        int graceH = (int) (24 * density);
        int graceV = (int) (16 * density);

        int[] coords = editor.getCursorScreenCoords(currentOffset);
        int anchorX = coords[0];
        int anchorYTop = coords[1];
        int anchorYBot = coords[2];

        int x = anchorX - (popupWidth / 2);
        if (x < graceH) x = graceH;
        if (x + popupWidth > screenW - graceH) x = screenW - popupWidth - graceH;

        int yAbove = anchorYTop - popupHeight - margin;
        int yBelow = anchorYBot + margin;

        int y;
        if (yAbove >= graceV) {
            y = yAbove;
        } else if (yBelow + popupHeight <= screenH - graceV) {
            y = yBelow;
        } else {
            y = yAbove;
        }

        if (y < graceV) y = graceV;
        if (y + popupHeight > screenH - graceV) y = screenH - popupHeight - graceV;

        popupWindow.update(x, y, popupWidth, popupHeight);
    }

    private void setupListeners() {
        binding.btnDefinition.setOnClickListener(v -> {
            LspLocation def = cachedDefinition;
            hide();
            if (navigationListener != null) {
                if (def != null) {
                    navigationListener.onNavigate(def);
                } else if (context != null) {
                    Toast.makeText(context, R.string.vcode_lsp_no_definition_found, Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (context != null) {
            TooltipCompat.setTooltipText(binding.btnDefinition, context.getString(R.string.vcode_lsp_go_to_definition));
        }

        binding.btnReferences.setOnClickListener(v -> {
            List<LspLocation> refs = cachedReferences;
            hide();
            if (navigationListener != null) {
                if (refs != null && !refs.isEmpty()) {
                    navigationListener.onShowReferences(refs);
                } else if (context != null) {
                    Toast.makeText(context, R.string.vcode_lsp_no_references_found, Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (context != null) {
            TooltipCompat.setTooltipText(binding.btnReferences, context.getString(R.string.vcode_lsp_find_references));
        }
    }
}
