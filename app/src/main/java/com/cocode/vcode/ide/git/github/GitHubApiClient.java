package com.cocode.vcode.ide.git.github;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GitHubApiClient {
    public static final String BASE_URL = "https://api.github.com";
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 15000;
    private final String token;

    public GitHubApiClient(String token) {
        this.token = token;
    }

    private HttpURLConnection openConnection(String path, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "VCode-IDE");
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        return conn;
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
        }
        if (code == 401) throw new IOException("Authentication failed. Check your token.");
        if (code == 403) throw new IOException("Access forbidden. Token may lack required scopes.");
        if (code == 404) throw new IOException("Not found.");
        if (code == 422) throw new IOException("Repository name already exists or is invalid.");
        if (code >= 400) throw new IOException("GitHub API error " + code + ": " + sb);
        return sb.toString();
    }

    private String get(String path) throws IOException {
        HttpURLConnection conn = openConnection(path, "GET");
        try {
            return readResponse(conn);
        } finally {
            conn.disconnect();
        }
    }

    private String post(String path, JSONObject body) throws IOException {
        HttpURLConnection conn = openConnection(path, "POST");
        conn.setDoOutput(true);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        try {
            return readResponse(conn);
        } finally {
            conn.disconnect();
        }
    }

    public GitHubUser validateToken() throws IOException {
        String json = get("/user");
        try {
            JSONObject obj = new JSONObject(json);
            return new GitHubUser(
                    obj.optString("login", ""),
                    obj.optString("name", ""),
                    obj.optString("email", ""),
                    obj.optString("avatar_url", ""),
                    obj.optInt("public_repos", 0),
                    obj.optInt("total_private_repos", 0)
            );
        } catch (Exception e) {
            throw new IOException("Failed to parse user response: " + e.getMessage());
        }
    }

    public CreateRepoResult createRepository(String name, String description, boolean isPrivate) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("description", description != null ? description : "");
            body.put("private", isPrivate);
            body.put("auto_init", false);
        } catch (Exception e) {
            throw new IOException("Failed to build request: " + e.getMessage());
        }
        String response = post("/user/repos", body);
        try {
            JSONObject obj = new JSONObject(response);
            return new CreateRepoResult(
                    obj.optString("full_name", ""),
                    obj.optString("clone_url", ""),
                    obj.optString("html_url", "")
            );
        } catch (Exception e) {
            throw new IOException("Failed to parse create-repo response: " + e.getMessage());
        }
    }

    // --- Data Models ---
    public static class GitHubUser {
        private final String login;
        private final String name;
        private final String email;
        private final String avatarUrl;
        private final int publicRepos;
        private final int totalPrivateRepos;

        public GitHubUser(String login, String name, String email, String avatarUrl, int publicRepos, int totalPrivateRepos) {
            this.login = login;
            this.name = name;
            this.email = email;
            this.avatarUrl = avatarUrl;
            this.publicRepos = publicRepos;
            this.totalPrivateRepos = totalPrivateRepos;
        }

        public String getLogin() {
            return login;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public int getPublicRepos() {
            return publicRepos;
        }

        public int getTotalPrivateRepos() {
            return totalPrivateRepos;
        }
    }

    public static class CreateRepoResult {
        private final String fullName, cloneUrl, htmlUrl;

        public CreateRepoResult(String fullName, String cloneUrl, String htmlUrl) {
            this.fullName = fullName;
            this.cloneUrl = cloneUrl;
            this.htmlUrl = htmlUrl;
        }

        public String getFullName() {
            return fullName;
        }

        public String getCloneUrl() {
            return cloneUrl;
        }

        public String getHtmlUrl() {
            return htmlUrl;
        }
    }
}