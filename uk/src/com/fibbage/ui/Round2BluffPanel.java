package com.fibbage.ui;

import com.fibbage.ai.AILieDetector;
import com.fibbage.network.GameClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Round 2: Bluff & AI Lie-Detector
 * Tracks live keystrokes, hesitation time, and backspaces for AI ML lie analysis.
 * Features submission status indicators, voting grid with AI risk %, and reveal breakdown.
 */
public class Round2BluffPanel extends JPanel {
    private final MainFrame mainFrame;
    private final GameClient client;

    // Header
    private final JLabel headerTitle = new JLabel("ROUND 2: BLUFF & AI LIE-DETECTOR", SwingConstants.CENTER);
    private final JLabel categoryBanner = new JLabel("CATEGORY: WEIRD FACTS", SwingConstants.CENTER);
    private final JLabel timerLabel = new JLabel("TIME: 35s", SwingConstants.RIGHT);
    private final JProgressBar timerBar = new JProgressBar(0, 35);

    // 4-Player Square
    private final PlayerSquareWidget squareWidget = new PlayerSquareWidget();

    // Card Layout
    private final CardLayout phaseCardLayout = new CardLayout();
    private final JPanel phaseContainer = new JPanel(phaseCardLayout);

    // Phase 1 (Input) Components
    private final JLabel promptLabel = new JLabel("Loading historical fact...", SwingConstants.CENTER);
    private final JTextField bluffInputField = UITheme.createStyledTextField(20);
    private final JLabel hesitationValLabel = new JLabel("0.0s");
    private final JLabel backspaceValLabel = new JLabel("0");
    private final JLabel riskBadgeLabel = new JLabel("30% Low");
    private final JProgressBar riskMeterBar = new JProgressBar(0, 100);
    private final JButton submitBluffBtn = UITheme.createStyledButton("Lock In My Bluff [Submit]", UITheme.ACCENT_YELLOW, UITheme.BG_DARK);
    private final JLabel bluffWarningLabel = new JLabel("", SwingConstants.CENTER);

    // Keystroke Tracking State
    private long typingStartTime = 0;
    private int backspaceCount = 0;
    private boolean typingStarted = false;

    // Phase 2 (Voting) Components
    private final JLabel votePromptLabel = new JLabel("", SwingConstants.CENTER);
    private final JPanel votingOptionsGrid = new JPanel(new GridLayout(0, 1, 10, 10));
    private final List<JButton> voteButtons = new ArrayList<>();

    // Phase 3 (Reveal) Components
    private final JLabel revealTitleLabel = new JLabel("THE TRUTH REVEALED!", SwingConstants.CENTER);
    private final JLabel revealRealAnswerLabel = new JLabel("", SwingConstants.CENTER);
    private final JTextArea revealSummaryArea = new JTextArea(4, 30);

    public Round2BluffPanel(MainFrame mainFrame, GameClient client) {
        this.mainFrame = mainFrame;
        this.client = client;

        setLayout(new BorderLayout());
        setBackground(UITheme.BG_DARK);
        setBorder(new EmptyBorder(10, 20, 10, 20));

        add(buildHeaderPanel(), BorderLayout.NORTH);

        phaseContainer.setOpaque(false);
        phaseContainer.add(buildInputPhasePanel(), "INPUT");
        phaseContainer.add(buildVotingPhasePanel(), "VOTING");
        phaseContainer.add(buildRevealPhasePanel(), "REVEAL");

        squareWidget.add(phaseContainer, BorderLayout.CENTER);
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

        timerBar.setValue(35);
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

    private JPanel buildInputPhasePanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 12));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(10, 30, 10, 30));

        // Prompt Card
        JPanel promptCard = UITheme.createCardPanel();
        promptCard.setLayout(new BorderLayout());
        promptLabel.setFont(new Font("Segoe UI", Font.BOLD, 19));
        promptLabel.setForeground(UITheme.TEXT_WHITE);
        promptCard.add(promptLabel, BorderLayout.CENTER);
        panel.add(promptCard, BorderLayout.NORTH);

        // Center form
        JPanel centerForm = new JPanel();
        centerForm.setLayout(new BoxLayout(centerForm, BoxLayout.Y_AXIS));
        centerForm.setOpaque(false);

        JLabel instruction = new JLabel("Invent a believable LIE to trick your friends into voting for it:");
        instruction.setFont(UITheme.FONT_BODY);
        instruction.setForeground(UITheme.TEXT_MUTED);
        instruction.setAlignmentX(Component.CENTER_ALIGNMENT);

        bluffInputField.setMaximumSize(new Dimension(440, 40));
        bluffInputField.setFont(new Font("Segoe UI", Font.BOLD, 17));
        bluffInputField.setHorizontalAlignment(JTextField.CENTER);

        // Keystroke listener for AI Lie Detector metrics
        bluffInputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!typingStarted) {
                    typingStartTime = System.currentTimeMillis();
                    typingStarted = true;
                }
                if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    backspaceCount++;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                updateLiveRiskDisplay();
            }
        });

        // AI Lie Detector HUD Card
        JPanel hudCard = UITheme.createCardPanel();
        hudCard.setLayout(new BoxLayout(hudCard, BoxLayout.Y_AXIS));
        hudCard.setMaximumSize(new Dimension(460, 95));

        JLabel hudTitle = new JLabel("BRAIN AI LIE-DETECTOR BIOMETRIC ANALYZER");
        hudTitle.setFont(UITheme.FONT_SMALL);
        hudTitle.setForeground(UITheme.ACCENT_CYAN);
        hudTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel metricsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 2));
        metricsRow.setOpaque(false);

        hesitationValLabel.setFont(UITheme.FONT_BOLD);
        hesitationValLabel.setForeground(UITheme.TEXT_WHITE);

        backspaceValLabel.setFont(UITheme.FONT_BOLD);
        backspaceValLabel.setForeground(UITheme.TEXT_WHITE);

        riskBadgeLabel.setFont(UITheme.FONT_BOLD);
        riskBadgeLabel.setForeground(UITheme.ACCENT_GREEN);

        metricsRow.add(new JLabel("Hesitation: ") {{ setForeground(UITheme.TEXT_MUTED); }});
        metricsRow.add(hesitationValLabel);
        metricsRow.add(new JLabel("  |  Backspaces: ") {{ setForeground(UITheme.TEXT_MUTED); }});
        metricsRow.add(backspaceValLabel);
        metricsRow.add(new JLabel("  |  Risk: ") {{ setForeground(UITheme.TEXT_MUTED); }});
        metricsRow.add(riskBadgeLabel);

        riskMeterBar.setMaximumSize(new Dimension(380, 8));
        riskMeterBar.setValue(30);
        riskMeterBar.setForeground(UITheme.ACCENT_GREEN);
        riskMeterBar.setBackground(UITheme.BG_CARD_LIGHT);
        riskMeterBar.setBorderPainted(false);
        riskMeterBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        hudCard.add(hudTitle);
        hudCard.add(Box.createVerticalStrut(4));
        hudCard.add(metricsRow);
        hudCard.add(Box.createVerticalStrut(4));
        hudCard.add(riskMeterBar);

        bluffWarningLabel.setFont(UITheme.FONT_BOLD);
        bluffWarningLabel.setForeground(UITheme.ACCENT_RED);
        bluffWarningLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        submitBluffBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        submitBluffBtn.addActionListener(e -> onSubmitBluff());

        centerForm.add(Box.createVerticalStrut(5));
        centerForm.add(instruction);
        centerForm.add(Box.createVerticalStrut(8));
        centerForm.add(bluffInputField);
        centerForm.add(Box.createVerticalStrut(8));
        centerForm.add(hudCard);
        centerForm.add(Box.createVerticalStrut(6));
        centerForm.add(bluffWarningLabel);
        centerForm.add(Box.createVerticalStrut(10));
        centerForm.add(submitBluffBtn);

        panel.add(centerForm, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildVotingPhasePanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 10));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(10, 30, 10, 30));

        votePromptLabel.setFont(UITheme.FONT_HEADER);
        votePromptLabel.setForeground(UITheme.TEXT_WHITE);

        JLabel voteSub = new JLabel("FIND THE REAL TRUTH! Check the AI Bluff Risk % carefully!", SwingConstants.CENTER);
        voteSub.setFont(UITheme.FONT_BODY);
        voteSub.setForeground(UITheme.ACCENT_YELLOW);

        JPanel topWrap = new JPanel(new GridLayout(2, 1, 4, 4));
        topWrap.setOpaque(false);
        topWrap.add(votePromptLabel);
        topWrap.add(voteSub);
        panel.add(topWrap, BorderLayout.NORTH);

        votingOptionsGrid.setOpaque(false);
        JScrollPane scroll = new JScrollPane(votingOptionsGrid);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildRevealPhasePanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(15, 30, 15, 30));

        revealTitleLabel.setFont(UITheme.FONT_TITLE);
        revealTitleLabel.setForeground(UITheme.ACCENT_CYAN);

        revealRealAnswerLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        revealRealAnswerLabel.setForeground(UITheme.ACCENT_GREEN);

        JPanel northWrap = new JPanel(new GridLayout(2, 1, 8, 8));
        northWrap.setOpaque(false);
        northWrap.add(revealTitleLabel);
        northWrap.add(revealRealAnswerLabel);
        panel.add(northWrap, BorderLayout.NORTH);

        revealSummaryArea.setFont(UITheme.FONT_BODY);
        revealSummaryArea.setForeground(UITheme.TEXT_WHITE);
        revealSummaryArea.setBackground(UITheme.BG_CARD);
        revealSummaryArea.setEditable(false);
        revealSummaryArea.setLineWrap(true);
        revealSummaryArea.setWrapStyleWord(true);
        revealSummaryArea.setBorder(new EmptyBorder(10, 15, 10, 15));

        panel.add(new JScrollPane(revealSummaryArea), BorderLayout.CENTER);
        return panel;
    }

    private void updateLiveRiskDisplay() {
        String text = bluffInputField.getText();
        long duration = typingStarted ? (System.currentTimeMillis() - typingStartTime) : 0;
        int risk = AILieDetector.calculateBluffRisk(text, duration, backspaceCount, false);
        String badge = AILieDetector.getRiskBadge(risk);

        hesitationValLabel.setText(String.format("%.1fs", duration / 1000.0));
        backspaceValLabel.setText(String.valueOf(backspaceCount));
        riskBadgeLabel.setText(String.format("%d%% %s", risk, badge));

        riskMeterBar.setValue(risk);
        if (risk <= 35) {
            riskBadgeLabel.setForeground(UITheme.ACCENT_GREEN);
            riskMeterBar.setForeground(UITheme.ACCENT_GREEN);
        } else if (risk <= 65) {
            riskBadgeLabel.setForeground(UITheme.ACCENT_YELLOW);
            riskMeterBar.setForeground(UITheme.ACCENT_YELLOW);
        } else {
            riskBadgeLabel.setForeground(UITheme.ACCENT_RED);
            riskMeterBar.setForeground(UITheme.ACCENT_RED);
        }
    }

    private void onSubmitBluff() {
        String bluff = bluffInputField.getText().trim();
        if (bluff.isEmpty()) {
            bluffWarningLabel.setText("Please enter a believable fake answer!");
            return;
        }

        long duration = typingStarted ? (System.currentTimeMillis() - typingStartTime) : 2000;
        client.sendSubmitBluff(bluff, duration, backspaceCount);

        bluffWarningLabel.setText("Sending to AI Lie Detector & Server...");
        bluffWarningLabel.setForeground(UITheme.ACCENT_CYAN);
        submitBluffBtn.setEnabled(false);
    }

    public void onBluffAck() {
        bluffInputField.setEnabled(false);
        submitBluffBtn.setText("Answer Locked In! [OK]");
        submitBluffBtn.setBackground(UITheme.ACCENT_GREEN);
        bluffWarningLabel.setText("Your bluff is saved! Waiting for others...");
        bluffWarningLabel.setForeground(UITheme.ACCENT_GREEN);
    }

    public void onBluffRejected(String reason) {
        bluffInputField.setEnabled(true);
        submitBluffBtn.setEnabled(true);
        bluffWarningLabel.setText("Alert: " + reason);
        bluffWarningLabel.setForeground(UITheme.ACCENT_RED);
        bluffInputField.requestFocus();
    }

    public void setQuestion(int qIdx, int totalQ, String category, String prompt, int timeoutSec) {
        headerTitle.setText(String.format("ROUND 2: BLUFF & AI LIE-DETECTOR (Question %d of %d)", qIdx, totalQ));
        categoryBanner.setText("CATEGORY: " + category.toUpperCase());
        promptLabel.setText("<html><center>" + prompt + "</center></html>");
        votePromptLabel.setText("<html><center>" + prompt + "</center></html>");

        bluffInputField.setText("");
        bluffInputField.setEnabled(true);
        submitBluffBtn.setText("Lock In My Bluff [Submit]");
        submitBluffBtn.setBackground(UITheme.ACCENT_YELLOW);
        submitBluffBtn.setEnabled(true);
        bluffWarningLabel.setText("");

        typingStarted = false;
        typingStartTime = 0;
        backspaceCount = 0;
        updateLiveRiskDisplay();

        squareWidget.resetAllStatusLights();
        phaseCardLayout.show(phaseContainer, "INPUT");
    }

    public void setVoteOptions(List<String> optionsWithRisk) {
        votingOptionsGrid.removeAll();
        voteButtons.clear();

        // Every option arrives as "text~risk~mine" - all players' bluffs plus the real answer
        for (String raw : optionsWithRisk) {
            String[] parts = raw.split("~");
            String text = parts[0];
            int risk = 50;
            boolean mine = false;
            try { if (parts.length > 1) risk = Integer.parseInt(parts[1].trim()); } catch (NumberFormatException ignored) {}
            if (parts.length > 2) mine = "1".equals(parts[2].trim());

            String colorHex = risk >= 70 ? "#FF3366" : (risk <= 35 ? "#06D6A0" : "#FFD166");
            String buttonText = String.format("<html><b>%s</b> &nbsp;&nbsp; <font color='%s'>[%d%% %s]</font>%s</html>",
                    text, colorHex, risk, AILieDetector.getRiskBadge(risk),
                    mine ? " &nbsp;&nbsp;<i>(your own bluff - can't vote for it)</i>" : "");

            JButton btn = UITheme.createStyledButton(buttonText, UITheme.BG_CARD_LIGHT, UITheme.TEXT_WHITE);
            btn.setFont(UITheme.FONT_BODY);
            btn.setHorizontalAlignment(SwingConstants.LEFT);
            if (mine) {
                btn.setEnabled(false);
            } else {
                btn.addActionListener(e -> onVoteClicked(text));
                voteButtons.add(btn);
            }
            votingOptionsGrid.add(btn);
        }

        votingOptionsGrid.revalidate();
        votingOptionsGrid.repaint();
        phaseCardLayout.show(phaseContainer, "VOTING");
    }

    private void onVoteClicked(String chosenOption) {
        for (JButton b : voteButtons) {
            b.setEnabled(false);
        }
        client.sendSubmitVote(chosenOption);
    }

    public void showReveal(String realAnswer, String scoreSummaries) {
        revealRealAnswerLabel.setText("<html><center>THE TRUTH WAS: <font color='#00F5D4'>\"" + realAnswer + "\"</font></center></html>");

        StringBuilder sb = new StringBuilder();
        sb.append("SCORES AFTER THIS BLUFF ROUND:\n\n");
        String[] entries = scoreSummaries.split(",");
        for (String e : entries) {
            String[] p = e.split("=");
            if (p.length == 2) {
                sb.append(String.format("  • %-15s : %,d pts\n", p[0], Integer.parseInt(p[1])));
                squareWidget.updateScore(p[0], Integer.parseInt(p[1]), 0);
            }
        }
        revealSummaryArea.setText(sb.toString());
        phaseCardLayout.show(phaseContainer, "REVEAL");
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
