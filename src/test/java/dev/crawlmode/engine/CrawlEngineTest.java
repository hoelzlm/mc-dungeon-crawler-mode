package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrawlEngineTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final FakeClock clock = new FakeClock();

    @Test
    void apocalypseCommandPullsEveryCrawlerToFloorOne() {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000));
        engine.handle(new Event.CrawlerJoined(CARL));

        List<Effect> effects = engine.handle(new Event.ApocalypseCommanded());

        assertEquals(List.of(new Effect.PullToFloor(CARL, 1)), effects);
    }

    @Test
    void apocalypseTriggersByItselfOnceTheConfiguredDelayHasPassed() {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000));
        engine.handle(new Event.CrawlerJoined(CARL));

        clock.advance(71_999);
        assertEquals(List.of(), engine.tick());

        clock.advance(1);
        assertEquals(List.of(new Effect.PullToFloor(CARL, 1)), engine.tick());
        assertEquals(List.of(), engine.tick(), "Apocalypse happens only once");
    }
}
