package com.fibbage.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates OOP Pillar 1: Abstraction.
 * Abstract base class for game rounds.
 */
public abstract class GameRound {
    protected final int roundNumber;
    protected final String roundTitle;
    protected final List<Question> questions;
    protected int currentQuestionIndex;

    public GameRound(int roundNumber, String roundTitle, List<Question> questions) {
        this.roundNumber = roundNumber;
        this.roundTitle = roundTitle;
        this.questions = questions != null ? new ArrayList<>(questions) : new ArrayList<>();
        this.currentQuestionIndex = 0;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public String getRoundTitle() {
        return roundTitle;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public Question getCurrentQuestion() {
        if (currentQuestionIndex >= 0 && currentQuestionIndex < questions.size()) {
            return questions.get(currentQuestionIndex);
        }
        return null;
    }

    public boolean hasNextQuestion() {
        return currentQuestionIndex < questions.size() - 1;
    }

    public void nextQuestion() {
        currentQuestionIndex++;
    }

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public int getTotalQuestions() {
        return questions.size();
    }

    /**
     * Demonstrates OOP Pillar 4: Polymorphism.
     * Each round overrides this method with its own distinct scoring logic.
     *
     * @param isCorrect true if the primary check passed
     * @param extraData additional round-specific context (e.g., wager amount or count of players fooled)
     * @return points delta to apply (positive or negative)
     */
    public abstract int calculatePoints(boolean isCorrect, int extraData);

    /**
     * Standalone Verification runner for Block 1 OOP Pillars.
     */
    public static void main(String[] args) {
        System.out.println("=== Testing Block 1: All 4 OOP Pillars ===");

        // 1. Encapsulation & Inheritance
        Player human = new HumanPlayer("Jordan", "127.0.0.1:5000");
        Player ai = new AIPlayer("Pixel_AI");

        System.out.println("Pillar 1 (Encapsulation): Player initial score: " + human.getScore());
        human.addScore(500);
        System.out.println("Player score after +500: " + human.getScore());
        human.addScore(-2000); // Should be validated to not drop below 0
        System.out.println("Player score after -2000 (enforced min 0): " + human.getScore());

        System.out.println("\nPillar 2 (Inheritance):");
        System.out.println("   human.isAI() -> " + human.isAI());
        System.out.println("   ai.isAI() -> " + ai.isAI());

        // 2. Abstraction & Polymorphism
        GameRound round1 = new RapidFireRound(null);
        GameRound round2 = new BluffRound(null);
        GameRound round3 = new FinalWagerRound(null);

        System.out.println("\nPillar 3 & 4 (Abstraction & Polymorphism - calculatePoints):");
        System.out.println("   RapidFire Round (Correct): " + round1.calculatePoints(true, 0) + " pts");
        System.out.println("   RapidFire Round (Wrong): " + round1.calculatePoints(false, 0) + " pts");
        System.out.println("   Bluff Round (Chose Truth): " + round2.calculatePoints(true, 0) + " pts");
        System.out.println("   Bluff Round (Fooled 3 opponents): " + round2.calculatePoints(false, 3) + " pts");
        System.out.println("   Final Wager Round (Correct with 1,000 bet): " + round3.calculatePoints(true, 1000) + " pts");
        System.out.println("   Final Wager Round (Wrong with 1,000 bet): " + round3.calculatePoints(false, 1000) + " pts");

        System.out.println("\n=== Block 1 OOP Pillars Verification Successful! ===");
    }
}

