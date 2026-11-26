package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WinningTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DONUT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final HostLineSource TOPIC_ECHO = topic -> java.util.Optional.of(new HostLine(topic, topic));
    private static final long MIN = 20 * 60;
    private static final long TIMER = 30 * MIN;
    private static final long FLOOR_3_START = 2 * TIMER;

    private final FakeClock clock = new FakeClock();
    private CrawlEngine engine;

    private void start(UUID... crawlers) {
        engine = new CrawlEngine(clock, new CrawlConfig(72_000, 0, TIMER), TOPIC_ECHO);
        for (UUID c : crawlers) {
            engine.handle(new Event.CrawlerJoined(c));
        }
        engine.handle(new Event.ApocalypseCommanded());
    }

    private List<Effect> tickTo(long tick) {
        clock.advance(tick - clock.nowTicks());
        return engine.tick();
    }

    private void everyoneToFloor3(UUID... crawlers) {
        for (long floorStart : new long[] {0, TIMER}) {
            tickTo(floorStart + 22 * MIN);
            for (UUID c : crawlers) {
                engine.handle(new Event.StairwellUsed(c));
            }
        }
        tickTo(FLOOR_3_START + 25 * MIN);
    }

    @Test
    void passingTheLastStairwellShowsTheWinScreenAndReturnsTheCrawlerToVanilla() {
        start(CARL);
        everyoneToFloor3(CARL);
        assertEquals(
                List.of(new Effect.HostSays(CARL, new HostLine("win", "win")),
                        new Effect.ShowWinScreen(CARL),
                        new Effect.RestoreWorld(CARL)),
                engine.handle(new Event.StairwellUsed(CARL)));
    }

    @Test
    void theLastStairwellIsStillSealedOutsideTheWindow() {
        start(CARL);
        everyoneToFloor3(CARL);
        tickTo(FLOOR_3_START + 5 * MIN);
        assertTrue(engine.handle(new Event.StairwellUsed(CARL)).get(0) instanceof Effect.StairwellSealed);
    }

    @Test
    void aWinnerIsNotKilledByTheLaterCollapseOfFloor3() {
        start(CARL, DONUT);
        everyoneToFloor3(CARL, DONUT);
        engine.handle(new Event.StairwellUsed(CARL));
        List<Effect> collapse = tickTo(FLOOR_3_START + TIMER);
        assertFalse(collapse.contains(new Effect.Collapse(CARL, 3)));
        assertTrue(collapse.contains(new Effect.Collapse(DONUT, 3)));
    }

    @Test
    void aWinnerWhoRejoinsIsNotPulledBackIntoTheCrawl() {
        start(CARL);
        everyoneToFloor3(CARL);
        engine.handle(new Event.StairwellUsed(CARL));
        engine.handle(new Event.CrawlerLeft(CARL));
        assertEquals(List.of(), engine.handle(new Event.CrawlerJoined(CARL)));
    }

    @Test
    void aWinnerDyingLaterIsNotEliminated() {
        start(CARL);
        everyoneToFloor3(CARL);
        engine.handle(new Event.StairwellUsed(CARL));
        assertEquals(List.of(), engine.handle(new Event.CrawlerDied(CARL)));
        assertFalse(engine.isEliminated(CARL));
    }
}
