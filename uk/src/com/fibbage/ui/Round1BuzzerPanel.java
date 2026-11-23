package com.fibbage.ui;

import com.fibbage.network.GameClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Round 1: Reflex Buzzer Sprint
 * Features 4-player square layout, giant synchronized 3D buzzer button,
 * options reveal, lockout states, and live score popups.
 */
public class Round1BuzzerPanel extends JPanel {
    private final MainFrame mainFrame;
    private final GameClient client;

    // Header UI
    private final JLabel headerTitle = new JLabel("ROUND 1: REFLEX BUZZER SPRINT", SwingConstants.CENTER);
    private final JLabel categoryBanner = new JLabel("CATEGORY: GENERAL KNOWLEDGE", SwingConstants.CENTER);
    private final JLabel timerLabel = new JLabel("TIME: 20s", SwingConstants.RIGHT);
    private final JProgressBar timerBar = new JProgressBar(0, 20);

    // 4-Player Square
    private final PlayerSquareWidget squareWidget = new PlayerSquareWidget();

    // Center Question Area
    private final JLabel questionLabel = new JLabel("Question prompt loading...", SwingConstants.CENTER);
    private final JLabel statusBanner = new JLabel("SLAM THE BUZZER FIRST TO ANSWER!", SwingConstants.CENTER);
    private JButton buzzButton;

    // 4 Option Buttons
    private final JPanel optionsGrid = new JPanel(new GridLayout(2, 2, 16, 12));
    private final List<JButton> optionButtons = new ArrayList<>();

    private boolean iAmBuzzerWinner = false;
    private boolean isLockedOut = false;

    public Round1BuzzerPanel(MainFrame mainFrame, GameClient client) {
        this.mainFrame = mainFrame;
        this.client = client;

        setLayout(new BorderLayout());
        setBackground(UITheme.BG_DARK);
        setBorder(new EmptyBorder(10, 20, 10, 20));

        add(buildHeaderPanel(), BorderLayout.NORTH);

        JPanel innerQuestionPanel = buildInnerArena();
        squareWidget.add(innerQuestionPanel, BorderLayout.CENTER);
        add(squareWidget, BorderLayout.CENTER);
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 10, 0));

        headerTitle.setFont(UITheme.FONT_TITLE);
        headerTitle.setForeground(UITheme.ACCENT_YELLOW);

        categoryBanner.setFont(UITheme.FONT_BOLD);
        categoryBanner.setForeground(UITheme.ACCENT_CYAN);

        JPanel topTitles = new JPanel(new GridLayout(2, 1, 2, 2));
        topTitles.setOpaque(false);
        topTitles.add(headerTitle);
        topTitles.add(categoryBanner);

        JPanel timerPanel = new JPanel(new BorderLayout(5, 5));
        timerPanel.setOpaque(false);
        timerLabel.setFont(UITheme.FONT_HEADER);
        timerLabel.setForeground(UITheme.TEXT_WHITE);

        timerBar.setValue(20);
        timerBar.setForeground(UITheme.ACCENT_CYAN);
        timerBar.setBackground(UITheme.BG_CARD);
        timerBar.setBorderPainted(false);
        timerBar.setPreferredSize(new Dimension(160, 12));

        timerPanel.add(timerLabel, BorderLayout.NORTH);
        timerPanel.add(timerBar, BorderLayout.SOUTH);

        header.add(topTitles, BorderLayout.CENTER);
        header.add(timerPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildInnerArena() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(10, 20, 10, 20));

        // Question Card
        JPanel qCard = UITheme.createCardPanel();
        qCard.setLayout(new GridLayout(2, 1, 4, 4));

        questionLabel.setFont(new Font("Segoe UI", Font.BOLD, 19));
        questionLabel.setForeground(UITheme.TEXT_WHITE);

        statusBanner.setFont(UITheme.FONT_BOLD);
        statusBanner.setForeground(UITheme.ACCENT_YELLOW);

        qCard.add(questionLabel);
        qCard.add(statusBanner);
        panel.add(qCard, BorderLayout.NORTH);

        // Giant Circular 3D Buzzer Button
        buzzButton = new JButton("BUZZ!") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                int dia = Math.min(w, h) - 10;
                int x = (w - dia) / 2;
                int y = (h - dia) / 2;

                if (!isEnabled()) {
                    g2.setColor(new Color(0x35, 0x30, 0x48));
                    g2.fill(new Ellipse2D.Float(x, y, dia, dia));
                } else if (getModel().isPressed()) {
                    g2.setColor(new Color(0xD9, 0x1A, 0x4D));
                    g2.fill(new Ellipse2D.Float(x + 2, y + 2, dia - 4, dia - 4));
                } else {
                    // Outer glow ring
                    g2.setColor(new Color(0xFF, 0x33, 0x66, 60));
                    g2.fill(new Ellipse2D.Float(x - 4, y - 4, dia + 8, dia + 8));

                    // Radial gradient button body
                    GradientPaint gp = new GradientPaint(x, y, new Color(0xFF, 0x60, 0x85), x, y + dia, new Color(0xC7, 0x14, 0x42));
                    g2.setPaint(gp);
                    g2.fill(new Ellipse2D.Float(x, y, dia, dia));

                    // Specular highlight
                    g2.setColor(new Color(255, 255, 255, 80));
                    g2.fill(new Ellipse2D.Float(x + dia * 0.15f, y + dia * 0.08f, dia * 0.7f, dia * 0.35f));
                }

                g2.dispose();
                super.paintComponent(g);
            }
        };

        buzzButton.setFont(UITheme.FONT_BUZZER);
        buzzButton.setForeground(Color.WHITE);
        buzzButton.setFocusPainted(false);
        buzzButton.setContentAreaFilled(false);
        buzzButton.setBorderPainted(false);
        buzzButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        buzzButton.setPreferredSize(new Dimension(200, 150));
        buzzButton.addActionListener(e -> onBuzzClicked());

        JPanel buzzerWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buzzerWrap.setOpaque(false);
        buzzerWrap.add(buzzButton);
        panel.add(buzzerWrap, BorderLayout.CENTER);

        // 4 Options Grid
        optionsGrid.setOpaque(false);
        optionsGrid.setPreferredSize(new Dimension(520, 115));

        for (int i = 0; i < 4; i++) {
            JButton optBtn = UITheme.createStyledButton("Option " + (i + 1), UITheme.BG_CARD_LIGHT, UITheme.TEXT_WHITE);
            optBtn.setEnabled(false);
            optBtn.addActionListener(e -> onOptionClicked(optBtn.getText()));
            optionButtons.add(optBtn);
            optionsGrid.add(optBtn);
        }

        panel.add(optionsGrid, BorderLayout.SOUTH);
        return panel;
    }

    private void onBuzzClicked() {
        if (!isLockedOut) {
            client.sendBuzz();
        }
    }

    private void onOptionClicked(String chosenOption) {
        if (iAmBuzzerWinner) {
            for (JButton btn : optionButtons) {
                btn.setEnabled(false);
            }
            client.sendBuzzAnswer(chosenOption);
        }
    }

    public void setQuestion(int qIdx, int totalQ, String category, String prompt, List<String> options) {
        headerTitle.setText(String.format("ROUND 1: REFLEX BUZZER SPRINT (Question %d of %d)", qIdx, totalQ));
        categoryBanner.setText("CATEGORY: " + category.toUpperCase());
        questionLabel.setText("<html><center>" + prompt + "</center></html>");

        statusBanner.setText("RACE TO SLAM THE BUZZER FIRST!");
        statusBanner.setForeground(UITheme.ACCENT_YELLOW);

        iAmBuzzerWinner = false;
        isLockedOut = false;

        buzzButton.setText("BUZZ!");
        buzzButton.setEnabled(true);

        squareWidget.resetAllStatusLights();

        for (int i = 0; i < optionButtons.size(); i++) {
            JButton btn = optionButtons.get(i);
            if (i < options.size()) {
                btn.setText(options.get(i));
                btn.setVisible(true);
            } else {
                btn.setVisible(false);
            }
            btn.setEnabled(false);
            btn.setBackground(UITheme.BG_CARD_LIGHT);
        }
    }

    public void onBuzzLocked(String buzzWinner) {
        boolean isMe = client.getMyPlayerName().equalsIgnoreCase(buzzWinner);
        iAmBuzzerWinner = isMe;

        buzzButton.setEnabled(false);

        if (isMe) {
            buzzButton.setText("YOUR TURN!");
            statusBanner.setText("YOU HAVE THE FLOOR! Choose your answer!");
            statusBanner.setForeground(UITheme.ACCENT_GREEN);

            for (JButton btn : optionButtons) {
                btn.setEnabled(true);
                btn.setBackground(UITheme.BG_CARD);
            }
        } else {
            buzzButton.setText(buzzWinner);
            statusBanner.setText(buzzWinner + " buzzed first! Waiting for answer...");
            statusBanner.setForeground(UITheme.ACCENT_CYAN);

            for (JButton btn : optionButtons) {
                btn.setEnabled(false);
            }
        }
    }

    public void updateScorePopup(String playerName, int delta, int newScore, String message) {
        statusBanner.setText(message);
        statusBanner.setForeground(delta > 0 ? UITheme.ACCENT_GREEN : UITheme.ACCENT_RED);

        if (playerName.equalsIgnoreCase("ALL")) {
            squareWidget.updateScore(client.getMyPlayerName(), newScore, delta);
        } else {
            squareWidget.updateScore(playerName, newScore, delta);

            if (delta < 0) {
                squareWidget.setPlayerLockedOut(playerName, true);
                if (playerName.equalsIgnoreCase(client.getMyPlayerName())) {
                    isLockedOut = true;
                    buzzButton.setText("LOCKED OUT");
                    buzzButton.setEnabled(false);
                } else if (!isLockedOut) {
                    buzzButton.setText("BUZZ REOPENED!");
                    buzzButton.setEnabled(true);
                }
            }
        }
    }

    public void updateTimer(int secondsRemaining) {
        timerLabel.setText("TIME: " + secondsRemaining + "s");
        timerBar.setMaximum(Math.max(timerBar.getMaximum(), secondsRemaining));
        timerBar.setValue(secondsRemaining);
    }

    public PlayerSquareWidget getSquareWidget() {
        return squareWidget;
    }
}
