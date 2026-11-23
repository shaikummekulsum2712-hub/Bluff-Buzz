package com.fibbage.network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Embedded Web & WebSocket Server.
 * Serves the modern HTML5 party game web client and handles real-time bidirectional
 * WebSocket connections so anyone on a mobile phone, tablet, or browser can scan the QR code
 * or click a link to host or join a session without installing Java or software!
 */
public class WebServer implements Runnable {
    private final int port;
    private final GameServer gameServer;
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public WebServer(int port, GameServer gameServer) {
        this.port = port;
        this.gameServer = gameServer;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[WebServer] Live on port " + port + ". Ready for Web & Mobile QR players!");

            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    threadPool.submit(() -> handleConnection(socket));
                } catch (IOException e) {
                    if (!running) break;
                }
            }
        } catch (IOException e) {
            System.err.println("[WebServer] Failed to bind port " + port + ": " + e.getMessage());
        } finally {
            stop();
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
        threadPool.shutdownNow();
    }

    private void handleConnection(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // Read HTTP request header
            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int b;
            int consecutiveLf = 0;
            while ((b = in.read()) != -1) {
                headerBuf.write(b);
                if (b == '\n') {
                    consecutiveLf++;
                    if (consecutiveLf == 2 || headerBuf.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
                        break;
                    }
                } else if (b != '\r') {
                    consecutiveLf = 0;
                }
            }

            String headerStr = headerBuf.toString(StandardCharsets.UTF_8);
            if (headerStr.isEmpty()) {
                socket.close();
                return;
            }

            String[] lines = headerStr.split("\r?\n");
            if (lines.length == 0) {
                socket.close();
                return;
            }

            String requestLine = lines[0];
            String[] reqParts = requestLine.split(" ");
            if (reqParts.length < 2) {
                socket.close();
                return;
            }

            String method = reqParts[0].toUpperCase();
            String fullUri = reqParts[1];
            String path = fullUri;
            String query = "";
            int qIdx = fullUri.indexOf('?');
            if (qIdx != -1) {
                path = fullUri.substring(0, qIdx);
                query = fullUri.substring(qIdx + 1);
            }

            Map<String, String> headers = new HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon != -1) {
                    headers.put(lines[i].substring(0, colon).trim().toLowerCase(), lines[i].substring(colon + 1).trim());
                }
            }

            // Check if WebSocket upgrade request
            if ("/ws".equalsIgnoreCase(path) || "/websocket".equalsIgnoreCase(path)) {
                String upgrade = headers.get("upgrade");
                if (upgrade != null && upgrade.equalsIgnoreCase("websocket")) {
                    handleWebSocketUpgrade(socket, in, out, headers);
                    return; // Socket control transferred to WebSocketClientHandler
                }
            }

            // HTTP Handlers
            if ("/qr".equalsIgnoreCase(path)) {
                handleQrRequest(out, query);
            } else if ("/api/info".equalsIgnoreCase(path)) {
                handleApiInfo(out);
            } else if ("/style.css".equalsIgnoreCase(path)) {
                serveFile(out, "style.css", "text/css; charset=utf-8");
            } else if ("/app.js".equalsIgnoreCase(path)) {
                serveFile(out, "app.js", "application/javascript; charset=utf-8");
            } else {
                serveFile(out, "index.html", "text/html; charset=utf-8");
            }

            out.flush();
            socket.close();
        } catch (Exception e) {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleWebSocketUpgrade(Socket socket, InputStream in, OutputStream out, Map<String, String> headers) throws IOException, NoSuchAlgorithmException {
        String key = headers.get("sec-websocket-key");
        if (key == null) {
            sendHttp(out, 400, "text/plain", "Missing Sec-WebSocket-Key".getBytes(StandardCharsets.UTF_8));
            socket.close();
            return;
        }

        String magic = key.trim() + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest(magic.getBytes(StandardCharsets.UTF_8));
        String acceptKey = Base64.getEncoder().encodeToString(hash);

        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";

        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();

        // WebSocket connection established!
        WebSocketClientHandler handler = new WebSocketClientHandler(socket, gameServer, in, out);
        gameServer.registerClient(handler);
        threadPool.submit(handler);
    }

    private void handleQrRequest(OutputStream out, String query) throws IOException {
        String targetText = "";
        Map<String, String> params = parseQuery(query);
        if (params.containsKey("room")) {
            targetText = NetworkUtils.getJoinUrl(NetworkUtils.getLocalIpAddress(), port, params.get("room"));
        } else if (params.containsKey("text")) {
            targetText = params.get("text");
        } else {
            targetText = NetworkUtils.getJoinUrl(NetworkUtils.getLocalIpAddress(), port, "");
        }

        byte[] pngData = QRCodeGenerator.toPngBytes(targetText, 300);
        sendHttp(out, 200, "image/png", pngData);
    }

    private void handleApiInfo(OutputStream out) throws IOException {
        String localIp = NetworkUtils.getLocalIpAddress();
        String json = "{\n" +
                "  \"localIp\": \"" + localIp + "\",\n" +
                "  \"webPort\": " + port + ",\n" +
                "  \"tcpPort\": " + gameServer.getPort() + ",\n" +
                "  \"joinUrl\": \"http://" + localIp + ":" + port + "/\"\n" +
                "}";
        sendHttp(out, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private void serveFile(OutputStream out, String filename, String contentType) throws IOException {
        // Try reading from ./web/ folder first (when running from project root)
        byte[] content = null;
        Path path = Paths.get("web", filename);
        if (Files.exists(path)) {
            content = Files.readAllBytes(path);
        }
        // Try bin/web/ (when running from project root with compiled output)
        if (content == null) {
            Path binPath = Paths.get("bin", "web", filename);
            if (Files.exists(binPath)) {
                content = Files.readAllBytes(binPath);
            }
        }
        // Check classpath / embedded resources
        if (content == null) {
            try (InputStream is = getClass().getResourceAsStream("/web/" + filename)) {
                if (is != null) {
                    content = is.readAllBytes();
                }
            }
        }

        if (content != null) {
            sendHttp(out, 200, contentType, content);
        } else {
            byte[] notFound = ("404: " + filename + " not found").getBytes(StandardCharsets.UTF_8);
            sendHttp(out, 404, "text/plain", notFound);
        }
    }

    private void sendHttp(OutputStream out, int status, String contentType, byte[] body) throws IOException {
        String statusText = (status == 200) ? "OK" : (status == 404) ? "Not Found" : "Error";
        String header = "HTTP/1.1 " + status + " " + statusText + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + body.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(body);
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                try {
                    String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name());
                    String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name());
                    map.put(k, v);
                } catch (Exception ignored) {}
            }
        }
        return map;
    }
}
