package com.cocode.vcode.ide.ui.packages;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.data.npm.model.NpmPackageInfo;
import com.cocode.vcode.ide.databinding.ItemPackageCardBinding;
import com.cocode.vcode.ide.utils.FontManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RecyclerView Adapter rendering npm package cards in the Package Manager Activity.
 * Supports individual actions (install, update, uninstall) and multi-selection for batch operations.
 */
public class PackagesAdapter extends RecyclerView.Adapter<PackagesAdapter.ViewHolder> {

    public interface PackageActionListener {
        void onInstallClick(@NonNull NpmPackageInfo pkg, boolean isDev);
        void onUpdateClick(@NonNull NpmPackageInfo pkg);
        void onUninstallClick(@NonNull NpmPackageInfo pkg);
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    private final List<NpmPackageInfo> packages = new ArrayList<>();
    private final Set<String> selectedPackageNames = new HashSet<>();
    private final PackageActionListener listener;
    private OnSelectionChangedListener selectionChangedListener;

    public PackagesAdapter(@Nullable PackageActionListener listener) {
        this.listener = listener;
    }

    public void setOnSelectionChangedListener(@Nullable OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }

    public void setPackages(@NonNull List<NpmPackageInfo> newPackages) {
        packages.clear();
        packages.addAll(newPackages);
        // Prune selections that no longer exist in new list if needed, or retain them
        notifyDataSetChanged();
    }

    public void updatePackageState(@NonNull String packageName, boolean inProgress) {
        for (int i = 0; i < packages.size(); i++) {
            if (packages.get(i).getName().equals(packageName)) {
                packages.get(i).setActionInProgress(inProgress);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void toggleSelection(@NonNull String packageName) {
        if (selectedPackageNames.contains(packageName)) {
            selectedPackageNames.remove(packageName);
        } else {
            selectedPackageNames.add(packageName);
        }
        notifyDataSetChanged();
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(selectedPackageNames.size());
        }
    }

    public void selectAll() {
        selectedPackageNames.clear();
        for (NpmPackageInfo pkg : packages) {
            selectedPackageNames.add(pkg.getName());
        }
        notifyDataSetChanged();
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(selectedPackageNames.size());
        }
    }

    public void clearSelection() {
        selectedPackageNames.clear();
        notifyDataSetChanged();
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(0);
        }
    }

    public boolean areAllSelected() {
        return !packages.isEmpty() && selectedPackageNames.size() == packages.size();
    }

    public int getSelectedCount() {
        return selectedPackageNames.size();
    }

    @NonNull
    public List<NpmPackageInfo> getSelectedPackages() {
        List<NpmPackageInfo> result = new ArrayList<>();
        for (NpmPackageInfo pkg : packages) {
            if (selectedPackageNames.contains(pkg.getName())) {
                result.add(pkg);
            }
        }
        return result;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPackageCardBinding binding = ItemPackageCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(packages.get(position));
    }

    @Override
    public int getItemCount() {
        return packages.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemPackageCardBinding binding;

        public ViewHolder(@NonNull ItemPackageCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(@NonNull NpmPackageInfo pkg) {
            Context context = itemView.getContext();
            FontManager fm = FontManager.getInstance();

            // Apply custom typography
            binding.tvPackageName.setTypeface(fm.getCodeFontBold(context));
            binding.tvVersionBadge.setTypeface(fm.getCodeFont(context));
            binding.tvPackageDescription.setTypeface(fm.getUiFont(context));
            binding.tvAuthor.setTypeface(fm.getUiFont(context));
            binding.tvLicense.setTypeface(fm.getUiFont(context));
            binding.tvSize.setTypeface(fm.getUiFont(context));
            binding.tvInstalledStatus.setTypeface(fm.getUiMedium(context));
            binding.btnUpdate.setTypeface(fm.getUiSemiBold(context));
            binding.btnInstall.setTypeface(fm.getUiSemiBold(context));
            binding.btnInstallDev.setTypeface(fm.getUiSemiBold(context));

            binding.tvPackageName.setText(pkg.getName());
            binding.tvVersionBadge.setText("v" + pkg.getVersion());

            String desc = pkg.getDescription();
            if (desc != null && !desc.isEmpty()) {
                binding.tvPackageDescription.setVisibility(View.VISIBLE);
                binding.tvPackageDescription.setText(desc);
            } else {
                binding.tvPackageDescription.setVisibility(View.GONE);
            }

            // Author
            String author = pkg.getAuthor();
            if (author != null && !author.isEmpty()) {
                binding.tvAuthor.setVisibility(View.VISIBLE);
                binding.tvAuthor.setText(author);
            } else {
                binding.tvAuthor.setVisibility(View.GONE);
            }

            // License
            String license = pkg.getLicense();
            if (license != null && !license.isEmpty()) {
                binding.tvLicense.setVisibility(View.VISIBLE);
                binding.tvLicense.setText(license);
                binding.tvDotAuthor.setVisibility(binding.tvAuthor.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
            } else {
                binding.tvLicense.setVisibility(View.GONE);
                binding.tvDotAuthor.setVisibility(View.GONE);
            }

            // Size
            String size = pkg.getFormattedSize();
            if (!size.isEmpty()) {
                binding.tvSize.setVisibility(View.VISIBLE);
                binding.tvSize.setText(size);
                binding.tvDotLicense.setVisibility((binding.tvAuthor.getVisibility() == View.VISIBLE || binding.tvLicense.getVisibility() == View.VISIBLE) ? View.VISIBLE : View.GONE);
            } else {
                binding.tvSize.setVisibility(View.GONE);
                binding.tvDotLicense.setVisibility(View.GONE);
            }

            // Multi-selection state
            boolean isSelected = selectedPackageNames.contains(pkg.getName());
            binding.cbSelect.setOnCheckedChangeListener(null);
            binding.cbSelect.setChecked(isSelected);
            binding.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked != selectedPackageNames.contains(pkg.getName())) {
                    toggleSelection(pkg.getName());
                }
            });

            // Card highlight stroke on selection
            int strokeColor = isSelected ? ContextCompat.getColor(context, R.color.vcode_accent_primary)
                    : ContextCompat.getColor(context, R.color.vcode_outline);
            binding.cardPackage.setStrokeColor(strokeColor);
            binding.cardPackage.setStrokeWidth(isSelected ? 2 : 1);

            binding.cardPackage.setOnClickListener(v -> toggleSelection(pkg.getName()));

            // Action progress spinner vs buttons
            if (pkg.isActionInProgress()) {
                binding.progressAction.setVisibility(View.VISIBLE);
                binding.layoutActionButtons.setVisibility(View.INVISIBLE);
            } else {
                binding.progressAction.setVisibility(View.GONE);
                binding.layoutActionButtons.setVisibility(View.VISIBLE);
            }

            // Installation state and button visibility
            if (pkg.isInstalled()) {
                binding.tvInstalledStatus.setVisibility(View.VISIBLE);
                String installedText = pkg.isDev() ? "dev: " + pkg.getInstalledVersion() : "v" + pkg.getInstalledVersion();
                binding.tvInstalledStatus.setText(installedText);

                binding.btnInstall.setVisibility(View.GONE);
                binding.btnInstallDev.setVisibility(View.GONE);
                binding.btnUninstall.setVisibility(View.VISIBLE);

                if (pkg.isUpdateAvailable()) {
                    binding.btnUpdate.setVisibility(View.VISIBLE);
                } else {
                    binding.btnUpdate.setVisibility(View.GONE);
                }
            } else {
                binding.tvInstalledStatus.setVisibility(View.GONE);
                binding.btnInstall.setVisibility(View.VISIBLE);
                binding.btnInstallDev.setVisibility(View.VISIBLE);
                binding.btnUninstall.setVisibility(View.GONE);
                binding.btnUpdate.setVisibility(View.GONE);
            }

            // Action button click handlers (prevent card click)
            binding.btnInstall.setOnClickListener(v -> {
                if (listener != null) listener.onInstallClick(pkg, false);
            });

            binding.btnInstallDev.setOnClickListener(v -> {
                if (listener != null) listener.onInstallClick(pkg, true);
            });

            binding.btnUpdate.setOnClickListener(v -> {
                if (listener != null) listener.onUpdateClick(pkg);
            });

            binding.btnUninstall.setOnClickListener(v -> {
                if (listener != null) listener.onUninstallClick(pkg);
            });
        }
    }
}
