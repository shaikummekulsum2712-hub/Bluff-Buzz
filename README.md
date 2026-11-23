# 🎭 Bluff & Buzz

### A Multiplayer Party Game of Reflexes, Deception & Strategy

**Bluff & Buzz** is a real-time multiplayer party game built entirely in Java. Designed for up to four players, it combines fast reflexes, creative deception, and strategic wagering into three competitive rounds.

Think fast. Bluff convincingly. Bet wisely.

Players can join using a phone or browser through a QR code, or connect through a Java Swing desktop client. No installation is required for browser-based players, and AI-controlled bots automatically fill empty seats.

---

## 🎮 Game Features

- **Three Unique Rounds:** Reflex Buzzer, Bluff, and Final Wager.
- **Multiplayer Support:** Play with up to four players simultaneously.
- **Cross-Platform Access:** Join through a browser, mobile phone, or Java desktop client.
- **QR Code Lobby:** Generate a room QR code for quick multiplayer connections.
- **AI Players:** Rule-based bots fill unoccupied player slots.
- **AI Lie Detector:** A rule-based system estimates bluff risk using typing behaviour and answer characteristics.
- **Real-Time Communication:** TCP networking and WebSocket-based browser communication.
- **Persistent Leaderboard:** SQLite database integration using JDBC.
- **Custom QR Generator:** QR codes generated from scratch without an external QR library.
- **Automatic Round Timers:** Scheduled round transitions and timed player actions.

---

## 🕹️ How to Play

Every player starts with **1,000 points**. The objective is to finish with the highest score.

### Round 1 — Reflex Buzzer ⚡

- Three multiple-choice questions.
- Players must press the BUZZ button before answering.
- The first player to buzz gets the opportunity to answer.
- Incorrect answers lock that player out of the current question.

### Round 2 — The Bluff 🎭

- Two questions containing incomplete facts.
- Players submit believable fake answers.
- Everyone votes on which answer they believe is real.
- You cannot vote for your own bluff.
- Typing the actual answer is rejected.

The objective is simple: make your lie believable and identify everyone else's.

### Round 3 — Final Wager 💰

- One final question.
- Players see the question category first.
- Everyone secretly places a wager.
- A multiple-choice question is revealed.
- Correct answers earn the wager; incorrect answers lose it.

### Scoring System

| Event | Points |
|---|---:|
| Correct Round 1 answer | +500 |
| Incorrect Round 1 answer | -200 |
| No one buzzes | -50 each |
| Vote for the real answer | +500 |
| Vote for a fake answer | -200 |
| Someone votes for your bluff | +250 per voter |
| Correct Round 3 answer | +Your wager |
| Incorrect Round 3 answer | -Your wager |

Scores never fall below zero.

---

## 🧠 AI Lie Detector

One of the interesting components of Bluff & Buzz is its built-in **AI Lie Detector**.

It estimates the probability that a submitted answer is a bluff using simple rule-based heuristics.

The system considers:

- Typing speed per character.
- Number of backspaces.
- Selected keywords and answer characteristics.

A risk score is calculated and clamped between 10% and 92%.

The actual answer receives a separate, lower risk score to help distinguish it from fabricated responses.

**Important:** This is a heuristic-based system, not a machine learning model. It does not claim to detect lies with scientific accuracy.

---

## 🏗️ Technical Architecture

Bluff & Buzz uses a Java-based client-server architecture with separate communication paths for browser and desktop clients.

### Architecture Overview

```text
                  GAME SERVER
                       |
          +------------+------------+
          |                         |
     TCP Server                 Web Server
          |                         |
   Desktop Clients           WebSocket Server
          |                         |
      Java Swing             Browser / Mobile
          |                         |
          +------------+------------+
                       |
                  Game Session
                       |
          +------------+------------+
          |            |            |
       Round 1       Round 2      Round 3
       Buzzer         Bluff        Wager
          |            |            |
          +------------+------------+
                       |
                Game State & Scoring
                       |
              SQLite Database
                       |
                 Leaderboard
```

### Core Engineering Concepts

| Component | Implementation |
|---|---|
| Programming Language | Java |
| Desktop Interface | Java Swing |
| Browser Interface | HTML, CSS, JavaScript |
| Desktop Networking | TCP Sockets |
| Browser Networking | WebSockets |
| Database | SQLite |
| Database Connectivity | JDBC |
| Concurrency | Threads, Synchronization |
| Timers | ScheduledExecutorService |
| QR Generation | Custom implementation |

### Notable Implementation Details

**1. Object-Oriented Programming**

- `Player` and `GameRound` are abstract classes.
- `HumanPlayer` and `AIPlayer` extend `Player`.
- Individual round classes extend `GameRound`.
- Each round implements its own scoring logic through method overriding.

**2. Concurrent Multiplayer Handling**

- Each TCP client is handled by a separate client-handler thread.
- `BuzzerController.tryBuzz()` uses synchronization to ensure that only the first valid buzzer wins.
- Game state modifications are protected using a shared lock.
- Phase validation prevents players from submitting actions during the wrong game phase.

**3. Real-Time Communication**

- Desktop clients communicate through TCP sockets.
- Browser clients communicate through WebSockets.
- A shared server-side game session coordinates both types of clients.
- WebSocket handling is implemented using the RFC 6455 protocol without a WebSocket framework.

**4. Database Management**

SQLite and JDBC handle:
- Question storage and retrieval.
- Random question selection.
- Leaderboard persistence.
- Player score management.

**5. Custom QR Code Generation**

The QR code generator is implemented from scratch and supports byte mode, error correction level M, and QR versions 1–9.

---

## 📁 Project Structure

```text
Bluff-Buzz/
│
├── bluff&buzz/
│   ├── src/
│   │   └── com/fibbage/
│   │       ├── model/
│   │       ├── db/
│   │       ├── concurrency/
│   │       ├── network/
│   │       ├── ai/
│   │       └── ui/
│   │
│   ├── web/
│   │   ├── index.html
│   │   ├── style.css
│   │   └── app.js
│   │
│   ├── lib/
│   │   ├── sqlite-jdbc.jar
│   │   ├── slf4j-api.jar
│   │   └── slf4j-simple.jar
│   │
│   ├── bin/
│   ├── game.db
│   ├── build.bat
│   ├── run_server.bat
│   ├── run_game.bat
│   └── test_blocks.bat
│
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

- Java 11 or newer.
- Windows PC (for the provided batch scripts).
- A browser for web-based gameplay.
- All three JAR dependencies included in the `lib/` directory.

### 1. Clone the Repository

```bash
git clone https://github.com/shaikummekulsum2712-hub/Bluff-Buzz.git
```

### 2. Navigate to the Project

```bash
cd Bluff-Buzz
cd "bluff&buzz"
```

### 3. Start the Game Server

Run:

```bat
run_server.bat
```

This compiles/starts the server and opens the game in your browser at:

```text
http://localhost:8080/
```

### 4. Create a Game

1. Click **Host Game**.
2. Enter your player name.
3. Click **Create Session**.
4. Share the generated QR code or room link.
5. Have other players join using their phones or browsers.
6. Click **Start Game** when everyone is ready.

Empty player slots are automatically filled by bots.

### 5. Run the Desktop Client

Alternatively, launch the Java Swing desktop client:

```bat
run_game.bat
```

Desktop clients connect through TCP on port `5000`.

---

## 🌐 Network Configuration

| Port | Purpose |
|---|---|
| 8080 | Browser and mobile clients (HTTP + WebSocket) |
| 5000 | Java Swing desktop clients (TCP) |

For mobile devices to connect:

- Both the computer and mobile devices must be connected to the same Wi-Fi network.
- Allow Java through Windows Firewall.
- Ensure port `8080` is accessible.
- Use the network URL displayed by the server instead of `localhost` on mobile devices.

---

## ⚙️ Customization

Several game settings can be modified directly in the source code.

| File | What You Can Customize |
|---|
