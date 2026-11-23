package com.fibbage.ui;

import com.fibbage.model.GameConstants;
import com.fibbage.network.GameClient;
import com.fibbage.network.GameServer;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Block 5 — Main Swing GUI Frame
 * CardLayout container hosting:
 * (1) LobbyPanel
 * (2) Round1BuzzerPanel
 * (3) Round2BluffPanel
 * (4) Round3WagerPanel
 * (5) LeaderboardPanel
 *
 * Ensures all UI modifications are executed strictly on the Swing Event Dispatch Thread (EDT)
 * using SwingUtilities.invokeLater().
 */
public class MainFrame extends JFrame implements GameClient.ClientEventListener {
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainContainer = new JPanel(cardLayout);

    private final GameClient client = new GameClient();
    private GameServer embeddedServer;

    private LobbyPanel lobbyPanel;
    private Round1BuzzerPanel round1Panel;
    private Round2BluffPanel round2Panel;
    private Round3WagerPanel round3Panel;
    private LeaderboardPanel leaderboardPanel;

    private String currentScreen = "LOBBY";

    public MainFrame() {
        super("BLUFF & BUZZ — Multiplayer Party Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1020, 720);
        setMinimumSize(new Dimension(920, 650));
        setLocationRelativeTo(null);

        // Apply dark background
        getContentPane().setBackground(UITheme.BG_DARK);

        client.setListener(this);

        initPanels();

        mainContainer.setOpaque(false);
        mainContainer.add(lobbyPanel, "LOBBY");
        mainContainer.add(round1Panel, "ROUND1");
        mainContainer.add(round2Panel, "ROUND2");
        mainContainer.add(round3Panel, "ROUND3");
        mainContainer.add(leaderboardPanel, "LEADERBOARD");

        add(mainContainer);
        showScreen("LOBBY");
    }

    private void initPanels() {
        lobbyPanel = new LobbyPanel(this, client);
        round1Panel = new Round1BuzzerPanel(this, client);
        round2Panel = new Round2BluffPanel(this, client);
        round3Panel = new Round3WagerPanel(this, client);
        leaderboardPanel = new LeaderboardPanel(this);
    }

    public void showScreen(String name) {
        SwingUtilities.invokeLater(() -> {
            this.currentScreen = name;
            cardLayout.show(mainContainer, name);
        });
    }

    public synchronized void startEmbeddedServer() {
        if (embeddedServer == null) {
            // Check if server is already running on port 5000
            try (java.net.Socket s = new java.net.Socket("localhost", GameConstants.DEFAULT_PORT)) {
                System.out.println("[MainFrame] Server is already running on port " + GameConstants.DEFAULT_PORT + ". Connecting to existing server.");
                return;
            } catch (Exception ignored) {}

            embeddedServer = new GameServer(GameConstants.DEFAULT_PORT);
            new Thread(embeddedServer, "EmbeddedServer-Thread").start();
            System.out.println("[MainFrame] Embedded GameServer started on port " + GameConstants.DEFAULT_PORT);
        }
    }

    // ==========================================
    // ClientEventListener Callbacks (Swing EDT)
    // ==========================================

    @Override
    public void onConnected() {
        System.out.println("[MainFrame] Connected to GameServer successfully.");
    }

    @Override
    public void onSessionCreated(String sessionId) {
        System.out.println("[MainFrame] Session created: " + sessionId);
    }

    @Override
    public void onLobbyUpdate(String sessionId, int count, List<String> playerNames, String hostName) {
        lobbyPanel.updateLobbyState(sessionId, count, playerNames, hostName);
        round1Panel.getSquareWidget().updatePlayers(playerNames, client.getMyPlayerName());
        round2Panel.getSquareWidget().updatePlayers(playerNames, client.getMyPlayerName());
        round3Panel.getSquareWidget().updatePlayers(playerNames, client.getMyPlayerName());
    }

    @Override
    public void onGameStarted() {
        showScreen("ROUND1");
    }

    @Override
    public void onRoundStarted(int roundNum, String roundTitle) {
        switch (roundNum) {
            case 1: showScreen("ROUND1"); break;
            case 2: showScreen("ROUND2"); break;
            case 3: showScreen("ROUND3"); break;
        }
    }

    @Override
    public void onTimerTick(int secondsRemaining) {
        if ("ROUND1".equals(currentScreen)) {
            round1Panel.updateTimer(secondsRemaining);
        } else if ("ROUND2".equals(currentScreen)) {
            round2Panel.updateTimer(secondsRemaining);
        } else if ("ROUND3".equals(currentScreen)) {
            round3Panel.updateTimer(secondsRemaining);
        }
    }

    @Override
    public void onRound1Question(int qIdx, int totalQ, String category, String prompt, List<String> options) {
        showScreen("ROUND1");
        round1Panel.setQuestion(qIdx, totalQ, category, prompt, options);
    }

    @Override
    public void onBuzzLocked(String buzzWinner) {
        round1Panel.onBuzzLocked(buzzWinner);
    }

    @Override
    public void onScoreUpdate(String playerName, int delta, int newScore, String message) {
        round1Panel.updateScorePopup(playerName, delta, newScore, message);
    }

    @Override
    public void onRound2Question(int qIdx, int totalQ, String category, String prompt, int timeoutSec) {
        showScreen("ROUND2");
        round2Panel.setQuestion(qIdx, totalQ, category, prompt, timeoutSec);
    }

    @Override
    public void onBluffAck() {
        round2Panel.onBluffAck();
    }

    @Override
    public void onPlayerSubmitted(String playerName) {
        round1Panel.getSquareWidget().setPlayerSubmitted(playerName, true);
        round2Panel.getSquareWidget().setPlayerSubmitted(playerName, true);
        round3Panel.getSquareWidget().setPlayerSubmitted(playerName, true);
    }

    @Override
    public void onRound2VoteOptions(List<String> optionsWithRisk) {
        round2Panel.setVoteOptions(optionsWithRisk);
    }

    @Override
    public void onRound2Reveal(String realAnswer, String scoreSummaries) {
        round2Panel.showReveal(realAnswer, scoreSummaries);
    }

    @Override
    public void onRound3Category(String category, int minWager) {
        showScreen("ROUND3");
        round3Panel.setCategory(category, minWager);
    }

    @Override
    public void onRound3Question(String prompt, List<String> options) {
        round3Panel.setQuestion(prompt, options);
    }

    @Override
    public void onRound3Reveal(String realAnswer, String resultsSummary) {
        round3Panel.showReveal(realAnswer, resultsSummary);
    }

    @Override
    public void onGameOver(String leaderboardSummary) {
        showScreen("LEADERBOARD");
        leaderboardPanel.setFinalLeaderboard(leaderboardSummary);
    }

    @Override
    public void onError(String errorMsg) {
        if (errorMsg.contains("Too close to the truth")) {
            round2Panel.onBluffRejected(errorMsg);
        } else {
            JOptionPane.showMessageDialog(this, errorMsg, "Game Alert", JOptionPane.WARNING_MESSAGE);
        }
    }

    @Override
    public void onDisconnected() {
        System.out.println("[MainFrame] Disconnected from server.");
    }

    public static void main(String[] args) {
        // Set cross-platform look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}

