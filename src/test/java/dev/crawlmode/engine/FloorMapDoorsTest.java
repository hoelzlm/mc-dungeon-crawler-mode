package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.crawlmode.engine.map.FloorMapConfig;
import dev.crawlmode.engine.map.MapCell;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FloorMapDoorsTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DONUT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final FakeClock clock = new FakeClock();

    private CrawlEngine crawl(int doorFloor, DoorPos... doors) {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000)).withFloorMap(new FloorMapConfig(2));
        engine.handle(new Event.SafeRoomDoorsPlanned(doorFloor, List.of(doors)));
        engine.handle(new Event.CrawlerJoined(CARL));
        engine.handle(new Event.CrawlerJoined(DONUT));
        engine.handle(new Event.ApocalypseCommanded());
        return engine;
    }

    private static List<Effect> doorEffects(List<Effect> effects) {
        return effects.stream().filter(e -> e instanceof Effect.ShowMapDoors).toList();
    }

    @Test
    void aDoorInARevealedCellIsShown() {
        CrawlEngine engine = crawl(1, new DoorPos(1, 2, 70, 0, DoorFacing.NORTH));

        assertEquals(List.of(new Effect.ShowMapDoors(CARL, 1, List.of(new MapCell(2, 0)))),
                doorEffects(engine.handle(new Event.CrawlerMoved(CARL, 0, 0))));
    }

    @Test
    void aDoorInAnUnrevealedCellIsNotSentToTheClient() {
        CrawlEngine engine = crawl(1, new DoorPos(1, 3, 70, 0, DoorFacing.NORTH));

        assertEquals(List.of(), doorEffects(engine.handle(new Event.CrawlerMoved(CARL, 0, 0))));
    }

    @Test
    void aDoorAppearsOnceItsCellIsRevealedByWalkingCloser() {
        CrawlEngine engine = crawl(1, new DoorPos(1, 3, 70, 0, DoorFacing.NORTH));
        engine.handle(new Event.CrawlerMoved(CARL, 0, 0));

        assertEquals(List.of(new Effect.ShowMapDoors(CARL, 1, List.of(new MapCell(3, 0)))),
                doorEffects(engine.handle(new Event.CrawlerMoved(CARL, 1, 0))));
    }

    @Test
    void aDoorIsSentOnlyOnce() {
        CrawlEngine engine = crawl(1, new DoorPos(1, 2, 70, 0, DoorFacing.NORTH));
        engine.handle(new Event.CrawlerMoved(CARL, 0, 0));

        assertEquals(List.of(), doorEffects(engine.handle(new Event.CrawlerMoved(CARL, 1, 0))));
    }

    @Test
    void aDoorOnAnotherFloorIsNeverShown() {
        CrawlEngine engine = crawl(2, new DoorPos(2, 2, 70, 0, DoorFacing.NORTH));

        assertEquals(List.of(), doorEffects(engine.handle(new Event.CrawlerMoved(CARL, 0, 0))));
    }

    @Test
    void aDoorRevealedForOneCrawlerIsNotSentToAnother() {
        CrawlEngine engine = crawl(1, new DoorPos(1, 2, 70, 0, DoorFacing.NORTH));
        engine.handle(new Event.CrawlerMoved(CARL, 0, 0));

        assertEquals(List.of(), doorEffects(engine.handle(new Event.CrawlerMoved(DONUT, 100, 100))));
    }

    @Test
    void leavingTheSafeRoomResendsTheDoorsAlreadyShown() {
        CrawlEngine engine = crawl(1, new DoorPos(1, 2, 70, 0, DoorFacing.NORTH));
        engine.handle(new Event.CrawlerMoved(CARL, 0, 0));
        engine.handle(new Event.SafeRoomDoorUsed(CARL, new DoorPos(1, 2, 70, 0, DoorFacing.NORTH)));

        assertEquals(List.of(new Effect.ShowMapDoors(CARL, 1, List.of(new MapCell(2, 0)))),
                doorEffects(engine.handle(new Event.SafeRoomExited(CARL))));
    }

    @Test
    void rejoiningResendsTheDoorsAlreadyShown() {
        CrawlEngine engine = crawl(1, new DoorPos(1, 2, 70, 0, DoorFacing.NORTH));
        engine.handle(new Event.CrawlerMoved(CARL, 0, 0));
        engine.handle(new Event.CrawlerLeft(CARL));

        assertEquals(List.of(new Effect.ShowMapDoors(CARL, 1, List.of(new MapCell(2, 0)))),
                doorEffects(engine.handle(new Event.CrawlerJoined(CARL))));
    }

    @Test
    void reShowingAMapWithNoDoorsSendsNoDoorEffect() {
        CrawlEngine engine = crawl(1, new DoorPos(1, 9, 70, 0, DoorFacing.NORTH));
        engine.handle(new Event.CrawlerMoved(CARL, 0, 0));
        engine.handle(new Event.SafeRoomDoorUsed(CARL, new DoorPos(1, 9, 70, 0, DoorFacing.NORTH)));

        assertEquals(List.of(), doorEffects(engine.handle(new Event.SafeRoomExited(CARL))));
    }
}
