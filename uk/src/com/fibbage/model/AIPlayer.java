package com.fibbage.model;

import java.util.Random;

/**
 * Demonstrates OOP Pillar 3: Inheritance & Polymorphism.
 * Represents an autonomous AI bot player that can generate bluff answers and participate.
 */
public class AIPlayer extends Player {
    private static final Random RNG = new Random();
    private static final String[] FAKE_BLUFFS = {
        "A Dancing Bear",
        "A Golden Pocket Watch",
        "A Wooden Broomstick",
        "A Trained Falcon",
        "A Barrel of Pickles",
        "An Italian Chef",
        "A Goat named Billy",
        "A Brass Trombone"
    };

    public AIPlayer(String name) {
        super(name);
    }

    @Override
    public boolean isAI() {
        return true;
    }

    /**
     * AI generates a believable fake bluff answer if human players are missing.
     */
    public String generateFakeAnswer() {
        return FAKE_BLUFFS[RNG.nextInt(FAKE_BLUFFS.length)];
    }
}

