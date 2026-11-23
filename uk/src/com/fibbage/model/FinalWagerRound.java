package com.fibbage.model;

import java.util.List;

/**
 * Demonstrates OOP Pillar 3: Inheritance & Pillar 4: Polymorphism.
 * Round 3: All-In Risk Wager
 * Category is revealed, players secretly bet a portion of their points.
 * Correct answer = +wager (doubles the bet)
 * Wrong answer = -wager (loses the bet)
 */
public class FinalWagerRound extends GameRound {

    public FinalWagerRound(List<Question> questions) {
        super(3, "Round 3 — All-In Risk Wager", questions);
    }

    @Override
    public int calculatePoints(boolean isCorrect, int wagerAmount) {
        if (isCorrect) {
            return wagerAmount; // +wager
        } else {
            return -wagerAmount; // -wager
        }
    }
}

