package com.fibbage.ai;

/**
 * AI Lie-Detector Module
 * Simulates / runs an ML decision model predicting the probability that a submitted answer is a bluff.
 * Evaluates feature vectors based on:
 * - Hesitation time before submission (milliseconds)
 * - Number of backspaces (measure of hesitation and second-guessing)
 * - Text length and character density
 * - Answer complexity
 *
 * Output: Bluff Risk % (0 to 100%)
 */
public class AILieDetector {

    /**
     * Calculates Bluff Risk % based on keystroke biometric features.
     *
     * @param answer the submitted answer text
     * @param typingDurationMs milliseconds spent typing the answer
     * @param backspaceCount number of backspaces pressed while formulating the answer
     * @param isRealTriviaAnswer whether this is the genuine database answer (for baseline scoring)
     * @return Bluff Risk % between 5% and 95%
     */
    public static int calculateBluffRisk(String answer, long typingDurationMs, int backspaceCount, boolean isRealTriviaAnswer) {
        if (isRealTriviaAnswer) {
            // Authentic historical facts score very low baseline risk
            int base = 18 + (Math.abs(answer.hashCode()) % 15);
            return Math.min(35, Math.max(12, base));
        }

        if (answer == null || answer.trim().isEmpty()) {
            return 85;
        }

        int riskScore = 40; // Base neutral prior

        // Feature 1: Backspace hesitation penalty
        // High backspaces indicate fabrication and rethinking lies
        if (backspaceCount == 0) {
            riskScore -= 18; // Confident typing
        } else if (backspaceCount <= 2) {
            riskScore += 5;
        } else if (backspaceCount <= 5) {
            riskScore += 20;
        } else {
            riskScore += 35; // Heavy corrections
        }

        // Feature 2: Typing duration relative to answer length
        int length = answer.trim().length();
        double msPerChar = length > 0 ? (double) typingDurationMs / length : 1000.0;

        if (msPerChar > 800.0) {
            // Unusually long pauses while creating a fake fact
            riskScore += 18;
        } else if (msPerChar < 250.0 && length > 5) {
            // Pre-formulated or rapid typing
            riskScore -= 10;
        }

        // Feature 3: Answer complexity and vocabulary
        if (answer.toLowerCase().contains("goat") || answer.toLowerCase().contains("donkey") || answer.toLowerCase().contains("bear")) {
            // Common fibbage tropes slightly elevated
            riskScore += 8;
        }

        // Add slight deterministic pseudo-random variance based on text hash
        int hashVariance = (Math.abs(answer.hashCode()) % 11) - 5;
        riskScore += hashVariance;

        // Clamp to realistic 10% - 92% range
        return Math.min(92, Math.max(10, riskScore));
    }

    /**
     * Helper to return a risk label badge for UI display.
     */
    public static String getRiskBadge(int riskPercent) {
        if (riskPercent >= 70) {
            return String.format("[%d%% High Bluff Risk!]", riskPercent);
        } else if (riskPercent >= 45) {
            return String.format("[%d%% Medium Risk?]", riskPercent);
        } else {
            return String.format("[%d%% Believable / Low Risk]", riskPercent);
        }
    }
}

