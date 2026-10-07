package com.cocode.vcode.ide.ui.sheets.settings;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.terminal.userland.HardwareCapabilityChecker;
import com.cocode.vcode.ide.core.terminal.userland.UserlandBootstrapManager;
import com.cocode.vcode.ide.databinding.BottomSheetDesktopTerminalInstallBinding;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;

import java.util.Locale;

/**
 * Modal bottom sheet displaying device hardware capabilities and real-time installation
 * progress for the rootless Linux environment.
 */
public class DesktopTerminalInstallBottomSheet extends BaseBottomSheetDialogFragment {

    public interface InstallListener {
        void onInstallSuccess();
        void onInstallCancelled();
    }

    private BottomSheetDesktopTerminalInstallBinding binding;
    private InstallListener installListener;
    private boolean isInstalling = false;

    public static DesktopTerminalInstallBottomSheet show(@NonNull FragmentManager manager,
                                                          @Nullable InstallListener listener) {
        DesktopTerminalInstallBottomSheet sheet = new DesktopTerminalInstallBottomSheet();
        sheet.setInstallListener(listener);
        sheet.show(manager, "DesktopTerminalInstallBottomSheet");
        return sheet;
    }

    public void setInstallListener(@Nullable InstallListener listener) {
        this.installListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetDesktopTerminalInstallBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        designUI();
        populateDiagnostics();
        setupListeners();
    }

    private void designUI() {
        FontManager fm = FontManager.getInstance();
        Context ctx = requireContext();

        binding.tvSheetTitle.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvSheetSubtitle.setTypeface(fm.getUiMedium(ctx));

        binding.tvRamLabel.setTypeface(fm.getUiMedium(ctx));
        binding.tvRamValue.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvStorageLabel.setTypeface(fm.getUiMedium(ctx));
        binding.tvStorageValue.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvArchLabel.setTypeface(fm.getUiMedium(ctx));
        binding.tvArchValue.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvSizeValue.setTypeface(fm.getUiMedium(ctx));

        binding.tvLowRamDesc.setTypeface(fm.getUiMedium(ctx));
        binding.tvLowStorageDesc.setTypeface(fm.getUiMedium(ctx));

        binding.btnCancelOverview.setTypeface(fm.getUiSemiBold(ctx));
        binding.btnStartInstall.setTypeface(fm.getUiSemiBold(ctx));

        binding.tvProgressStage.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvProgressDetail.setTypeface(fm.getUiMedium(ctx));
        binding.tvProgressPercentage.setTypeface(fm.getUiSemiBold(ctx));
        binding.btnCancelProgress.setTypeface(fm.getUiSemiBold(ctx));
    }

    private void populateDiagnostics() {
        Context ctx = requireContext();
        HardwareCapabilityChecker.DeviceDiagnostic diag = HardwareCapabilityChecker.checkDevice(ctx);

        binding.tvRamValue.setText(diag.getFormattedRam());
        binding.tvStorageValue.setText(diag.getFormattedStorage());

        String arch = UserlandBootstrapManager.getInstance().getNormalizedArch();
        binding.tvArchValue.setText(arch);

        if (diag.isLowRam) {
            binding.layoutLowRamWarning.setVisibility(View.VISIBLE);
            binding.tvLowRamDesc.setText(getString(R.string.vcode_desktop_terminal_low_ram_warning, diag.getFormattedRam()));
        } else {
            binding.layoutLowRamWarning.setVisibility(View.GONE);
        }

        if (diag.isStorageLow()) {
            binding.layoutLowStorageWarning.setVisibility(View.VISIBLE);
        } else {
            binding.layoutLowStorageWarning.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        binding.btnCancelOverview.setOnClickListener(v -> dismiss());
        binding.btnStartInstall.setOnClickListener(v -> startInstallation());
        binding.btnCancelProgress.setOnClickListener(v -> cancelInstallation());
    }

    private void startInstallation() {
        isInstalling = true;
        setCancelable(false);

        binding.layoutInfo.setVisibility(View.GONE);
        binding.layoutProgress.setVisibility(View.VISIBLE);

        binding.progressIndicator.setProgress(0);
        binding.tvProgressPercentage.setText("0%");
        binding.tvProgressStage.setText(R.string.vcode_desktop_terminal_installing);
        binding.tvProgressDetail.setText("");

        Context appContext = requireContext().getApplicationContext();
        UserlandBootstrapManager.getInstance().installBootstrap(appContext, null,
                new UserlandBootstrapManager.BootstrapCallback() {
                    @Override
                    public void onProgress(int stage, int totalStages, @NonNull String stageName, int percent, @Nullable String detail) {
                        ExecutorProvider.getInstance().runOnMain(() -> {
                            if (!isAdded() || binding == null) return;
                            binding.tvProgressStage.setText(String.format(Locale.US, "[%d/%d] %s", stage, totalStages, stageName));
                            binding.progressIndicator.setProgress(percent);
                            binding.tvProgressPercentage.setText(String.format(Locale.US, "%d%%", percent));
                            if (detail != null && !detail.isEmpty()) {
                                binding.tvProgressDetail.setText(detail);
                            }
                        });
                    }

                    @Override
                    public void onSuccess() {
                        ExecutorProvider.getInstance().runOnMain(() -> {
                            isInstalling = false;
                            if (isAdded()) {
                                Toast.makeText(requireContext(), R.string.vcode_desktop_terminal_installed, Toast.LENGTH_SHORT).show();
                            }
                            if (installListener != null) {
                                installListener.onInstallSuccess();
                            }
                            dismissAllowingStateLoss();
                        });
                    }

                    @Override
                    public void onError(@NonNull String error) {
                        ExecutorProvider.getInstance().runOnMain(() -> {
                            isInstalling = false;
                            if (isAdded()) {
                                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                            }
                            if (installListener != null) {
                                installListener.onInstallCancelled();
                            }
                            dismissAllowingStateLoss();
                        });
                    }
                });
    }

    private void cancelInstallation() {
        if (!isInstalling) {
            dismiss();
            return;
        }
        isInstalling = false;
        Context appContext = requireContext().getApplicationContext();
        UserlandBootstrapManager.getInstance().cancelInstall();
        UserlandBootstrapManager.getInstance().uninstallBootstrap(appContext);

        Toast.makeText(appContext, R.string.vcode_desktop_terminal_cancelled, Toast.LENGTH_SHORT).show();
        if (installListener != null) {
            installListener.onInstallCancelled();
        }
        dismissAllowingStateLoss();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (isInstalling) {
            isInstalling = false;
            Context ctx = getContext();
            if (ctx != null) {
                Context appContext = ctx.getApplicationContext();
                UserlandBootstrapManager.getInstance().cancelInstall();
                UserlandBootstrapManager.getInstance().uninstallBootstrap(appContext);
            }
            if (installListener != null) {
                installListener.onInstallCancelled();
            }
        }
    }

    @Override
    public void onDestroyView() {
        if (isInstalling) {
            isInstalling = false;
            Context ctx = getContext();
            if (ctx != null) {
                Context appContext = ctx.getApplicationContext();
                UserlandBootstrapManager.getInstance().cancelInstall();
                UserlandBootstrapManager.getInstance().uninstallBootstrap(appContext);
            }
            if (installListener != null) {
                installListener.onInstallCancelled();
            }
        }
        super.onDestroyView();
        binding = null;
    }
}
