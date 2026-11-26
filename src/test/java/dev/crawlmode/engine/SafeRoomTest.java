package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SafeRoomTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DONUT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final DoorPos FLOOR_ONE_DOOR = new DoorPos(1, 10, 64, 20);
    private static final DoorPos OTHER_FLOOR_ONE_DOOR = new DoorPos(1, -30, 64, 5);

    private final FakeClock clock = new FakeClock();

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
    void doorsWorkThroughoutTheFloorTimerIncludingTheDescentWindow() {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000, 0, 30 * 20 * 60));
        engine.handle(new Event.CrawlerJoined(CARL));
        engine.handle(new Event.ApocalypseCommanded());

        for (long minute : new long[] {1, 15, 25, 29}) {
            clock.advance(minute * 20 * 60 - clock.nowTicks());
            engine.tick();
            assertEquals(1, engine.handle(new Event.SafeRoomDoorUsed(CARL, FLOOR_ONE_DOOR)).size(), "minute " + minute);
            engine.handle(new Event.SafeRoomExited(CARL));
        }
    }
}
