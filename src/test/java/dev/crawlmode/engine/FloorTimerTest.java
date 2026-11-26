package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FloorTimerTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final HostLineSource TOPIC_ECHO = topic -> java.util.Optional.of(new HostLine(topic, topic));
    private static final long MIN = 20 * 60;

    private final FakeClock clock = new FakeClock();

    private CrawlEngine started(long timerTicks) {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000, 0, timerTicks), TOPIC_ECHO);
        engine.handle(new Event.CrawlerJoined(CARL));
        engine.handle(new Event.ApocalypseCommanded());
        return engine;
    }

    private long lastShown(List<Effect> effects) {
        long shown = -1;
        for (Effect e : effects) {
            if (e instanceof Effect.ShowTimer t && t.crawler().equals(CARL)) {
                shown = t.remainingTicks();
            }
        }
        return shown;
    }

    @Test
    void defaultFloorTimerIsNinetyMinutes() {
        assertEquals(90 * MIN, CrawlConfig.DEFAULT.floorTimerTicks());
        assertEquals(90 * MIN, new CrawlConfig(1).floorTimerTicks());
    }

    @Test
    void timerShowsRemainingTimeCountingDownFromTheApocalypse() {
        CrawlEngine engine = started(90 * MIN);
        clock.advance(100);
        assertEquals(90 * MIN - 100, lastShown(engine.tick()));
    }

    @Test
    void timerDoesNotShowBeforeTheApocalypse() {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000, 0, 90 * MIN), TOPIC_ECHO);
        engine.handle(new Event.CrawlerJoined(CARL));
        clock.advance(100);
        assertEquals(-1, lastShown(engine.tick()));
    }

    @Test
    void timerPausesWhileNobodyIsOnline() {
        CrawlEngine engine = started(1000);
        clock.advance(100);
        engine.tick();
        engine.handle(new Event.CrawlerLeft(CARL));
        clock.advance(5000);
        engine.tick();
        engine.handle(new Event.CrawlerJoined(CARL));
        clock.advance(50);
        assertEquals(1000 - 150, lastShown(engine.tick()));
    }

    @Test
    void configuredLengthIsUsed() {
        CrawlEngine engine = started(5 * MIN);
        clock.advance(20);
        assertEquals(5 * MIN - 20, lastShown(engine.tick()));
    }

    @Test
    void hostWarnsAtTenMinutesOneMinuteAndTenSecondsExactlyOnce() {
        CrawlEngine engine = started(90 * MIN);
        List<String> topics = new ArrayList<>();
        for (long t = 20; t <= 90 * MIN; t += 20) {
            clock.advance(20);
            for (Effect e : engine.tick()) {
                if (e instanceof Effect.HostSays s && !s.line().topic().equals("descent-window")
                        && !s.line().topic().equals("elimination")) {
                    topics.add(s.line().topic());
                    long remaining = 90 * MIN - t;
                    long expected = switch (s.line().topic()) {
                        case "timer-10m" -> 10 * MIN;
                        case "timer-1m" -> MIN;
                        case "timer-10s" -> 200;
                        default -> -1;
                    };
                    assertEquals(expected, remaining);
                }
            }
        }
        assertEquals(List.of("timer-10m", "timer-1m", "timer-10s"), topics);
    }
}
