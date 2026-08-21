package com.cocode.vcode.ide.views;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.cocode.vcode.ide.databinding.ViewDiagnosticBarBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

/**
 * Status bar widget displaying real-time diagnostic counts (errors, warnings, infos)
 * or an analyzing spinner during background lint passes.
 */
public class DiagnosticBar extends LinearLayout {

    private ViewDiagnosticBarBinding binding;

    public DiagnosticBar(Context context) {
        super(context);
        init(context);
    }

    public DiagnosticBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DiagnosticBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        binding = ViewDiagnosticBarBinding.inflate(LayoutInflater.from(context), this);

        setTypefaces();

        // Default state
        setLoading();
    }

    private void setTypefaces() {
        FontManager fm = FontManager.getInstance();
        Typeface font = fm.getUiMedium(getContext());
        binding.tvErrorCount.setTypeface(font);
        binding.tvWarningCount.setTypeface(font);
        binding.tvInfoCount.setTypeface(font);
        binding.tvAnalyzing.setTypeface(font);
    }

    public void setLoading() {
        binding.progressLoading.setVisibility(View.VISIBLE);
        binding.tvAnalyzing.setVisibility(View.VISIBLE);
        binding.ivClean.setVisibility(View.GONE);
        binding.llErrors.setVisibility(View.GONE);
        binding.llWarnings.setVisibility(View.GONE);
        binding.llInfos.setVisibility(View.GONE);
        animateAlpha(1f);
    }

    public void update(int errors, int warnings, int infos) {
        binding.progressLoading.setVisibility(View.GONE);
        binding.tvAnalyzing.setVisibility(View.GONE);

        if (errors == 0 && warnings == 0 && infos == 0) {
            binding.ivClean.setVisibility(View.VISIBLE);
            binding.llErrors.setVisibility(View.GONE);
            binding.llWarnings.setVisibility(View.GONE);
            binding.llInfos.setVisibility(View.GONE);
        } else {
            binding.ivClean.setVisibility(View.GONE);

            if (errors > 0) {
                binding.llErrors.setVisibility(View.VISIBLE);
                binding.tvErrorCount.setText(String.valueOf(errors));
            } else {
                binding.llErrors.setVisibility(View.GONE);
            }

            if (warnings > 0) {
                binding.llWarnings.setVisibility(View.VISIBLE);
                binding.tvWarningCount.setText(String.valueOf(warnings));
                // Add margin if errors is also visible
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) binding.llWarnings.getLayoutParams();
                params.setMarginStart(errors > 0 ? UiUtils.dpToPx(getContext(), 8) : 0);
                binding.llWarnings.setLayoutParams(params);
            } else {
                binding.llWarnings.setVisibility(View.GONE);
            }

            if (infos > 0) {
                binding.llInfos.setVisibility(View.VISIBLE);
                binding.tvInfoCount.setText(String.valueOf(infos));
                // Add margin if errors or warnings are visible
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) binding.llInfos.getLayoutParams();
                params.setMarginStart((errors > 0 || warnings > 0) ? UiUtils.dpToPx(getContext(), 8) : 0);
                binding.llInfos.setLayoutParams(params);
            } else {
                binding.llInfos.setVisibility(View.GONE);
            }
        }
        animateAlpha(1f);
    }

    private void animateAlpha(float targetAlpha) {
        if (getAlpha() != targetAlpha) {
            animate().alpha(targetAlpha).setDuration(150).start();
        }
    }
}
