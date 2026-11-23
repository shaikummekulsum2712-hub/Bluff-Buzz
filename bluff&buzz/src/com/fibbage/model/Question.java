package com.fibbage.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Question Model
 * Encapsulates trivia question data across all three rounds.
 */
public class Question {
    private final int id;
    private final String roundType; // "RAPID_FIRE", "BLUFF", "WAGER"
    private final String category;
    private final String prompt;
    private final String correctAnswer;
    private final List<String> wrongOptions;

    public Question(int id, String roundType, String category, String prompt, String correctAnswer, List<String> wrongOptions) {
        this.id = id;
        this.roundType = roundType;
        this.category = category;
        this.prompt = prompt;
        this.correctAnswer = correctAnswer;
        this.wrongOptions = wrongOptions != null ? new ArrayList<>(wrongOptions) : new ArrayList<>();
    }

    public int getId() { return id; }
    public String getRoundType() { return roundType; }
    public String getCategory() { return category; }
    public String getPrompt() { return prompt; }
    public String getCorrectAnswer() { return correctAnswer; }
    public List<String> getWrongOptions() { return Collections.unmodifiableList(wrongOptions); }

    /**
     * Returns shuffled choices containing the correct answer and wrong options.
     */
    public List<String> getAllOptionsShuffled() {
        List<String> all = new ArrayList<>(wrongOptions);
        all.add(correctAnswer);
        Collections.shuffle(all);
        return all;
    }
}

