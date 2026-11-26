package dev.crawlmode.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Floor 1's countdown. Starts at the Apocalypse and only runs while at least one Crawler is online.
 */
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

    /** Back to untouched, as before the Apocalypse; a fresh Crawl starts the cycle over. */
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

    /** Move the timer on to {@code now}; time only counts if a Crawler was online up to this moment. */
    void advance(long now, boolean crawlerOnline) {
        if (running && crawlerOnline) {
            elapsed += now - lastSeen;
        }
        lastSeen = now;
    }

    long remaining() {
        return Math.max(0, totalTicks - elapsed);
    }

    /** Whether the displayed whole second changed since last asked. */
    boolean displayChanged() {
        long seconds = secondsLeft();
        if (!running || seconds == lastShownSecond) {
            return false;
        }
        lastShownSecond = seconds;
        return true;
    }

    /** Topics of warnings whose threshold has just been crossed, each reported once. */
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

    /** How far past zero the timer has run, in ticks (a late tick or a long pause-free gap). */
    long overshoot() {
        return Math.max(0, elapsed - totalTicks);
    }

    boolean expired() {
        return running && remaining() == 0;
    }

    /** Whether the Descent Window (the last ten minutes) is open. */
    boolean windowOpen() {
        return running && remaining() <= DESCENT_WINDOW_TICKS;
    }

    /** Ticks until the Descent Window opens; zero once it has. */
    long untilWindow() {
        return Math.max(0, remaining() - DESCENT_WINDOW_TICKS);
    }

    private long secondsLeft() {
        return (remaining() + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }
}
