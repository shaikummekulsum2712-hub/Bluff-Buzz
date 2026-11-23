package com.fibbage.model;

import java.util.List;

/**
 * Demonstrates OOP Pillar 3: Inheritance & Pillar 4: Polymorphism.
 * Round 2: Bluff & AI Lie-Detector
 * Players write fake answers to fool others.
 * Guessing the real answer = +500
 * Falling for a fake answer = -200
 * Fooled players: player whose lie was picked gets +250 per friend fooled
 */
public class BluffRound extends GameRound {

    public BluffRound(List<Question> questions) {
        super(2, "Round 2 — Bluff & AI Lie-Detector", questions);
    }

    @Override
    public int calculatePoints(boolean isCorrect, int extraData) {
        // Mode 1: Guessing scoring (extraData == 0)
        // Mode 2: Bluff author scoring (extraData > 0: count of people fooled)
        if (extraData > 0) {
            // Point reward for author whose fake bluffed friends (+250 per friend fooled)
            return extraData * GameConstants.ROUND2_FOOLED_OPPONENT_POINTS;
        }

        if (isCorrect) {
            return GameConstants.ROUND2_REAL_ANSWER_POINTS; // +500
        } else {
            return -GameConstants.ROUND2_WRONG_CHOICE_PENALTY; // -200
        }
    }
}

