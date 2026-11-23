package com.fibbage.model;

/**
 * Demonstrates OOP Pillar 1: Abstraction & Pillar 2: Encapsulation.
 * Abstract Player class with private encapsulated fields and validated getters/setters.
 */
public abstract class Player {
    // Encapsulated private fields
    private final String name;
    private int score;
    private int wager;
    private boolean lockedOut;
    private String currentBluff;
    private String currentVote;
    private long typingDurationMs;
    private int backspaceCount;
    private int bluffRiskScore;

    public Player(String name) {
        if (name == null || name.trim().isEmpty()) {
            this.name = "Player_" + System.currentTimeMillis() % 1000;
        } else {
            this.name = name.trim();
        }
        this.score = GameConstants.STARTING_SCORE;
        this.wager = 0;
        this.lockedOut = false;
        this.currentBluff = "";
        this.currentVote = "";
        this.typingDurationMs = 0;
        this.backspaceCount = 0;
        this.bluffRiskScore = 50;
    }

    // Abstract method to be implemented by HumanPlayer and AIPlayer (Polymorphism & Abstraction)
    public abstract boolean isAI();

    // Getters and Validated Setters (Encapsulation)
    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    /**
     * Validates that score cannot fall below MIN_SCORE (0).
     */
    public void setScore(int score) {
        this.score = Math.max(GameConstants.MIN_SCORE, score);
    }

    /**
     * Safely updates score by delta while preventing negative values.
     */
    public void addScore(int delta) {
        setScore(this.score + delta);
    }

    public int getWager() {
        return wager;
    }

    /**
     * Validates wager: must be between MIN_WAGER (or player's score if less) and player's current score.
     */
    public void setWager(int wager) {
        int maxAllowed = Math.max(GameConstants.ROUND3_MIN_WAGER, this.score);
        if (wager < 0) {
            this.wager = 0;
        } else if (wager > maxAllowed) {
            this.wager = maxAllowed;
        } else {
            this.wager = Math.max(GameConstants.ROUND3_MIN_WAGER, wager);
        }
    }

    public boolean isLockedOut() {
        return lockedOut;
    }

    public void setLockedOut(boolean lockedOut) {
        this.lockedOut = lockedOut;
    }

    public String getCurrentBluff() {
        return currentBluff;
    }

    public void setCurrentBluff(String currentBluff) {
        this.currentBluff = currentBluff != null ? currentBluff.trim() : "";
    }

    public String getCurrentVote() {
        return currentVote;
    }

    public void setCurrentVote(String currentVote) {
        this.currentVote = currentVote != null ? currentVote.trim() : "";
    }

    public long getTypingDurationMs() {
        return typingDurationMs;
    }

    public void setTypingDurationMs(long typingDurationMs) {
        this.typingDurationMs = Math.max(0, typingDurationMs);
    }

    public int getBackspaceCount() {
        return backspaceCount;
    }

    public void setBackspaceCount(int backspaceCount) {
        this.backspaceCount = Math.max(0, backspaceCount);
    }

    public int getBluffRiskScore() {
        return bluffRiskScore;
    }

    public void setBluffRiskScore(int bluffRiskScore) {
        this.bluffRiskScore = Math.max(0, Math.min(100, bluffRiskScore));
    }

    public void resetRoundState() {
        this.lockedOut = false;
        this.currentBluff = "";
        this.currentVote = "";
        this.wager = 0;
        this.typingDurationMs = 0;
        this.backspaceCount = 0;
        this.bluffRiskScore = 50;
    }

    @Override
    public String toString() {
        return String.format("%s (Score: %d)", name, score);
    }
}

