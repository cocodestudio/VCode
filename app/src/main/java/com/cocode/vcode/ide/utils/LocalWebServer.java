package com.cocode.vcode.ide.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Lightweight embedded HTTP server running on the device loopback interface.
 * Serves static workspace project files to the internal preview WebView with CORS headers enabled.
 */
public class LocalWebServer {

    private final File documentRoot;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private int port = 8080;
    private Thread serverThread;

    public LocalWebServer(File documentRoot) {
        this.documentRoot = documentRoot;
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Starts the embedded server on an auto-allocated local port.
     */
    public void start() {
        if (isRunning) return;

        try {
            // Port 0 auto-allocates an available system port
            serverSocket = new ServerSocket(0);
            port = serverSocket.getLocalPort();
            isRunning = true;

            serverThread = new Thread(() -> {
                try {
                    while (isRunning) {
                        Socket socket = serverSocket.accept();
                        com.cocode.vcode.ide.utils.ExecutorProvider.getInstance().runOnCpu(() -> handleRequest(socket));
                    }
                } catch (Exception e) {
                    isRunning = false;
                }
            });
            serverThread.start();
        } catch (Exception e) {
            isRunning = false;
        }
    }

    /**
     * Stops the embedded server and releases the server socket.
     */
    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
            if (serverThread != null) {
                serverThread.interrupt();
                serverThread = null;
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Returns the HTTP localhost URL for the specified file in the document root.
     */
    public String getUrl(String fileName) {
        return "http://localhost:" + port + "/" + fileName;
    }

    /**
     * Handles incoming HTTP GET requests, serving files from the document root.
     */
    private void handleRequest(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             OutputStream out = socket.getOutputStream()) {

            String request = in.readLine();
            if (request == null) return;

            String[] parts = request.split(" ");
            if (parts.length < 2) return;

            String path = parts[1];
            if (path.equals("/")) path = "/index.html";

            if (path.startsWith("/")) path = path.substring(1);

            File file = new File(documentRoot, path);

            if (!file.getCanonicalPath().startsWith(documentRoot.getCanonicalPath())) {
                out.write(("HTTP/1.1 403 Forbidden\r\n\r\n").getBytes());
                out.flush();
                return;
            }

            if (file.exists() && !file.isDirectory()) {
                String mimeType = getMimeType(path);

                byte[] content = new byte[(int) file.length()];
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.read(content);
                }

                out.write(("HTTP/1.1 200 OK\r\n").getBytes());
                out.write(("Content-Type: " + mimeType + "\r\n").getBytes());
                out.write(("Content-Length: " + content.length + "\r\n").getBytes());
                out.write(("Access-Control-Allow-Origin: *\r\n").getBytes());
                out.write(("\r\n").getBytes());
                out.write(content);
            } else {
                out.write(("HTTP/1.1 404 Not Found\r\n\r\n").getBytes());
            }
            out.flush();
        } catch (Exception ignored) {
        }
    }

    /**
     * Resolves the MIME type for the requested file path.
     */
    private String getMimeType(String path) {
        path = path.toLowerCase();
        if (path.endsWith(".html") || path.endsWith(".htm")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "text/plain";
    }
}