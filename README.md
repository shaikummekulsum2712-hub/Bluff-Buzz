BLUFF & BUZZ

A multiplayer party game for up to 4 players, written in Java. It has a reflex buzzer round, a bluffing round and a final wager round. Players join from a phone or browser by scanning a QR code, or from a Java desktop client. Empty seats are filled by bots.

Features
Three rounds: Reflex Buzzer, Bluff, and Final Wager
Join from any phone or browser with a QR code or link, with nothing to install
Optional Java Swing desktop client
Bots fill empty seats, up to 4 players
A rule-based "AI Lie-Detector" that shows a bluff-risk % beside each answer
SQLite question bank and leaderboard, accessed with plain JDBC
Built with only the JDK and three jars, no frameworks
How to run

Requirements: Java 11 or newer, and a Windows PC (the .bat files use Windows paths).

Double-click run_server.bat. This starts the server and opens http://localhost:8080/ in your browser.
Click Host Game, enter a name and click Create Session.
Share the QR code or link. Friends on the same Wi-Fi scan it, enter a name and join.
When everyone is in, the host clicks Start Game. Empty seats become bots.

To use the desktop client, run run_game.bat instead.

Port	Used by
8080	Browsers and phones (HTTP and WebSocket)
5000	Desktop Swing clients (TCP)

If a phone can't open the link: both devices must be on the same Wi-Fi, and Windows Firewall must allow Java on port 8080.

How to play

Everyone starts with 1,000 points. The highest score at the end wins.

Round 1: Reflex Buzzer (3 questions). Press BUZZ first, then pick an answer from 4 options.

Round 2: Bluff (2 questions). You see a fact with a blank. Write a believable fake answer. Then everyone sees all the fakes plus the real answer, and votes for the one they think is true. You can't vote for your own bluff, and typing the real answer is rejected.

Round 3: Final Wager (1 question). You see only the category, then secretly bet points, then answer a 4-option question.

Event	Points
Round 1 correct buzz	+500
Round 1 wrong buzz	−200, and you're locked out of that question
Round 1 nobody buzzes	−50 for everyone
Round 2 vote for the real answer	+500
Round 2 vote for a fake	−200
Round 2 someone votes for your fake	+250 per voter
Round 3 correct	+your bet
Round 3 wrong	−your bet

A score never goes below 0. A Round 3 bet is at least 100 and never more than you have.

Timers (seconds): Round 1 buzz 20, answer 15, reopened buzzer 13. Round 2 write 35, vote 25. Round 3 wager 20, answer 25. A round ends early when every connected human has acted.

Project structure
uk/
├── src/com/fibbage/
│   ├── model/         Player, HumanPlayer, AIPlayer, Question, GameConstants,
│   │                  GameRound, RapidFireRound, BluffRound, FinalWagerRound
│   ├── db/            DatabaseManager (JDBC + SQLite)
│   ├── concurrency/   BuzzerController, RoundTimer
│   ├── network/       GameServer, ClientHandler, GameClient, WebServer,
│   │                  WebSocketClientHandler, NetworkProtocol, NetworkUtils,
│   │                  QRCodeGenerator
│   ├── ai/            AILieDetector
│   └── ui/            MainFrame, LobbyPanel, Round1/2/3 panels,
│                      LeaderboardPanel, PlayerSquareWidget, UITheme
├── web/               index.html, style.css, app.js  (browser client)
├── lib/               sqlite-jdbc.jar, slf4j-api.jar, slf4j-simple.jar
├── bin/               compiled classes + copy of web/
├── game.db            SQLite file (created on first run)
├── build.bat          compile
├── run_server.bat     start the server
├── run_game.bat       start the desktop client
└── test_blocks.bat    run self-tests for blocks 1, 2 and 3

The Java package is com.fibbage, the project's old name. It isn't visible to players.

The five blocks
OOP. Player and GameRound are abstract. HumanPlayer and AIPlayer extend Player. The three round classes extend GameRound and override calculatePoints. Player keeps its fields private and validates its setters.
JDBC. DatabaseManager creates the questions and leaderboard tables, loads 21 questions at every start, picks random questions with a PreparedStatement, and saves and reads scores. If the SQLite jar is missing, it falls back to in-memory questions.
Concurrency. BuzzerController.tryBuzz is synchronized, so only the first buzz wins. RoundTimer uses a ScheduledExecutorService. All game state in GameSession changes under one lock, with a phase check on every message.
Networking. GameServer accepts TCP clients, with one ClientHandler thread each. WebServer serves the page and upgrades /ws to a WebSocket (RFC 6455, written by hand). WebSocketClientHandler extends ClientHandler, so browsers and desktop clients are handled the same way.
Swing GUI. MainFrame switches screens with a CardLayout. GameClient reads server messages on a background thread and hands them to the interface through SwingUtilities.invokeLater.
Network protocol

Each message is plain text: a command or event name, then fields separated by ::. Lists use | between items, , between names and = between a name and a score. Player names and bluffs have : | , = ~ replaced by spaces.

Client to server	Example
CREATE_SESSION	CREATE_SESSION::Al
JOIN_SESSION	JOIN_SESSION::K7XZ::Bob
START_GAME	
BUZZ, ANSWER_BUZZ	ANSWER_BUZZ::Gold
SUBMIT_BLUFF	SUBMIT_BLUFF::Cow::3200::1 (text, typing ms, backspaces)
SUBMIT_VOTE	SUBMIT_VOTE::Year
SUBMIT_WAGER, SUBMIT_WAGER_ANSWER	SUBMIT_WAGER::400

The server sends events such as LOBBY_UPDATE, ROUND1_QUESTION, BUZZ_LOCKED, ROUND2_VOTE_OPTIONS, ROUND2_REVEAL, ROUND3_REVEAL, SCORES, GAME_OVER and ERROR. The full list is in NetworkProtocol.java.

The Round 2 vote list uses the format text~risk~mine, with items joined by |. For example: Horse~25~0|Cow~58~1. mine=1 marks the bluff you wrote.

Bots and the lie-detector

These are rule-based. They are not machine learning.

Bots buzz after a random 5 to 14 seconds and answer correctly 55% of the time. In Round 2 they pick from a fixed list of silly answers and vote at random. In Round 3 they bet about half their score and answer at random.
Lie-detector starts at a risk of 40 and adjusts it for backspaces, typing speed per character and a few keywords. The result is clamped to 10–92. The real answer gets a fixed low value between 12 and 35.
QR code

The server answers GET /qr?room=ABCD with a PNG of http://<LAN-IP>:8080/?room=ABCD. The encoder in QRCodeGenerator.java is written from scratch, with no libraries. It supports byte mode, error-correction level M and versions 1 to 9. The server prefers a private Wi-Fi address (192.168.x.x, 10.x.x.x or 172.16–31.x.x) over VPN or virtual adapters.

Customising
Questions: edit the QUESTIONS array in DatabaseManager.java. Each row is: round, category, prompt, correct answer, wrong options joined by |. Then run build.bat. For Round 2 only the prompt and the correct answer are used.
Points, timers, ports: edit GameConstants.java.
Bot bluffs: edit the FAKE_BLUFFS list in AIPlayer.java.

After any change to src/ or web/, run build.bat. It compiles into bin/ and copies web/ into bin/web/.

To run without the batch files (Linux or macOS), from the uk folder:

java -cp "bin:lib/*" com.fibbage.network.GameServer 5000 8080
Troubleshooting
Problem	Fix
Phone can't open the link	Use the same Wi-Fi as the PC and allow Java through Windows Firewall on port 8080
Address already in use	An earlier server is still running. Close it and start again
QR opens the wrong address	Read the address under "Share this URL" on the PC page and type it on the phone
Console says "embedded in-memory fallback"	lib/sqlite-jdbc.jar is missing from the class path, so scores won't be saved
Browser shows "Disconnected" mid-game	Refresh and join a new game. The server forgets disconnected players
Known limitations
Disconnected web players can't rejoin the same game.
ROUND2_AI_FOOLED_BONUS is defined in GameConstants.java but not used.
The lie-detector gives the real answer a separate low risk score, so the truth tends to look lower-risk.
