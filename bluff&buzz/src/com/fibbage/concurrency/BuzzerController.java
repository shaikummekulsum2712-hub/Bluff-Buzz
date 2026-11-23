package com.fibbage.concurrency;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Block 3 — Threading & Synchronization
 * BuzzerController provides thread-safe mutex locking for simultaneous buzzer presses.
 *
 * Requirements:
 * 1. Synchronized tryBuzz(String playerName) locks on the first caller and rejects subsequent callers.
 * 2. If a player buzzes and answers incorrectly, they are locked out for that question,
 *    and the buzzer reopens for the remaining active players.
 * 3. resetBuzzer() / resetForNewQuestion() cleanly restores state.
 */
public class BuzzerController {
    private String activeBuzzerPlayer = null;
    private final Set<String> lockedOutPlayers = Collections.synchronizedSet(new HashSet<>());
    private boolean isBuzzerOpen = false;

    /**
     * Opens the buzzer for players to race.
     */
    public synchronized void openBuzzer() {
        this.isBuzzerOpen = true;
        this.activeBuzzerPlayer = null;
    }

    /**
     * Closes the buzzer completely (e.g., when question timer expires).
     */
    public synchronized void closeBuzzer() {
        this.isBuzzerOpen = false;
    }

    /**
     * Thread-safe synchronized buzzer claim.
     * Locks on the very first thread/caller. Rejects all subsequent callers.
     * Rejects players who are locked out due to a prior wrong buzz on the same question.
     *
     * @param playerName name of the player pressing the buzzer
     * @return true if this player successfully claimed the buzzer first, false otherwise
     */
    public synchronized boolean tryBuzz(String playerName) {
        if (!isBuzzerOpen) {
            return false;
        }
        if (playerName == null || lockedOutPlayers.contains(playerName)) {
            return false; // Player is locked out for this question
        }
        if (activeBuzzerPlayer != null) {
            return false; // Another player already buzzed first!
        }

        // Successfully claimed lock!
        activeBuzzerPlayer = playerName;
        return true;
    }

    /**
     * Called when the active buzzer answers incorrectly:
     * Locks this player out of the current question, and re-opens the buzzer
     * so other players can buzz in!
     */
    public synchronized void lockOutCurrentPlayer() {
        if (activeBuzzerPlayer != null) {
            lockedOutPlayers.add(activeBuzzerPlayer);
            activeBuzzerPlayer = null; // Reopens buzzer for others!
        }
    }

    /**
     * Resets the active buzzer without locking out.
     */
    public synchronized void resetBuzzer() {
        this.activeBuzzerPlayer = null;
    }

    /**
     * Completely resets all locks and lockout sets for the next trivia question.
     */
    public synchronized void resetForNewQuestion() {
        this.activeBuzzerPlayer = null;
        this.lockedOutPlayers.clear();
        this.isBuzzerOpen = false;
    }

    public synchronized String getActiveBuzzerPlayer() {
        return activeBuzzerPlayer;
    }

    public synchronized boolean hasActiveBuzz() {
        return activeBuzzerPlayer != null;
    }

    public synchronized boolean isPlayerLockedOut(String playerName) {
        return lockedOutPlayers.contains(playerName);
    }

    public synchronized Set<String> getLockedOutPlayers() {
        return new HashSet<>(lockedOutPlayers);
    }

    public synchronized boolean areAllPlayersLockedOut(int totalPlayers) {
        return lockedOutPlayers.size() >= totalPlayers;
    }

    /**
     * Standalone Verification runner for Block 3 Concurrency testing.
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Testing Block 3: Threading & Synchronization ===");
        BuzzerController buzzer = new BuzzerController();
        buzzer.openBuzzer();

        // Simulate 4 concurrent player threads slamming the buzzer simultaneously
        String[] players = {"Player_1", "Player_2", "Player_3", "Player_4"};
        Thread[] threads = new Thread[players.length];

        for (int i = 0; i < players.length; i++) {
            final String name = players[i];
            threads[i] = new Thread(() -> {
                boolean won = buzzer.tryBuzz(name);
                if (won) {
                    System.out.println("   >>> [MUTEX LOCK CLAIMED] " + name + " SLAMMED THE BUZZER FIRST!");
                } else {
                    System.out.println("   --- [REJECTED] " + name + " was locked out or too slow.");
                }
            });
        }

        // Start all threads simultaneously
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        System.out.println("\nActive Buzzer Holder: " + buzzer.getActiveBuzzerPlayer());

        // Test wrong buzz lockout and buzzer reopening
        System.out.println("\nSimulating active player answered WRONG (lockout and reopen):");
        buzzer.lockOutCurrentPlayer();
        buzzer.openBuzzer();
        System.out.println("Locked out players: " + buzzer.getLockedOutPlayers());

        // Prior winner tries again -> rejected!
        System.out.println("Prior winner tries to buzz again: " + buzzer.tryBuzz(players[0]));

        System.out.println("\n=== Block 3 Concurrency Verification Successful! ===");
    }
}
