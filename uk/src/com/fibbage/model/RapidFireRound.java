package com.fibbage.model;

import java.util.List;

/**
 * Demonstrates OOP Pillar 3: Inheritance & Pillar 4: Polymorphism.
 * Round 1: Reflex Buzzer Sprint
 * First person to buzz gets to answer.
 * Correct answer = +500
 * Wrong answer = -200 (and player locked out so others can buzz)
 * Timeout (nobody buzzes) = -50
 */
public class RapidFireRound extends GameRound {

    public RapidFireRound(List<Question> questions) {
        super(1, "Round 1 — Reflex Buzzer Sprint", questions);
    }

    @Override
    public int calculatePoints(boolean isCorrect, int extraData) {
        // extraData == -1 indicates timeout where nobody buzzed
        if (extraData == -1) {
            return -GameConstants.ROUND1_TIMEOUT_PENALTY; // -50
        }
        if (isCorrect) {
            return GameConstants.ROUND1_CORRECT_POINTS; // +500
        } else {
            return -GameConstants.ROUND1_WRONG_PENALTY; // -200
        }
    }
}

