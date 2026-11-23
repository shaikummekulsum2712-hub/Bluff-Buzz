# BLUFF & BUZZ 🎮
**Multiplayer Party & Bluffing Game in Java & Web**

A complete, production-ready multiplayer party game demonstrating all four Object-Oriented Programming (OOP) pillars, multithreaded concurrency, pure JDBC database integration with SQLite, multi-client TCP & WebSocket server architecture, real-time AI Lie-Detector keystroke biometrics, QR Code mobile phone joining, responsive web party client, and a sleek dark-themed Java Swing GUI.

---

## 🚀 Quick Start (How to Run)

### Option 1: Standalone Server & Mobile QR Play (Recommended)
1. Double-click `run_server.bat` to launch the dedicated GameServer.
2. The server binds:
   - **Web & Mobile (WebSocket/HTTP):** Port `8080` (e.g. `http://192.168.1.X:8080`)
   - **Desktop Swing TCP Sockets:** Port `5000`
3. A scannable **QR Code** and link are displayed in the console:
   - Anyone on the same Wi-Fi / LAN can point their phone camera at the QR code or click the link to join instantly in their mobile browser (no installation needed!).
   - In the web app, any player can **Host a new game** or **Join an existing session** with a 4-letter room code!

### Option 2: Desktop Party GUI Launcher
1. Double-click `run_game.bat` to launch the desktop client (`MainFrame`).
2. In the lobby:
   - **Host a Game:** Click **"Create Session [Host]"** (connects to running server or launches embedded server, generates room code like `SHOW1`, and displays a live on-screen **QR Code** so friends in the room can scan and join).
   - **Join a Game:** Enter your friend's **Session Code** and click **"Join Game [Play]"**.
   - Click **"Start Game [Launch]"** to begin the showdown! (Up to 4 players per game. If fewer than 4 humans join, AI bots fill the empty seats - and the bots buzz in too!).

### Option 3: Command Line (Build & Run)
```bash
# 1. Compile source files & package web assets
build.bat

# 2. Run standalone block verifications (OOP, Database, Concurrency)
test_blocks.bat

# 3. Launch Server or GUI
run_server.bat
run_game.bat
```

---

## 📦 Project Structure & The 5 Core Blocks

The project is cleanly divided into 5 modular blocks according to your course requirements:

```
uk/
├── src/
│   └── com/fibbage/
│       ├── model/             --> [Block 1: All 4 OOP Pillars]
│       │   ├── Player.java            (Abstract Player: encapsulation, validated getters/setters)
│       │   ├── HumanPlayer.java       (Inheritance: human socket player)
│       │   ├── AIPlayer.java          (Inheritance: autonomous bluffing bot)
│       │   ├── Question.java          (Trivia data model)
│       │   ├── GameConstants.java     (All point values, penalties & timers)
│       │   ├── GameRound.java         (Abstract base round: polymorphism)
│       │   ├── RapidFireRound.java    (Subclass: Round 1 reflex buzzer scoring)
│       │   ├── BluffRound.java        (Subclass: Round 2 fake answer & fool scoring)
│       │   └── FinalWagerRound.java   (Subclass: Round 3 high-stakes wagering)
│       ├── db/                --> [Block 2: JDBC Database Layer]
│       │   └── DatabaseManager.java   (Pure Java JDBC, PreparedStatement, ResultSet, SQLite)
│       ├── concurrency/       --> [Block 3: Threading & Synchronization]
│       │   ├── BuzzerController.java  (Synchronized tryBuzz mutex locking & wrong buzz lockout)
│       │   └── RoundTimer.java        (ScheduledExecutorService countdown thread)
│       ├── network/           --> [Block 4: Multi-Client TCP Sockets]
│       │   ├── NetworkProtocol.java   (Session creation, buzzer locks, scoring messages)
│       │   ├── GameServer.java        (ServerSocket on port 5000, multi-session state machine)
│       │   ├── ClientHandler.java     (Per-player worker thread with PrintWriter & BufferedReader)
│       │   └── GameClient.java        (TCP client dispatching events to Swing EDT)
│       ├── ai/                --> [AI / ML Lie Detector Module]
│       │   └── AILieDetector.java     (Keystroke biometric analysis, backspace & hesitation metrics)
│       └── ui/                --> [Block 5: Java Swing GUI]
│           ├── UITheme.java           (Dark aesthetic palette, custom fonts, rounded cards)
│           ├── PlayerSquareWidget.java (4-player square layout: Top, Bottom, Left, Right badges)
│           ├── LobbyPanel.java        (Host/Join session panel & waiting room)
│           ├── Round1BuzzerPanel.java (Reflex sprint with giant buzzer button & lockout states)
│           ├── Round2BluffPanel.java  (Fact with blank, live keystroke tracker, AI risk voting)
│           ├── Round3WagerPanel.java  (Category reveal, secret wager slider, high-stakes question)
│           ├── LeaderboardPanel.java  (Final victory podium & SQLite hall of fame)
│           └── MainFrame.java         (Master JFrame with CardLayout & SwingUtilities.invokeLater)
├── lib/
│   ├── sqlite-jdbc.jar        (SQLite JDBC Driver)
│   ├── slf4j-api.jar
│   └── slf4j-simple.jar
├── build.bat                  (Compilation script)
├── run_game.bat               (GUI launcher)
├── run_server.bat             (Standalone dedicated server)
├── test_blocks.bat            (Automated verification test runner)
└── BluffAndBuzz.zip (Complete self-contained submission archive)
```

---

## 🎯 Step-by-Step Block Breakdown

### Block 1 — All 4 OOP Pillars
1. **Encapsulation:**
   - `Player.java` protects `score`, `wager`, and `lockedOut` behind `private` fields.
   - Validated setters ensure scores never fall below `0` (`Math.max(0, score)`) and wagers stay within the legal window (`100` to current score).
2. **Abstraction:**
   - `Player` and `GameRound` are abstract classes defining common state and abstract contracts (`isAI()`, `calculatePoints()`).
3. **Inheritance:**
   - `HumanPlayer` and `AIPlayer` extend `Player`.
   - `RapidFireRound`, `BluffRound`, and `FinalWagerRound` extend `GameRound`.
4. **Polymorphism:**
   - Each round overrides `calculatePoints(boolean isCorrect, int extraData)` with its distinct rules:
     - `RapidFireRound`: `+500` for correct buzz, `-200` for wrong buzz, `-50` for nobody buzzing.
     - `BluffRound`: `+500` for truth, `-200` for picking fake, `+250` per opponent fooled by your bluff.
     - `FinalWagerRound`: `+wager` or `-wager`.

---

### Block 2 — Pure JDBC Database Layer
- `DatabaseManager.java` connects via `jdbc:sqlite:game.db`.
- **PreparedStatement & ResultSet:**
  1. `initTables()`: Automatically creates the `questions` and `leaderboard` tables on first launch.
  2. `seedDefaultQuestions()`: Inserts 13+ rich trivia questions across all 3 rounds.
  3. `getRandomQuestions(String roundType, int limit)`: Queries random questions using `PreparedStatement` with parameterized placeholders (`?`).
  4. `saveScore(String playerName, int score)`: Inserts match records with timestamps into SQLite.
  5. `getTopScores(int limit)`: Retrieves the Hall of Fame leaderboard using `ResultSet`.

---

### Block 3 — Threading & Synchronization
- `BuzzerController.java`:
  - Uses `synchronized tryBuzz(String playerName)` to guarantee that when multiple laptops slam the buzzer at the exact same millisecond, **only the very first caller wins the lock**.
  - Subsequent callers are safely rejected until the question resolves.
  - **Wrong Buzz Lockout Rule:** If the active player buzzes incorrectly, `lockOutCurrentPlayer()` flags that player and immediately re-opens the buzzer so the remaining active players can buzz in!
- `RoundTimer.java`:
  - Employs a thread-safe `ScheduledExecutorService` with `scheduleAtFixedRate`.
  - Decrements an `AtomicInteger` countdown and invokes UI callbacks every second, followed by a completion trigger when time hits 0.

---

### Block 4 — Multi-Client TCP Sockets
- `GameServer.java` (Port 5000):
  - Spawns a dedicated `ClientHandler` thread per connected player.
  - Features session management: hosts can create a Session ID (e.g., `SHOW1`), and other players join using that code.
  - Coordinates round transitions, routes client commands (`BUZZ`, `SUBMIT_BLUFF`, `SUBMIT_VOTE`, `SUBMIT_WAGER`), and broadcasts game state updates.
- `GameClient.java`:
  - Listens in a background thread and posts incoming events safely onto the Swing Event Dispatch Thread (EDT) using `SwingUtilities.invokeLater()`.

---

### Block 5 — Modern Java Swing GUI
- Built with a modern dark theme (`UITheme.java`).
- Uses `CardLayout` across 5 dynamic panels:
  1. **Lobby Panel:** Host/Join forms with big Session Code display and 4-player waiting room.
  2. **Round 1 Buzzer Panel:** Features the **4-player square arena** (Top, Bottom, Left, Right player badges), center trivia question, giant synchronized buzzer button, lockout indicator, and floating score animations (`+500` / `-200`).
  3. **Round 2 Bluff Panel:** Fact with blank, secret typing input with live **AI Keystroke Tracker** (measuring hesitation milliseconds & backspace count), green status indicator lights, and a voting grid showing each option with its **AI Bluff Risk %**.
  4. **Round 3 Wager Panel:** Category reveal, secret risk wagering slider/inputs, high-stakes question, and dramatic reveal breakdown.
  5. **Leaderboard Panel:** Champion victory banner, match results, and SQLite database Hall of Fame.

---

## ⚖️ Game Rules & Scoring Reference

| Event / Action | Points Awarded | Notes |
| :--- | :--- | :--- |
| **Starting Score** | `1,000 pts` | Everyone begins with 1,000 pts. Score never drops below 0. |
| **Round 1: Correct Buzz** | `+500 pts` | First to slam the buzzer answers correctly. |
| **Round 1: Wrong Buzz** | `-200 pts` | Buzzer reopens for the other players; you are locked out for this Q. |
| **Round 1: Timeout (Nobody Buzzes)** | `-50 pts` | Small penalty applied to all players if nobody buzzes in 20s. |
| **Round 2: Guess The Real Answer** | `+500 pts` | Selected the authentic historical fact. |
| **Round 2: Fall For A Fake Bluff** | `-200 pts` | Selected an opponent's invented lie. |
| **Round 2: Fooled An Opponent** | `+250 pts` | Awarded to the author for every friend who fell for their bluff. |
| **Round 2: Real Answer Rejected** | `0 pts` | If you type the true answer, server rejects it with *"Too close to the truth!"*. |
| **Round 2: Voting** | - | Everyone sees ALL submitted bluffs plus the real answer from the database, shuffled. You can't vote for your own bluff. |
| **Round 3: Wager Correct** | `+wager` | Doubles your bet (min 100, max current score). |
| **Round 3: Wager Wrong** | `-wager` | Loses your bet amount. |

