package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.crawlmode.engine.map.DotKind;
import dev.crawlmode.engine.map.FloorMapConfig;
import dev.crawlmode.engine.map.MapDot;
import dev.crawlmode.engine.map.Sighting;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MapDotsTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final FakeClock clock = new FakeClock();

    private CrawlEngine crawl(int revealRadius, int dotRadius) {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000))
                .withFloorMap(new FloorMapConfig(revealRadius, dotRadius));
        engine.handle(new Event.CrawlerJoined(CARL));
        engine.handle(new Event.ApocalypseCommanded());
        engine.handle(new Event.CrawlerMoved(CARL, 0, 0));
        return engine;
    }

    private static List<Effect> scan(CrawlEngine engine, Sighting... sightings) {
        return engine.handle(new Event.EntitiesScanned(CARL, 0, 0, List.of(sightings)));
    }

    private static List<Effect> dots(DotKind kind, int x, int z) {
        return List.of(new Effect.UpdateMapDots(CARL, 1, List.of(new MapDot(kind, x, z))));
    }

    @Test
    void aMonsterInRangeInARevealedCellShowsRed() {
        assertEquals(dots(DotKind.MONSTER, 3, 0), scan(crawl(10, 10), new Sighting(DotKind.MONSTER, 3, 0)));
    }

    @Test
    void aMonsterInRangeInAnUnrevealedCellShowsNothing() {
        assertEquals(List.of(), scan(crawl(2, 10), new Sighting(DotKind.MONSTER, 5, 0)));
    }

    @Test
    void aMonsterOutOfRangeShowsNothing() {
        assertEquals(List.of(), scan(crawl(10, 3), new Sighting(DotKind.MONSTER, 4, 0)));
    }

    @Test
    void aMonsterAtTheDotRadiusStillShows() {
        assertEquals(dots(DotKind.MONSTER, 3, 0), scan(crawl(10, 3), new Sighting(DotKind.MONSTER, 3, 0)));
    }

    @Test
    void anotherCrawlerFollowsTheSameRulesAndShowsBlue() {
        assertEquals(dots(DotKind.CRAWLER, 0, 2), scan(crawl(10, 10), new Sighting(DotKind.CRAWLER, 0, 2)));
    }

    @Test
    void anNpcFollowsTheSameRulesAndShowsWhite() {
        assertEquals(dots(DotKind.NPC, 0, -2), scan(crawl(10, 10), new Sighting(DotKind.NPC, 0, -2)));
    }

    @Test
    void onlyQualifyingEntitiesAreSent() {
        List<Effect> effects = scan(crawl(10, 5),
                new Sighting(DotKind.MONSTER, 20, 0), new Sighting(DotKind.NPC, 1, 1));

        assertEquals(dots(DotKind.NPC, 1, 1), effects);
    }

    @Test
    void anUnchangedPictureIsNotSentAgain() {
        CrawlEngine engine = crawl(10, 10);
        scan(engine, new Sighting(DotKind.MONSTER, 3, 0));

        assertEquals(List.of(), scan(engine, new Sighting(DotKind.MONSTER, 3, 0)));
    }

    @Test
    void dotsThatVanishAreCleared() {
        CrawlEngine engine = crawl(10, 10);
        scan(engine, new Sighting(DotKind.MONSTER, 3, 0));

        assertEquals(List.of(new Effect.UpdateMapDots(CARL, 1, List.of())), scan(engine));
    }

    @Test
    void aCrawlerInTheSafeRoomGetsNoDots() {
        CrawlEngine engine = crawl(10, 10);
        engine.handle(new Event.SafeRoomDoorUsed(CARL, new DoorPos(1, 0, 64, 0)));

        assertEquals(List.of(), scan(engine, new Sighting(DotKind.MONSTER, 3, 0)));
    }

    @Test
    void theDefaultDotRadiusIs24() {
        assertEquals(24, FloorMapConfig.DEFAULT.dotRadius());
    }

    @Test
    void aNonPositiveDotRadiusIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FloorMapConfig(24, 0));
    }

    private static final DoorPos DOOR = new DoorPos(1, 10, 64, 20);

    @Test
    void enteringTheSafeRoomHidesTheMapAndNoDotsAreSentWhileInside() {
        CrawlEngine engine = crawl(10, 10);
        scan(engine, new Sighting(DotKind.MONSTER, 3, 0));

        assertEquals(true, engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR))
                .contains(new Effect.HideFloorMap(CARL)));
        assertEquals(List.of(), scan(engine, new Sighting(DotKind.MONSTER, 4, 0)));
    }

    @Test
    void leavingTheSafeRoomReplaysNoStaleDotsButTheNextScanSendsFreshOnes() {
        CrawlEngine engine = crawl(10, 10);
        scan(engine, new Sighting(DotKind.MONSTER, 3, 0));
        engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR));

        List<Effect> effects = engine.handle(new Event.SafeRoomExited(CARL));

        assertEquals(true, effects.stream().anyMatch(e -> e instanceof Effect.ShowFloorMap));
        assertEquals(false, effects.stream().anyMatch(e -> e instanceof Effect.UpdateMapDots));
        assertEquals(dots(DotKind.MONSTER, 3, 0), scan(engine, new Sighting(DotKind.MONSTER, 3, 0)));
    }

    @Test
    void rejoiningReplaysNoStaleDotsButTheNextScanSendsFreshOnes() {
        CrawlEngine engine = crawl(10, 10);
        scan(engine, new Sighting(DotKind.MONSTER, 3, 0));
        engine.handle(new Event.CrawlerLeft(CARL));

        assertEquals(false, engine.handle(new Event.CrawlerJoined(CARL)).stream()
                .anyMatch(e -> e instanceof Effect.UpdateMapDots));
        assertEquals(dots(DotKind.MONSTER, 3, 0), scan(engine, new Sighting(DotKind.MONSTER, 3, 0)));
    }

    @Test
    void aReShownMapWithNoDotsSendsNoDotUpdate() {
        CrawlEngine engine = crawl(10, 10);
        engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR));

        assertEquals(false, engine.handle(new Event.SafeRoomExited(CARL)).stream()
                .anyMatch(e -> e instanceof Effect.UpdateMapDots));
    }
}
