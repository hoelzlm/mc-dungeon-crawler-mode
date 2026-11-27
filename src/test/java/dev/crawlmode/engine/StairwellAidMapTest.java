package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.crawlmode.engine.aids.Area;
import dev.crawlmode.engine.aids.StairwellAids;
import dev.crawlmode.engine.floorgen.CellPos;
import dev.crawlmode.engine.floorgen.Stairwell;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StairwellAidMapTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Stairwell WELL = new Stairwell(new CellPos(2, 5), 37, 61, 91);

    private final FakeClock clock = new FakeClock();

    private CrawlEngine crawl() {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000));
        engine.handle(new Event.CrawlerJoined(CARL));
        engine.handle(new Event.ApocalypseCommanded());
        return engine;
    }

    private static Event.StairwellAidUsed use(String aid) {
        return new Event.StairwellAidUsed(CARL, aid, 0, 0,
                List.of(WELL, new Stairwell(new CellPos(30, 30), 500, 61, 500)), 7L);
    }

    private static Area painted(List<Effect> effects) {
        assertEquals(1, effects.size());
        return ((Effect.PaintMapArea) effects.get(0)).area();
    }

    @Test
    void everyAidPaintsAnAreaContainingAStairwellThatIsNeverAnExactCell() {
        for (String aid : StairwellAids.ids()) {
            Area area = painted(crawl().handle(use(aid)));

            assertTrue(area.contains(WELL.x(), WELL.z()), aid);
            assertTrue(area.maxX() > area.minX() && area.maxZ() > area.minZ(), aid);
        }
    }

    @Test
    void theFourAidsPaintAreasOfDifferentSizes() {
        Set<Integer> sizes = new HashSet<>();
        for (String aid : StairwellAids.ids()) {
            Area area = painted(crawl().handle(use(aid)));
            sizes.add(area.maxX() - area.minX() + 1);
        }

        assertEquals(4, sizes.size());
    }

    @Test
    void thePaintedAreaIsOnTheCrawlersFloorAndIsKeptOnTheirMap() {
        CrawlEngine engine = crawl();

        Effect.PaintMapArea effect = (Effect.PaintMapArea) engine.handle(use(StairwellAids.FRAGMENT_MAP)).get(0);

        assertEquals(1, effect.floor());
        assertEquals(List.of(effect.area()), engine.paintedAreas(CARL));
    }

    @Test
    void withoutAnAidNoAreaIsPaintedEvenStandingOnAStairwell() {
        CrawlEngine engine = crawl();
        engine.handle(new Event.CrawlerMoved(CARL, WELL.x(), WELL.z()));

        assertEquals(List.of(), engine.paintedAreas(CARL));
    }

    @Test
    void anAidWithNoStairwellToFindPaintsNothing() {
        CrawlEngine engine = crawl();

        assertEquals(List.of(), engine.handle(
                new Event.StairwellAidUsed(CARL, StairwellAids.ECHO_CHIME, 0, 0, List.of(), 7L)));
    }

    @Test
    void anAidUsedByAnEliminatedCrawlerPaintsNothing() {
        CrawlEngine engine = crawl();
        engine.handle(new Event.CrawlerDied(CARL));

        assertTrue(engine.isEliminated(CARL));
        assertEquals(List.of(), engine.handle(use(StairwellAids.ECHO_CHIME)));
    }

    @Test
    void theAreaIsDeletedWithTheMapWhenTheCrawlerIsEliminated() {
        CrawlEngine engine = crawl();
        engine.handle(use(StairwellAids.ECHO_CHIME));
        assertNotEquals(List.of(), engine.paintedAreas(CARL));

        engine.handle(new Event.CrawlerDied(CARL));

        assertEquals(List.of(), engine.paintedAreas(CARL));
    }
}
