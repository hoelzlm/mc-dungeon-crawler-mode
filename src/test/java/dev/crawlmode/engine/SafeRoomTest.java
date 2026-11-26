package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SafeRoomTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DONUT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final DoorPos FLOOR_ONE_DOOR = new DoorPos(1, 10, 64, 20);
    private static final DoorPos OTHER_FLOOR_ONE_DOOR = new DoorPos(1, -30, 64, 5);

    private static final long MIN = 20 * 60;
    private static final long TIMER = 30 * MIN;

    private final FakeClock clock = new FakeClock();

    private CrawlEngine timedCrawl(UUID... crawlers) {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000, 0, TIMER));
        for (UUID c : crawlers) {
            engine.handle(new Event.CrawlerJoined(c));
        }
        engine.handle(new Event.ApocalypseCommanded());
        return engine;
    }

    private List<Effect> tickTo(CrawlEngine engine, long tick) {
        clock.advance(tick - clock.nowTicks());
        return engine.tick();
    }

    private CrawlEngine crawl(UUID... crawlers) {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000));
        for (UUID c : crawlers) {
            engine.handle(new Event.CrawlerJoined(c));
        }
        engine.handle(new Event.ApocalypseCommanded());
        return engine;
    }

    @Test
    void firstEntryCreatesTheRoomAndLaterEntriesReuseIt() {
        CrawlEngine engine = crawl(CARL);

        assertEquals(List.of(new Effect.EnterSafeRoom(CARL, true)),
                engine.handle(new Event.SafeRoomDoorUsed(CARL, FLOOR_ONE_DOOR)));
        engine.handle(new Event.SafeRoomExited(CARL));

        assertEquals(List.of(new Effect.EnterSafeRoom(CARL, false)),
                engine.handle(new Event.SafeRoomDoorUsed(CARL, FLOOR_ONE_DOOR)));
    }

    @Test
    void leavingReturnsTheCrawlerToTheDoorTheyEnteredFrom() {
        CrawlEngine engine = crawl(CARL);
        engine.handle(new Event.SafeRoomDoorUsed(CARL, OTHER_FLOOR_ONE_DOOR));

        assertEquals(List.of(new Effect.ExitSafeRoom(CARL, OTHER_FLOOR_ONE_DOOR)),
                engine.handle(new Event.SafeRoomExited(CARL)));
    }

    @Test
    void leavingWhenNotInsideDoesNothing() {
        CrawlEngine engine = crawl(CARL);

        assertEquals(List.of(), engine.handle(new Event.SafeRoomExited(CARL)));
    }

    @Test
    void usingADoorWhileAlreadyInsideDoesNothing() {
        CrawlEngine engine = crawl(CARL);
        engine.handle(new Event.SafeRoomDoorUsed(CARL, FLOOR_ONE_DOOR));

        assertEquals(List.of(), engine.handle(new Event.SafeRoomDoorUsed(CARL, OTHER_FLOOR_ONE_DOOR)));
    }

    @Test
    void entryAndExitHaveNoCooldown() {
        CrawlEngine engine = crawl(CARL);

        for (int i = 0; i < 3; i++) {
            assertEquals(1, engine.handle(new Event.SafeRoomDoorUsed(CARL, FLOOR_ONE_DOOR)).size());
            assertEquals(1, engine.handle(new Event.SafeRoomExited(CARL)).size());
        }
    }

    @Test
    void eachCrawlerHasTheirOwnRoomAndOrigin() {
        CrawlEngine engine = crawl(CARL, DONUT);

        assertEquals(List.of(new Effect.EnterSafeRoom(CARL, true)),
                engine.handle(new Event.SafeRoomDoorUsed(CARL, FLOOR_ONE_DOOR)));
        assertEquals(List.of(), engine.handle(new Event.SafeRoomExited(DONUT)));
        assertEquals(List.of(new Effect.EnterSafeRoom(DONUT, true)),
                engine.handle(new Event.SafeRoomDoorUsed(DONUT, OTHER_FLOOR_ONE_DOOR)));
        assertEquals(List.of(new Effect.ExitSafeRoom(CARL, FLOOR_ONE_DOOR)),
                engine.handle(new Event.SafeRoomExited(CARL)));
    }

    @Test
    void doorsDoNothingBeforeTheApocalypse() {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000));
        engine.handle(new Event.CrawlerJoined(CARL));

        assertEquals(List.of(), engine.handle(new Event.SafeRoomDoorUsed(CARL, FLOOR_ONE_DOOR)));
    }

    @Test
    void eliminatedCrawlersCannotUseDoors() {
        CrawlEngine engine = crawl(CARL, DONUT);
        engine.handle(new Event.CrawlerDied(CARL));

        assertEquals(List.of(), engine.handle(new Event.SafeRoomDoorUsed(CARL, FLOOR_ONE_DOOR)));
    }

    @Test
    void aCrawlerInsideAtCollapseIsEliminatedLikeAnyoneLeftOnTheFloor() {
        CrawlEngine engine = timedCrawl(CARL);
        engine.handle(new Event.SafeRoomDoorUsed(CARL, FLOOR_ONE_DOOR));

        List<Effect> collapse = tickTo(engine, TIMER);

        assertTrue(collapse.contains(new Effect.Collapse(CARL, 1)));
        assertTrue(collapse.contains(new Effect.Spectate(CARL)));
        assertTrue(engine.isEliminated(CARL));
    }

    @Test
    void timeSpentInsideDoesNotPauseTheFloorTimer() {
        CrawlEngine engine = timedCrawl(CARL, DONUT);
        engine.handle(new Event.SafeRoomDoorUsed(CARL, FLOOR_ONE_DOOR));

        assertFalse(tickTo(engine, TIMER - 1).contains(new Effect.Collapse(DONUT, 1)));
        assertTrue(tickTo(engine, TIMER).contains(new Effect.Collapse(DONUT, 1)));
    }

    @Test
    void leavingAndDescendingBeforeTheCollapseSurvives() {
        CrawlEngine engine = timedCrawl(CARL);
        engine.handle(new Event.SafeRoomDoorUsed(CARL, FLOOR_ONE_DOOR));
        tickTo(engine, 25 * MIN);
        engine.handle(new Event.SafeRoomExited(CARL));

        assertEquals(List.of(new Effect.PullToFloor(CARL, 2)), engine.handle(new Event.StairwellUsed(CARL)));
        assertFalse(tickTo(engine, TIMER).contains(new Effect.Collapse(CARL, 1)));
        assertFalse(engine.isEliminated(CARL));
    }

    @Test
    void anEliminatedCrawlerHasNoDoorOriginLeftToExitThrough() {
        CrawlEngine engine = timedCrawl(CARL);
        engine.handle(new Event.SafeRoomDoorUsed(CARL, FLOOR_ONE_DOOR));
        tickTo(engine, TIMER);

        assertEquals(List.of(), engine.handle(new Event.SafeRoomExited(CARL)));
    }
}
