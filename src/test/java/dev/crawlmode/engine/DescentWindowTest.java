package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DescentWindowTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DONUT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final HostLineSource TOPIC_ECHO = topic -> java.util.Optional.of(new HostLine(topic, topic));
    private static final long MIN = 20 * 60;
    private static final long TIMER = 30 * MIN;
    /** Floor 2 runs 80 percent of the configured timer (see FloorSpec). */
    private static final long FLOOR_2_TIMER = 24 * MIN;

    private final FakeClock clock = new FakeClock();
    private CrawlEngine engine;

    private void start(UUID... crawlers) {
        engine = new CrawlEngine(clock, new CrawlConfig(72_000, 0, TIMER), TOPIC_ECHO);
        for (UUID c : crawlers) {
            engine.handle(new Event.CrawlerJoined(c));
        }
        engine.handle(new Event.ApocalypseCommanded());
    }

    /** Move the clock to an absolute tick (the Apocalypse is at tick 0) and tick the engine once. */
    private List<Effect> tickTo(long tick) {
        clock.advance(tick - clock.nowTicks());
        return engine.tick();
    }

    @Test
    void stairwellRejectsUseBeforeTheWindowAndSaysWhenItOpens() {
        start(CARL);
        tickTo(5 * MIN);
        // timer 30m, window opens with 10m left, i.e. at 20m; 15m to go
        assertEquals(
                List.of(new Effect.StairwellSealed(CARL, 15 * MIN)),
                engine.handle(new Event.StairwellUsed(CARL)));
    }

    @Test
    void hostAnnouncesTheWindowOnceWhenItOpens() {
        start(CARL);
        assertFalse(topics(tickTo(20 * MIN - 1)).contains("descent-window"));
        assertTrue(topics(tickTo(20 * MIN)).contains("descent-window"));
        assertFalse(topics(tickTo(21 * MIN)).contains("descent-window"));
    }

    @Test
    void descendingInTheWindowMovesTheCrawlerAndTheyLiveThroughTheCollapse() {
        start(CARL, DONUT);
        tickTo(25 * MIN);
        assertEquals(List.of(new Effect.PullToFloor(CARL, 2)), engine.handle(new Event.StairwellUsed(CARL)));
        List<Effect> collapse = tickTo(TIMER);
        assertTrue(collapse.contains(new Effect.Collapse(DONUT, 1)));
        assertFalse(collapse.contains(new Effect.Collapse(CARL, 1)));
    }

    @Test
    void nextFloorsTimerStartsAtTheCollapseNotOnArrival() {
        start(CARL);
        tickTo(25 * MIN);
        engine.handle(new Event.StairwellUsed(CARL));
        tickTo(TIMER);
        assertEquals(FLOOR_2_TIMER - 100, lastShown(tickTo(TIMER + 100), CARL));
    }

    @Test
    void floorTwoCollapsesAtItsOwnShorterTimer() {
        start(CARL);
        tickTo(25 * MIN);
        engine.handle(new Event.StairwellUsed(CARL));
        tickTo(TIMER);
        tickTo(TIMER + FLOOR_2_TIMER - 1);
        assertTrue(tickTo(TIMER + FLOOR_2_TIMER).contains(new Effect.Collapse(CARL, 2)));
    }

    @Test
    void timerDoesNotShowOnTheNextFloorBeforeTheCollapse() {
        start(CARL);
        tickTo(25 * MIN);
        engine.handle(new Event.StairwellUsed(CARL));
        assertTrue(tickTo(26 * MIN).stream().noneMatch(e -> e instanceof Effect.ShowTimer));
    }

    @Test
    void theNextFloorHasItsOwnSealedWindow() {
        start(CARL);
        tickTo(25 * MIN);
        engine.handle(new Event.StairwellUsed(CARL));
        tickTo(TIMER + 100);
        assertEquals(
                List.of(new Effect.StairwellSealed(CARL, FLOOR_2_TIMER - 10 * MIN - 100)),
                engine.handle(new Event.StairwellUsed(CARL)));
    }

    @Test
    void aCollapseKillsEachCrawlerOnlyOnce() {
        start(CARL);
        tickTo(TIMER);
        assertTrue(tickTo(TIMER + 100).stream().noneMatch(e -> e instanceof Effect.Collapse));
    }

    private static long lastShown(List<Effect> effects, UUID who) {
        long shown = -1;
        for (Effect e : effects) {
            if (e instanceof Effect.ShowTimer t && t.crawler().equals(who)) {
                shown = t.remainingTicks();
            }
        }
        return shown;
    }

    private static List<String> topics(List<Effect> effects) {
        return effects.stream()
                .filter(e -> e instanceof Effect.HostSays)
                .map(e -> ((Effect.HostSays) e).line().topic())
                .toList();
    }
}
