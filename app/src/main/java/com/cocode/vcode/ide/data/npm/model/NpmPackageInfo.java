package com.cocode.vcode.ide.data.npm.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.utils.FileUtils;

import java.util.Objects;

/**
 * Data model representing an npm package retrieved from registry search,
 * package metadata, or parsed from workspace package.json.
 */
public class NpmPackageInfo {

    @NonNull
    private final String name;
    @NonNull
    private String version;
    @Nullable
    private String description;
    @Nullable
    private String author;
    @Nullable
    private String license;
    private long unpackedSize;
    @Nullable
    private String tarballUrl;
    @Nullable
    private String integrity;
    @Nullable
    private String installedVersion;
    private boolean isDev;
    private boolean isActionInProgress;

    public NpmPackageInfo(@NonNull String name, @NonNull String version) {
        this.name = name;
        this.version = version;
    }

    @Nullable
    public String getIntegrity() {
        return integrity;
    }

    public void setIntegrity(@Nullable String integrity) {
        this.integrity = integrity;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getVersion() {
        return version;
    }

    public void setVersion(@NonNull String version) {
        this.version = version;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    @Nullable
    public String getAuthor() {
        return author;
    }

    public void setAuthor(@Nullable String author) {
        this.author = author;
    }

    @Nullable
    public String getLicense() {
        return license;
    }

    public void setLicense(@Nullable String license) {
        this.license = license;
    }

    public long getUnpackedSize() {
        return unpackedSize;
    }

    public void setUnpackedSize(long unpackedSize) {
        this.unpackedSize = unpackedSize;
    }

    @NonNull
    public String getFormattedSize() {
        if (unpackedSize <= 0) return "";
        return FileUtils.formatSize(unpackedSize);
    }

    @Nullable
    public String getTarballUrl() {
        return tarballUrl;
    }

    public void setTarballUrl(@Nullable String tarballUrl) {
        this.tarballUrl = tarballUrl;
    }

    @Nullable
    public String getInstalledVersion() {
        return installedVersion;
    }

    public void setInstalledVersion(@Nullable String installedVersion) {
        this.installedVersion = installedVersion;
    }

    public boolean isInstalled() {
        return installedVersion != null && !installedVersion.isEmpty();
    }

    public boolean isDev() {
        return isDev;
    }

    public void setDev(boolean dev) {
        isDev = dev;
    }

    public boolean isActionInProgress() {
        return isActionInProgress;
    }

    public void setActionInProgress(boolean actionInProgress) {
        isActionInProgress = actionInProgress;
    }

    public boolean isUpdateAvailable() {
        if (installedVersion == null || installedVersion.isEmpty() || version.isEmpty()) {
            return false;
        }
        String cleanInstalled = installedVersion.replaceAll("^[~^>=< ]+", "").trim();
        String cleanLatest = version.replaceAll("^[~^>=< ]+", "").trim();
        return !cleanInstalled.equals(cleanLatest);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NpmPackageInfo that = (NpmPackageInfo) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
