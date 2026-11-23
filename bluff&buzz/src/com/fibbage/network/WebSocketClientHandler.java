package com.fibbage.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * WebSocketClientHandler manages a browser client connection over RFC 6455 WebSocket.
 * Inherits all session and player routing capabilities from ClientHandler so GameServer
 * treats web browser clients and desktop TCP clients 100% symmetrically.
 */
public class WebSocketClientHandler extends ClientHandler {
    private final InputStream rawIn;
    private final OutputStream rawOut;

    public WebSocketClientHandler(Socket socket, GameServer server, InputStream rawIn, OutputStream rawOut) {
        super(socket, server);
        this.rawIn = rawIn;
        this.rawOut = rawOut;
    }

    @Override
    public void run() {
        try {
            while (running) {
                String message = readWebSocketFrame();
                if (message == null) {
                    break; // EOF or Close frame
                }
                message = message.trim();
                if (message.isEmpty()) continue;

                server.handleClientMessage(this, message);
            }
        } catch (IOException e) {
            // Browser tab closed or network disconnected
        } finally {
            close();
            server.onClientDisconnected(this);
        }
    }

    /**
     * Reads and decodes a single RFC 6455 WebSocket frame from client.
     */
    private String readWebSocketFrame() throws IOException {
        int b1 = rawIn.read();
        if (b1 == -1) return null;

        int opcode = b1 & 0x0F;
        if (opcode == 0x8) {
            // Close frame
            return null;
        }

        int b2 = rawIn.read();
        if (b2 == -1) return null;

        boolean isMasked = (b2 & 0x80) != 0;
        long payloadLength = b2 & 0x7F;

        if (payloadLength == 126) {
            int byte1 = rawIn.read();
            int byte2 = rawIn.read();
            if (byte1 == -1 || byte2 == -1) return null;
            payloadLength = ((byte1 & 0xFF) << 8) | (byte2 & 0xFF);
        } else if (payloadLength == 127) {
            payloadLength = 0;
            for (int i = 0; i < 8; i++) {
                int b = rawIn.read();
                if (b == -1) return null;
                payloadLength = (payloadLength << 8) | (b & 0xFF);
            }
        }

        byte[] maskingKey = new byte[4];
        if (isMasked) {
            if (rawIn.readNBytes(maskingKey, 0, 4) < 4) return null;
        }

        if (payloadLength > 1024 * 1024) {
            throw new IOException("Frame payload too large: " + payloadLength);
        }

        byte[] payload = new byte[(int) payloadLength];
        int readBytes = 0;
        while (readBytes < payloadLength) {
            int chunk = rawIn.read(payload, readBytes, (int) payloadLength - readBytes);
            if (chunk == -1) return null;
            readBytes += chunk;
        }

        // Unmask
        if (isMasked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ maskingKey[i % 4]);
            }
        }

        // Handle Ping
        if (opcode == 0x9) {
            sendPong(payload);
            return ""; // empty ping handled internally
        }

        return new String(payload, StandardCharsets.UTF_8);
    }

    private synchronized void sendPong(byte[] payload) {
        try {
            rawOut.write(0x8A); // FIN + Pong
            rawOut.write(payload.length & 0x7F);
            rawOut.write(payload);
            rawOut.flush();
        } catch (IOException ignored) {}
    }

    @Override
    public synchronized void sendMessage(String message) {
        if (!running || rawOut == null) return;
        try {
            byte[] payload = message.getBytes(StandardCharsets.UTF_8);
            int len = payload.length;

            rawOut.write(0x81); // FIN + Text frame (0x1)

            if (len <= 125) {
                rawOut.write(len);
            } else if (len <= 65535) {
                rawOut.write(126);
                rawOut.write((len >> 8) & 0xFF);
                rawOut.write(len & 0xFF);
            } else {
                rawOut.write(127);
                for (int i = 7; i >= 0; i--) {
                    rawOut.write((int) ((len >> (i * 8)) & 0xFF));
                }
            }

            rawOut.write(payload);
            rawOut.flush();
        } catch (IOException e) {
            close();
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        try {
            // Send close frame if possible
            if (rawOut != null) {
                rawOut.write(new byte[]{(byte) 0x88, 0x00});
                rawOut.flush();
            }
        } catch (Exception ignored) {}

        try {
            if (rawIn != null) rawIn.close();
            if (rawOut != null) rawOut.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
