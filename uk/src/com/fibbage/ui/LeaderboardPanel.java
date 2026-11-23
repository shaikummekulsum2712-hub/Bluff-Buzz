package com.fibbage.ui;

import com.fibbage.db.DatabaseManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Final Leaderboard & Victory Screen
 * Displays final game rankings, victory podium, champion banner, and all-time leaderboard from SQLite JDBC database.
 */
public class LeaderboardPanel extends JPanel {
    private final MainFrame mainFrame;

    private final JLabel crownLabel = new JLabel("\uD83D\uDC51 SHOWDOWN CHAMPION \uD83D\uDC51", SwingConstants.CENTER);
    private final JLabel winnerNameLabel = new JLabel("Winner Player", SwingConstants.CENTER);

    // Podium Pedestals
    private final JPanel podiumPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 0));
    private final PedestalWidget secondPedestal = new PedestalWidget("2nd Place", "---", "0 pts", UITheme.ACCENT_CYAN, 80);
    private final PedestalWidget firstPedestal = new PedestalWidget("1st Place", "---", "0 pts", UITheme.ACCENT_YELLOW, 115);
    private final PedestalWidget thirdPedestal = new PedestalWidget("3rd Place", "---", "0 pts", UITheme.ACCENT_PURPLE, 65);

    private final DefaultListModel<String> matchResultsModel = new DefaultListModel<>();
    private final JList<String> matchResultsList = new JList<>(matchResultsModel);

    private final DefaultListModel<String> dbLeaderboardModel = new DefaultListModel<>();
    private final JList<String> dbLeaderboardList = new JList<>(dbLeaderboardModel);

    public LeaderboardPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;

        setLayout(new BorderLayout(15, 12));
        setBackground(UITheme.BG_DARK);
        setBorder(new EmptyBorder(15, 30, 15, 30));

        // Top Winner Banner + Podium
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setOpaque(false);

        crownLabel.setFont(UITheme.FONT_HEADER);
        crownLabel.setForeground(UITheme.ACCENT_YELLOW);
        crownLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        winnerNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 30));
        winnerNameLabel.setForeground(UITheme.ACCENT_CYAN);
        winnerNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        podiumPanel.setOpaque(false);
        podiumPanel.add(secondPedestal);
        podiumPanel.add(firstPedestal);
        podiumPanel.add(thirdPedestal);
        podiumPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        topContainer.add(crownLabel);
        topContainer.add(Box.createVerticalStrut(4));
        topContainer.add(winnerNameLabel);
        topContainer.add(Box.createVerticalStrut(12));
        topContainer.add(podiumPanel);

        add(topContainer, BorderLayout.NORTH);

        // Center Split: Match Scores vs All-Time Database Leaderboard
        JPanel centerSplit = new JPanel(new GridLayout(1, 2, 20, 0));
        centerSplit.setOpaque(false);

        // Match Results Card
        JPanel matchCard = UITheme.createCardPanel();
        matchCard.setLayout(new BorderLayout(10, 10));

        JLabel matchHeader = new JLabel("Match Rankings", SwingConstants.CENTER);
        matchHeader.setFont(UITheme.FONT_HEADER);
        matchHeader.setForeground(UITheme.ACCENT_YELLOW);
        matchCard.add(matchHeader, BorderLayout.NORTH);

        matchResultsList.setFont(new Font("Consolas", Font.PLAIN, 14));
        matchResultsList.setForeground(UITheme.TEXT_WHITE);
        matchResultsList.setBackground(UITheme.BG_CARD_LIGHT);
        matchResultsList.setBorder(new EmptyBorder(8, 12, 8, 12));
        matchCard.add(new JScrollPane(matchResultsList), BorderLayout.CENTER);

        // DB Leaderboard Card
        JPanel dbCard = UITheme.createCardPanel();
        dbCard.setLayout(new BorderLayout(10, 10));

        JLabel dbHeader = new JLabel("SQLite All-Time Hall of Fame", SwingConstants.CENTER);
        dbHeader.setFont(UITheme.FONT_HEADER);
        dbHeader.setForeground(UITheme.ACCENT_CYAN);
        dbCard.add(dbHeader, BorderLayout.NORTH);

        dbLeaderboardList.setFont(new Font("Consolas", Font.PLAIN, 14));
        dbLeaderboardList.setForeground(UITheme.TEXT_WHITE);
        dbLeaderboardList.setBackground(UITheme.BG_CARD_LIGHT);
        dbLeaderboardList.setBorder(new EmptyBorder(8, 12, 8, 12));
        dbCard.add(new JScrollPane(dbLeaderboardList), BorderLayout.CENTER);

        centerSplit.add(matchCard);
        centerSplit.add(dbCard);
        add(centerSplit, BorderLayout.CENTER);

        // Bottom Controls
        JPanel bottomWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomWrap.setOpaque(false);

        JButton playAgainBtn = UITheme.createStyledButton("Return to Lobby [Play Again]", UITheme.ACCENT_CYAN, UITheme.BG_DARK);
        playAgainBtn.setFont(UITheme.FONT_HEADER);
        playAgainBtn.addActionListener(e -> mainFrame.showScreen("LOBBY"));

        bottomWrap.add(playAgainBtn);
        add(bottomWrap, BorderLayout.SOUTH);
    }

    public void setFinalLeaderboard(String leaderboardSummary) {
        matchResultsModel.clear();
        dbLeaderboardModel.clear();

        // format: "Name=Score,Name=Score,..."
        String[] pairs = leaderboardSummary.split(",");
        int rank = 1;

        for (String pair : pairs) {
            String[] p = pair.split("=");
            if (p.length == 2) {
                String name = p[0];
                int score = Integer.parseInt(p[1]);
                if (rank == 1) {
                    winnerNameLabel.setText(name + " (" + String.format("%,d pts", score) + ")");
                    firstPedestal.setData(name, String.format("%,d pts", score));
                } else if (rank == 2) {
                    secondPedestal.setData(name, String.format("%,d pts", score));
                } else if (rank == 3) {
                    thirdPedestal.setData(name, String.format("%,d pts", score));
                }

                String medal = rank == 1 ? "[1st]" : (rank == 2 ? "[2nd]" : (rank == 3 ? "[3rd]" : "     "));
                matchResultsModel.addElement(String.format(" %s #%d  %-14s  %,d pts", medal, rank, name, score));
                rank++;
            }
        }

        // Fetch Top scores from SQLite Database via JDBC
        List<DatabaseManager.ScoreEntry> topScores = DatabaseManager.getInstance().getTopScores(10);
        int dbRank = 1;
        for (DatabaseManager.ScoreEntry se : topScores) {
            dbLeaderboardModel.addElement(String.format(" #%d  %-14s  %,d pts  (%s)",
                    dbRank, se.getPlayerName(), se.getScore(), se.getPlayedAt()));
            dbRank++;
        }
    }

    /**
     * Visual 3D Podium Column Widget
     */
    public static class PedestalWidget extends JPanel {
        private final JLabel titleLabel;
        private final JLabel nameLabel;
        private final JLabel scoreLabel;
        private final Color accentColor;
        private final int barHeight;

        public PedestalWidget(String rankTitle, String name, String score, Color accentColor, int barHeight) {
            this.accentColor = accentColor;
            this.barHeight = barHeight;

            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setPreferredSize(new Dimension(140, barHeight + 70));

            titleLabel = new JLabel(rankTitle, SwingConstants.CENTER);
            titleLabel.setFont(UITheme.FONT_SMALL);
            titleLabel.setForeground(accentColor);
            titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            nameLabel = new JLabel(name, SwingConstants.CENTER);
            nameLabel.setFont(UITheme.FONT_BOLD);
            nameLabel.setForeground(UITheme.TEXT_WHITE);
            nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            scoreLabel = new JLabel(score, SwingConstants.CENTER);
            scoreLabel.setFont(UITheme.FONT_SMALL);
            scoreLabel.setForeground(UITheme.TEXT_MUTED);
            scoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            add(titleLabel);
            add(Box.createVerticalStrut(2));
            add(nameLabel);
            add(Box.createVerticalStrut(2));
            add(scoreLabel);
            add(Box.createVerticalStrut(6));
        }

        public void setData(String name, String score) {
            nameLabel.setText(name);
            scoreLabel.setText(score);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int barY = h - barHeight;

            // Pillar
            g2.setColor(UITheme.BG_CARD_LIGHT);
            g2.fill(new RoundRectangle2D.Float(10, barY, w - 20, barHeight, 14, 14));

            // Top accent cap
            g2.setColor(accentColor);
            g2.fill(new RoundRectangle2D.Float(10, barY, w - 20, 8, 8, 8));

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
