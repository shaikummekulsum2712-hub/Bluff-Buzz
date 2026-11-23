package com.fibbage.concurrency;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Block 3 — Threading & Synchronization
 * RoundTimer manages countdowns using ScheduledExecutorService.
 * Calls a listener each second and a completion handler when time runs out.
 *
 * Safety: every start() gets a new "generation" number. A countdown that was
 * stopped or replaced notices its number is old and quietly does nothing, so an
 * old timer can never end a newer one.
 */
public class RoundTimer {
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> timerHandle;
    private int generation = 0;
    private int remainingSeconds = 0;

    public RoundTimer() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RoundTimer-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts a countdown from initialSeconds.
     *
     * @param initialSeconds total countdown duration
     * @param onTickCallback called every 1 second with remaining seconds
     * @param onCompleteCallback called when time reaches 0
     */
    public synchronized void start(int initialSeconds, Consumer<Integer> onTickCallback, Runnable onCompleteCallback) {
        stop(); // cancels any running countdown

        final int myGeneration = generation;
        remainingSeconds = initialSeconds;
        if (onTickCallback != null) {
            onTickCallback.accept(initialSeconds);
        }

        timerHandle = scheduler.scheduleAtFixedRate(() -> {
            int current;
            synchronized (RoundTimer.this) {
                if (myGeneration != generation) return; // old timer: ignore
                remainingSeconds--;
                current = remainingSeconds;
                if (current <= 0) {
                    stop(); // this countdown is finished
                }
            }
            try {
                if (current > 0) {
                    if (onTickCallback != null) onTickCallback.accept(current);
                } else {
                    if (onTickCallback != null) onTickCallback.accept(0);
                    if (onCompleteCallback != null) onCompleteCallback.run();
                }
            } catch (Exception e) {
                e.printStackTrace(); // an error must never silently kill the timer
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Stops the running timer immediately.
     */
    public synchronized void stop() {
        generation++;
        if (timerHandle != null) {
            timerHandle.cancel(false);
            timerHandle = null;
        }
    }

    public synchronized int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void shutdown() {
        stop();
        scheduler.shutdownNow();
    }
}
