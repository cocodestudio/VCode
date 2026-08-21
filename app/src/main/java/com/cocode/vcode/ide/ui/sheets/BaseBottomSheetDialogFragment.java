package com.cocode.vcode.ide.ui.sheets;

import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.cocode.vcode.ide.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Base class for bottom sheet dialogs providing consistent styling and lifecycle handling.
 */
public class BaseBottomSheetDialogFragment extends BottomSheetDialogFragment {

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog) {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialog;
            View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                int maxWidth = getResources().getDimensionPixelSize(R.dimen.bottom_sheet_max_width);
                if (maxWidth > 0) {
                    BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                    behavior.setMaxWidth(maxWidth);
                    ViewGroup.LayoutParams lp = bottomSheet.getLayoutParams();
                    if (lp instanceof CoordinatorLayout.LayoutParams) {
                        CoordinatorLayout.LayoutParams clp = (CoordinatorLayout.LayoutParams) lp;
                        clp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                        bottomSheet.setLayoutParams(clp);
                    }
                }
            }
        }
    }
}
