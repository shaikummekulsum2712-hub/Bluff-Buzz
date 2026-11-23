package com.fibbage.ui;

import com.fibbage.model.GameConstants;
import com.fibbage.network.GameClient;
import com.fibbage.network.NetworkUtils;
import com.fibbage.network.QRCodeGenerator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * LobbyPanel handles:
 * 1. Hosting a new session or joining an existing session
 * 2. Auto-detecting LAN IP and displaying scannable QR Code for smartphone players
 * 3. 4-Player square waiting room with host controls
 */
public class LobbyPanel extends JPanel {
    private final MainFrame mainFrame;
    private final GameClient client;

    // View Switching
    private final CardLayout lobbyCardLayout = new CardLayout();
    private final JPanel containerPanel = new JPanel(lobbyCardLayout);

    // Initial Choice Form
    private final JTextField hostNameField = UITheme.createStyledTextField(12);
    private final JTextField joinNameField = UITheme.createStyledTextField(12);
    private final JTextField joinSessionField = UITheme.createStyledTextField(6);
    private final JTextField serverIpField = UITheme.createStyledTextField(10);

    // Waiting Room UI
    private final JLabel sessionIdLabel = new JLabel("SESSION: ----");
    private final JLabel playerCountLabel = new JLabel("Players: 1 / 4");
    private final JButton startGameBtn = UITheme.createStyledButton("Start Game [Launch Showdown]", UITheme.ACCENT_GREEN, UITheme.BG_DARK);
    private final JButton copyLinkBtn = UITheme.createStyledButton("Copy Invite Link", UITheme.BG_CARD_LIGHT, UITheme.TEXT_WHITE);
    private final JLabel statusLabel = new JLabel("Share Session Code or QR Code with friends to join!");
    private final JLabel qrCodeLabel = new JLabel();
    private final PlayerSquareWidget squareWidget = new PlayerSquareWidget();

    private String currentSessionId = "";

    public LobbyPanel(MainFrame mainFrame, GameClient client) {
        this.mainFrame = mainFrame;
        this.client = client;

        setLayout(new BorderLayout());
        setBackground(UITheme.BG_DARK);

        hostNameField.setText("Host");
        joinNameField.setText("Player");
        serverIpField.setText(NetworkUtils.getLocalIpAddress());

        JPanel connectScreen = buildConnectScreen();
        JPanel waitingRoomScreen = buildWaitingRoomScreen();

        containerPanel.setOpaque(false);
        containerPanel.add(connectScreen, "CONNECT");
        containerPanel.add(waitingRoomScreen, "WAITING_ROOM");

        add(containerPanel, BorderLayout.CENTER);
    }

    private JPanel buildConnectScreen() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0;
        gbc.gridy = 0;

        // Title
        JLabel title = new JLabel("BLUFF & BUZZ");
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.ACCENT_CYAN);
        panel.add(title, gbc);

        gbc.gridy++;
        JLabel subtitle = new JLabel("Reflex Trivia \u2022 AI Lie-Detector Bluffing \u2022 High-Stakes Risk");
        subtitle.setFont(UITheme.FONT_BODY);
        subtitle.setForeground(UITheme.TEXT_MUTED);
        panel.add(subtitle, gbc);

        // Network Pill
        gbc.gridy++;
        String localIp = NetworkUtils.getLocalIpAddress();
        JLabel networkInfo = new JLabel("Network IP: " + localIp + " (Mobile QR players can join on Wi-Fi)");
        networkInfo.setFont(UITheme.FONT_SMALL);
        networkInfo.setForeground(UITheme.ACCENT_YELLOW);
        panel.add(networkInfo, gbc);

        gbc.gridy++;
        JPanel cardsWrap = new JPanel(new GridLayout(1, 2, 30, 0));
        cardsWrap.setOpaque(false);

        // Host Card
        JPanel hostCard = UITheme.createCardPanel();
        hostCard.setLayout(new BoxLayout(hostCard, BoxLayout.Y_AXIS));
        hostCard.setPreferredSize(new Dimension(340, 270));

        JLabel hostHeader = new JLabel("Host New Game");
        hostHeader.setFont(UITheme.FONT_HEADER);
        hostHeader.setForeground(UITheme.ACCENT_YELLOW);
        hostHeader.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hostDesc = new JLabel("Runs session on this computer / LAN");
        hostDesc.setFont(UITheme.FONT_SMALL);
        hostDesc.setForeground(UITheme.TEXT_MUTED);
        hostDesc.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hNameLbl = new JLabel("Your Name:");
        hNameLbl.setFont(UITheme.FONT_SMALL);
        hNameLbl.setForeground(UITheme.TEXT_WHITE);
        hNameLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        hostNameField.setMaximumSize(new Dimension(240, 36));
        hostNameField.setHorizontalAlignment(JTextField.CENTER);

        JButton createBtn = UITheme.createStyledButton("Create Session [Host]", UITheme.ACCENT_YELLOW, UITheme.BG_DARK);
        createBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        createBtn.addActionListener(e -> onHostClicked());

        hostCard.add(hostHeader);
        hostCard.add(Box.createVerticalStrut(4));
        hostCard.add(hostDesc);
        hostCard.add(Box.createVerticalStrut(15));
        hostCard.add(hNameLbl);
        hostCard.add(Box.createVerticalStrut(5));
        hostCard.add(hostNameField);
        hostCard.add(Box.createVerticalStrut(22));
        hostCard.add(createBtn);

        // Join Card
        JPanel joinCard = UITheme.createCardPanel();
        joinCard.setLayout(new BoxLayout(joinCard, BoxLayout.Y_AXIS));
        joinCard.setPreferredSize(new Dimension(340, 270));

        JLabel joinHeader = new JLabel("Join Session");
        joinHeader.setFont(UITheme.FONT_HEADER);
        joinHeader.setForeground(UITheme.ACCENT_CYAN);
        joinHeader.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel joinDesc = new JLabel("Enter code to join a friend's room");
        joinDesc.setFont(UITheme.FONT_SMALL);
        joinDesc.setForeground(UITheme.TEXT_MUTED);
        joinDesc.setAlignmentX(Component.CENTER_ALIGNMENT);

        joinNameField.setMaximumSize(new Dimension(240, 32));
        joinNameField.setHorizontalAlignment(JTextField.CENTER);

        joinSessionField.setMaximumSize(new Dimension(240, 32));
        joinSessionField.setFont(new Font("Segoe UI", Font.BOLD, 18));
        joinSessionField.setHorizontalAlignment(JTextField.CENTER);

        serverIpField.setMaximumSize(new Dimension(240, 32));
        serverIpField.setHorizontalAlignment(JTextField.CENTER);

        JButton joinBtn = UITheme.createStyledButton("Join Game [Play]", UITheme.ACCENT_CYAN, UITheme.BG_DARK);
        joinBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        joinBtn.addActionListener(e -> onJoinClicked());

        joinCard.add(joinHeader);
        joinCard.add(Box.createVerticalStrut(4));
        joinCard.add(joinDesc);
        joinCard.add(Box.createVerticalStrut(10));
        joinCard.add(new JLabel("Your Name:") {{ setForeground(UITheme.TEXT_WHITE); setAlignmentX(Component.CENTER_ALIGNMENT); setFont(UITheme.FONT_SMALL); }});
        joinCard.add(joinNameField);
        joinCard.add(Box.createVerticalStrut(6));
        joinCard.add(new JLabel("Session Code (e.g. SHOW):") {{ setForeground(UITheme.TEXT_WHITE); setAlignmentX(Component.CENTER_ALIGNMENT); setFont(UITheme.FONT_SMALL); }});
        joinCard.add(joinSessionField);
        joinCard.add(Box.createVerticalStrut(6));
        joinCard.add(new JLabel("Server IP:") {{ setForeground(UITheme.TEXT_MUTED); setAlignmentX(Component.CENTER_ALIGNMENT); setFont(UITheme.FONT_SMALL); }});
        joinCard.add(serverIpField);
        joinCard.add(Box.createVerticalStrut(15));
        joinCard.add(joinBtn);

        cardsWrap.add(hostCard);
        cardsWrap.add(joinCard);

        panel.add(cardsWrap, gbc);
        return panel;
    }

    private JPanel buildWaitingRoomScreen() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(15, 25, 15, 25));

        // Top info bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        sessionIdLabel.setFont(UITheme.FONT_TITLE);
        sessionIdLabel.setForeground(UITheme.ACCENT_YELLOW);

        playerCountLabel.setFont(UITheme.FONT_HEADER);
        playerCountLabel.setForeground(UITheme.ACCENT_CYAN);

        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        topActions.setOpaque(false);

        copyLinkBtn.addActionListener(e -> {
            String url = NetworkUtils.getJoinUrl(NetworkUtils.getLocalIpAddress(), GameConstants.DEFAULT_WEB_PORT, currentSessionId);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
            copyLinkBtn.setText("Copied!");
            Timer t = new Timer(2000, ev -> copyLinkBtn.setText("Copy Invite Link"));
            t.setRepeats(false);
            t.start();
        });

        topActions.add(copyLinkBtn);
        topActions.add(playerCountLabel);

        topBar.add(sessionIdLabel, BorderLayout.WEST);
        topBar.add(topActions, BorderLayout.EAST);

        panel.add(topBar, BorderLayout.NORTH);

        // Center Area: QR Code Panel (West) + 4-Player Square Preview (Center)
        JPanel centerContainer = new JPanel(new BorderLayout(20, 10));
        centerContainer.setOpaque(false);

        // QR Code Side Card
        JPanel qrCard = UITheme.createCardPanel();
        qrCard.setLayout(new BoxLayout(qrCard, BoxLayout.Y_AXIS));
        qrCard.setPreferredSize(new Dimension(210, 280));

        JLabel qrTitle = new JLabel("Scan to Join");
        qrTitle.setFont(UITheme.FONT_HEADER);
        qrTitle.setForeground(UITheme.ACCENT_CYAN);
        qrTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel qrSub = new JLabel("Phone camera / QR");
        qrSub.setFont(UITheme.FONT_SMALL);
        qrSub.setForeground(UITheme.TEXT_MUTED);
        qrSub.setAlignmentX(Component.CENTER_ALIGNMENT);

        qrCodeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        qrCodeLabel.setPreferredSize(new Dimension(160, 160));
        qrCodeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        qrCard.add(qrTitle);
        qrCard.add(Box.createVerticalStrut(4));
        qrCard.add(qrSub);
        qrCard.add(Box.createVerticalStrut(10));
        qrCard.add(qrCodeLabel);

        centerContainer.add(qrCard, BorderLayout.WEST);

        // 4-Player Square
        JPanel arenaWrap = new JPanel(new BorderLayout());
        arenaWrap.setOpaque(false);
        JLabel midText = new JLabel("4-PLAYER SQUARE ARENA (AI FILLS REMAINING SPOTS)", SwingConstants.CENTER);
        midText.setFont(UITheme.FONT_BOLD);
        midText.setForeground(UITheme.TEXT_MUTED);
        arenaWrap.add(midText, BorderLayout.NORTH);
        arenaWrap.add(squareWidget, BorderLayout.CENTER);

        centerContainer.add(arenaWrap, BorderLayout.CENTER);
        panel.add(centerContainer, BorderLayout.CENTER);

        // Bottom Controls
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 10));
        bottomBar.setOpaque(false);

        statusLabel.setFont(UITheme.FONT_BODY);
        statusLabel.setForeground(UITheme.TEXT_WHITE);

        startGameBtn.setFont(UITheme.FONT_HEADER);
        startGameBtn.addActionListener(e -> client.startGame());

        bottomBar.add(statusLabel);
        bottomBar.add(startGameBtn);

        panel.add(bottomBar, BorderLayout.SOUTH);
        return panel;
    }

    private void onHostClicked() {
        String name = hostNameField.getText().trim();
        if (name.isEmpty()) name = "Host";

        // Spin up local server in background if not already started
        mainFrame.startEmbeddedServer();

        // Connect client to localhost
        if (client.connect("localhost", GameConstants.DEFAULT_PORT)) {
            client.createSession(name);
            lobbyCardLayout.show(containerPanel, "WAITING_ROOM");
            statusLabel.setText("You are the host! Start game when ready.");
            startGameBtn.setVisible(true);
        } else {
            JOptionPane.showMessageDialog(this, "Could not bind or connect to server on port " + GameConstants.DEFAULT_PORT, "Server Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onJoinClicked() {
        String name = joinNameField.getText().trim();
        String code = joinSessionField.getText().trim();
        String host = serverIpField.getText().trim();

        if (name.isEmpty()) name = "Guest";
        if (code.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a valid Session Code!", "Missing Code", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (host.isEmpty()) host = "localhost";

        if (client.connect(host, GameConstants.DEFAULT_PORT)) {
            client.joinSession(code, name);
            lobbyCardLayout.show(containerPanel, "WAITING_ROOM");
            statusLabel.setText("Joined Session! Waiting for host to launch...");
            startGameBtn.setVisible(false);
        } else {
            JOptionPane.showMessageDialog(this, "Could not connect to server at " + host + ":" + GameConstants.DEFAULT_PORT, "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void updateLobbyState(String sessionId, int count, List<String> playerNames, String hostName) {
        this.currentSessionId = sessionId;
        sessionIdLabel.setText("SESSION CODE: " + sessionId);
        playerCountLabel.setText("Players: " + count + " / 4");
        squareWidget.updatePlayers(playerNames, client.getMyPlayerName());

        // Update QR Code
        String joinUrl = NetworkUtils.getJoinUrl(NetworkUtils.getLocalIpAddress(), GameConstants.DEFAULT_WEB_PORT, sessionId);
        try {
            BufferedImage qrImg = QRCodeGenerator.toBufferedImage(joinUrl, 160);
            qrCodeLabel.setIcon(new ImageIcon(qrImg));
        } catch (Exception ignored) {}

        if (client.getMyPlayerName().equalsIgnoreCase(hostName)) {
            startGameBtn.setVisible(true);
            statusLabel.setText("Host controls: Click Start Game to launch!");
        } else {
            startGameBtn.setVisible(false);
            statusLabel.setText("Waiting for host (" + hostName + ") to start the game...");
        }
    }
}
