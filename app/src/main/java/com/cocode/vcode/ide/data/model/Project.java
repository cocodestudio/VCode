package com.cocode.vcode.ide.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data model representing a project in the workspace, including metadata like name, creation date,
 * last modification timestamp, main entry file, and file count.
 */
public class Project {

    private String id;
    private String name;
    private long createdAt;
    private long lastModifiedAt;
    private String mainFile; // Path to project entry point (e.g. index.html)
    private int fileCount;

    private File rootDir;
    private final Map<Class<? extends ProjectFacet>, ProjectFacet> facets = new ConcurrentHashMap<>();

    public Project() {
    }

    public Project(String id, String name, long createdAt, long lastModifiedAt,
                   String mainFile, int fileCount) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.lastModifiedAt = lastModifiedAt;
        this.mainFile = mainFile;
        this.fileCount = fileCount;
    }

    public File getRootDir() {
        return rootDir;
    }

    public void setRootDir(File rootDir) {
        this.rootDir = rootDir;
    }

    /**
     * Checks if this project has a facet of the specified class.
     */
    public <T extends ProjectFacet> boolean hasFacet(@NonNull Class<T> facetClass) {
        return facets.containsKey(facetClass);
    }

    /**
     * Retrieves the facet instance of the given class, or null if not attached.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends ProjectFacet> T getFacet(@NonNull Class<T> facetClass) {
        return (T) facets.get(facetClass);
    }

    /**
     * Attaches a facet to this project. If a facet of the same class is already attached,
     * it will be detached first.
     */
    public void addFacet(@NonNull ProjectFacet facet) {
        Class<? extends ProjectFacet> facetClass = facet.getClass();
        ProjectFacet old = facets.remove(facetClass);
        if (old != null) {
            old.detach();
        }
        facets.put(facetClass, facet);
        facet.attach(this);
    }

    /**
     * Removes and detaches a facet from this project.
     */
    public <T extends ProjectFacet> void removeFacet(@NonNull Class<T> facetClass) {
        ProjectFacet removed = facets.remove(facetClass);
        if (removed != null) {
            removed.detach();
        }
    }

    /**
     * Returns an unmodifiable collection of all active facets attached to this project.
     */
    @NonNull
    public Collection<ProjectFacet> getFacets() {
        return Collections.unmodifiableCollection(facets.values());
    }

    /**
     * Disposes this project, detaching and releasing all attached facets.
     */
    public void dispose() {
        for (ProjectFacet facet : facets.values()) {
            try {
                facet.detach();
            } catch (Exception ignored) {
            }
        }
        facets.clear();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastModifiedAt() {
        return lastModifiedAt;
    }

    public void setLastModifiedAt(long lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
    }

    public String getMainFile() {
        return mainFile;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }
}