package com.fibbage.ui;

import com.fibbage.network.GameClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Round 3: All-In Risk Wager
 * Category reveal, secret risk wagering slider/inputs, high-stakes question, and dramatic reveal.
 */
public class Round3WagerPanel extends JPanel {
    private final MainFrame mainFrame;
    private final GameClient client;

    // Header
    private final JLabel headerTitle = new JLabel("ROUND 3: ALL-IN RISK WAGER", SwingConstants.CENTER);
    private final JLabel categoryBanner = new JLabel("CATEGORY: FINAL SHOWDOWN", SwingConstants.CENTER);
    private final JLabel timerLabel = new JLabel("TIME: 20s", SwingConstants.RIGHT);
    private final JProgressBar timerBar = new JProgressBar(0, 20);

    // 4-Player Square
    private final PlayerSquareWidget squareWidget = new PlayerSquareWidget();

    // Card Layout
    private final CardLayout phaseCardLayout = new CardLayout();
    private final JPanel phaseContainer = new JPanel(phaseCardLayout);

    // Phase 1 (Wager Selection)
    private final JLabel wagerCategoryPrompt = new JLabel("CATEGORY: ???", SwingConstants.CENTER);
    private final JSlider wagerSlider = new JSlider(100, 2000, 500);
    private final JLabel wagerAmountLabel = new JLabel("500 pts", SwingConstants.CENTER);
    private final JButton lockWagerBtn = UITheme.createStyledButton("Lock In Secret Wager", UITheme.ACCENT_YELLOW, UITheme.BG_DARK);
    private final JLabel wagerStatusLabel = new JLabel("Wagers remain strictly hidden until the final reveal!", SwingConstants.CENTER);

    // Phase 2 (Question & Answering)
    private final JLabel questionLabel = new JLabel("Final Question Prompt", SwingConstants.CENTER);
    private final JPanel optionsGrid = new JPanel(new GridLayout(2, 2, 16, 12));
    private final List<JButton> optionButtons = new ArrayList<>();
    private final JLabel answerStatusLabel = new JLabel("Submit your secret answer!", SwingConstants.CENTER);

    // Phase 3 (Dramatic Reveal)
    private final JLabel revealAnswerLabel = new JLabel("Correct Answer", SwingConstants.CENTER);
    private final JTextArea resultsTextArea = new JTextArea(5, 30);

    public Round3WagerPanel(MainFrame mainFrame, GameClient client) {
        this.mainFrame = mainFrame;
        this.client = client;

        setLayout(new BorderLayout());
        setBackground(UITheme.BG_DARK);
        setBorder(new EmptyBorder(10, 20, 10, 20));

        add(buildHeaderPanel(), BorderLayout.NORTH);

        phaseContainer.setOpaque(false);
        phaseContainer.add(buildWagerPhasePanel(), "WAGER");
        phaseContainer.add(buildQuestionPhasePanel(), "QUESTION");
        phaseContainer.add(buildRevealPhasePanel(), "REVEAL");

        squareWidget.add(phaseContainer, BorderLayout.CENTER);
        add(squareWidget, BorderLayout.CENTER);
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 10, 0));

        headerTitle.setFont(UITheme.FONT_TITLE);
        headerTitle.setForeground(UITheme.ACCENT_RED);

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
        timerBar.setForeground(UITheme.ACCENT_RED);
        timerBar.setBackground(UITheme.BG_CARD);
        timerBar.setBorderPainted(false);
        timerBar.setPreferredSize(new Dimension(160, 12));

        timerPanel.add(timerLabel, BorderLayout.NORTH);
        timerPanel.add(timerBar, BorderLayout.SOUTH);

        header.add(topTitles, BorderLayout.CENTER);
        header.add(timerPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildWagerPhasePanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(15, 30, 15, 30));

        JPanel wagerCard = UITheme.createCardPanel();
        wagerCard.setLayout(new BoxLayout(wagerCard, BoxLayout.Y_AXIS));

        wagerCategoryPrompt.setFont(new Font("Segoe UI", Font.BOLD, 22));
        wagerCategoryPrompt.setForeground(UITheme.ACCENT_YELLOW);
        wagerCategoryPrompt.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel info = new JLabel("Secretly wager any amount of your points. Double it or lose it all!");
        info.setFont(UITheme.FONT_BODY);
        info.setForeground(UITheme.TEXT_MUTED);
        info.setAlignmentX(Component.CENTER_ALIGNMENT);

        wagerAmountLabel.setFont(new Font("Segoe UI", Font.BOLD, 36));
        wagerAmountLabel.setForeground(UITheme.ACCENT_CYAN);
        wagerAmountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        wagerSlider.setOpaque(false);
        wagerSlider.setMaximumSize(new Dimension(420, 40));
        wagerSlider.addChangeListener(e -> {
            wagerAmountLabel.setText(String.format("%,d pts", wagerSlider.getValue()));
        });

        // Quick buttons
        JPanel quickWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 5));
        quickWrap.setOpaque(false);

        JButton minBtn = UITheme.createStyledButton("Min (100)", UITheme.BG_CARD_LIGHT, UITheme.TEXT_WHITE);
        minBtn.addActionListener(e -> wagerSlider.setValue(wagerSlider.getMinimum()));

        JButton halfBtn = UITheme.createStyledButton("Half (50%)", UITheme.BG_CARD_LIGHT, UITheme.TEXT_WHITE);
        halfBtn.addActionListener(e -> wagerSlider.setValue(wagerSlider.getMaximum() / 2));

        JButton allInBtn = UITheme.createStyledButton("ALL-IN!", UITheme.ACCENT_RED, UITheme.TEXT_WHITE);
        allInBtn.addActionListener(e -> wagerSlider.setValue(wagerSlider.getMaximum()));

        quickWrap.add(minBtn);
        quickWrap.add(halfBtn);
        quickWrap.add(allInBtn);
        quickWrap.setAlignmentX(Component.CENTER_ALIGNMENT);

        lockWagerBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        lockWagerBtn.addActionListener(e -> onLockWagerClicked());

        wagerStatusLabel.setFont(UITheme.FONT_SMALL);
        wagerStatusLabel.setForeground(UITheme.TEXT_MUTED);
        wagerStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        wagerCard.add(wagerCategoryPrompt);
        wagerCard.add(Box.createVerticalStrut(6));
        wagerCard.add(info);
        wagerCard.add(Box.createVerticalStrut(15));
        wagerCard.add(wagerAmountLabel);
        wagerCard.add(Box.createVerticalStrut(10));
        wagerCard.add(wagerSlider);
        wagerCard.add(Box.createVerticalStrut(8));
        wagerCard.add(quickWrap);
        wagerCard.add(Box.createVerticalStrut(18));
        wagerCard.add(lockWagerBtn);
        wagerCard.add(Box.createVerticalStrut(6));
        wagerCard.add(wagerStatusLabel);

        panel.add(wagerCard, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildQuestionPhasePanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(10, 25, 10, 25));

        JPanel qCard = UITheme.createCardPanel();
        qCard.setLayout(new GridLayout(2, 1, 4, 4));

        questionLabel.setFont(new Font("Segoe UI", Font.BOLD, 19));
        questionLabel.setForeground(UITheme.TEXT_WHITE);

        answerStatusLabel.setFont(UITheme.FONT_BOLD);
        answerStatusLabel.setForeground(UITheme.ACCENT_YELLOW);

        qCard.add(questionLabel);
        qCard.add(answerStatusLabel);
        panel.add(qCard, BorderLayout.NORTH);

        optionsGrid.setOpaque(false);
        optionsGrid.setPreferredSize(new Dimension(520, 120));

        for (int i = 0; i < 4; i++) {
            JButton btn = UITheme.createStyledButton("Option " + (i + 1), UITheme.BG_CARD_LIGHT, UITheme.TEXT_WHITE);
            btn.addActionListener(e -> onOptionAnswerClicked(btn.getText()));
            optionButtons.add(btn);
            optionsGrid.add(btn);
        }

        panel.add(optionsGrid, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRevealPhasePanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(15, 30, 15, 30));

        JLabel title = new JLabel("THE GRAND FINAL REVEAL!", SwingConstants.CENTER);
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(UITheme.ACCENT_RED);

        revealAnswerLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        revealAnswerLabel.setForeground(UITheme.ACCENT_GREEN);

        JPanel top = new JPanel(new GridLayout(2, 1, 6, 6));
        top.setOpaque(false);
        top.add(title);
        top.add(revealAnswerLabel);
        panel.add(top, BorderLayout.NORTH);

        resultsTextArea.setFont(UITheme.FONT_BODY);
        resultsTextArea.setForeground(UITheme.TEXT_WHITE);
        resultsTextArea.setBackground(UITheme.BG_CARD);
        resultsTextArea.setEditable(false);
        resultsTextArea.setBorder(new EmptyBorder(12, 16, 12, 16));

        panel.add(new JScrollPane(resultsTextArea), BorderLayout.CENTER);
        return panel;
    }

    private void onLockWagerClicked() {
        int amount = wagerSlider.getValue();
        client.sendSubmitWager(amount);

        lockWagerBtn.setEnabled(false);
        lockWagerBtn.setText("Wager Locked In! [OK]");
        lockWagerBtn.setBackground(UITheme.ACCENT_GREEN);
        wagerStatusLabel.setText(String.format("Locked in %,d pts! Waiting for other players...", amount));
        wagerStatusLabel.setForeground(UITheme.ACCENT_GREEN);
    }

    private void onOptionAnswerClicked(String chosenOption) {
        for (JButton b : optionButtons) {
            b.setEnabled(false);
        }
        client.sendSubmitWagerAnswer(chosenOption);
        answerStatusLabel.setText("Secret answer submitted! Waiting for final reveal...");
        answerStatusLabel.setForeground(UITheme.ACCENT_GREEN);
    }

    public void setCategory(String category, int minWager) {
        categoryBanner.setText("CATEGORY: " + category.toUpperCase());
        wagerCategoryPrompt.setText("FINAL CATEGORY: " + category.toUpperCase());

        int currentScore = client.getMyScore();
        if (currentScore <= 0) currentScore = 1000;

        int maxWager = Math.max(minWager, currentScore);
        wagerSlider.setMinimum(minWager);
        wagerSlider.setMaximum(maxWager);
        wagerSlider.setValue(Math.min(maxWager, Math.max(minWager, maxWager / 2)));
        wagerAmountLabel.setText(String.format("%,d pts", wagerSlider.getValue()));

        lockWagerBtn.setEnabled(true);
        lockWagerBtn.setText("Lock In Secret Wager");
        lockWagerBtn.setBackground(UITheme.ACCENT_YELLOW);
        wagerStatusLabel.setText("Wagers remain strictly hidden until the final reveal!");
        wagerStatusLabel.setForeground(UITheme.TEXT_MUTED);

        squareWidget.resetAllStatusLights();
        phaseCardLayout.show(phaseContainer, "WAGER");
    }

    public void setQuestion(String prompt, List<String> options) {
        questionLabel.setText("<html><center>" + prompt + "</center></html>");
        answerStatusLabel.setText("Submit your secret answer!");
        answerStatusLabel.setForeground(UITheme.ACCENT_YELLOW);

        for (int i = 0; i < optionButtons.size(); i++) {
            JButton btn = optionButtons.get(i);
            if (i < options.size()) {
                btn.setText(options.get(i));
                btn.setVisible(true);
            } else {
                btn.setVisible(false);
            }
            btn.setEnabled(true);
            btn.setBackground(UITheme.BG_CARD_LIGHT);
        }

        squareWidget.resetAllStatusLights();
        phaseCardLayout.show(phaseContainer, "QUESTION");
    }

    public void showReveal(String realAnswer, String resultsSummary) {
        revealAnswerLabel.setText("<html><center>THE REAL ANSWER: <font color='#00F5D4'>\"" + realAnswer + "\"</font></center></html>");

        StringBuilder sb = new StringBuilder();
        sb.append("FINAL HIGH-STAKES WAGER RESULTS:\n\n");

        String[] parts = resultsSummary.split(",");
        for (String p : parts) {
            String[] tokens = p.split(":");
            if (tokens.length == 3) {
                String name = tokens[0];
                int wager = Integer.parseInt(tokens[1]);
                int finalScore = Integer.parseInt(tokens[2]);

                sb.append(String.format("  • %-15s : Bet %,d pts  --->  Final: %,d pts\n", name, wager, finalScore));
                squareWidget.updateScore(name, finalScore, 0);
            }
        }

        resultsTextArea.setText(sb.toString());
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
