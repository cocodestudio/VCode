package com.cocode.vcode.ide.ui.packages;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.data.npm.PackageManagerService;
import com.cocode.vcode.ide.data.npm.model.NpmPackageInfo;
import com.cocode.vcode.ide.databinding.ActivityPackageManagerBinding;
import com.cocode.vcode.ide.ui.base.BaseActivity;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated Activity providing full-screen npm package management, search, single & batch installations,
 * dependency updates, and uninstallation with unified typography via FontManager.
 */
public class PackageManagerActivity extends BaseActivity implements PackagesAdapter.PackageActionListener {

    public static final String EXTRA_PROJECT_ROOT = "extra_project_root";

    private static final int TAB_SEARCH = 0;
    private static final int TAB_INSTALLED = 1;
    private static final long SEARCH_DEBOUNCE_MS = 400L;

    private ActivityPackageManagerBinding binding;
    private PackagesAdapter adapter;
    private File projectRoot;

    private int activeTab = TAB_SEARCH;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable = null;
    private final List<NpmPackageInfo> currentPackages = new ArrayList<>();
    private boolean isBatchOperating = false;

    public static void start(@NonNull Context context, @NonNull File projectRoot) {
        Intent intent = new Intent(context, PackageManagerActivity.class);
        intent.putExtra(EXTRA_PROJECT_ROOT, projectRoot.getAbsolutePath());
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityPackageManagerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UiUtils.applySystemBarInsets(binding.getRoot());

        String rootPath = getIntent().getStringExtra(EXTRA_PROJECT_ROOT);
        if (rootPath != null && !rootPath.isEmpty()) {
            projectRoot = new File(rootPath);
        }

        setupTypefaces();
        setupToolbar();
        setupRecyclerView();
        setupTabs();
        setupSearchInput();
        setupBatchActions();

        loadInstalledDependencies(false);
    }

    private void setupTypefaces() {
        FontManager fm = FontManager.getInstance();
        binding.tvTitle.setTypeface(fm.getUiSemiBold(this));
        binding.tvSubtitle.setTypeface(fm.getUiMedium(this));
        binding.btnSelectAll.setTypeface(fm.getUiSemiBold(this));
        binding.btnTabSearch.setTypeface(fm.getUiMedium(this));
        binding.btnTabInstalled.setTypeface(fm.getUiMedium(this));
        binding.etSearch.setTypeface(fm.getUiMedium(this));
        binding.tvStatusMessage.setTypeface(fm.getUiMedium(this));
        binding.tvEmptyTitle.setTypeface(fm.getUiSemiBold(this));
        binding.tvEmptyDesc.setTypeface(fm.getUiFont(this));
        binding.tvSelectedCount.setTypeface(fm.getUiSemiBold(this));
        binding.btnClearSelection.setTypeface(fm.getUiMedium(this));
        binding.btnBatchInstall.setTypeface(fm.getUiSemiBold(this));
        binding.btnBatchInstallDev.setTypeface(fm.getUiSemiBold(this));
        binding.btnBatchUninstall.setTypeface(fm.getUiSemiBold(this));
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
        updateSubtitle();

        binding.btnSelectAll.setOnClickListener(v -> {
            if (adapter.areAllSelected()) {
                adapter.clearSelection();
            } else {
                adapter.selectAll();
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new PackagesAdapter(this);
        adapter.setOnSelectionChangedListener(this::updateBatchActionBar);
        binding.rvPackages.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPackages.setAdapter(adapter);
    }

    private void setupTabs() {
        binding.btnTabSearch.setOnClickListener(v -> switchTab(TAB_SEARCH));
        binding.btnTabInstalled.setOnClickListener(v -> switchTab(TAB_INSTALLED));
    }

    private void switchTab(int tab) {
        if (activeTab == tab && !currentPackages.isEmpty()) return;
        activeTab = tab;
        adapter.clearSelection();

        int activeColor = ContextCompat.getColor(this, R.color.vcode_accent_primary);
        int onPrimaryColor = ContextCompat.getColor(this, R.color.vcode_on_primary);
        int inactiveTextColor = ContextCompat.getColor(this, R.color.vcode_text_secondary);

        if (tab == TAB_SEARCH) {
            binding.btnTabSearch.setBackgroundTintList(ColorStateList.valueOf(activeColor));
            binding.btnTabSearch.setTextColor(onPrimaryColor);
            binding.btnTabSearch.setIconTint(ColorStateList.valueOf(onPrimaryColor));

            binding.btnTabInstalled.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            binding.btnTabInstalled.setTextColor(inactiveTextColor);
            binding.btnTabInstalled.setIconTint(ColorStateList.valueOf(inactiveTextColor));

            binding.layoutSearchBar.setVisibility(View.VISIBLE);

            String query = binding.etSearch.getText() != null ? binding.etSearch.getText().toString().trim() : "";
            if (query.isEmpty()) {
                performSearch("react");
            } else {
                performSearch(query);
            }
        } else {
            binding.btnTabInstalled.setBackgroundTintList(ColorStateList.valueOf(activeColor));
            binding.btnTabInstalled.setTextColor(onPrimaryColor);
            binding.btnTabInstalled.setIconTint(ColorStateList.valueOf(onPrimaryColor));

            binding.btnTabSearch.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            binding.btnTabSearch.setTextColor(inactiveTextColor);
            binding.btnTabSearch.setIconTint(ColorStateList.valueOf(inactiveTextColor));

            binding.layoutSearchBar.setVisibility(View.GONE);
            loadInstalledDependencies(true);
        }
    }

    private void setupSearchInput() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                String q = s.toString().trim();
                if (q.length() >= 2) {
                    searchRunnable = () -> performSearch(q);
                    searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.btnClearSearch.setOnClickListener(v -> {
            binding.etSearch.setText("");
            if (activeTab == TAB_SEARCH) {
                performSearch("popular");
            }
        });
    }

    private void setupBatchActions() {
        binding.btnClearSelection.setOnClickListener(v -> adapter.clearSelection());

        binding.btnBatchInstall.setOnClickListener(v -> executeBatchInstall(false));
        binding.btnBatchInstallDev.setOnClickListener(v -> executeBatchInstall(true));
        binding.btnBatchUninstall.setOnClickListener(v -> executeBatchUninstall());
    }

    private void updateBatchActionBar(int selectedCount) {
        if (selectedCount > 0 && !isBatchOperating) {
            binding.layoutBatchBar.setVisibility(View.VISIBLE);
            binding.tvSelectedCount.setText(getString(R.string.vcode_packages_selected, selectedCount));

            if (activeTab == TAB_INSTALLED) {
                binding.btnBatchUninstall.setVisibility(View.VISIBLE);
                binding.btnBatchUninstall.setText(getString(R.string.vcode_uninstall_selected, selectedCount));
                binding.btnBatchInstall.setVisibility(View.GONE);
                binding.btnBatchInstallDev.setVisibility(View.GONE);
            } else {
                binding.btnBatchUninstall.setVisibility(View.GONE);
                binding.btnBatchInstall.setVisibility(View.VISIBLE);
                binding.btnBatchInstall.setText(getString(R.string.vcode_install_selected, selectedCount));
                binding.btnBatchInstallDev.setVisibility(View.VISIBLE);
                binding.btnBatchInstallDev.setText(getString(R.string.vcode_install_dev_selected, selectedCount));
            }

            binding.btnSelectAll.setText(adapter.areAllSelected() ? R.string.vcode_clear_selection : R.string.vcode_select_all);
            binding.btnSelectAll.setVisibility(View.VISIBLE);
        } else {
            binding.layoutBatchBar.setVisibility(View.GONE);
            binding.btnSelectAll.setText(R.string.vcode_select_all);
            binding.btnSelectAll.setVisibility(currentPackages.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void executeBatchInstall(boolean isDev) {
        if (projectRoot == null || isBatchOperating) return;
        List<NpmPackageInfo> selected = adapter.getSelectedPackages();
        if (selected.isEmpty()) return;

        setBatchOperating(true);
        binding.tvStatusMessage.setVisibility(View.VISIBLE);
        binding.loadingIndicator.setVisibility(View.VISIBLE);

        PackageManagerService.getInstance().installPackagesBatch(
                projectRoot,
                selected,
                isDev,
                new PackageManagerService.BatchOperationCallback() {
                    @Override
                    public void onProgress(int current, int total, @NonNull String currentPackage, @NonNull String message, int percent) {
                        if (isFinishing() || isDestroyed()) return;
                        binding.tvStatusMessage.setText(getString(R.string.vcode_batch_install_progress, currentPackage, current, total));
                    }

                    @Override
                    public void onComplete(int successCount, int failCount, @NonNull List<String> errors) {
                        if (isFinishing() || isDestroyed()) return;
                        setBatchOperating(false);
                        adapter.clearSelection();

                        if (failCount == 0) {
                            Toast.makeText(PackageManagerActivity.this,
                                    getString(R.string.vcode_batch_install_complete, successCount),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(PackageManagerActivity.this,
                                    getString(R.string.vcode_batch_install_partial, successCount, failCount),
                                    Toast.LENGTH_LONG).show();
                        }

                        refreshCurrentView();
                    }
                }
        );
    }

    private void executeBatchUninstall() {
        if (projectRoot == null || isBatchOperating) return;
        List<NpmPackageInfo> selected = adapter.getSelectedPackages();
        if (selected.isEmpty()) return;

        setBatchOperating(true);
        binding.tvStatusMessage.setVisibility(View.VISIBLE);
        binding.loadingIndicator.setVisibility(View.VISIBLE);

        PackageManagerService.getInstance().uninstallPackagesBatch(
                projectRoot,
                selected,
                new PackageManagerService.BatchOperationCallback() {
                    @Override
                    public void onProgress(int current, int total, @NonNull String currentPackage, @NonNull String message, int percent) {
                        if (isFinishing() || isDestroyed()) return;
                        binding.tvStatusMessage.setText(getString(R.string.vcode_batch_uninstall_progress, currentPackage, current, total));
                    }

                    @Override
                    public void onComplete(int successCount, int failCount, @NonNull List<String> errors) {
                        if (isFinishing() || isDestroyed()) return;
                        setBatchOperating(false);
                        adapter.clearSelection();

                        if (failCount == 0) {
                            Toast.makeText(PackageManagerActivity.this,
                                    getString(R.string.vcode_batch_uninstall_complete, successCount),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(PackageManagerActivity.this,
                                    getString(R.string.vcode_batch_uninstall_partial, successCount, failCount),
                                    Toast.LENGTH_LONG).show();
                        }

                        refreshCurrentView();
                    }
                }
        );
    }

    private void setBatchOperating(boolean operating) {
        this.isBatchOperating = operating;
        binding.btnBatchInstall.setEnabled(!operating);
        binding.btnBatchInstallDev.setEnabled(!operating);
        binding.btnBatchUninstall.setEnabled(!operating);
        binding.btnClearSelection.setEnabled(!operating);
        binding.btnSelectAll.setEnabled(!operating);
        if (!operating) {
            binding.tvStatusMessage.setVisibility(View.GONE);
            binding.loadingIndicator.setVisibility(View.GONE);
        }
    }

    private void performSearch(@NonNull String query) {
        if (projectRoot == null) return;
        setLoading(true);

        PackageManagerService.getInstance().searchPackages(projectRoot, query, new PackageManagerService.PackageListCallback() {
            @Override
            public void onResult(@NonNull List<NpmPackageInfo> packages) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                currentPackages.clear();
                currentPackages.addAll(packages);
                adapter.setPackages(packages);
                binding.btnSelectAll.setVisibility(packages.isEmpty() ? View.GONE : View.VISIBLE);

                if (packages.isEmpty()) {
                    showEmptyState(getString(R.string.vcode_no_packages_found),
                            getString(R.string.vcode_no_packages_found_desc));
                } else {
                    hideEmptyState();
                }
            }

            @Override
            public void onError(@NonNull String error) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                showEmptyState(getString(R.string.vcode_no_packages_found), error);
            }
        });
    }

    private void loadInstalledDependencies(boolean forceSwitch) {
        if (projectRoot == null) return;
        setLoading(true);

        PackageManagerService.getInstance().getInstalledPackages(projectRoot, new PackageManagerService.PackageListCallback() {
            @Override
            public void onResult(@NonNull List<NpmPackageInfo> packages) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                updateInstalledTabBadge(packages.size());

                if (forceSwitch || activeTab == TAB_INSTALLED) {
                    currentPackages.clear();
                    currentPackages.addAll(packages);
                    adapter.setPackages(packages);
                    binding.btnSelectAll.setVisibility(packages.isEmpty() ? View.GONE : View.VISIBLE);

                    if (packages.isEmpty()) {
                        showEmptyState(getString(R.string.vcode_no_installed_packages),
                                getString(R.string.vcode_no_installed_packages_desc));
                    } else {
                        hideEmptyState();
                    }
                } else if (packages.isEmpty() && activeTab == TAB_SEARCH) {
                    performSearch("lodash");
                }
            }

            @Override
            public void onError(@NonNull String error) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                if (activeTab == TAB_INSTALLED) {
                    showEmptyState(getString(R.string.vcode_no_installed_packages), error);
                }
            }
        });
    }

    private void updateInstalledTabBadge(int count) {
        if (count > 0) {
            binding.btnTabInstalled.setText(getString(R.string.vcode_tab_installed, count));
        } else {
            binding.btnTabInstalled.setText(R.string.vcode_tab_installed_plain);
        }
    }

    private void updateSubtitle() {
        if (projectRoot != null) {
            String projectName = com.cocode.vcode.ide.data.repository.ProjectRepository.getProjectName(projectRoot);
            binding.tvSubtitle.setText(projectName + " • node_modules");
        }
    }

    private void setLoading(boolean loading) {
        binding.loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showEmptyState(String title, String desc) {
        binding.layoutEmptyState.setVisibility(View.VISIBLE);
        binding.tvEmptyTitle.setText(title);
        binding.tvEmptyDesc.setText(desc);
        binding.rvPackages.setVisibility(View.GONE);
    }

    private void hideEmptyState() {
        binding.layoutEmptyState.setVisibility(View.GONE);
        binding.rvPackages.setVisibility(View.VISIBLE);
    }

    @Override
    public void onInstallClick(@NonNull NpmPackageInfo pkg, boolean isDev) {
        if (projectRoot == null) return;
        adapter.updatePackageState(pkg.getName(), true);
        binding.tvStatusMessage.setVisibility(View.VISIBLE);
        binding.tvStatusMessage.setText(getString(R.string.vcode_installing) + " " + pkg.getName());

        PackageManagerService.getInstance().installPackage(
                projectRoot,
                pkg.getName(),
                pkg.getVersion(),
                isDev,
                (status, percent) -> {
                    if (!isFinishing() && !isDestroyed()) {
                        binding.tvStatusMessage.setText(status);
                    }
                },
                new PackageManagerService.PackageOperationCallback() {
                    @Override
                    public void onSuccess(@NonNull String message) {
                        if (isFinishing() || isDestroyed()) return;
                        binding.tvStatusMessage.setVisibility(View.GONE);
                        adapter.updatePackageState(pkg.getName(), false);
                        Toast.makeText(PackageManagerActivity.this,
                                getString(R.string.vcode_package_installed_success, pkg.getName()),
                                Toast.LENGTH_SHORT).show();
                        refreshCurrentView();
                    }

                    @Override
                    public void onError(@NonNull String error) {
                        if (isFinishing() || isDestroyed()) return;
                        binding.tvStatusMessage.setVisibility(View.GONE);
                        adapter.updatePackageState(pkg.getName(), false);
                        Toast.makeText(PackageManagerActivity.this,
                                getString(R.string.vcode_package_install_failed, pkg.getName(), error),
                                Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    @Override
    public void onUpdateClick(@NonNull NpmPackageInfo pkg) {
        onInstallClick(pkg, pkg.isDev());
    }

    @Override
    public void onUninstallClick(@NonNull NpmPackageInfo pkg) {
        if (projectRoot == null) return;
        adapter.updatePackageState(pkg.getName(), true);
        binding.tvStatusMessage.setVisibility(View.VISIBLE);
        binding.tvStatusMessage.setText(getString(R.string.vcode_uninstalling) + " " + pkg.getName());

        PackageManagerService.getInstance().uninstallPackage(
                projectRoot,
                pkg.getName(),
                new PackageManagerService.PackageOperationCallback() {
                    @Override
                    public void onSuccess(@NonNull String message) {
                        if (isFinishing() || isDestroyed()) return;
                        binding.tvStatusMessage.setVisibility(View.GONE);
                        adapter.updatePackageState(pkg.getName(), false);
                        Toast.makeText(PackageManagerActivity.this,
                                getString(R.string.vcode_package_uninstalled_success, pkg.getName()),
                                Toast.LENGTH_SHORT).show();
                        refreshCurrentView();
                    }

                    @Override
                    public void onError(@NonNull String error) {
                        if (isFinishing() || isDestroyed()) return;
                        binding.tvStatusMessage.setVisibility(View.GONE);
                        adapter.updatePackageState(pkg.getName(), false);
                        Toast.makeText(PackageManagerActivity.this,
                                getString(R.string.vcode_package_uninstall_failed, pkg.getName(), error),
                                Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void refreshCurrentView() {
        if (activeTab == TAB_INSTALLED) {
            loadInstalledDependencies(true);
        } else {
            String q = binding.etSearch.getText() != null ? binding.etSearch.getText().toString().trim() : "";
            performSearch(q.isEmpty() ? "react" : q);
            loadInstalledDependencies(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        super.onDestroy();
        binding = null;
    }
}
