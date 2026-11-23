package com.fibbage.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Block 4 — Multi-Client TCP Sockets
 * ClientHandler runs on a dedicated thread for each connected player client.
 * Reads messages from the client's socket and dispatches to GameServer.
 */
public class ClientHandler implements Runnable {
    protected final Socket socket;
    protected final GameServer server;
    protected BufferedReader in;
    protected PrintWriter out;
    protected String playerName;
    protected String sessionId;
    protected volatile boolean running = true;

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line;
            while (running && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                server.handleClientMessage(this, line);
            }
        } catch (IOException e) {
            // Client disconnected or network reset
        } finally {
            close();
            server.onClientDisconnected(this);
        }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    public void close() {
        running = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Socket getSocket() {
        return socket;
    }
}

