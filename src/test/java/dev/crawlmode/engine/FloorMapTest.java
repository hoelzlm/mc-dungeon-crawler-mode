package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.crawlmode.engine.map.FloorMapConfig;
import dev.crawlmode.engine.map.MapCell;
import dev.crawlmode.engine.map.MapSpan;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FloorMapTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DONUT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final FakeClock clock = new FakeClock();

    private CrawlEngine crawl(int revealRadius, UUID... crawlers) {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000))
                .withFloorMap(new FloorMapConfig(revealRadius));
        for (UUID c : crawlers) {
            engine.handle(new Event.CrawlerJoined(c));
        }
        engine.handle(new Event.ApocalypseCommanded());
        return engine;
    }

    @Test
    void walkingRevealsTheCellsWithinTheRadiusAndNoOthers() {
        CrawlEngine engine = crawl(2, CARL);

        engine.handle(new Event.CrawlerMoved(CARL, 0, 0));

        assertEquals(Set.of(
                        new MapCell(0, -2),
                        new MapCell(-1, -1), new MapCell(0, -1), new MapCell(1, -1),
                        new MapCell(-2, 0), new MapCell(-1, 0), new MapCell(0, 0), new MapCell(1, 0), new MapCell(2, 0),
                        new MapCell(-1, 1), new MapCell(0, 1), new MapCell(1, 1),
                        new MapCell(0, 2)),
                engine.revealedCells(CARL));
    }

    @Test
    void theRevealIsSentAsRowSpansOnTheCrawlersFloor() {
        CrawlEngine engine = crawl(1, CARL);

        assertEquals(List.of(new Effect.RevealMapCells(CARL, 1, List.of(
                        new MapSpan(-1, 0, 0), new MapSpan(0, -1, 1), new MapSpan(1, 0, 0)))),
                engine.handle(new Event.CrawlerMoved(CARL, 0, 0)));
    }

    @Test
    void movingSendsOnlyTheCellsNotYetRevealed() {
        CrawlEngine engine = crawl(1, CARL);
        engine.handle(new Event.CrawlerMoved(CARL, 0, 0));

        assertEquals(List.of(new Effect.RevealMapCells(CARL, 1, List.of(
                        new MapSpan(-1, 1, 1), new MapSpan(0, 2, 2), new MapSpan(1, 1, 1)))),
                engine.handle(new Event.CrawlerMoved(CARL, 1, 0)));
    }

    @Test
    void standingStillSendsNothing() {
        CrawlEngine engine = crawl(1, CARL);
        engine.handle(new Event.CrawlerMoved(CARL, 0, 0));

        assertEquals(List.of(), engine.handle(new Event.CrawlerMoved(CARL, 0, 0)));
    }

    @Test
    void anotherCrawlerNeverGetsTheMapOrItsEffects() {
        CrawlEngine engine = crawl(2, CARL, DONUT);

        List<Effect> effects = engine.handle(new Event.CrawlerMoved(CARL, 0, 0));

        assertTrue(effects.stream().allMatch(e -> e instanceof Effect.RevealMapCells r && r.crawler().equals(CARL)));
        assertEquals(Set.of(), engine.revealedCells(DONUT));
    }

    @Test
    void aCrawlerNotYetOnAFloorRevealsNothing() {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000)).withFloorMap(new FloorMapConfig(2));
        engine.handle(new Event.CrawlerJoined(CARL));

        assertEquals(List.of(), engine.handle(new Event.CrawlerMoved(CARL, 0, 0)));
    }

    @Test
    void theDefaultRevealRadiusIs24() {
        assertEquals(24, FloorMapConfig.DEFAULT.revealRadius());
    }

    @Test
    void aNonPositiveRevealRadiusIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FloorMapConfig(0));
    }
}
