package com.cocode.vcode.ide.git.github;

import android.os.Handler;
import android.os.Looper;

import com.cocode.vcode.ide.utils.ExecutorProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client implementing the GitHub OAuth 2.0 Device Authorization Flow (RFC 8628).
 */
public class GitHubDeviceFlowClient {

    public static final String CLIENT_ID = "Ov23li6GbHl3HBgK3EEG";

    private static final String BASE_URL = "https://github.com/login/device/code";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";

    public void requestDeviceCode(DeviceCodeCallback callback) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                URL url = new URL(BASE_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);

                String body = "client_id=" + CLIENT_ID + "&scope=repo";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }

                String response = readResponse(conn);
                JSONObject obj = new JSONObject(response);

                if (obj.has("error")) {
                    postError(callback, obj.optString("error_description", obj.getString("error")));
                    return;
                }

                DeviceCodeResponse res = new DeviceCodeResponse(
                        obj.getString("device_code"),
                        obj.getString("user_code"),
                        obj.optString("verification_uri_complete", obj.optString("verification_uri", "https://github.com/login/device")),
                        obj.getInt("expires_in"),
                        obj.getInt("interval")
                );

                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(res));

            } catch (Exception e) {
                postError(callback, e.getMessage());
            }
        });
    }

    private void postError(DeviceCodeCallback callback, String error) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onError(error));
    }

    public void pollForToken(String deviceCode, int initialInterval, AtomicBoolean isCancelled, TokenPollListener listener) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            int interval = initialInterval;
            long startTime = System.currentTimeMillis();
            long expiryTime = startTime + (900 * 1000); // Default to 15m if not specified, though loop will cancel on expired_token from API anyway

            Handler mainHandler = new Handler(Looper.getMainLooper());

            while (!isCancelled.get()) {
                if (System.currentTimeMillis() > expiryTime) {
                    mainHandler.post(listener::onExpired);
                    return;
                }

                try {
                    Thread.sleep(interval * 1000L);
                } catch (InterruptedException e) {
                    return;
                }

                if (isCancelled.get()) return;

                try {
                    URL url = new URL(TOKEN_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    conn.setDoOutput(true);

                    String body = "client_id=" + CLIENT_ID +
                            "&device_code=" + deviceCode +
                            "&grant_type=urn:ietf:params:oauth:grant-type:device_code";

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }

                    String response = readResponse(conn);
                    JSONObject obj = new JSONObject(response);

                    if (obj.has("error")) {
                        String error = obj.getString("error");
                        if ("authorization_pending".equals(error)) {
                            // Keep polling
                            continue;
                        } else if ("slow_down".equals(error)) {
                            interval += 5;
                            continue;
                        } else if ("expired_token".equals(error)) {
                            mainHandler.post(listener::onExpired);
                            return;
                        } else if ("access_denied".equals(error)) {
                            mainHandler.post(listener::onDenied);
                            return;
                        } else {
                            mainHandler.post(() -> listener.onError(error));
                            return;
                        }
                    } else if (obj.has("access_token")) {
                        String token = obj.getString("access_token");
                        mainHandler.post(() -> listener.onSuccess(token));
                        return;
                    }

                } catch (Exception e) {
                    mainHandler.post(() -> listener.onError(e.getMessage()));
                    return;
                }
            }
        });
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        InputStream is = (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300)
                ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "{}";

        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    public interface DeviceCodeCallback {
        void onSuccess(DeviceCodeResponse response);

        void onError(String error);
    }

    public interface TokenPollListener {
        void onSuccess(String accessToken);

        void onExpired();

        void onDenied();

        void onError(String error);
    }

    public static class DeviceCodeResponse {
        public final String deviceCode;
        public final String userCode;
        public final String verificationUriComplete;
        public final int expiresInSeconds;
        public final int intervalSeconds;

        public DeviceCodeResponse(String deviceCode, String userCode, String verificationUriComplete, int expiresInSeconds, int intervalSeconds) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUriComplete = verificationUriComplete;
            this.expiresInSeconds = expiresInSeconds;
            this.intervalSeconds = intervalSeconds;
        }
    }
}
