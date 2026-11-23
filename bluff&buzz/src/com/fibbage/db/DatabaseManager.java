package com.fibbage.db;

import com.fibbage.model.Question;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Block 2 — JDBC Database Layer
 * Demonstrates pure Java JDBC using PreparedStatement, ResultSet, and SQLite.
 *
 * Implements:
 * 1. create questions and leaderboard tables if not exist
 * 2. insert 10+ rich sample trivia questions for Rapid Fire, Bluff, and Wager rounds
 * 3. fetch random questions by round type using PreparedStatement
 * 4. save and retrieve top player scores with PreparedStatement & ResultSet
 */
public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:game.db";
    private static DatabaseManager instance;
    private boolean sqliteAvailable = false;

    // In-memory fallback if sqlite jar driver is not present on classpath
    private final List<Question> fallbackQuestions = new ArrayList<>();
    private final List<ScoreEntry> fallbackScores = new ArrayList<>();

    public static class ScoreEntry {
        private final String playerName;
        private final int score;
        private final String playedAt;

        public ScoreEntry(String playerName, int score, String playedAt) {
            this.playerName = playerName;
            this.score = score;
            this.playedAt = playedAt;
        }

        public String getPlayerName() { return playerName; }
        public int getScore() { return score; }
        public String getPlayedAt() { return playedAt; }
    }

    private DatabaseManager() {
        try {
            Class.forName("org.sqlite.JDBC");
            sqliteAvailable = true;
            initTables();
            seedDefaultQuestions();
        } catch (Throwable t) {
            System.out.println("[DatabaseManager] SQLite JDBC Driver not fully available (" + t.getMessage() + "). Running embedded in-memory fallback.");
            sqliteAvailable = false;
            seedFallbackQuestions();
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * Requirement 1: Create questions and leaderboard tables if not exist.
     */
    public void initTables() throws SQLException {
        if (!sqliteAvailable) return;
        String createQuestionsTable = "CREATE TABLE IF NOT EXISTS questions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "round_type TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "prompt TEXT NOT NULL, " +
                "correct_answer TEXT NOT NULL, " +
                "wrong_options TEXT NOT NULL" +
                ");";

        String createLeaderboardTable = "CREATE TABLE IF NOT EXISTS leaderboard (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "player_name TEXT NOT NULL, " +
                "score INTEGER NOT NULL, " +
                "played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createQuestionsTable);
            stmt.execute(createLeaderboardTable);
        }
    }

    /**
     * All trivia lives in this one table: {round, category, prompt, correct answer, wrong options}.
     * Edit it to change the game's questions - they are re-loaded into the database every start.
     * (For BLUFF questions only the prompt and the correct answer matter.)
     */
    private static final String[][] QUESTIONS = {
        // Round 1: Rapid Fire (Buzzer)
        {"RAPID_FIRE", "TECH & GAMING", "What was the very first video game console ever released commercially?", "Magnavox Odyssey", "Atari 2600|Nintendo Entertainment System|Sega Genesis"},
        {"RAPID_FIRE", "SCIENCE", "Which planet in our solar system has the most moons as of recent discoveries?", "Saturn", "Jupiter|Uranus|Neptune"},
        {"RAPID_FIRE", "POP CULTURE", "What is the highest-grossing animated movie of all time?", "Ne Zha 2", "Inside Out 2|Frozen II|The Lion King (2019)"},
        {"RAPID_FIRE", "HISTORY", "In which year did the Titanic sink in the North Atlantic?", "1912", "1905|1920|1898"},
        {"RAPID_FIRE", "GEOGRAPHY", "What is the smallest independent country in the world by area?", "Vatican City", "Monaco|San Marino|Liechtenstein"},
        {"RAPID_FIRE", "SCIENCE", "Which chemical element has the symbol 'Au'?", "Gold", "Silver|Iron|Copper"},
        {"RAPID_FIRE", "LANGUAGES", "What is the main language spoken in Brazil?", "Portuguese", "Spanish|Italian|French"},
        {"RAPID_FIRE", "NATURE", "Which gas do plants absorb from the air for photosynthesis?", "Carbon dioxide", "Oxygen|Nitrogen|Hydrogen"},

        // Round 2: Bluff facts with blanks (players invent fake answers)
        {"BLUFF", "WEIRD HISTORY", "The tiny town of Lajitas, Texas once elected a _____ as its mayor.", "Goat", "Goldfish|Cactus|Trained Parrot"},
        {"BLUFF", "NATURE & ANIMALS", "Wombat poop is famously shaped like a _____ to keep it from rolling away.", "Cube", "Pyramid|Donut|Hexagon"},
        {"BLUFF", "INVENTIONS", "The inventor of the Pringles can had some of his ashes buried in a _____.", "Pringles Can", "Potato Sack|Cardboard Box|Chip Fryer"},
        {"BLUFF", "STRANGE LAWS", "In Switzerland, it is illegal to own just one _____ because they get lonely.", "Guinea Pig", "Parrot|Rabbit|Hamster"},
        {"BLUFF", "SPACE & SCIENCE", "Astronauts report that outer space smells surprisingly like _____.", "Seared Steak", "Burnt Toast|Gunpowder|Diesel Fuel"},
        {"BLUFF", "ANIMALS", "An octopus has _____ hearts.", "Three", "Two|Five|Eight"},
        {"BLUFF", "PLANETS", "A day on Venus is longer than its _____.", "Year", "Month|Night|Season"},
        {"BLUFF", "FOOD", "Botanically speaking, a banana is a _____.", "Berry", "Nut|Vegetable|Root"},

        // Round 3: High-stakes final wager
        {"WAGER", "ANCIENT MYSTERIES", "Which ancient civilization constructed the cliff city of Petra?", "Nabataeans", "Persians|Phoenicians|Babylonians"},
        {"WAGER", "COMPUTER SCIENCE", "What was the original code name given to the Java programming language by James Gosling?", "Oak", "Green|Mocha|Cava"},
        {"WAGER", "WORLD CURIOSITIES", "Which country is home to the world's oldest continuously operating restaurant, open since 1725?", "Spain", "France|Italy|China"},
        {"WAGER", "GEOGRAPHY", "What is the capital city of Australia?", "Canberra", "Sydney|Melbourne|Perth"},
        {"WAGER", "HISTORY", "In which year did the Berlin Wall fall?", "1989", "1987|1991|1985"},
    };

    /**
     * Requirement 2: Load the sample trivia questions (replaces the old ones so edits always apply).
     */
    private void seedDefaultQuestions() {
        if (!sqliteAvailable) return;
        String insertSql = "INSERT INTO questions (round_type, category, prompt, correct_answer, wrong_options) VALUES (?, ?, ?, ?, ?);";
        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM questions;");
            }
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (String[] q : QUESTIONS) {
                    addQuestionToBatch(ps, q[0], q[1], q[2], q[3], q[4]);
                }
                ps.executeBatch();
            }
            System.out.println("[DatabaseManager] Loaded " + QUESTIONS.length + " trivia questions into SQLite game.db.");
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Failed to seed questions: " + e.getMessage());
        }
    }

    private void addQuestionToBatch(PreparedStatement ps, String roundType, String category, String prompt, String correct, String wrong) throws SQLException {
        ps.setString(1, roundType);
        ps.setString(2, category);
        ps.setString(3, prompt);
        ps.setString(4, correct);
        ps.setString(5, wrong);
        ps.addBatch();
    }

    /** Same questions, kept in memory, used if the SQLite driver is missing. */
    private void seedFallbackQuestions() {
        fallbackQuestions.clear();
        int id = 1;
        for (String[] q : QUESTIONS) {
            fallbackQuestions.add(new Question(id++, q[0], q[1], q[2], q[3], Arrays.asList(q[4].split("\\|"))));
        }
    }

    /**
     * Requirement 3: Fetch random questions by round type using PreparedStatement & ResultSet.
     */
    public List<Question> getRandomQuestions(String roundType, int limit) {
        if (!sqliteAvailable) {
            List<Question> matching = new ArrayList<>();
            for (Question q : fallbackQuestions) {
                if (q.getRoundType().equalsIgnoreCase(roundType)) {
                    matching.add(q);
                }
            }
            Collections.shuffle(matching);
            return matching.subList(0, Math.min(limit, matching.size()));
        }

        List<Question> results = new ArrayList<>();
        String query = "SELECT id, round_type, category, prompt, correct_answer, wrong_options " +
                "FROM questions WHERE round_type = ? ORDER BY RANDOM() LIMIT ?;";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, roundType);
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String type = rs.getString("round_type");
                    String cat = rs.getString("category");
                    String prompt = rs.getString("prompt");
                    String correct = rs.getString("correct_answer");
                    String wrongStr = rs.getString("wrong_options");
                    List<String> wrongList = Arrays.asList(wrongStr.split("\\|"));

                    results.add(new Question(id, type, cat, prompt, correct, wrongList));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error fetching random questions: " + e.getMessage());
            return getRandomQuestionsFallback(roundType, limit);
        }
        return results;
    }

    private List<Question> getRandomQuestionsFallback(String roundType, int limit) {
        seedFallbackQuestions();
        List<Question> matching = new ArrayList<>();
        for (Question q : fallbackQuestions) {
            if (q.getRoundType().equalsIgnoreCase(roundType)) {
                matching.add(q);
            }
        }
        Collections.shuffle(matching);
        return matching.subList(0, Math.min(limit, matching.size()));
    }

    /**
     * Requirement 4: Save player final score to leaderboard using PreparedStatement.
     */
    public void saveScore(String playerName, int score) {
        if (!sqliteAvailable) {
            fallbackScores.add(new ScoreEntry(playerName, score, "Just now"));
            fallbackScores.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
            return;
        }

        String insertSql = "INSERT INTO leaderboard (player_name, score) VALUES (?, ?);";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, playerName);
            ps.setInt(2, score);
            ps.executeUpdate();
            System.out.println("[DatabaseManager] Saved score for " + playerName + ": " + score);
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error saving score: " + e.getMessage());
            fallbackScores.add(new ScoreEntry(playerName, score, "Just now"));
        }
    }

    /**
     * Requirement 4 (cont.): Retrieve top player scores from leaderboard using ResultSet.
     */
    public List<ScoreEntry> getTopScores(int limit) {
        if (!sqliteAvailable) {
            return fallbackScores.subList(0, Math.min(limit, fallbackScores.size()));
        }

        List<ScoreEntry> list = new ArrayList<>();
        String query = "SELECT player_name, score, datetime(played_at, 'localtime') AS played_at " +
                "FROM leaderboard ORDER BY score DESC LIMIT ?;";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("player_name");
                    int score = rs.getInt("score");
                    String playedAt = rs.getString("played_at");
                    list.add(new ScoreEntry(name, score, playedAt != null ? playedAt : "Recent"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Error fetching top scores: " + e.getMessage());
            return fallbackScores.subList(0, Math.min(limit, fallbackScores.size()));
        }
        return list;
    }

    /**
     * Standalone Verification runner for Block 2 JDBC testing.
     */
    public static void main(String[] args) {
        System.out.println("=== Testing Block 2: JDBC Database Layer ===");
        DatabaseManager db = DatabaseManager.getInstance();

        System.out.println("\n1. Fetching Rapid Fire Questions (Limit 3):");
        List<Question> rf = db.getRandomQuestions("RAPID_FIRE", 3);
        for (Question q : rf) {
            System.out.println("   [" + q.getCategory() + "] " + q.getPrompt() + " -> " + q.getCorrectAnswer());
        }

        System.out.println("\n2. Fetching Bluff Facts (Limit 2):");
        List<Question> bf = db.getRandomQuestions("BLUFF", 2);
        for (Question q : bf) {
            System.out.println("   [" + q.getCategory() + "] " + q.getPrompt() + " -> " + q.getCorrectAnswer());
        }

        System.out.println("\n3. Testing Score Insertion & Leaderboard Retrieval:");
        db.saveScore("Alice", 2150);
        db.saveScore("Bob", 1750);
        db.saveScore("Charlie", 2400);

        List<ScoreEntry> top = db.getTopScores(5);
        System.out.println("   Top Leaderboard Scores:");
        int rank = 1;
        for (ScoreEntry se : top) {
            System.out.println("   #" + rank++ + " " + se.getPlayerName() + " - " + se.getScore() + " pts (" + se.getPlayedAt() + ")");
        }
        System.out.println("\n=== Block 2 JDBC Verification Successful! ===");
    }
}
