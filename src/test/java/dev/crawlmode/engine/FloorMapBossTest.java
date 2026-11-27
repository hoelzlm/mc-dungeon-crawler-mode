package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.crawlmode.engine.map.FloorMapConfig;
import dev.crawlmode.engine.map.MapCell;
import dev.crawlmode.engine.map.MapRect;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FloorMapBossTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DONUT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final FakeClock clock = new FakeClock();

    private CrawlEngine crawl(FloorMapConfig config, DoorPos... doors) {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000)).withFloorMap(config);
        engine.handle(new Event.SafeRoomDoorsPlanned(1, List.of(doors)));
        engine.handle(new Event.CrawlerJoined(CARL));
        engine.handle(new Event.CrawlerJoined(DONUT));
        engine.handle(new Event.ApocalypseCommanded());
        return engine;
    }

    private static List<Effect> doorEffects(List<Effect> effects) {
        return effects.stream().filter(e -> e instanceof Effect.ShowMapDoors).toList();
    }

    @Test
    void aBossKillRevealsTheWholeAreaItDiedIn() {
        CrawlEngine engine = crawl(new FloorMapConfig(2, 1, 1));

        engine.handle(new Event.BossKilled(CARL, 0, 0, List.of(new MapRect(100, 100, 115, 103))));

        assertTrue(engine.revealedCells(CARL).containsAll(List.of(
                new MapCell(100, 100), new MapCell(115, 103), new MapCell(107, 101))));
        assertFalse(engine.revealedCells(CARL).contains(new MapCell(116, 100)));
    }

    @Test
    void aBossKillRevealsAtLeastTheConfiguredDiscAroundTheBoss() {
        CrawlEngine engine = crawl(new FloorMapConfig(2, 1, 5));

        engine.handle(new Event.BossKilled(CARL, 0, 0, List.of()));

        assertTrue(engine.revealedCells(CARL).containsAll(List.of(
                new MapCell(5, 0), new MapCell(-5, 0), new MapCell(0, 5), new MapCell(0, -5))));
        assertFalse(engine.revealedCells(CARL).contains(new MapCell(4, 4)));
        assertFalse(engine.revealedCells(CARL).contains(new MapCell(6, 0)));
    }

    @Test
    void theDefaultBossRevealRadiusIs64() {
        assertEquals(64, FloorMapConfig.DEFAULT.bossRevealRadius());
        assertEquals(64, new FloorMapConfig(24).bossRevealRadius());
    }

    @Test
    void aNonPositiveBossRevealRadiusIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FloorMapConfig(24, 1, 0));
    }

    @Test
    void theBossRevealIsSentOnlyToTheKillerAndOnlyOnce() {
        CrawlEngine engine = crawl(new FloorMapConfig(2, 1, 1));
        Event kill = new Event.BossKilled(CARL, 0, 0, List.of(new MapRect(10, 10, 11, 10)));

        List<Effect> effects = engine.handle(kill);

        assertFalse(effects.isEmpty());
        assertTrue(effects.stream().allMatch(e -> e instanceof Effect.RevealMapCells r && r.crawler().equals(CARL)));
        assertEquals(Set.of(), engine.revealedCells(DONUT));
        assertEquals(List.of(), engine.handle(kill));
    }

    @Test
    void aDoorInTheBossRevealedAreaAppearsOnTheMap() {
        CrawlEngine engine = crawl(new FloorMapConfig(2, 1, 1), new DoorPos(1, 105, 70, 101, DoorFacing.NORTH));

        assertEquals(List.of(new Effect.ShowMapDoors(CARL, 1, List.of(new MapCell(105, 101)))),
                doorEffects(engine.handle(new Event.BossKilled(CARL, 0, 0, List.of(new MapRect(100, 100, 115, 103))))));
    }

    @Test
    void aDoorOutsideTheBossRevealedAreaStaysHidden() {
        CrawlEngine engine = crawl(new FloorMapConfig(2, 1, 1), new DoorPos(1, 200, 70, 200, DoorFacing.NORTH));

        assertEquals(List.of(),
                doorEffects(engine.handle(new Event.BossKilled(CARL, 0, 0, List.of(new MapRect(100, 100, 115, 103))))));
    }

    @Test
    void aCrawlerNotOnAFloorGetsNoBossReveal() {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000)).withFloorMap(new FloorMapConfig(2, 1, 1));
        engine.handle(new Event.CrawlerJoined(CARL));

        assertEquals(List.of(), engine.handle(new Event.BossKilled(CARL, 0, 0, List.of())));
    }

    @Test
    void anEliminatedCrawlerGetsNoBossReveal() {
        CrawlEngine engine = crawl(new FloorMapConfig(2, 1, 1));
        engine.handle(new Event.CrawlerDied(CARL));

        assertEquals(List.of(), engine.handle(new Event.BossKilled(CARL, 0, 0, List.of(new MapRect(1, 1, 5, 5)))));
        assertEquals(Set.of(), engine.revealedCells(CARL));
    }

    @Test
    void aCrawlerInTheSafeRoomGetsNoBossReveal() {
        CrawlEngine engine = crawl(new FloorMapConfig(2, 1, 1));
        engine.handle(new Event.SafeRoomDoorUsed(CARL, new DoorPos(1, 10, 64, 20, DoorFacing.NORTH)));

        assertEquals(List.of(), engine.handle(new Event.BossKilled(CARL, 0, 0, List.of(new MapRect(1, 1, 5, 5)))));
        assertEquals(Set.of(), engine.revealedCells(CARL));
    }

    @Test
    void aDoorRevealedByABossKillIsShownAgainWhenTheMapIsReShown() {
        CrawlEngine engine = crawl(new FloorMapConfig(2, 1, 1), new DoorPos(1, 105, 70, 101, DoorFacing.NORTH));
        engine.handle(new Event.BossKilled(CARL, 0, 0, List.of(new MapRect(100, 100, 115, 103))));
        engine.handle(new Event.SafeRoomDoorUsed(CARL, new DoorPos(1, 10, 64, 20, DoorFacing.NORTH)));

        assertTrue(engine.handle(new Event.SafeRoomExited(CARL))
                .contains(new Effect.ShowMapDoors(CARL, 1, List.of(new MapCell(105, 101)))));
    }
}
