package com.fibbage.network;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Block 4 & 5 — Client Network Handler
 * Manages TCP connection from the Swing UI to GameServer.
 * Dispatches all server events to the Swing Event Dispatch Thread (EDT).
 */
public class GameClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread listenerThread;
    private volatile boolean connected = false;

    private String myPlayerName = "Player";
    private String currentSessionId = "";
    private boolean isHost = false;
    private int myScore = 1000;

    private ClientEventListener listener;

    public interface ClientEventListener {
        void onConnected();
        void onSessionCreated(String sessionId);
        void onLobbyUpdate(String sessionId, int count, List<String> playerNames, String hostName);
        void onGameStarted();
        void onRoundStarted(int roundNum, String roundTitle);
        void onTimerTick(int secondsRemaining);
        void onRound1Question(int qIdx, int totalQ, String category, String prompt, List<String> options);
        void onBuzzLocked(String buzzWinner);
        void onScoreUpdate(String playerName, int delta, int newScore, String message);
        void onRound2Question(int qIdx, int totalQ, String category, String prompt, int timeoutSec);
        void onBluffAck();
        void onPlayerSubmitted(String playerName);
        void onRound2VoteOptions(List<String> optionsWithRisk);
        void onRound2Reveal(String realAnswer, String scoreSummaries);
        void onRound3Category(String category, int minWager);
        void onRound3Question(String prompt, List<String> options);
        void onRound3Reveal(String realAnswer, String resultsSummary);
        void onGameOver(String leaderboardSummary);
        void onError(String errorMsg);
        void onDisconnected();
    }

    public void setListener(ClientEventListener listener) {
        this.listener = listener;
    }

    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            connected = true;

            listenerThread = new Thread(this::listenLoop, "GameClient-Listener");
            listenerThread.setDaemon(true);
            listenerThread.start();

            if (listener != null) {
                SwingUtilities.invokeLater(listener::onConnected);
            }
            return true;
        } catch (IOException e) {
            System.err.println("[GameClient] Connection failed: " + e.getMessage());
            return false;
        }
    }

    private void listenLoop() {
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                String message = line.trim();
                if (message.isEmpty()) continue;
                processMessage(message);
            }
        } catch (IOException e) {
            // Disconnected
        } finally {
            disconnect();
        }
    }

    private void processMessage(String message) {
        String[] parts = message.split(NetworkProtocol.DELIMITER);
        if (parts.length == 0 || listener == null) return;

        String evt = parts[0];

        SwingUtilities.invokeLater(() -> {
            try {
                switch (evt) {
                    case NetworkProtocol.EVT_SESSION_CREATED:
                        currentSessionId = parts.length > 1 ? parts[1] : "";
                        isHost = true;
                        listener.onSessionCreated(currentSessionId);
                        break;

                    case NetworkProtocol.EVT_LOBBY_UPDATE:
                        String sId = parts.length > 1 ? parts[1] : "";
                        currentSessionId = sId;
                        int count = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                        String namesStr = parts.length > 3 ? parts[3] : "";
                        String hostName = parts.length > 4 ? parts[4] : "";
                        List<String> names = namesStr.isEmpty() ? new ArrayList<>() : Arrays.asList(namesStr.split(","));
                        if (myPlayerName.equalsIgnoreCase(hostName)) {
                            isHost = true;
                        }
                        listener.onLobbyUpdate(sId, count, names, hostName);
                        break;

                    case NetworkProtocol.EVT_GAME_STARTED:
                        listener.onGameStarted();
                        break;

                    case NetworkProtocol.EVT_ROUND_STARTED:
                        int rNum = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                        String rTitle = parts.length > 2 ? parts[2] : "";
                        listener.onRoundStarted(rNum, rTitle);
                        break;

                    case NetworkProtocol.EVT_TIMER_TICK:
                        int sec = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                        listener.onTimerTick(sec);
                        break;

                    case NetworkProtocol.EVT_ROUND1_QUESTION:
                        int qIdx = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                        int totQ = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;
                        String cat = parts.length > 3 ? parts[3] : "";
                        String prompt = parts.length > 4 ? parts[4] : "";
                        String optsStr = parts.length > 5 ? parts[5] : "";
                        List<String> opts = optsStr.isEmpty() ? new ArrayList<>() : Arrays.asList(optsStr.split("\\|"));
                        listener.onRound1Question(qIdx, totQ, cat, prompt, opts);
                        break;

                    case NetworkProtocol.EVT_BUZZ_LOCKED:
                        String buzzerPlayer = parts.length > 1 ? parts[1] : "";
                        listener.onBuzzLocked(buzzerPlayer);
                        break;

                    case NetworkProtocol.EVT_SCORE_UPDATE:
                        String pName = parts.length > 1 ? parts[1] : "";
                        int delta = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                        int newScore = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
                        String msg = parts.length > 4 ? parts[4] : "";
                        if (pName.equalsIgnoreCase(myPlayerName)) {
                            myScore = newScore;
                        }
                        listener.onScoreUpdate(pName, delta, newScore, msg);
                        break;

                    case NetworkProtocol.EVT_ROUND2_QUESTION:
                        int r2QIdx = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                        int r2TotQ = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;
                        String r2Cat = parts.length > 3 ? parts[3] : "";
                        String r2Prompt = parts.length > 4 ? parts[4] : "";
                        int r2Timeout = parts.length > 5 ? Integer.parseInt(parts[5]) : 30;
                        listener.onRound2Question(r2QIdx, r2TotQ, r2Cat, r2Prompt, r2Timeout);
                        break;

                    case NetworkProtocol.EVT_BLUFF_ACK:
                        listener.onBluffAck();
                        break;

                    case NetworkProtocol.EVT_PLAYER_SUBMITTED:
                        String subName = parts.length > 1 ? parts[1] : "";
                        listener.onPlayerSubmitted(subName);
                        break;

                    case NetworkProtocol.EVT_ROUND2_VOTE_OPTIONS:
                        String voteOptsStr = parts.length > 1 ? parts[1] : "";
                        List<String> voteOpts = voteOptsStr.isEmpty() ? new ArrayList<>() : Arrays.asList(voteOptsStr.split("\\|"));
                        listener.onRound2VoteOptions(voteOpts);
                        break;

                    case NetworkProtocol.EVT_ROUND2_REVEAL:
                        String r2Real = parts.length > 1 ? parts[1] : "";
                        String r2Scores = parts.length > 2 ? parts[2] : "";
                        listener.onRound2Reveal(r2Real, r2Scores);
                        break;

                    case NetworkProtocol.EVT_ROUND3_CATEGORY:
                        String r3Cat = parts.length > 1 ? parts[1] : "";
                        int minWager = parts.length > 2 ? Integer.parseInt(parts[2]) : 100;
                        listener.onRound3Category(r3Cat, minWager);
                        break;

                    case NetworkProtocol.EVT_ROUND3_QUESTION:
                        String r3Prompt = parts.length > 1 ? parts[1] : "";
                        String r3OptsStr = parts.length > 2 ? parts[2] : "";
                        List<String> r3Opts = r3OptsStr.isEmpty() ? new ArrayList<>() : Arrays.asList(r3OptsStr.split("\\|"));
                        listener.onRound3Question(r3Prompt, r3Opts);
                        break;

                    case NetworkProtocol.EVT_ROUND3_REVEAL:
                        String r3Real = parts.length > 1 ? parts[1] : "";
                        String r3Res = parts.length > 2 ? parts[2] : "";
                        listener.onRound3Reveal(r3Real, r3Res);
                        break;

                    case NetworkProtocol.EVT_GAME_OVER:
                        String lBoard = parts.length > 1 ? parts[1] : "";
                        listener.onGameOver(lBoard);
                        break;

                    case NetworkProtocol.EVT_SCORES:
                        // "Alice=1500,Bob=900" -> remember my own score (used for the wager limit)
                        for (String pair : (parts.length > 1 ? parts[1] : "").split(",")) {
                            String[] kv = pair.split("=");
                            if (kv.length == 2 && kv[0].equalsIgnoreCase(myPlayerName)) {
                                myScore = Integer.parseInt(kv[1].trim());
                            }
                        }
                        break;

                    case NetworkProtocol.EVT_ERROR:
                        String err = parts.length > 1 ? parts[1] : "Unknown error";
                        listener.onError(err);
                        break;
                }
            } catch (Exception e) {
                System.err.println("[GameClient] Error parsing message: " + message + " -> " + e.getMessage());
            }
        });
    }

    public void sendMessage(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }

    // Client Actions
    public void createSession(String playerName) {
        this.myPlayerName = playerName;
        sendMessage(NetworkProtocol.CMD_CREATE_SESSION + NetworkProtocol.DELIMITER + playerName);
    }

    public void joinSession(String sessionId, String playerName) {
        this.myPlayerName = playerName;
        this.currentSessionId = sessionId;
        sendMessage(NetworkProtocol.CMD_JOIN_SESSION + NetworkProtocol.DELIMITER + sessionId + NetworkProtocol.DELIMITER + playerName);
    }

    public void startGame() {
        sendMessage(NetworkProtocol.CMD_START_GAME);
    }

    public void sendBuzz() {
        sendMessage(NetworkProtocol.CMD_BUZZ);
    }

    public void sendBuzzAnswer(String chosenOption) {
        sendMessage(NetworkProtocol.CMD_ANSWER_BUZZ + NetworkProtocol.DELIMITER + chosenOption);
    }

    public void sendSubmitBluff(String bluffText, long typingDurationMs, int backspaces) {
        sendMessage(NetworkProtocol.CMD_SUBMIT_BLUFF + NetworkProtocol.DELIMITER +
                bluffText + NetworkProtocol.DELIMITER +
                typingDurationMs + NetworkProtocol.DELIMITER +
                backspaces);
    }

    public void sendSubmitVote(String chosenOption) {
        sendMessage(NetworkProtocol.CMD_SUBMIT_VOTE + NetworkProtocol.DELIMITER + chosenOption);
    }

    public void sendSubmitWager(int amount) {
        sendMessage(NetworkProtocol.CMD_SUBMIT_WAGER + NetworkProtocol.DELIMITER + amount);
    }

    public void sendSubmitWagerAnswer(String chosenOption) {
        sendMessage(NetworkProtocol.CMD_SUBMIT_WAGER_ANSWER + NetworkProtocol.DELIMITER + chosenOption);
    }

    public void disconnect() {
        connected = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        if (listener != null) {
            SwingUtilities.invokeLater(listener::onDisconnected);
        }
    }

    public String getMyPlayerName() { return myPlayerName; }
    public String getCurrentSessionId() { return currentSessionId; }
    public boolean isHost() { return isHost; }
    public boolean isConnected() { return connected; }
    public int getMyScore() { return myScore; }
}

