package com.cocode.vcode.ide.ui.packages;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
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

            // Apply unified custom typography
            binding.tvPackageName.setTypeface(fm.getCodeFontBold(context));
            binding.tvScopeBadge.setTypeface(fm.getCodeFontBold(context));
            binding.tvLatestVersion.setTypeface(fm.getCodeFontBold(context));
            binding.tvInstalledVersion.setTypeface(fm.getCodeFont(context));
            binding.tvStatusBadge.setTypeface(fm.getCodeFont(context));
            binding.tvPackageDescription.setTypeface(fm.getUiFont(context));
            binding.tvAuthor.setTypeface(fm.getUiFont(context));
            binding.tvLicense.setTypeface(fm.getUiFont(context));
            binding.tvSize.setTypeface(fm.getUiFont(context));
            binding.tvDependencyType.setTypeface(fm.getUiFont(context));
            binding.tvItemStatus.setTypeface(fm.getCodeFontBold(context));
            binding.btnInstall.setTypeface(fm.getUiSemiBold(context));
            binding.btnInstallDev.setTypeface(fm.getUiSemiBold(context));
            binding.btnUpdate.setTypeface(fm.getUiSemiBold(context));

            binding.tvPackageName.setText(pkg.getName());

            // Scope Badge (prod vs dev vs registry)
            if (pkg.isInstalled()) {
                if (pkg.isDev()) {
                    binding.tvScopeBadge.setText(R.string.vcode_scope_dev);
                    binding.tvScopeBadge.setBackgroundResource(R.drawable.vcode_bg_badge_neutral);
                    binding.tvScopeBadge.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_secondary));
                    binding.tvDependencyType.setText(R.string.vcode_dev_dependency);
                } else {
                    binding.tvScopeBadge.setText(R.string.vcode_scope_prod);
                    binding.tvScopeBadge.setBackgroundResource(R.drawable.vcode_bg_badge_success);
                    binding.tvScopeBadge.setTextColor(ContextCompat.getColor(context, R.color.vcode_accent_success));
                    binding.tvDependencyType.setText(R.string.vcode_direct_dependency);
                }
            } else {
                binding.tvScopeBadge.setText(R.string.vcode_scope_registry);
                binding.tvScopeBadge.setBackgroundResource(R.drawable.vcode_bg_badge_neutral);
                binding.tvScopeBadge.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_hint));
                binding.tvDependencyType.setText(R.string.vcode_direct_dependency);
            }

            // Version Flow and Status Badge
            if (pkg.isInstalled()) {
                binding.tvInstalledVersion.setVisibility(View.VISIBLE);
                binding.tvInstalledVersion.setText("v" + pkg.getInstalledVersion());

                if (pkg.isUpdateAvailable()) {
                    binding.ivVersionArrow.setVisibility(View.VISIBLE);
                    binding.tvLatestVersion.setVisibility(View.VISIBLE);
                    binding.tvLatestVersion.setText("v" + pkg.getVersion());
                    binding.tvLatestVersion.setTextColor(ContextCompat.getColor(context, R.color.vcode_accent_warning));

                    binding.tvStatusBadge.setVisibility(View.VISIBLE);
                    binding.tvStatusBadge.setText(R.string.vcode_update_available);
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.vcode_bg_badge_warning);
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.vcode_accent_warning));
                } else {
                    binding.ivVersionArrow.setVisibility(View.GONE);
                    binding.tvLatestVersion.setVisibility(View.GONE);

                    binding.tvStatusBadge.setVisibility(View.VISIBLE);
                    binding.tvStatusBadge.setText(R.string.vcode_up_to_date);
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.vcode_bg_badge_success);
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.vcode_accent_success));
                }
            } else {
                binding.tvInstalledVersion.setVisibility(View.GONE);
                binding.ivVersionArrow.setVisibility(View.GONE);

                binding.tvLatestVersion.setVisibility(View.VISIBLE);
                binding.tvLatestVersion.setText("v" + pkg.getVersion());
                binding.tvLatestVersion.setTextColor(ContextCompat.getColor(context, R.color.vcode_accent_primary));

                binding.tvStatusBadge.setVisibility(View.VISIBLE);
                binding.tvStatusBadge.setText(R.string.vcode_not_installed);
                binding.tvStatusBadge.setBackgroundResource(R.drawable.vcode_bg_badge_neutral);
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_hint));
            }

            // Description
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
                binding.tvAuthor.setText(context.getString(R.string.vcode_author_format, author));
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

            // Action progress indicator vs buttons
            if (pkg.isActionInProgress()) {
                binding.layoutItemProgress.setVisibility(View.VISIBLE);
                binding.tvItemStatus.setText(pkg.isInstalled()
                        ? context.getString(R.string.vcode_uninstalling)
                        : context.getString(R.string.vcode_installing));
                binding.layoutActionButtons.setVisibility(View.INVISIBLE);
            } else {
                binding.layoutItemProgress.setVisibility(View.GONE);
                binding.layoutActionButtons.setVisibility(View.VISIBLE);

                if (pkg.isInstalled()) {
                    binding.btnInstall.setVisibility(View.GONE);
                    binding.btnInstallDev.setVisibility(View.GONE);
                    binding.btnUninstall.setVisibility(View.VISIBLE);

                    if (pkg.isUpdateAvailable()) {
                        binding.btnUpdate.setVisibility(View.VISIBLE);
                        binding.btnUpdate.setText(context.getString(R.string.vcode_update_to, "v" + pkg.getVersion()));
                    } else {
                        binding.btnUpdate.setVisibility(View.GONE);
                    }
                } else {
                    binding.btnInstall.setVisibility(View.VISIBLE);
                    binding.btnInstallDev.setVisibility(View.VISIBLE);
                    binding.btnUninstall.setVisibility(View.GONE);
                    binding.btnUpdate.setVisibility(View.GONE);
                }
            }

            // Button click handlers
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

            // More Options menu
            binding.btnMoreOptions.setOnClickListener(v -> showPopupMenu(v, pkg));
        }

        private void showPopupMenu(@NonNull View anchor, @NonNull NpmPackageInfo pkg) {
            Context context = anchor.getContext();
            PopupMenu popup = new PopupMenu(context, anchor);

            final int MENU_COPY_NAME = 1;
            final int MENU_COPY_IMPORT = 2;
            final int MENU_INSTALL_PROD = 3;
            final int MENU_INSTALL_DEV = 4;
            final int MENU_UPDATE = 5;
            final int MENU_UNINSTALL = 6;

            popup.getMenu().add(0, MENU_COPY_NAME, 0, R.string.vcode_copy_package_name);
            popup.getMenu().add(0, MENU_COPY_IMPORT, 1, R.string.vcode_copy_import_statement);

            if (pkg.isInstalled()) {
                if (pkg.isUpdateAvailable()) {
                    popup.getMenu().add(0, MENU_UPDATE, 2, context.getString(R.string.vcode_update_to, "v" + pkg.getVersion()));
                }
                popup.getMenu().add(0, MENU_UNINSTALL, 3, R.string.vcode_uninstall);
            } else {
                popup.getMenu().add(0, MENU_INSTALL_PROD, 2, R.string.vcode_install);
                popup.getMenu().add(0, MENU_INSTALL_DEV, 3, R.string.vcode_install_dev);
            }

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == MENU_COPY_NAME) {
                    copyToClipboard(context, pkg.getName());
                    return true;
                } else if (id == MENU_COPY_IMPORT) {
                    String cleanName = pkg.getName().replaceAll("[^a-zA-Z0-9_]", "");
                    if (cleanName.isEmpty()) cleanName = "pkg";
                    String importStmt = "import " + cleanName + " from '" + pkg.getName() + "';";
                    copyToClipboard(context, importStmt);
                    return true;
                } else if (id == MENU_INSTALL_PROD) {
                    if (listener != null) listener.onInstallClick(pkg, false);
                    return true;
                } else if (id == MENU_INSTALL_DEV) {
                    if (listener != null) listener.onInstallClick(pkg, true);
                    return true;
                } else if (id == MENU_UPDATE) {
                    if (listener != null) listener.onUpdateClick(pkg);
                    return true;
                } else if (id == MENU_UNINSTALL) {
                    if (listener != null) listener.onUninstallClick(pkg);
                    return true;
                }
                return false;
            });
            popup.show();
        }

        private void copyToClipboard(@NonNull Context context, @NonNull String text) {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("text", text));
                Toast.makeText(context, R.string.vcode_copied, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
