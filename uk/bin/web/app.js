/**
 * BLUFF & BUZZ — Web Party Client
 * Connects over WebSocket, renders real-time party UI, supports QR joining,
 * live buzzer actions, AI lie-detector biometric keystroke tracking, and victory podium.
 */

(function() {
    'use strict';

    // Game State
    let ws = null;
    let myPlayerName = 'Player';
    let currentSessionId = '';
    let isHost = false;
    let currentScore = 1000;
    let currentRoundMaxTime = 20;

    // Keystroke Biometrics for Round 2
    let typingStartTime = 0;
    let backspaceCount = 0;
    let typingStarted = false;
    let biometricTimer = null;

    // Web Audio Synthesizer
    let audioCtx = null;
    function getAudio() {
        if (!audioCtx) {
            const AudioContext = window.AudioContext || window.webkitAudioContext;
            if (AudioContext) audioCtx = new AudioContext();
        }
        if (audioCtx && audioCtx.state === 'suspended') {
            audioCtx.resume();
        }
        return audioCtx;
    }

    function playBuzzerSound() {
        const ctx = getAudio();
        if (!ctx) return;
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(220, ctx.currentTime);
        osc.frequency.exponentialRampToValueAtTime(110, ctx.currentTime + 0.25);
        gain.gain.setValueAtTime(0.4, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.25);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start();
        osc.stop(ctx.currentTime + 0.25);
    }

    function playChimeSound() {
        const ctx = getAudio();
        if (!ctx) return;
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(587.33, ctx.currentTime); // D5
        osc.frequency.setValueAtTime(880.00, ctx.currentTime + 0.1); // A5
        gain.gain.setValueAtTime(0.3, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.35);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start();
        osc.stop(ctx.currentTime + 0.35);
    }

    // UI Elements
    const connectionStatus = document.getElementById('connectionStatus');
    const statusText = document.getElementById('statusText');
    const roomPill = document.getElementById('roomPill');
    const roomCodeDisplay = document.getElementById('roomCodeDisplay');
    const copyRoomBtn = document.getElementById('copyRoomBtn');

    // Screens
    const screens = {
        lobby: document.getElementById('screenLobby'),
        waiting: document.getElementById('screenWaiting'),
        round1: document.getElementById('screenRound1'),
        round2: document.getElementById('screenRound2'),
        round3: document.getElementById('screenRound3'),
        leaderboard: document.getElementById('screenLeaderboard')
    };

    function showScreen(name) {
        Object.values(screens).forEach(s => s && s.classList.remove('active'));
        if (screens[name]) {
            screens[name].classList.add('active');
        }
        window.scrollTo(0, 0);
    }

    function showToast(msg, type = 'info') {
        const container = document.getElementById('toastContainer');
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.textContent = msg;
        container.appendChild(toast);
        setTimeout(() => {
            toast.style.opacity = '0';
            setTimeout(() => toast.remove(), 300);
        }, 3500);
    }

    // ── Share URL Banner ─────────────────────────────────────────────
    // Fetch LAN IP from server and show the real join URL so the host
    // can copy-paste or read it out to friends on the same Wi-Fi.
    let _shareUrl = location.origin; // fallback: works for localhost
    function loadShareUrl() {
        fetch('/api/info')
            .then(r => r.ok ? r.json() : null)
            .then(info => {
                if (info && info.joinUrl) {
                    _shareUrl = info.joinUrl;
                    const el = document.getElementById('shareUrlText');
                    if (el) el.textContent = info.joinUrl;
                }
            })
            .catch(() => {
                // Server might not be up yet or fetch blocked; keep origin
                const el = document.getElementById('shareUrlText');
                if (el) el.textContent = location.origin + '/';
            });
    }

    window.copyShareUrl = function() {
        // Use the server address we discovered, or fall back to location.host
        const url = _shareUrl;
        navigator.clipboard.writeText(url).then(() => {
            showToast('📋 Share URL copied! Send to friends on Wi-Fi.', 'success');
        }).catch(() => {
            prompt('Copy this join link:', url || _shareUrl || `${location.origin}/`);
        });
    };

    // Also make the URL text clickable to copy
    document.addEventListener('DOMContentLoaded', () => {
        const el = document.getElementById('shareUrlText');
        if (el) el.onclick = window.copyShareUrl;
    });

    // WebSocket Connection
    // Server address will be set after fetching /api/info
    let serverAddress = null;

    function connectWebSocket() {
        // Use available serverAddress, otherwise fallback to location.host
        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const address = serverAddress || location.host;
        const wsUrl = `${protocol}//${address}/ws`;

        connectionStatus.className = 'status-pill status-disconnected';
        statusText.textContent = 'Connecting...';

        try {
            ws = new WebSocket(wsUrl);

            ws.onopen = () => {
                connectionStatus.className = 'status-pill status-connected';
                statusText.textContent = 'Connected';
            };

            ws.onmessage = (evt) => {
                const message = evt.data.trim();
                if (message) processMessage(message);
            };

            ws.onerror = () => {
                connectionStatus.className = 'status-pill status-disconnected';
                statusText.textContent = 'Error';
            };

            ws.onclose = () => {
                connectionStatus.className = 'status-pill status-disconnected';
                statusText.textContent = 'Disconnected';
                if (currentSessionId) {
                    // The server forgets you when the connection drops, so reconnecting inside a game cannot work.
                    showToast('Connection lost. Refresh the page to join a new game.', 'error');
                } else {
                    setTimeout(connectWebSocket, 2500);
                }
            };
        } catch (e) {
            setTimeout(connectWebSocket, 3000);
        }
    }

    function sendCommand(cmd) {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(cmd);
        } else {
            showToast('Not connected to game server', 'error');
        }
    }

    // Message Protocol Parser
    // NetworkProtocol.DELIMITER is "::" (two colons)
    function processMessage(msg) {
        const parts = msg.split('::');
        // The server sends names like LOBBY_UPDATE; the cases below are written as EVT_LOBBY_UPDATE
        const evt = 'EVT_' + parts[0];

        switch (evt) {
            case 'EVT_SESSION_CREATED': {
                currentSessionId = parts[1] || '';
                isHost = true;
                enterWaitingRoom(currentSessionId);
                break;
            }

            case 'EVT_LOBBY_UPDATE': {
                currentSessionId = parts[1] || '';
                const count = parseInt(parts[2], 10) || 0;
                const namesStr = parts[3] || '';
                const hostName = parts[4] || '';
                const names = namesStr ? namesStr.split(',') : [];

                if (myPlayerName.toLowerCase() === hostName.toLowerCase()) {
                    isHost = true;
                }

                enterWaitingRoom(currentSessionId);
                updateLobbyPlayers(names, count, hostName);
                break;
            }

            case 'EVT_GAME_STARTED': {
                showToast('🚀 Showdown is starting!', 'info');
                playChimeSound();
                break;
            }

            case 'EVT_ROUND_STARTED': {
                const rNum = parts[1] || '1';
                const rTitle = parts[2] || '';
                showToast(`🔔 Round ${rNum}: ${rTitle}`, 'info');
                if (rNum === '1') showScreen('round1');
                else if (rNum === '2') showScreen('round2');
                else if (rNum === '3') showScreen('round3');
                break;
            }

            case 'EVT_TIMER_TICK': {
                const sec = parseInt(parts[1], 10) || 0;
                updateTimers(sec);
                break;
            }

            // --- Round 1 Events ---
            case 'EVT_ROUND1_QUESTION': {
                showScreen('round1');
                currentRoundMaxTime = 20; // 20s
                const qIdx = parts[1];
                const totalQ = parts[2];
                const category = parts[3];
                const prompt = parts[4];
                const opts = parts[5] ? parts[5].split('|') : [];

                document.getElementById('r1QuestionIndex').textContent = `Q ${qIdx}/${totalQ}`;
                document.getElementById('r1Category').textContent = category.toUpperCase();
                document.getElementById('r1Prompt').textContent = prompt;

                // Reset buzzer button
                const buzzerBtn = document.getElementById('bigBuzzerBtn');
                buzzerBtn.disabled = false;
                document.getElementById('buzzerStatusMsg').textContent = 'SLAM THE BUZZER FIRST TO ANSWER!';
                document.getElementById('buzzerStatusMsg').style.color = 'var(--accent-yellow)';
                document.getElementById('r1BuzzerArea').classList.remove('hidden');
                document.getElementById('r1OptionsArea').classList.add('hidden');

                // Prepopulate options
                const optGrid = document.getElementById('r1OptionsGrid');
                optGrid.innerHTML = '';
                opts.forEach(opt => {
                    const btn = document.createElement('button');
                    btn.className = 'option-btn';
                    btn.textContent = opt;
                    btn.onclick = () => {
                        sendCommand(`ANSWER_BUZZ::${opt}`);
                        optGrid.querySelectorAll('button').forEach(b => b.disabled = true);
                        btn.style.borderColor = 'var(--accent-yellow)';
                    };
                    optGrid.appendChild(btn);
                });
                break;
            }

            case 'EVT_BUZZ_LOCKED': {
                const winner = parts[1] || '';
                const buzzerBtn = document.getElementById('bigBuzzerBtn');
                buzzerBtn.disabled = true;

                if (winner.toLowerCase() === myPlayerName.toLowerCase()) {
                    playChimeSound();
                    document.getElementById('buzzerStatusMsg').textContent = '⚡ YOU BUZZED FIRST! Choose your answer:';
                    document.getElementById('buzzerStatusMsg').style.color = 'var(--accent-cyan)';
                    document.getElementById('r1BuzzerArea').classList.add('hidden');
                    document.getElementById('r1OptionsArea').classList.remove('hidden');
                    // Enable options
                    document.querySelectorAll('#r1OptionsGrid button').forEach(b => b.disabled = false);
                } else {
                    document.getElementById('buzzerStatusMsg').textContent = `🔒 ${winner} BUZZED FIRST!`;
                    document.getElementById('buzzerStatusMsg').style.color = 'var(--accent-red)';
                }
                break;
            }

            case 'EVT_SCORES': {
                // "Alice=1500,Bob=900" -> remember my own score (used for the wager limit)
                (parts[1] || '').split(',').forEach(pair => {
                    const kv = pair.split('=');
                    if (kv.length === 2 && kv[0].toLowerCase() === myPlayerName.toLowerCase()) {
                        currentScore = parseInt(kv[1], 10) || 0;
                    }
                });
                break;
            }

            case 'EVT_BUZZ_REOPENED': {
                // Someone answered wrong: everyone except the locked-out players can buzz again
                const lockedOut = (parts[1] || '').split(',').map(n => n.toLowerCase());
                const btn = document.getElementById('bigBuzzerBtn');
                const msg = document.getElementById('buzzerStatusMsg');
                document.getElementById('r1BuzzerArea').classList.remove('hidden');
                document.getElementById('r1OptionsArea').classList.add('hidden');
                if (lockedOut.includes(myPlayerName.toLowerCase())) {
                    btn.disabled = true;
                    msg.textContent = '🚫 You are locked out for this question';
                    msg.style.color = 'var(--accent-red)';
                } else {
                    btn.disabled = false;
                    msg.textContent = '🔓 BUZZER REOPENED - SLAM IT!';
                    msg.style.color = 'var(--accent-yellow)';
                }
                break;
            }

            case 'EVT_SCORE_UPDATE': {
                const pName = parts[1];
                const delta = parseInt(parts[2], 10) || 0;
                const newScore = parseInt(parts[3], 10) || 0;
                const msgText = parts[4] || '';

                if (pName.toLowerCase() === myPlayerName.toLowerCase()) {
                    currentScore = newScore;
                    if (delta > 0) playChimeSound();
                }

                showToast(msgText, delta >= 0 ? 'success' : 'warn');
                break;
            }

            // --- Round 2 Events ---
            case 'EVT_ROUND2_QUESTION': {
                showScreen('round2');
                currentRoundMaxTime = 35; // 35s
                const qIdx = parts[1];
                const totalQ = parts[2];
                const category = parts[3];
                const prompt = parts[4];

                document.getElementById('r2Category').textContent = category.toUpperCase();
                document.getElementById('r2Prompt').textContent = prompt;
                document.getElementById('r2PhaseBadge').textContent = 'PHASE: WRITE BLUFF';

                // Switch to write phase
                document.getElementById('r2WriteArea').classList.remove('hidden');
                document.getElementById('r2VoteArea').classList.add('hidden');
                document.getElementById('r2RevealArea').classList.add('hidden');

                // Reset input and biometrics
                const input = document.getElementById('bluffInput');
                input.value = '';
                input.disabled = false;
                document.getElementById('btnSubmitBluff').disabled = false;
                document.getElementById('btnSubmitBluff').textContent = 'Lock In My Bluff [Submit]';
                resetBiometrics();
                break;
            }

            case 'EVT_BLUFF_ACK': {
                showToast('✅ Bluff submitted & analyzed by AI!', 'success');
                document.getElementById('bluffInput').disabled = true;
                document.getElementById('btnSubmitBluff').disabled = true;
                document.getElementById('btnSubmitBluff').textContent = '✓ Bluff Submitted!';
                break;
            }

            case 'EVT_ROUND2_VOTE_OPTIONS': {
                currentRoundMaxTime = 25; // 25s
                document.getElementById('r2PhaseBadge').textContent = 'PHASE: VOTE FOR TRUTH';
                document.getElementById('r2WriteArea').classList.add('hidden');
                document.getElementById('r2VoteArea').classList.remove('hidden');
                document.getElementById('r2RevealArea').classList.add('hidden');

                const optsStr = parts[1] || '';
                const rawOpts = optsStr ? optsStr.split('|') : [];
                const voteGrid = document.getElementById('r2VoteGrid');
                voteGrid.innerHTML = '';

                document.querySelector('#r2VoteArea .vote-instructions').textContent =
                    `Choose the answer you believe is the GENUINE TRUTH (${rawOpts.length} options):`;

                // Every option arrives as "text~risk~mine": all players' bluffs + the real answer, shuffled.
                rawOpts.forEach(raw => {
                    const sub = raw.split('~');
                    const text = sub[0];
                    const parsedRisk = parseInt(sub[1], 10);
                    const risk = isNaN(parsedRisk) ? 40 : parsedRisk;
                    const mine = sub[2] === '1';

                    const card = document.createElement('button');
                    card.className = 'vote-card-btn';

                    const riskClass = risk <= 35 ? 'risk-low' : (risk <= 65 ? 'risk-med' : 'risk-high');
                    const riskLabel = risk <= 35 ? 'Low' : (risk <= 65 ? 'Med' : 'High');

                    card.innerHTML = `
                        <span class="vote-text">${escapeHtml(text)}${mine ? ' <em class="vote-mine">(your bluff)</em>' : ''}</span>
                        <span class="risk-badge ${riskClass}">AI Risk: ${risk}% ${riskLabel}</span>
                    `;

                    if (mine) {
                        card.disabled = true;
                        card.classList.add('vote-card-own');
                    } else {
                        card.onclick = () => {
                            sendCommand(`SUBMIT_VOTE::${text}`);
                            voteGrid.querySelectorAll('button').forEach(b => b.disabled = true);
                            card.classList.add('vote-card-picked');
                            showToast(`Voted for: "${text}"`, 'info');
                        };
                    }

                    voteGrid.appendChild(card);
                });
                break;
            }

            case 'EVT_ROUND2_REVEAL': {
                document.getElementById('r2PhaseBadge').textContent = 'PHASE: THE REVEAL';
                document.getElementById('r2WriteArea').classList.add('hidden');
                document.getElementById('r2VoteArea').classList.add('hidden');
                document.getElementById('r2RevealArea').classList.remove('hidden');

                const realAnswer = parts[1] || '';
                const scores = parts[2] || '';
                document.getElementById('r2RealAnswerText').textContent = realAnswer;
                document.getElementById('r2ScoresSummary').textContent = `Scores: ${scores.replace(/,/g, '  •  ')}`;
                playChimeSound();
                break;
            }

            // --- Round 3 Events ---
            case 'EVT_ROUND3_CATEGORY': {
                showScreen('round3');
                currentRoundMaxTime = 20; // 20s
                const cat = parts[1] || 'FINAL CATEGORY';
                const minW = parseInt(parts[2], 10) || 100;

                document.getElementById('r3Category').textContent = cat.toUpperCase();
                document.getElementById('r3WagerCategory').textContent = cat;
                document.getElementById('r3PhaseBadge').textContent = 'PHASE: SECRET WAGER';

                document.getElementById('r3WagerArea').classList.remove('hidden');
                document.getElementById('r3QuestionArea').classList.add('hidden');
                document.getElementById('r3RevealArea').classList.add('hidden');

                const slider = document.getElementById('wagerSlider');
                const maxWager = Math.max(minW, currentScore);
                slider.min = minW;
                slider.max = maxWager;
                slider.value = Math.min(maxWager, Math.max(minW, Math.floor(maxWager / 2)));
                document.getElementById('wagerAmountDisplay').textContent = slider.value;
                document.getElementById('wagerMaxLabel').textContent = `${maxWager} pts`;

                document.getElementById('btnLockWager').disabled = false;
                document.getElementById('btnLockWager').textContent = 'Lock In Secret Wager';
                break;
            }

            case 'EVT_ROUND3_QUESTION': {
                currentRoundMaxTime = 25; // 25s
                document.getElementById('r3PhaseBadge').textContent = 'PHASE: HIGH-STAKES QUESTION';
                document.getElementById('r3WagerArea').classList.add('hidden');
                document.getElementById('r3QuestionArea').classList.remove('hidden');
                document.getElementById('r3RevealArea').classList.add('hidden');

                const prompt = parts[1] || '';
                const opts = parts[2] ? parts[2].split('|') : [];

                document.getElementById('r3Prompt').textContent = prompt;
                const optGrid = document.getElementById('r3OptionsGrid');
                optGrid.innerHTML = '';

                opts.forEach(opt => {
                    const btn = document.createElement('button');
                    btn.className = 'option-btn';
                    btn.textContent = opt;
                    btn.onclick = () => {
                        sendCommand(`SUBMIT_WAGER_ANSWER::${opt}`);
                        optGrid.querySelectorAll('button').forEach(b => b.disabled = true);
                        btn.style.borderColor = 'var(--accent-red)';
                        showToast(`Submitted final answer: "${opt}"`, 'info');
                    };
                    optGrid.appendChild(btn);
                });
                break;
            }

            case 'EVT_ROUND3_REVEAL': {
                document.getElementById('r3PhaseBadge').textContent = 'PHASE: DRAMATIC REVEAL';
                document.getElementById('r3WagerArea').classList.add('hidden');
                document.getElementById('r3QuestionArea').classList.add('hidden');
                document.getElementById('r3RevealArea').classList.remove('hidden');

                const realAns = parts[1] || '';
                const summary = parts[2] || '';
                document.getElementById('r3RealAnswerText').textContent = realAns;
                document.getElementById('r3WagerSummary').textContent = summary.replace(/,/g, '\n');
                playChimeSound();
                break;
            }

            // --- Game Over ---
            case 'EVT_GAME_OVER': {
                showScreen('leaderboard');
                const leaderboardSummary = parts[1] || '';
                renderLeaderboard(leaderboardSummary);
                playChimeSound();
                break;
            }

            case 'EVT_ERROR': {
                showToast(parts[1] || 'Error', 'error');
                break;
            }
        }
    }

    // Helper Functions
    function enterWaitingRoom(sessionId) {
        showScreen('waiting');
        roomPill.classList.remove('hidden');
        roomCodeDisplay.textContent = sessionId;
        document.getElementById('waitingSessionCode').textContent = sessionId;

        // Render QR Code image
        const qrImg = document.getElementById('qrImage');
        if (qrImg.dataset.room !== sessionId) {
            qrImg.dataset.room = sessionId;
            qrImg.src = `/qr?room=${encodeURIComponent(sessionId)}`;
        }

        if (isHost) {
            document.getElementById('btnStartGame').classList.remove('hidden');
            document.getElementById('waitingStatusMsg').textContent = 'You are the host! Start game when ready.';
        } else {
            document.getElementById('btnStartGame').classList.add('hidden');
            document.getElementById('waitingStatusMsg').textContent = 'Joined session! Waiting for host to launch...';
        }
    }

    function updateLobbyPlayers(names, count, hostName) {
        document.getElementById('waitingPlayerCount').textContent = count;
        const grid = document.getElementById('playerSlotsGrid');
        grid.innerHTML = '';

        // Show actual player count / 4 max
        const displayCount = Math.min(names.length, 4);
        for (let i = 0; i < 4; i++) {
            const card = document.createElement('div');
            card.className = 'player-badge-card';

            if (i < displayCount) {
                const pName = names[i];
                const isYou = pName.toLowerCase() === myPlayerName.toLowerCase();
                const isHostP = pName.toLowerCase() === hostName.toLowerCase();
                if (isYou) card.classList.add('is-you');

                card.innerHTML = `
                    <div class="badge-avatar">${escapeHtml(pName.charAt(0).toUpperCase())}</div>
                    <div class="badge-info">
                        <span class="badge-name">${escapeHtml(pName)} ${isYou ? '(You)' : ''}</span>
                        <span class="badge-status">${isHostP ? '👑 Host' : '🟢 Ready'}</span>
                    </div>
                `;
            } else {
                card.innerHTML = `
                    <div class="badge-avatar" style="background:#2D284E;">?</div>
                    <div class="badge-info">
                        <span class="badge-name" style="color:var(--text-dim);">Empty Seat</span>
                        <span class="badge-status" style="color:var(--text-dim);">Waiting...</span>
                    </div>
                `;
            }
            grid.appendChild(card);
        }

        // Update session code display
        const sessionCodeEl = document.getElementById('waitingSessionCode');
        if (sessionCodeEl) {
            sessionCodeEl.textContent = currentSessionId || '----';
        }
    }

    function updateTimers(secondsLeft) {
        const screensList = [
            { digits: 'r1TimerText', bar: 'r1TimerBar' },
            { digits: 'r2TimerText', bar: 'r2TimerBar' },
            { digits: 'r3TimerText', bar: 'r3TimerBar' }
        ];

        screensList.forEach(item => {
            const d = document.getElementById(item.digits);
            const b = document.getElementById(item.bar);
            if (d && b) {
                d.textContent = `${secondsLeft}s`;
                const pct = Math.max(0, Math.min(100, (secondsLeft / currentRoundMaxTime) * 100));
                b.style.width = `${pct}%`;
            }
        });
    }

    function renderLeaderboard(summary) {
        // format: "Alice=2400,Bob=1900,Nova_AI=1500"
        const pairs = summary.split(',').map(s => {
            const p = s.split('=');
            return { name: p[0], score: parseInt(p[1], 10) || 0 };
        }).sort((a, b) => b.score - a.score);

        if (pairs.length > 0) {
            document.getElementById('winnerCrownName').textContent = pairs[0].name.toUpperCase();
        }

        // Render Podium
        const podium = document.getElementById('podiumGrid');
        podium.innerHTML = '';

        const podiumOrder = [
            { idx: 1, class: 'second-place', rank: '2' },
            { idx: 0, class: 'first-place', rank: '1' },
            { idx: 2, class: 'third-place', rank: '3' }
        ];

        podiumOrder.forEach(slot => {
            if (slot.idx < pairs.length) {
                const p = pairs[slot.idx];
                const ped = document.createElement('div');
                ped.className = `podium-pedestal ${slot.class}`;
                ped.innerHTML = `
                    <div class="podium-avatar" style="background:var(--accent-purple)">${p.name.charAt(0)}</div>
                    <div class="podium-name">${escapeHtml(p.name)}</div>
                    <div class="podium-score">${p.score.toLocaleString()} pts</div>
                    <div class="podium-bar">${slot.rank}</div>
                `;
                podium.appendChild(ped);
            }
        });

        // Match Rankings
        const list = document.getElementById('matchRankingsList');
        list.innerHTML = '';
        pairs.forEach((p, idx) => {
            const row = document.createElement('div');
            row.className = 'rank-row';
            row.innerHTML = `
                <span>#${idx + 1} ${escapeHtml(p.name)}</span>
                <span class="accent-text">${p.score.toLocaleString()} pts</span>
            `;
            list.appendChild(row);
        });
    }

    // Biometric Lie-Detector Tracker for Round 2
    function resetBiometrics() {
        typingStarted = false;
        typingStartTime = 0;
        backspaceCount = 0;
        document.getElementById('r2HesitationVal').textContent = '0.0s';
        document.getElementById('r2BackspaceVal').textContent = '0';
        updateRiskUI(35);
        if (biometricTimer) clearInterval(biometricTimer);
    }

    function calculateClientRisk(text, ms, backspaces) {
        if (!text || text.trim().length === 0) return 85;
        let risk = 40;
        if (backspaces === 0) risk -= 18;
        else if (backspaces <= 2) risk += 5;
        else if (backspaces <= 5) risk += 20;
        else risk += 35;

        const len = text.trim().length;
        const msPerChar = len > 0 ? ms / len : 1000;
        if (msPerChar > 800) risk += 18;
        else if (msPerChar < 250 && len > 5) risk -= 10;

        return Math.max(5, Math.min(95, risk));
    }

    function updateRiskUI(risk) {
        const badge = document.getElementById('r2RiskBadge');
        const meter = document.getElementById('r2RiskMeter');
        badge.className = `risk-badge ${risk <= 35 ? 'risk-low' : (risk <= 65 ? 'risk-med' : 'risk-high')}`;
        badge.textContent = `${risk}% ${risk <= 35 ? 'Low' : (risk <= 65 ? 'Med' : 'High')}`;
        meter.style.width = `${risk}%`;
    }

    function escapeHtml(str) {
        return (str || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // Event Listeners Initialization
    window.addEventListener('DOMContentLoaded', () => {
        // First fetch server info to get the LAN IP for WebSocket connection
        fetch('/api/info')
            .then(r => r.ok ? r.json() : null)
            .then(info => {
                if (info && info.localIp && info.localIp !== 'localhost') {
                    serverAddress = info.localIp + ':' + info.webPort;
                } else if (!serverAddress) {
                    serverAddress = location.host;
                }
                loadShareUrl(); // Show LAN share URL on lobby screen
            })
            .catch(() => {
                // Keep default serverAddress
                if (!serverAddress) serverAddress = location.host;
                loadShareUrl();
            });

        // Now connect WebSocket with the correct server address
        connectWebSocket();

        // Check if query string has ?room=ABCD
        const urlParams = new URLSearchParams(window.location.search);
        const prefillRoom = urlParams.get('room');
        if (prefillRoom) {
            document.getElementById('joinRoom').value = prefillRoom.toUpperCase();
            document.getElementById('joinName').focus();
        }

        // Host Button
        document.getElementById('btnHost').onclick = () => {
            getAudio();
            const name = document.getElementById('hostName').value.trim() || 'Host';
            myPlayerName = name;
            sendCommand(`CREATE_SESSION::${name}`);
        };

        // Join Button
        document.getElementById('btnJoin').onclick = () => {
            getAudio();
            const name = document.getElementById('joinName').value.trim() || 'Player';
            const room = document.getElementById('joinRoom').value.trim().toUpperCase();
            if (!room) {
                showToast('Please enter a 4-letter room code', 'error');
                return;
            }
            myPlayerName = name;
            sendCommand(`JOIN_SESSION::${room}::${name}`);
        };

        // Start Game Button
        document.getElementById('btnStartGame').onclick = () => {
            sendCommand('START_GAME');
        };

        // Copy Room Code / Invite Link
        function copyLink() {
            const url = `${_shareUrl.replace(/\/$/, '')}/?room=${encodeURIComponent(currentSessionId)}`;
            navigator.clipboard.writeText(url).then(() => {
                showToast('📋 Copied join link to clipboard!', 'success');
            }).catch(() => {
                prompt('Copy this join link:', url);
            });
        }
        copyRoomBtn.onclick = copyLink;
        document.getElementById('btnCopyInvite').onclick = copyLink;

        // Big Buzzer Button
        document.getElementById('bigBuzzerBtn').onclick = () => {
            playBuzzerSound();
            sendCommand('BUZZ');
        };

        // Round 2 Bluff Input Listeners
        const bluffInput = document.getElementById('bluffInput');
        bluffInput.addEventListener('keydown', (e) => {
            if (!typingStarted) {
                typingStarted = true;
                typingStartTime = Date.now();
                biometricTimer = setInterval(() => {
                    const elapsedSec = ((Date.now() - typingStartTime) / 1000).toFixed(1);
                    document.getElementById('r2HesitationVal').textContent = `${elapsedSec}s`;
                    const curRisk = calculateClientRisk(bluffInput.value, Date.now() - typingStartTime, backspaceCount);
                    updateRiskUI(curRisk);
                }, 200);
            }
            if (e.key === 'Backspace') {
                backspaceCount++;
                document.getElementById('r2BackspaceVal').textContent = backspaceCount;
            }
        });

        document.getElementById('btnSubmitBluff').onclick = () => {
            const text = bluffInput.value.trim();
            if (!text) {
                showToast('Please enter a fake bluff!', 'error');
                return;
            }
            const durationMs = typingStarted ? (Date.now() - typingStartTime) : 2000;
            if (biometricTimer) clearInterval(biometricTimer);
            sendCommand(`SUBMIT_BLUFF::${text}::${durationMs}::${backspaceCount}`);
        };

        // Round 3 Slider
        const slider = document.getElementById('wagerSlider');
        slider.addEventListener('input', () => {
            document.getElementById('wagerAmountDisplay').textContent = slider.value;
        });

        document.getElementById('btnLockWager').onclick = () => {
            const amount = parseInt(slider.value, 10) || 100;
            sendCommand(`SUBMIT_WAGER::${amount}`);
            document.getElementById('btnLockWager').disabled = true;
            document.getElementById('btnLockWager').textContent = `✓ Wager Locked: ${amount} pts`;
            showToast(`Secret wager locked: ${amount} pts`, 'success');
        };

        // Return to Lobby
        document.getElementById('btnReturnLobby').onclick = () => {
            location.reload();
        };
    });
})();
