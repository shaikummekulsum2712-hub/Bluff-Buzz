package com.fibbage.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PlayerSquareWidget renders the 4 players on the 4 sides of the screen (Top, Bottom, Left, Right).
 * Features rounded avatar initials, live scores, status badges, lockout tags, and score change popups.
 */
public class PlayerSquareWidget extends JPanel {
    private final PlayerBadge topBadge;
    private final PlayerBadge bottomBadge;
    private final PlayerBadge leftBadge;
    private final PlayerBadge rightBadge;

    private final Map<String, PlayerBadge> playerToBadgeMap = new HashMap<>();

    public PlayerSquareWidget() {
        setLayout(new BorderLayout());
        setOpaque(false);

        topBadge = new PlayerBadge("Top Player", "Waiting...", false);
        bottomBadge = new PlayerBadge("Bottom Player (You)", "1,000 pts", true);
        leftBadge = new PlayerBadge("Left Player", "Waiting...", false);
        rightBadge = new PlayerBadge("Right Player", "Waiting...", false);

        JPanel topWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        topWrap.setOpaque(false);
        topWrap.add(topBadge);

        JPanel bottomWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomWrap.setOpaque(false);
        bottomWrap.add(bottomBadge);

        JPanel leftWrap = new JPanel(new GridBagLayout());
        leftWrap.setOpaque(false);
        leftWrap.add(leftBadge);

        JPanel rightWrap = new JPanel(new GridBagLayout());
        rightWrap.setOpaque(false);
        rightWrap.add(rightBadge);

        add(topWrap, BorderLayout.NORTH);
        add(bottomWrap, BorderLayout.SOUTH);
        add(leftWrap, BorderLayout.WEST);
        add(rightWrap, BorderLayout.EAST);
    }

    public void updatePlayers(List<String> playerNames, String localPlayerName) {
        playerToBadgeMap.clear();

        // 1. Assign local player to bottom
        bottomBadge.setPlayerName(localPlayerName != null ? localPlayerName + " (You)" : "You");
        playerToBadgeMap.put(localPlayerName != null ? localPlayerName : "You", bottomBadge);

        // 2. Assign other players to Top, Left, Right
        int otherIdx = 0;
        PlayerBadge[] others = {topBadge, leftBadge, rightBadge};

        for (String name : playerNames) {
            if (name.equalsIgnoreCase(localPlayerName)) continue;
            if (otherIdx < others.length) {
                others[otherIdx].setPlayerName(name);
                playerToBadgeMap.put(name, others[otherIdx]);
                otherIdx++;
            }
        }

        // Hide or mark unassigned badges
        for (int i = otherIdx; i < others.length; i++) {
            others[i].setPlayerName("Empty Seat");
            others[i].setScoreText("Waiting...");
        }
    }

    public void updateScore(String playerName, int newScore, int delta) {
        PlayerBadge badge = findBadge(playerName);
        if (badge != null) {
            badge.setScoreText(String.format("%,d pts", newScore));
            if (delta != 0) {
                badge.triggerScoreDelta(delta);
            }
        }
    }

    public void setPlayerSubmitted(String playerName, boolean submitted) {
        PlayerBadge badge = findBadge(playerName);
        if (badge != null) {
            badge.setSubmittedLight(submitted);
        }
    }

    public void setPlayerLockedOut(String playerName, boolean lockedOut) {
        PlayerBadge badge = findBadge(playerName);
        if (badge != null) {
            badge.setLockedOut(lockedOut);
        }
    }

    public void resetAllStatusLights() {
        topBadge.setSubmittedLight(false);
        topBadge.setLockedOut(false);
        bottomBadge.setSubmittedLight(false);
        bottomBadge.setLockedOut(false);
        leftBadge.setSubmittedLight(false);
        leftBadge.setLockedOut(false);
        rightBadge.setSubmittedLight(false);
        rightBadge.setLockedOut(false);
    }

    private PlayerBadge findBadge(String playerName) {
        if (playerName == null) return null;
        for (Map.Entry<String, PlayerBadge> e : playerToBadgeMap.entrySet()) {
            if (e.getKey().equalsIgnoreCase(playerName) || (e.getKey().startsWith(playerName) && e.getKey().contains("(You)"))) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * Individual Player Badge Component
     */
    public static class PlayerBadge extends JPanel {
        private final JLabel avatarLabel = new JLabel("?", SwingConstants.CENTER);
        private final JLabel nameLabel = new JLabel("Player");
        private final JLabel scoreLabel = new JLabel("1,000 pts");
        private final JLabel statusDot = new JLabel("\u25CF");
        private final JLabel deltaLabel = new JLabel("");
        private boolean isLocal = false;
        private boolean lockedOut = false;
        private boolean submitted = false;

        public PlayerBadge(String name, String score, boolean isLocal) {
            this.isLocal = isLocal;
            setLayout(new BorderLayout(10, 0));
            setOpaque(false);
            setPreferredSize(new Dimension(195, 68));
            setBorder(new EmptyBorder(8, 12, 8, 12));

            // Avatar circle
            avatarLabel.setPreferredSize(new Dimension(38, 38));
            avatarLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
            avatarLabel.setForeground(Color.WHITE);
            avatarLabel.setOpaque(false);

            JPanel textWrap = new JPanel(new GridLayout(2, 1, 0, 2));
            textWrap.setOpaque(false);

            nameLabel.setFont(UITheme.FONT_BOLD);
            nameLabel.setForeground(isLocal ? UITheme.ACCENT_CYAN : UITheme.TEXT_WHITE);

            JPanel scoreWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            scoreWrap.setOpaque(false);

            statusDot.setFont(new Font("Segoe UI", Font.BOLD, 12));
            statusDot.setForeground(new Color(0x55, 0x55, 0x66));

            scoreLabel.setFont(UITheme.FONT_SMALL);
            scoreLabel.setForeground(UITheme.ACCENT_YELLOW);

            deltaLabel.setFont(UITheme.FONT_SMALL);
            deltaLabel.setForeground(UITheme.ACCENT_GREEN);

            scoreWrap.add(statusDot);
            scoreWrap.add(scoreLabel);
            scoreWrap.add(deltaLabel);

            textWrap.add(nameLabel);
            textWrap.add(scoreWrap);

            add(avatarLabel, BorderLayout.WEST);
            add(textWrap, BorderLayout.CENTER);

            setPlayerName(name);
            setScoreText(score);
        }

        public void setPlayerName(String name) {
            nameLabel.setText(name);
            String initial = "?";
            if (name != null && !name.trim().isEmpty()) {
                initial = name.trim().substring(0, 1).toUpperCase();
            }
            avatarLabel.setText(initial);
        }

        public void setScoreText(String text) {
            scoreLabel.setText(text);
        }

        public void setSubmittedLight(boolean on) {
            this.submitted = on;
            statusDot.setForeground(on ? UITheme.ACCENT_GREEN : new Color(0x55, 0x55, 0x66));
            repaint();
        }

        public void setLockedOut(boolean locked) {
            this.lockedOut = locked;
            if (locked) {
                deltaLabel.setText("LOCKED OUT");
                deltaLabel.setForeground(UITheme.ACCENT_RED);
            } else if (deltaLabel.getText().contains("LOCKED OUT")) {
                deltaLabel.setText("");
            }
            repaint();
        }

        public void triggerScoreDelta(int delta) {
            String text = (delta > 0 ? "+" : "") + delta;
            deltaLabel.setText(text);
            deltaLabel.setForeground(delta > 0 ? UITheme.ACCENT_GREEN : UITheme.ACCENT_RED);

            Timer t = new Timer(3000, e -> {
                if (!lockedOut) {
                    deltaLabel.setText("");
                }
            });
            t.setRepeats(false);
            t.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color bg = isLocal ? new Color(0x22, 0x1E, 0x3D) : UITheme.BG_CARD;
            if (submitted) {
                bg = new Color(0x13, 0x33, 0x28);
            } else if (lockedOut) {
                bg = new Color(0x35, 0x17, 0x22);
            }

            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));

            // Avatar background circle
            int avX = 12;
            int avY = (getHeight() - 36) / 2;
            g2.setColor(isLocal ? UITheme.ACCENT_CYAN : (submitted ? UITheme.ACCENT_GREEN : UITheme.ACCENT_PURPLE));
            g2.fillOval(avX, avY, 36, 36);

            // Border
            g2.setColor(isLocal ? UITheme.ACCENT_CYAN : (submitted ? UITheme.ACCENT_GREEN : UITheme.BORDER_CARD));
            g2.setStroke(new BasicStroke(isLocal ? 1.8f : 1f));
            g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 16, 16));

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
