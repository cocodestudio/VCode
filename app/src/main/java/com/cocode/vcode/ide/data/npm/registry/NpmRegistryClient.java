package com.cocode.vcode.ide.data.npm.registry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cocode.vcode.ide.data.npm.model.NpmPackageInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure Java HTTP client communicating directly with the npm registry (https://registry.npmjs.org).
 * Uses native {@link HttpURLConnection} and {@link JSONObject} with zero third-party networking overhead.
 */
public final class NpmRegistryClient {

    private static final String REGISTRY_BASE_URL = "https://registry.npmjs.org/";
    private static final String SEARCH_ENDPOINT = "https://registry.npmjs.org/-/v1/search";
    private static final int TIMEOUT_CONNECT_MS = 10000;
    private static final int TIMEOUT_READ_MS = 15000;

    private NpmRegistryClient() {
    }

    /**
     * Searches the npm registry for packages matching the query string.
     *
     * @param query search query
     * @param size  maximum items to return (capped at 50)
     * @return list of matching package info records
     * @throws Exception on network or parsing error
     */
    @NonNull
    public static List<NpmPackageInfo> searchPackages(@NonNull String query, int size) throws Exception {
        int limit = Math.max(1, Math.min(size, 50));
        String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name());
        String urlStr = SEARCH_ENDPOINT + "?text=" + encodedQuery + "&size=" + limit;

        String response = executeHttpGet(urlStr);
        JSONObject root = new JSONObject(response);
        JSONArray objects = root.optJSONArray("objects");

        List<NpmPackageInfo> results = new ArrayList<>();
        if (objects == null) return results;

        for (int i = 0; i < objects.length(); i++) {
            JSONObject item = objects.getJSONObject(i);
            JSONObject pkg = item.optJSONObject("package");
            if (pkg == null) continue;

            String name = pkg.optString("name", "");
            String version = pkg.optString("version", "latest");
            if (name.isEmpty()) continue;

            NpmPackageInfo info = new NpmPackageInfo(name, version);
            info.setDescription(pkg.optString("description", ""));
            info.setLicense(pkg.optString("license", ""));

            JSONObject authorObj = pkg.optJSONObject("author");
            if (authorObj != null && authorObj.has("name")) {
                info.setAuthor(authorObj.optString("name", ""));
            } else {
                JSONObject publisherObj = pkg.optJSONObject("publisher");
                if (publisherObj != null) {
                    info.setAuthor(publisherObj.optString("username", ""));
                }
            }

            results.add(info);
        }

        return results;
    }

    /**
     * Fetches detailed metadata for a specific package from the npm registry.
     * Resolves the latest version and the direct download tarball URL.
     *
     * @param packageName name of the package (e.g. "axios" or "@types/node")
     * @return NpmPackageInfo with resolved version and tarballUrl
     * @throws Exception on network failure or 404
     */
    @NonNull
    public static NpmPackageInfo fetchPackageMetadata(@NonNull String packageName) throws Exception {
        String cleanName = packageName.trim();
        String encodedName;
        if (cleanName.startsWith("@") && cleanName.contains("/")) {
            int slash = cleanName.indexOf('/');
            String scope = cleanName.substring(0, slash);
            String subName = cleanName.substring(slash + 1);
            encodedName = scope + "%2F" + URLEncoder.encode(subName, StandardCharsets.UTF_8.name());
        } else {
            encodedName = URLEncoder.encode(cleanName, StandardCharsets.UTF_8.name());
        }

        String urlStr = REGISTRY_BASE_URL + encodedName;
        String response = executeHttpGet(urlStr);
        JSONObject root = new JSONObject(response);

        String name = root.optString("name", cleanName);
        String description = root.optString("description", "");
        String license = root.optString("license", "");

        String latestVersion = "latest";
        JSONObject distTags = root.optJSONObject("dist-tags");
        if (distTags != null) {
            latestVersion = distTags.optString("latest", "latest");
        }

        NpmPackageInfo info = new NpmPackageInfo(name, latestVersion);
        info.setDescription(description);
        info.setLicense(license);

        JSONObject versions = root.optJSONObject("versions");
        if (versions != null && versions.has(latestVersion)) {
            JSONObject verObj = versions.getJSONObject(latestVersion);
            JSONObject dist = verObj.optJSONObject("dist");
            if (dist != null) {
                info.setTarballUrl(dist.optString("tarball", null));
                info.setUnpackedSize(dist.optLong("unpackedSize", 0));
                info.setIntegrity(dist.optString("integrity", null));
            }
        }

        // Fallback tarball URL standard pattern if dist was missing
        if (info.getTarballUrl() == null || info.getTarballUrl().isEmpty()) {
            if (cleanName.startsWith("@") && cleanName.contains("/")) {
                String subName = cleanName.substring(cleanName.indexOf('/') + 1);
                info.setTarballUrl(REGISTRY_BASE_URL + cleanName + "/-/" + subName + "-" + latestVersion + ".tgz");
            } else {
                info.setTarballUrl(REGISTRY_BASE_URL + cleanName + "/-/" + cleanName + "-" + latestVersion + ".tgz");
            }
        }

        return info;
    }

    private static String executeHttpGet(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_CONNECT_MS);
        conn.setReadTimeout(TIMEOUT_READ_MS);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "VCode-IDE-Mobile/1.0");
        conn.setRequestProperty("Accept", "application/json");

        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            throw new Exception("npm registry HTTP error: " + code);
        }

        StringBuilder sb = new StringBuilder();
        try (InputStream in = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
        } finally {
            conn.disconnect();
        }
        return sb.toString();
    }
}
