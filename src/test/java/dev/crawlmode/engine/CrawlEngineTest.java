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

    private static final HostLineSource TOPIC_ECHO = topic -> java.util.Optional.of(new HostLine(topic, topic));

    @Test
    void delayDefaultsToInGameDayThree() {
        assertEquals(3 * 24_000L, CrawlConfig.DEFAULT.apocalypseDelayTicks());
    }

    @Test
    void hostWarnsAJoiningCrawlerAtTheStart() {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000, 6_000), TOPIC_ECHO);

        List<Effect> effects = engine.handle(new Event.CrawlerJoined(CARL));

        assertEquals(List.of(new Effect.HostSays(CARL, new HostLine("apocalypse-start", "apocalypse-start"))), effects);
    }

    @Test
    void hostWarnsShortlyBeforeTheApocalypseExactlyOnce() {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000, 6_000), TOPIC_ECHO);
        engine.handle(new Event.CrawlerJoined(CARL));

        clock.advance(65_999);
        assertEquals(List.of(), engine.tick());

        clock.advance(1);
        assertEquals(List.of(new Effect.HostSays(CARL, new HostLine("apocalypse-soon", "apocalypse-soon"))), engine.tick());
        assertEquals(List.of(), engine.tick());
    }

    @Test
    void crawlerJoiningAfterTheApocalypseIsPulledStraightIn() {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000));
        engine.handle(new Event.ApocalypseCommanded());

        assertEquals(List.of(new Effect.PullToFloor(CARL, 1)), engine.handle(new Event.CrawlerJoined(CARL)));
    }

    @Test
    void commandAfterTheApocalypseDoesNothing() {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000));
        engine.handle(new Event.CrawlerJoined(CARL));
        engine.handle(new Event.ApocalypseCommanded());

        assertEquals(List.of(), engine.handle(new Event.ApocalypseCommanded()));
    }
}
