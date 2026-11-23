package com.fibbage.network;

import com.fibbage.ai.AILieDetector;
import com.fibbage.concurrency.BuzzerController;
import com.fibbage.concurrency.RoundTimer;
import com.fibbage.db.DatabaseManager;
import com.fibbage.model.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Block 4 — Multi-Client TCP Sockets
 * GameServer runs on port 5000 using ServerSocket.
 * Accepts multiple clients, spawns ClientHandler threads, manages sessions,
 * coordinates game rounds, threading locks, timers, and scoring.
 *
 * Simple rules this class follows:
 *  - every GameSession method that changes game state is "synchronized" (one lock = no races)
 *  - every handler first checks the current "phase" (so late / duplicate / early messages are ignored)
 *  - bad input from a client can never crash the client's thread
 */
public class GameServer implements Runnable {
    private final int port;
    private final int webPort;
    private ServerSocket serverSocket;
    private WebServer webServer;
    private volatile boolean running = true;

    // Active sessions mapped by Session ID
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    // All connected client handlers
    private final List<ClientHandler> allClients = new CopyOnWriteArrayList<>();

    public GameServer(int port) {
        this(port, GameConstants.DEFAULT_WEB_PORT);
    }

    public GameServer(int port, int webPort) {
        this.port = port;
        this.webPort = webPort;
    }

    public int getPort() { return port; }
    public int getWebPort() { return webPort; }

    public void registerClient(ClientHandler handler) {
        allClients.add(handler);
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public static void main(String[] args) {
        int port = GameConstants.DEFAULT_PORT;
        int webPort = GameConstants.DEFAULT_WEB_PORT;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        if (args.length > 1) {
            try { webPort = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }
        GameServer server = new GameServer(port, webPort);
        new Thread(server, "GameServer-Main").start();
    }

    @Override
    public void run() {
        String localIp = NetworkUtils.getLocalIpAddress();
        String joinUrl = NetworkUtils.getJoinUrl(localIp, webPort, "");

        System.out.println("===============================================================");
        System.out.println("  BLUFF & BUZZ - MULTIPLAYER SERVER");
        System.out.println("===============================================================");
        System.out.println("  Web & QR Join Link: " + joinUrl);
        System.out.println("  Localhost Link:     http://localhost:" + webPort + "/");
        System.out.println("  TCP Socket Port:    " + port);
        System.out.println("---------------------------------------------------------------");
        System.out.println("  Anyone on Wi-Fi/LAN can scan this QR code on phone to join:");
        try {
            System.out.println(QRCodeGenerator.toAscii(joinUrl));
        } catch (Exception ignored) {}
        System.out.println("===============================================================");

        // Start embedded Web & WebSocket server on webPort
        webServer = new WebServer(webPort, this);
        new Thread(webServer, "WebServer-Thread").start();

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[GameServer] TCP Socket bound to port " + port + ". Ready for party connections.");

            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(socket, this);
                    allClients.add(handler);
                    new Thread(handler, "Client-" + socket.getRemoteSocketAddress()).start();
                } catch (IOException e) {
                    if (!running) break;
                }
            }
        } catch (IOException e) {
            System.err.println("[GameServer] Server socket error: " + e.getMessage());
        } finally {
            stop();
        }
    }

    public void stop() {
        running = false;
        if (webServer != null) {
            webServer.stop();
        }
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
        for (ClientHandler ch : allClients) {
            ch.close();
        }
        allClients.clear();
    }

    /** Finds the session a client belongs to (null if none). */
    private GameSession sessionOf(ClientHandler handler) {
        String sId = handler.getSessionId();
        return sId == null ? null : sessions.get(sId);
    }

    public void onClientDisconnected(ClientHandler handler) {
        allClients.remove(handler);
        GameSession session = sessionOf(handler);
        if (session != null) {
            session.removePlayer(handler);
            if (session.hasNoPlayersLeft()) {
                session.shutdown();
                sessions.remove(session.getSessionId());
            }
        }
    }

    /**
     * Dispatch incoming protocol messages from clients.
     * Any bad / unexpected message is logged and ignored — it never kills the connection.
     */
    public void handleClientMessage(ClientHandler handler, String message) {
        try {
            process(handler, message);
        } catch (Exception e) {
            System.err.println("[GameServer] Ignored bad message '" + message + "': " + e);
        }
    }

    private void process(ClientHandler handler, String message) {
        String[] parts = message.split(NetworkProtocol.DELIMITER);
        String cmd = parts[0];
        GameSession session = sessionOf(handler);

        switch (cmd) {
            case NetworkProtocol.CMD_CREATE_SESSION: {
                if (session != null) {
                    sendError(handler, "You are already in a session.");
                    return;
                }
                String playerName = cleanName(parts, 1, "Host");
                String sId = generateSessionId();
                GameSession created = new GameSession(sId, handler, playerName, this);
                sessions.put(sId, created);
                handler.setSessionId(sId);
                handler.sendMessage(NetworkProtocol.EVT_SESSION_CREATED + NetworkProtocol.DELIMITER + sId);
                created.broadcastLobbyUpdate();
                System.out.println("[GameServer] Created Session " + sId + " for host " + playerName);
                break;
            }

            case NetworkProtocol.CMD_JOIN_SESSION: {
                if (session != null) {
                    sendError(handler, "You are already in a session.");
                    return;
                }
                String sId = parts.length > 1 ? parts[1].trim().toUpperCase() : "";
                String playerName = cleanName(parts, 2, "Player");

                GameSession target = sId.isEmpty() ? null : sessions.get(sId);
                if (target == null) {
                    sendError(handler, "Session ID not found: " + sId);
                    return;
                }
                handler.setSessionId(sId);
                String problem = target.tryAddPlayer(handler, playerName);
                if (problem != null) {
                    handler.setSessionId(null);
                    sendError(handler, problem);
                    return;
                }
                target.broadcastLobbyUpdate();
                System.out.println("[GameServer] Player " + playerName + " joined Session " + sId);
                break;
            }

            case NetworkProtocol.CMD_START_GAME: {
                if (session == null) return;
                if (session.getHost() == handler) {
                    session.startGame();
                } else {
                    sendError(handler, "Only the host can start the game.");
                }
                break;
            }

            case NetworkProtocol.CMD_BUZZ: {
                if (session != null) session.handleBuzz(handler.getPlayerName());
                break;
            }

            case NetworkProtocol.CMD_ANSWER_BUZZ: {
                if (session != null) session.handleBuzzAnswer(handler.getPlayerName(), arg(parts, 1));
                break;
            }

            case NetworkProtocol.CMD_SUBMIT_BLUFF: {
                if (session == null) return;
                // Format: SUBMIT_BLUFF::text::typingMs::backspaces
                // If the text itself contained "::" the extra pieces are simply joined back together.
                int n = parts.length;
                long typingMs = 2000;
                int backspaces = 0;
                int textEnd = n;
                if (n >= 4) {
                    typingMs = toLong(parts[n - 2], 2000);
                    backspaces = (int) toLong(parts[n - 1], 0);
                    textEnd = n - 2;
                }
                String text = String.join(" ", Arrays.asList(parts).subList(1, textEnd));
                session.handleBluffSubmission(handler, text, typingMs, backspaces);
                break;
            }

            case NetworkProtocol.CMD_SUBMIT_VOTE: {
                if (session != null) session.handleVoteSubmission(handler.getPlayerName(), arg(parts, 1));
                break;
            }

            case NetworkProtocol.CMD_SUBMIT_WAGER: {
                int amount = (int) toLong(arg(parts, 1), GameConstants.ROUND3_MIN_WAGER);
                if (session != null) session.handleWagerSubmission(handler.getPlayerName(), amount);
                break;
            }

            case NetworkProtocol.CMD_SUBMIT_WAGER_ANSWER: {
                if (session != null) session.handleWagerAnswer(handler.getPlayerName(), arg(parts, 1));
                break;
            }

            default:
                break; // unknown command: ignore
        }
    }

    // ---------- tiny helpers ----------

    private static void sendError(ClientHandler handler, String text) {
        handler.sendMessage(NetworkProtocol.EVT_ERROR + NetworkProtocol.DELIMITER + text);
    }

    private static String arg(String[] parts, int index) {
        return parts.length > index ? parts[index] : "";
    }

    private static long toLong(String s, long fallback) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Removes the characters our text protocol uses as separators (: | , = ~)
     * so a player's name or bluff can never break a message.
     */
    static String clean(String s, int maxLength) {
        if (s == null) return "";
        String out = s.replaceAll("[:|,=~]", " ").replaceAll("\\s+", " ").trim();
        if (out.length() > maxLength) out = out.substring(0, maxLength).trim();
        return out;
    }

    private static String cleanName(String[] parts, int index, String fallback) {
        String name = clean(arg(parts, index), 15);
        return name.isEmpty() ? fallback : name;
    }

    private String generateSessionId() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random r = new Random();
        String id;
        do {
            StringBuilder sb = new StringBuilder(4);
            for (int i = 0; i < 4; i++) {
                sb.append(chars.charAt(r.nextInt(chars.length())));
            }
            id = sb.toString();
        } while (sessions.containsKey(id)); // never reuse a live code
        return id;
    }

    /**
     * One running game for up to 4 players (empty seats are filled with AI bots).
     */
    public static class GameSession {
        // Phases: what the game is waiting for right now
        private static final String LOBBY = "LOBBY";
        private static final String R1_BUZZ = "R1_BUZZ";       // buzzer open
        private static final String R1_ANSWER = "R1_ANSWER";   // someone buzzed, choosing an answer
        private static final String R1_WAIT = "R1_WAIT";       // pause between questions
        private static final String R2_WRITE = "R2_WRITE";
        private static final String R2_VOTE = "R2_VOTE";
        private static final String R2_REVEAL = "R2_REVEAL";
        private static final String R3_WAGER = "R3_WAGER";
        private static final String R3_ANSWER = "R3_ANSWER";
        private static final String R3_REVEAL = "R3_REVEAL";
        private static final String DONE = "DONE";

        // One shared background scheduler for the short pauses between questions
        private static final ScheduledExecutorService SCHEDULER =
                Executors.newScheduledThreadPool(2, r -> {
                    Thread t = new Thread(r, "Session-Scheduler");
                    t.setDaemon(true);
                    return t;
                });

        private final String sessionId;
        private final GameServer server;
        private ClientHandler host;
        private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
        private final Map<String, Player> players = new LinkedHashMap<>(); // keeps join order
        private final Set<String> gone = new HashSet<>();                  // humans who disconnected mid-game
        private final BuzzerController buzzerController = new BuzzerController();
        private final RoundTimer timer = new RoundTimer();
        private final Random rng = new Random();
        private boolean gameStarted = false;
        private String phase = LOBBY;

        // Rounds
        private RapidFireRound round1;
        private BluffRound round2;
        private FinalWagerRound round3;

        private Question currentQuestion = null;

        // Round 2 State
        private final Map<String, BluffEntry> currentRound2Bluffs = new LinkedHashMap<>();
        private final Map<String, String> currentRound2Votes = new LinkedHashMap<>();
        private final List<String> currentVoteOptions = new ArrayList<>();

        // Round 3 State
        private final Map<String, Integer> currentWagers = new LinkedHashMap<>();
        private final Map<String, String> currentWagerAnswers = new LinkedHashMap<>();
        private List<String> currentOptions = new ArrayList<>();

        public static class BluffEntry {
            public final String author;
            public final String text;
            public final int riskPercent;
            public final boolean isRealAnswer;

            public BluffEntry(String author, String text, int riskPercent, boolean isRealAnswer) {
                this.author = author;
                this.text = text;
                this.riskPercent = riskPercent;
                this.isRealAnswer = isRealAnswer;
            }
        }

        public GameSession(String sessionId, ClientHandler host, String hostPlayerName, GameServer server) {
            this.sessionId = sessionId;
            this.host = host;
            this.server = server;
            tryAddPlayer(host, hostPlayerName);
        }

        public String getSessionId() { return sessionId; }
        public synchronized ClientHandler getHost() { return host; }

        /** Adds a player to the lobby. Returns null on success, or the reason it was refused. */
        public synchronized String tryAddPlayer(ClientHandler handler, String name) {
            if (gameStarted) return "Game already in progress for session: " + sessionId;
            if (players.size() >= GameConstants.TARGET_PLAYERS) {
                return "Session is full (max " + GameConstants.TARGET_PLAYERS + " players).";
            }
            for (String existing : players.keySet()) {
                if (existing.equalsIgnoreCase(name)) {
                    return "That name is already taken in this session - pick another one.";
                }
            }
            handler.setPlayerName(name);
            clients.add(handler);
            players.put(name, new HumanPlayer(name, handler.getSocket().getRemoteSocketAddress().toString()));
            return null;
        }

        /**
         * Called when a client disconnects.
         * In the lobby the player simply leaves. After the game started the player stays in the
         * game (score kept, auto-skipped on timeouts) so nobody else is affected.
         */
        public synchronized void removePlayer(ClientHandler handler) {
            clients.remove(handler);
            String name = handler.getPlayerName();
            if (name == null) return;

            if (!gameStarted) {
                players.remove(name);
                if (handler == host && !clients.isEmpty()) {
                    host = clients.get(0); // pass the crown to the next player
                }
                broadcastLobbyUpdate();
            } else {
                gone.add(name);
            }
        }

        public synchronized boolean hasNoPlayersLeft() {
            return clients.isEmpty();
        }

        /** Stops all timers; the session is finished. */
        public synchronized void shutdown() {
            phase = DONE;
            timer.shutdown();
        }

        public void broadcast(String message) {
            for (ClientHandler ch : clients) {
                ch.sendMessage(message);
            }
        }

        public synchronized void broadcastLobbyUpdate() {
            if (clients.isEmpty()) return;
            StringBuilder sb = new StringBuilder();
            sb.append(NetworkProtocol.EVT_LOBBY_UPDATE).append(NetworkProtocol.DELIMITER)
              .append(sessionId).append(NetworkProtocol.DELIMITER)
              .append(players.size()).append(NetworkProtocol.DELIMITER)
              .append(String.join(",", players.keySet())).append(NetworkProtocol.DELIMITER)
              .append(host.getPlayerName());
            broadcast(sb.toString());
        }

        /** Sends everyone's current score, so every client always shows correct numbers. */
        private void broadcastScores() {
            List<String> list = new ArrayList<>();
            for (Player p : players.values()) {
                list.add(p.getName() + "=" + p.getScore());
            }
            broadcast(NetworkProtocol.EVT_SCORES + NetworkProtocol.DELIMITER + String.join(",", list));
        }

        private void sendTo(String playerName, String message) {
            for (ClientHandler ch : clients) {
                if (playerName.equals(ch.getPlayerName())) ch.sendMessage(message);
            }
        }

        /** True when every human who is still connected is in the given set of names. */
        private boolean allHumansIn(Set<String> submitted) {
            for (Player p : players.values()) {
                if (!p.isAI() && !gone.contains(p.getName()) && !submitted.contains(p.getName())) {
                    return false;
                }
            }
            return true;
        }

        private int activePlayerCount() {
            return players.size() - gone.size();
        }

        /** Runs a task after a pause, under the session lock, unless the game is over. */
        private void later(long delayMs, Runnable task) {
            SCHEDULER.schedule(() -> {
                synchronized (GameSession.this) {
                    if (DONE.equals(phase)) return;
                    try {
                        task.run();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }

        private void tick(int secondsLeft) {
            broadcast(NetworkProtocol.EVT_TIMER_TICK + NetworkProtocol.DELIMITER + secondsLeft);
        }

        /**
         * Starts the game session. Fills empty seats with AI bots.
         */
        public synchronized void startGame() {
            if (gameStarted) return;
            gameStarted = true;

            String[] botNames = {"Echo", "Pixel", "Nova", "Cipher"};
            for (String base : botNames) {
                if (players.size() >= GameConstants.TARGET_PLAYERS) break;
                String bName = base + "_AI";
                if (!players.containsKey(bName)) players.put(bName, new AIPlayer(bName));
            }

            broadcast(NetworkProtocol.EVT_GAME_STARTED);
            System.out.println("[Session " + sessionId + "] Game started with " + players.size() + " players.");

            DatabaseManager db = DatabaseManager.getInstance();
            round1 = new RapidFireRound(db.getRandomQuestions("RAPID_FIRE", GameConstants.ROUND1_NUM_QUESTIONS));
            round2 = new BluffRound(db.getRandomQuestions("BLUFF", GameConstants.ROUND2_NUM_QUESTIONS));
            round3 = new FinalWagerRound(db.getRandomQuestions("WAGER", GameConstants.ROUND3_NUM_QUESTIONS));

            startRound1();
        }

        // ==========================================
        // ROUND 1: REFLEX BUZZER SPRINT
        // ==========================================
        private void startRound1() {
            broadcast(NetworkProtocol.EVT_ROUND_STARTED + NetworkProtocol.DELIMITER + "1" + NetworkProtocol.DELIMITER + round1.getRoundTitle());
            sendNextRound1Question();
        }

        private void sendNextRound1Question() {
            currentQuestion = round1.getCurrentQuestion();
            if (currentQuestion == null) {
                startRound2();
                return;
            }

            buzzerController.resetForNewQuestion();
            buzzerController.openBuzzer();
            phase = R1_BUZZ;

            List<String> options = currentQuestion.getAllOptionsShuffled();
            broadcast(NetworkProtocol.EVT_ROUND1_QUESTION + NetworkProtocol.DELIMITER +
                    (round1.getCurrentQuestionIndex() + 1) + NetworkProtocol.DELIMITER +
                    round1.getTotalQuestions() + NetworkProtocol.DELIMITER +
                    currentQuestion.getCategory() + NetworkProtocol.DELIMITER +
                    currentQuestion.getPrompt() + NetworkProtocol.DELIMITER +
                    String.join("|", options));

            timer.start(GameConstants.ROUND1_BUZZ_TIMEOUT_SEC, this::tick, this::onRound1BuzzerTimeout);
            scheduleBotBuzz();
        }

        /** Moves to the next Round 1 question (or on to Round 2). */
        private void goToNextRound1Question() {
            if (round1.hasNextQuestion()) {
                round1.nextQuestion();
                sendNextRound1Question();
            } else {
                startRound2();
            }
        }

        /** Gives the AI bots a chance to buzz in, so Round 1 is a real race. */
        private void scheduleBotBuzz() {
            List<String> bots = new ArrayList<>();
            for (Player p : players.values()) {
                if (p.isAI() && !buzzerController.isPlayerLockedOut(p.getName())) bots.add(p.getName());
            }
            if (bots.isEmpty()) return;

            final int questionIndex = round1.getCurrentQuestionIndex();
            final String bot = bots.get(rng.nextInt(bots.size()));
            later(5000 + rng.nextInt(9000), () -> {
                if (!R1_BUZZ.equals(phase) || round1.getCurrentQuestionIndex() != questionIndex) return;
                handleBuzz(bot);
                // the bot thinks for 2 seconds, then answers (right about half the time)
                later(2000, () -> {
                    if (!R1_ANSWER.equals(phase) || !bot.equals(buzzerController.getActiveBuzzerPlayer())) return;
                    String choice;
                    if (rng.nextInt(100) < 55) {
                        choice = currentQuestion.getCorrectAnswer();
                    } else {
                        List<String> wrong = currentQuestion.getWrongOptions();
                        choice = wrong.get(rng.nextInt(wrong.size()));
                    }
                    handleBuzzAnswer(bot, choice);
                });
            });
        }

        public synchronized void handleBuzz(String playerName) {
            if (!R1_BUZZ.equals(phase) || playerName == null || !players.containsKey(playerName)) return;

            // Thread-safe buzzer claim: only the very first player wins
            if (buzzerController.tryBuzz(playerName)) {
                phase = R1_ANSWER;
                broadcast(NetworkProtocol.EVT_BUZZ_LOCKED + NetworkProtocol.DELIMITER + playerName);

                // The player who buzzed gets a limited time to answer
                timer.start(GameConstants.ROUND1_ANSWER_TIMEOUT_SEC, this::tick,
                        () -> handleBuzzAnswer(playerName, "__TIMEOUT__"));
            }
        }

        public synchronized void handleBuzzAnswer(String playerName, String chosenOption) {
            // Only the player who buzzed, and only once
            if (!R1_ANSWER.equals(phase) || currentQuestion == null) return;
            if (playerName == null || !playerName.equals(buzzerController.getActiveBuzzerPlayer())) return;
            timer.stop();

            Player p = players.get(playerName);
            boolean correct = chosenOption.equalsIgnoreCase(currentQuestion.getCorrectAnswer());

            if (correct) {
                phase = R1_WAIT;
                buzzerController.closeBuzzer();
                int delta = round1.calculatePoints(true, 0); // +500
                p.addScore(delta);
                broadcast(NetworkProtocol.EVT_SCORE_UPDATE + NetworkProtocol.DELIMITER +
                        playerName + NetworkProtocol.DELIMITER + delta + NetworkProtocol.DELIMITER +
                        p.getScore() + NetworkProtocol.DELIMITER +
                        playerName + " slammed the buzzer and got it RIGHT! (+" + delta + ")");
                broadcastScores();
                later(3000, this::goToNextRound1Question);
                return;
            }

            int delta = round1.calculatePoints(false, 0); // -200
            p.addScore(delta);
            buzzerController.lockOutCurrentPlayer(); // this player is out for this question
            broadcastScores();

            if (buzzerController.areAllPlayersLockedOut(activePlayerCount())) {
                // Everyone got it wrong
                phase = R1_WAIT;
                buzzerController.closeBuzzer();
                broadcast(NetworkProtocol.EVT_SCORE_UPDATE + NetworkProtocol.DELIMITER +
                        playerName + NetworkProtocol.DELIMITER + delta + NetworkProtocol.DELIMITER +
                        p.getScore() + NetworkProtocol.DELIMITER +
                        playerName + " got it WRONG (" + delta + ") and nobody is left! The answer was: " +
                        currentQuestion.getCorrectAnswer());
                later(3000, this::goToNextRound1Question);
            } else {
                // Reopen the buzzer for the players who are not locked out
                broadcast(NetworkProtocol.EVT_SCORE_UPDATE + NetworkProtocol.DELIMITER +
                        playerName + NetworkProtocol.DELIMITER + delta + NetworkProtocol.DELIMITER +
                        p.getScore() + NetworkProtocol.DELIMITER +
                        playerName + " buzzed WRONG! (" + delta + ") Buzzer reopened!");
                broadcast(NetworkProtocol.EVT_BUZZ_REOPENED + NetworkProtocol.DELIMITER +
                        String.join(",", buzzerController.getLockedOutPlayers()));
                phase = R1_BUZZ;
                buzzerController.openBuzzer();
                timer.start(GameConstants.ROUND1_REOPEN_TIMEOUT_SEC, this::tick, this::onRound1BuzzerTimeout);
                scheduleBotBuzz();
            }
        }

        private synchronized void onRound1BuzzerTimeout() {
            if (!R1_BUZZ.equals(phase)) return; // stale timer
            phase = R1_WAIT;
            buzzerController.closeBuzzer();

            // Small penalty for everyone for freezing
            int delta = round1.calculatePoints(false, -1); // -50
            for (Player p : players.values()) {
                p.addScore(delta);
            }
            broadcast(NetworkProtocol.EVT_SCORE_UPDATE + NetworkProtocol.DELIMITER +
                    "ALL" + NetworkProtocol.DELIMITER + delta + NetworkProtocol.DELIMITER + 0 + NetworkProtocol.DELIMITER +
                    "Nobody buzzed in time! Everyone loses " + Math.abs(delta) + " pts! The answer was: " +
                    currentQuestion.getCorrectAnswer());
            broadcastScores();
            later(3000, this::goToNextRound1Question);
        }

        // ==========================================
        // ROUND 2: BLUFF & AI LIE-DETECTOR
        // ==========================================
        private void startRound2() {
            broadcast(NetworkProtocol.EVT_ROUND_STARTED + NetworkProtocol.DELIMITER + "2" + NetworkProtocol.DELIMITER + round2.getRoundTitle());
            sendNextRound2Question();
        }

        private void sendNextRound2Question() {
            currentQuestion = round2.getCurrentQuestion();
            if (currentQuestion == null) {
                startRound3();
                return;
            }

            phase = R2_WRITE;
            currentRound2Bluffs.clear();
            currentRound2Votes.clear();
            currentVoteOptions.clear();

            broadcast(NetworkProtocol.EVT_ROUND2_QUESTION + NetworkProtocol.DELIMITER +
                    (round2.getCurrentQuestionIndex() + 1) + NetworkProtocol.DELIMITER +
                    round2.getTotalQuestions() + NetworkProtocol.DELIMITER +
                    currentQuestion.getCategory() + NetworkProtocol.DELIMITER +
                    currentQuestion.getPrompt() + NetworkProtocol.DELIMITER +
                    GameConstants.ROUND2_INPUT_TIMEOUT_SEC);

            // AI players write their bluffs straight away (each one different)
            for (Player p : players.values()) {
                if (p.isAI()) {
                    String fake;
                    int tries = 0;
                    do {
                        fake = ((AIPlayer) p).generateFakeAnswer();
                        tries++;
                    } while (bluffTextTaken(fake) && tries < 20);
                    int aiRisk = AILieDetector.calculateBluffRisk(fake, 1800, 0, false);
                    currentRound2Bluffs.put(p.getName(), new BluffEntry(p.getName(), fake, aiRisk, false));
                    broadcast(NetworkProtocol.EVT_PLAYER_SUBMITTED + NetworkProtocol.DELIMITER + p.getName());
                }
            }

            timer.start(GameConstants.ROUND2_INPUT_TIMEOUT_SEC, this::tick, this::transitionToRound2Voting);
        }

        /** True if this text is already used as a bluff or is the real answer. */
        private boolean bluffTextTaken(String text) {
            if (text.equalsIgnoreCase(currentQuestion.getCorrectAnswer().trim())) return true;
            for (BluffEntry be : currentRound2Bluffs.values()) {
                if (be.text.equalsIgnoreCase(text)) return true;
            }
            return false;
        }

        public synchronized void handleBluffSubmission(ClientHandler handler, String rawText, long typingMs, int backspaces) {
            if (!R2_WRITE.equals(phase)) return;
            String pName = handler.getPlayerName();
            if (pName == null || !players.containsKey(pName) || currentRound2Bluffs.containsKey(pName)) return;

            String text = clean(rawText, 40);
            if (text.isEmpty()) return;

            // Rule: if player types the exact real answer, reject with "too close to the truth"!
            if (text.equalsIgnoreCase(currentQuestion.getCorrectAnswer().trim())) {
                handler.sendMessage(NetworkProtocol.EVT_ERROR + NetworkProtocol.DELIMITER +
                        "Too close to the truth! You know the real answer — now invent a believable LIE to trick friends!");
                return;
            }
            // Rule: two players can't write the same lie
            if (bluffTextTaken(text)) {
                handler.sendMessage(NetworkProtocol.EVT_ERROR + NetworkProtocol.DELIMITER +
                        "Too close to the truth! Another player already wrote that exact lie — invent a different one!");
                return;
            }

            long ms = Math.max(0, Math.min(typingMs, 600000));
            int bs = Math.max(0, Math.min(backspaces, 500));
            int riskPercent = AILieDetector.calculateBluffRisk(text, ms, bs, false);
            currentRound2Bluffs.put(pName, new BluffEntry(pName, text, riskPercent, false));

            handler.sendMessage(NetworkProtocol.EVT_BLUFF_ACK);
            broadcast(NetworkProtocol.EVT_PLAYER_SUBMITTED + NetworkProtocol.DELIMITER + pName);

            if (allHumansIn(currentRound2Bluffs.keySet())) {
                transitionToRound2Voting();
            }
        }

        private synchronized void transitionToRound2Voting() {
            if (!R2_WRITE.equals(phase)) return; // stale timer or double call
            phase = R2_VOTE;
            timer.stop();

            // Players who wrote nothing get an automatic (weak) bluff
            for (Player p : players.values()) {
                if (!currentRound2Bluffs.containsKey(p.getName())) {
                    String fallbackLie = "Mystery Option from " + p.getName();
                    currentRound2Bluffs.put(p.getName(), new BluffEntry(p.getName(), fallbackLie, 70, false));
                    broadcast(NetworkProtocol.EVT_PLAYER_SUBMITTED + NetworkProtocol.DELIMITER + p.getName());
                }
            }

            // Real answer entry with baseline AI risk
            int realRisk = AILieDetector.calculateBluffRisk(currentQuestion.getCorrectAnswer(), 2500, 0, true);
            BluffEntry realEntry = new BluffEntry("__REAL__", currentQuestion.getCorrectAnswer(), realRisk, true);

            List<BluffEntry> allEntries = new ArrayList<>(currentRound2Bluffs.values());
            allEntries.add(realEntry);
            Collections.shuffle(allEntries);

            // Every player gets the SAME list: all bluffs + the real answer from the database, shuffled.
            // Option format: "text~risk~mine" joined by "|"   (mine = 1 when you wrote that bluff)
            // ("~" is stripped from all player text, so it can never clash with the real separators)
            currentVoteOptions.clear();
            for (BluffEntry be : allEntries) currentVoteOptions.add(be.text);
            for (ClientHandler ch : clients) {
                String who = ch.getPlayerName();
                List<String> formatted = new ArrayList<>();
                for (BluffEntry be : allEntries) {
                    formatted.add(be.text + "~" + be.riskPercent + "~" + (be.author.equals(who) ? 1 : 0));
                }
                ch.sendMessage(NetworkProtocol.EVT_ROUND2_VOTE_OPTIONS + NetworkProtocol.DELIMITER + String.join("|", formatted));
            }

            // AI players vote randomly (never for their own bluff)
            for (Player p : players.values()) {
                if (p.isAI()) {
                    List<String> validVotes = new ArrayList<>();
                    for (BluffEntry be : allEntries) {
                        if (!be.author.equals(p.getName())) validVotes.add(be.text);
                    }
                    currentRound2Votes.put(p.getName(), validVotes.get(rng.nextInt(validVotes.size())));
                }
            }

            timer.start(GameConstants.ROUND2_VOTE_TIMEOUT_SEC, this::tick, this::revealRound2Results);
        }

        public synchronized void handleVoteSubmission(String playerName, String chosenVote) {
            if (!R2_VOTE.equals(phase)) return;
            if (playerName == null || !players.containsKey(playerName) || currentRound2Votes.containsKey(playerName)) return;

            // The vote must be one of the options on screen
            String chosen = null;
            for (String option : currentVoteOptions) {
                if (option.equalsIgnoreCase(chosenVote.trim())) chosen = option;
            }
            if (chosen == null) return;

            // Rule: you cannot vote for your own bluff
            BluffEntry own = currentRound2Bluffs.get(playerName);
            if (own != null && own.text.equalsIgnoreCase(chosen)) {
                sendTo(playerName, NetworkProtocol.EVT_ERROR + NetworkProtocol.DELIMITER +
                        "That is YOUR bluff - you can't vote for it! Pick another option.");
                return;
            }

            currentRound2Votes.put(playerName, chosen);
            broadcast(NetworkProtocol.EVT_PLAYER_SUBMITTED + NetworkProtocol.DELIMITER + playerName);

            if (allHumansIn(currentRound2Votes.keySet())) {
                revealRound2Results();
            }
        }

        private synchronized void revealRound2Results() {
            if (!R2_VOTE.equals(phase)) return; // stale timer or double call
            phase = R2_REVEAL;
            timer.stop();

            String realAnswer = currentQuestion.getCorrectAnswer();
            Map<String, Integer> authorFoolCounts = new HashMap<>();

            for (Map.Entry<String, String> vote : currentRound2Votes.entrySet()) {
                String voterName = vote.getKey();
                String chosenText = vote.getValue();
                Player voter = players.get(voterName);
                if (voter == null) continue;

                if (chosenText.equalsIgnoreCase(realAnswer)) {
                    voter.addScore(round2.calculatePoints(true, 0)); // picked the truth: +500
                } else {
                    voter.addScore(round2.calculatePoints(false, 0)); // picked a fake: -200
                    // credit the author of that fake (but never for fooling yourself)
                    for (BluffEntry be : currentRound2Bluffs.values()) {
                        if (be.text.equalsIgnoreCase(chosenText) && !be.author.equals(voterName)) {
                            authorFoolCounts.merge(be.author, 1, Integer::sum);
                            break;
                        }
                    }
                }
            }

            // +250 per friend fooled
            for (Map.Entry<String, Integer> entry : authorFoolCounts.entrySet()) {
                Player author = players.get(entry.getKey());
                if (author != null) {
                    author.addScore(round2.calculatePoints(false, entry.getValue()));
                }
            }

            broadcastScores();

            List<String> playerSummaries = new ArrayList<>();
            for (Player p : players.values()) {
                playerSummaries.add(p.getName() + "=" + p.getScore());
            }
            broadcast(NetworkProtocol.EVT_ROUND2_REVEAL + NetworkProtocol.DELIMITER +
                    realAnswer + NetworkProtocol.DELIMITER + String.join(",", playerSummaries));

            later(5000, () -> {
                if (round2.hasNextQuestion()) {
                    round2.nextQuestion();
                    sendNextRound2Question();
                } else {
                    startRound3();
                }
            });
        }

        // ==========================================
        // ROUND 3: ALL-IN RISK WAGER
        // ==========================================
        private void startRound3() {
            broadcast(NetworkProtocol.EVT_ROUND_STARTED + NetworkProtocol.DELIMITER + "3" + NetworkProtocol.DELIMITER + round3.getRoundTitle());

            currentQuestion = round3.getCurrentQuestion();
            if (currentQuestion == null) {
                finishGame();
                return;
            }

            phase = R3_WAGER;
            currentWagers.clear();
            currentWagerAnswers.clear();

            // Step 1: send only the category
            broadcast(NetworkProtocol.EVT_ROUND3_CATEGORY + NetworkProtocol.DELIMITER +
                    currentQuestion.getCategory() + NetworkProtocol.DELIMITER + GameConstants.ROUND3_MIN_WAGER);

            // Bots wager about half their points
            for (Player p : players.values()) {
                if (p.isAI()) {
                    int w = Math.min(p.getScore(), Math.max(GameConstants.ROUND3_MIN_WAGER, p.getScore() / 2));
                    currentWagers.put(p.getName(), w);
                    broadcast(NetworkProtocol.EVT_PLAYER_SUBMITTED + NetworkProtocol.DELIMITER + p.getName());
                }
            }

            timer.start(GameConstants.ROUND3_WAGER_TIMEOUT_SEC, this::tick, this::revealRound3Question);
        }

        public synchronized void handleWagerSubmission(String playerName, int wagerAmount) {
            if (!R3_WAGER.equals(phase)) return;
            Player p = players.get(playerName);
            if (p == null || currentWagers.containsKey(playerName)) return;

            p.setWager(wagerAmount); // Player validates the amount
            currentWagers.put(playerName, p.getWager());
            broadcast(NetworkProtocol.EVT_PLAYER_SUBMITTED + NetworkProtocol.DELIMITER + playerName);

            if (allHumansIn(currentWagers.keySet())) {
                revealRound3Question();
            }
        }

        private synchronized void revealRound3Question() {
            if (!R3_WAGER.equals(phase)) return; // stale timer or double call
            phase = R3_ANSWER;
            timer.stop();

            // Default wager for anyone who did not choose
            for (Player p : players.values()) {
                currentWagers.putIfAbsent(p.getName(), Math.min(GameConstants.ROUND3_MIN_WAGER, p.getScore()));
            }

            currentOptions = currentQuestion.getAllOptionsShuffled();
            broadcast(NetworkProtocol.EVT_ROUND3_QUESTION + NetworkProtocol.DELIMITER +
                    currentQuestion.getPrompt() + NetworkProtocol.DELIMITER + String.join("|", currentOptions));

            // Bots answer randomly
            for (Player p : players.values()) {
                if (p.isAI()) {
                    currentWagerAnswers.put(p.getName(), currentOptions.get(rng.nextInt(currentOptions.size())));
                }
            }

            timer.start(GameConstants.ROUND3_ANSWER_TIMEOUT_SEC, this::tick, this::revealRound3Results);
        }

        public synchronized void handleWagerAnswer(String playerName, String chosenOption) {
            if (!R3_ANSWER.equals(phase)) return;
            if (playerName == null || !players.containsKey(playerName) || currentWagerAnswers.containsKey(playerName)) return;

            String chosen = null;
            for (String option : currentOptions) {
                if (option.equalsIgnoreCase(chosenOption.trim())) chosen = option;
            }
            if (chosen == null) return;

            currentWagerAnswers.put(playerName, chosen);
            broadcast(NetworkProtocol.EVT_PLAYER_SUBMITTED + NetworkProtocol.DELIMITER + playerName);

            if (allHumansIn(currentWagerAnswers.keySet())) {
                revealRound3Results();
            }
        }

        private synchronized void revealRound3Results() {
            if (!R3_ANSWER.equals(phase)) return; // stale timer or double call
            phase = R3_REVEAL;
            timer.stop();

            String realAns = currentQuestion.getCorrectAnswer();
            List<String> res = new ArrayList<>();

            for (Player p : players.values()) {
                // you can never bet more than you have
                int wager = Math.min(currentWagers.getOrDefault(p.getName(), 0), p.getScore());
                boolean correct = currentWagerAnswers.getOrDefault(p.getName(), "").equalsIgnoreCase(realAns);
                p.addScore(round3.calculatePoints(correct, wager));
                res.add(p.getName() + ":" + wager + ":" + p.getScore());
            }

            broadcastScores();
            broadcast(NetworkProtocol.EVT_ROUND3_REVEAL + NetworkProtocol.DELIMITER +
                    realAns + NetworkProtocol.DELIMITER + String.join(",", res));

            later(5000, this::finishGame);
        }

        private void finishGame() {
            if (DONE.equals(phase)) return;

            List<Player> sorted = new ArrayList<>(players.values());
            sorted.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

            // Save only real players to the Hall of Fame (not the bots)
            DatabaseManager db = DatabaseManager.getInstance();
            for (Player p : sorted) {
                if (!p.isAI()) db.saveScore(p.getName(), p.getScore());
            }

            List<String> parts = new ArrayList<>();
            for (Player p : sorted) {
                parts.add(p.getName() + "=" + p.getScore());
            }
            broadcast(NetworkProtocol.EVT_GAME_OVER + NetworkProtocol.DELIMITER + String.join(",", parts));
            System.out.println("[Session " + sessionId + "] Game Over! Winner: " + (sorted.isEmpty() ? "None" : sorted.get(0).getName()));

            // The session is finished: free the room code and the timer thread
            for (ClientHandler ch : clients) {
                ch.setSessionId(null);
            }
            server.removeSession(sessionId);
            shutdown();
        }
    }
}
