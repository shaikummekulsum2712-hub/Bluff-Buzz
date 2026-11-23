package com.fibbage.model;

/**
 * GameConstants holds all configuration and scoring values in one central place.
 * Easily tune points, penalties, timers, and round parameters after playtesting.
 */
public final class GameConstants {
    private GameConstants() {} // Prevent instantiation

    // Base Player Rules
    public static final int STARTING_SCORE = 1000;
    public static final int MIN_SCORE = 0;
    public static final int TARGET_PLAYERS = 4; // 4 players arranged on 4 sides of the screen

    // Round 1 — Reflex Buzzer Sprint
    public static final int ROUND1_NUM_QUESTIONS = 3;
    public static final int ROUND1_CORRECT_POINTS = 500;
    public static final int ROUND1_WRONG_PENALTY = 200;
    public static final int ROUND1_TIMEOUT_PENALTY = 50; // Penalty per player if no one buzzes
    public static final int ROUND1_BUZZ_TIMEOUT_SEC = 20; // Increased by 5s (was 15)
    public static final int ROUND1_ANSWER_TIMEOUT_SEC = 15; // Increased by 5s (was 10)
    public static final int ROUND1_REOPEN_TIMEOUT_SEC = 13; // Time left for others after a wrong buzz

    // Round 2 — Bluff & AI Lie-Detector
    public static final int ROUND2_NUM_QUESTIONS = 2;
    public static final int ROUND2_REAL_ANSWER_POINTS = 500;
    public static final int ROUND2_WRONG_CHOICE_PENALTY = 200;
    public static final int ROUND2_FOOLED_OPPONENT_POINTS = 250; // Earned per opponent who picks your fake
    public static final int ROUND2_AI_FOOLED_BONUS = 250;       // Bonus if your lie has low AI risk
    public static final int ROUND2_INPUT_TIMEOUT_SEC = 35; // Increased by 5s (was 30)
    public static final int ROUND2_VOTE_TIMEOUT_SEC = 25; // Increased by 5s (was 20)

    // Round 3 — All-In Risk Wager
    public static final int ROUND3_MIN_WAGER = 100;
    public static final int ROUND3_NUM_QUESTIONS = 1;
    public static final int ROUND3_WAGER_TIMEOUT_SEC = 20; // Increased by 5s (was 15)
    public static final int ROUND3_ANSWER_TIMEOUT_SEC = 25; // Increased by 5s (was 20)

    // Networking
    public static final int DEFAULT_PORT = 5000;
    public static final int DEFAULT_WEB_PORT = 8080;
}

