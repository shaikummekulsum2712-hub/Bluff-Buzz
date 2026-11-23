package com.fibbage.network;

/**
 * Block 4 — Network Protocol
 * Defines standard command strings and delimiters for multi-client communication.
 */
public final class NetworkProtocol {
    private NetworkProtocol() {}

    public static final String DELIMITER = "::";

    // Client -> Server Commands
    public static final String CMD_CREATE_SESSION = "CREATE_SESSION";
    public static final String CMD_JOIN_SESSION = "JOIN_SESSION";
    public static final String CMD_START_GAME = "START_GAME";
    public static final String CMD_BUZZ = "BUZZ";
    public static final String CMD_ANSWER_BUZZ = "ANSWER_BUZZ";
    public static final String CMD_SUBMIT_BLUFF = "SUBMIT_BLUFF";
    public static final String CMD_SUBMIT_VOTE = "SUBMIT_VOTE";
    public static final String CMD_SUBMIT_WAGER = "SUBMIT_WAGER";
    public static final String CMD_SUBMIT_WAGER_ANSWER = "SUBMIT_WAGER_ANSWER";

    // Server -> Client Events
    public static final String EVT_SESSION_CREATED = "SESSION_CREATED";
    public static final String EVT_LOBBY_UPDATE = "LOBBY_UPDATE";
    public static final String EVT_GAME_STARTED = "GAME_STARTED";
    public static final String EVT_ROUND_STARTED = "ROUND_STARTED";
    public static final String EVT_TIMER_TICK = "TIMER_TICK";
    public static final String EVT_ROUND1_QUESTION = "ROUND1_QUESTION";
    public static final String EVT_BUZZ_LOCKED = "BUZZ_LOCKED";
    public static final String EVT_SCORE_UPDATE = "SCORE_UPDATE";
    public static final String EVT_ROUND2_QUESTION = "ROUND2_QUESTION";
    public static final String EVT_BLUFF_ACK = "BLUFF_ACK";
    public static final String EVT_PLAYER_SUBMITTED = "PLAYER_SUBMITTED";
    public static final String EVT_ROUND2_VOTE_OPTIONS = "ROUND2_VOTE_OPTIONS";
    public static final String EVT_ROUND2_REVEAL = "ROUND2_REVEAL";
    public static final String EVT_ROUND3_CATEGORY = "ROUND3_CATEGORY";
    public static final String EVT_ROUND3_QUESTION = "ROUND3_QUESTION";
    public static final String EVT_ROUND3_REVEAL = "ROUND3_REVEAL";
    public static final String EVT_GAME_OVER = "GAME_OVER";
    public static final String EVT_ERROR = "ERROR";
    public static final String EVT_SCORES = "SCORES";                 // SCORES::name=score,name=score
    public static final String EVT_BUZZ_REOPENED = "BUZZ_REOPENED";   // BUZZ_REOPENED::lockedOutName,lockedOutName
}

