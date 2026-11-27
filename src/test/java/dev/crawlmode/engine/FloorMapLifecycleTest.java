package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.crawlmode.engine.map.FloorMapConfig;
import dev.crawlmode.engine.map.MapCell;
import dev.crawlmode.engine.map.MapSpan;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FloorMapLifecycleTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final DoorPos DOOR = new DoorPos(1, 10, 64, 20, DoorFacing.NORTH);
    private static final long MIN = 20 * 60;
    private static final long TIMER = 30 * MIN;
    private static final EliminationConfig SINGLE_PLAYER =
            new EliminationConfig(EliminationConfig.Mode.PERMANENT, true);
    private static final Effect.ShowFloorMap FLOOR_ONE_MAP = new Effect.ShowFloorMap(CARL, 1,
            List.of(new MapSpan(-1, 0, 0), new MapSpan(0, -1, 1), new MapSpan(1, 0, 0)));

    private final FakeClock clock = new FakeClock();

    private CrawlEngine crawl() {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000, 0, TIMER), HostLineSource.SILENT,
                SINGLE_PLAYER).withFloorMap(new FloorMapConfig(1));
        engine.handle(new Event.CrawlerJoined(CARL));
        engine.handle(new Event.ApocalypseCommanded());
        engine.handle(new Event.CrawlerMoved(CARL, 0, 0));
        return engine;
    }

    private List<Effect> tickTo(CrawlEngine engine, long tick) {
        clock.advance(tick - clock.nowTicks());
        return engine.tick();
    }

    @Test
    void rejoiningKeepsTheMapAndResendsItToTheClient() {
        CrawlEngine engine = crawl();
        engine.handle(new Event.CrawlerLeft(CARL));

        List<Effect> effects = engine.handle(new Event.CrawlerJoined(CARL));

        assertTrue(effects.contains(FLOOR_ONE_MAP));
        assertEquals(5, engine.revealedCells(CARL).size());
    }

    @Test
    void enteringTheSafeRoomHidesTheOverlayButKeepsTheMap() {
        CrawlEngine engine = crawl();

        List<Effect> effects = engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR));

        assertTrue(effects.contains(new Effect.HideFloorMap(CARL)));
        assertEquals(5, engine.revealedCells(CARL).size());
    }

    @Test
    void leavingTheSafeRoomShowsTheSameMapAgain() {
        CrawlEngine engine = crawl();
        engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR));

        assertTrue(engine.handle(new Event.SafeRoomExited(CARL)).contains(FLOOR_ONE_MAP));
    }

    @Test
    void walkingInsideTheSafeRoomRevealsNothingOnTheFloor() {
        CrawlEngine engine = crawl();
        engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR));

        assertEquals(List.of(), engine.handle(new Event.CrawlerMoved(CARL, 50, 50)));
    }

    @Test
    void descendingStartsAnEmptyMapAndHidesTheOldOne() {
        CrawlEngine engine = crawl();
        tickTo(engine, 25 * MIN);

        List<Effect> effects = engine.handle(new Event.StairwellUsed(CARL));

        assertTrue(effects.contains(new Effect.HideFloorMap(CARL)));
        assertEquals(Set.of(), engine.revealedCells(CARL));
    }

    @Test
    void aCollapsedFloorDeletesTheMapOfACrawlerWhoIsOffline() {
        CrawlEngine engine = crawl();
        engine.handle(new Event.CrawlerLeft(CARL));
        engine.handle(new Event.CrawlerJoined(UUID.fromString("00000000-0000-0000-0000-000000000002")));

        tickTo(engine, TIMER);

        assertEquals(Set.of(), engine.revealedCells(CARL));
    }

    @Test
    void aFreshCrawlStartsWithNoMapsAndHidesTheOverlay() {
        CrawlEngine engine = crawl();
        engine.handle(new Event.CrawlerDied(CARL));

        List<Effect> effects = engine.handle(new Event.FreshCrawlChosen(CARL));

        assertEquals(Set.<MapCell>of(), engine.revealedCells(CARL));
        assertFalse(effects.isEmpty());
    }

    @Test
    void anEliminatedCrawlerLosesTheOverlayAndTheMap() {
        CrawlEngine engine = crawl();

        List<Effect> effects = engine.handle(new Event.CrawlerDied(CARL));

        assertTrue(effects.contains(new Effect.HideFloorMap(CARL)));
        assertEquals(Set.of(), engine.revealedCells(CARL));
    }

    @Test
    void anEliminatedCrawlerWhoRejoinsGetsNoMap() {
        CrawlEngine engine = crawl();
        engine.handle(new Event.CrawlerDied(CARL));
        engine.handle(new Event.CrawlerLeft(CARL));

        assertEquals(List.of(new Effect.Spectate(CARL)), engine.handle(new Event.CrawlerJoined(CARL)));
    }

    @Test
    void theFloorTimerKeepsRunningWhileTheOverlayIsHidden() {
        CrawlEngine engine = crawl();
        engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR));

        assertTrue(tickTo(engine, TIMER).contains(new Effect.Collapse(CARL, 1)));
    }
}
