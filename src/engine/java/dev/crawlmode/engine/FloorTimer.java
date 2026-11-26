package dev.crawlmode.engine;

import java.util.ArrayList;
import java.util.List;

final class FloorTimer {
    private static final long TICKS_PER_SECOND = 20;
    private static final long[] WARNING_TICKS = {10 * 60 * TICKS_PER_SECOND, 60 * TICKS_PER_SECOND, 10 * TICKS_PER_SECOND};
    private static final String[] WARNING_TOPICS = {"timer-10m", "timer-1m", "timer-10s"};

    private final long totalTicks;
    private boolean running;
    private long elapsed;
    private long lastSeen;
    private long lastShownSecond;
    private int warningsGiven;

    FloorTimer(long totalTicks) {
        this.totalTicks = totalTicks;
    }

    void reset() {
        running = false;
        elapsed = 0;
        lastSeen = 0;
        lastShownSecond = 0;
        warningsGiven = 0;
    }

    void start(long now) {
        if (!running) {
            running = true;
            lastSeen = now;
            lastShownSecond = secondsLeft();
            while (warningsGiven < WARNING_TICKS.length && WARNING_TICKS[warningsGiven] >= totalTicks) {
                warningsGiven++;
            }
        }
    }

    void advance(long now, boolean crawlerOnline) {
        if (running && crawlerOnline) {
            elapsed += now - lastSeen;
        }
        lastSeen = now;
    }

    long remaining() {
        return Math.max(0, totalTicks - elapsed);
    }

    boolean displayChanged() {
        long seconds = secondsLeft();
        if (!running || seconds == lastShownSecond) {
            return false;
        }
        lastShownSecond = seconds;
        return true;
    }

    List<String> newWarnings() {
        List<String> topics = new ArrayList<>();
        if (!running) {
            return topics;
        }
        while (warningsGiven < WARNING_TICKS.length && remaining() <= WARNING_TICKS[warningsGiven]) {
            topics.add(WARNING_TOPICS[warningsGiven++]);
        }
        return topics;
    }

    static final long DESCENT_WINDOW_TICKS = 10 * 60 * TICKS_PER_SECOND;

    long overshoot() {
        return Math.max(0, elapsed - totalTicks);
    }

    boolean expired() {
        return running && remaining() == 0;
    }

    boolean windowOpen() {
        return running && remaining() <= DESCENT_WINDOW_TICKS;
    }

    long untilWindow() {
        return Math.max(0, remaining() - DESCENT_WINDOW_TICKS);
    }

    private long secondsLeft() {
        return (remaining() + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }
}
