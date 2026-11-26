package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EliminationTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DONUT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final HostLineSource TOPIC_ECHO = topic -> Optional.of(new HostLine(topic, topic));
    private static final EliminationConfig SINGLE_PLAYER = new EliminationConfig(EliminationConfig.Mode.PERMANENT, true);

    private final FakeClock clock = new FakeClock();

    private CrawlEngine engine(EliminationConfig elimination, UUID... crawlers) {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000), TOPIC_ECHO, elimination);
        for (UUID c : crawlers) {
            engine.handle(new Event.CrawlerJoined(c));
        }
        engine.handle(new Event.ApocalypseCommanded());
        return engine;
    }

    private static List<Effect> withoutTimer(List<Effect> effects) {
        return effects.stream().filter(e -> !(e instanceof Effect.ShowTimer)).toList();
    }

    @Test
    void eliminationIsPermanentByDefaultAndTheCrawlerSpectates() {
        CrawlEngine engine = engine(EliminationConfig.DEFAULT, CARL, DONUT);

        List<Effect> effects = engine.handle(new Event.CrawlerDied(CARL));

        assertEquals(List.of(new Effect.Spectate(CARL),
                new Effect.HostSays(CARL, new HostLine("elimination", "elimination"))), effects);
        assertEquals(true, engine.isEliminated(CARL));
        assertEquals(false, engine.isEliminated(DONUT));
    }

    @Test
    void softModeRespawnsTheCrawlerOnFloorOneInsteadOfEliminating() {
        CrawlEngine engine = engine(new EliminationConfig(EliminationConfig.Mode.RESPAWN, false), CARL, DONUT);

        List<Effect> effects = engine.handle(new Event.CrawlerDied(CARL));

        assertEquals(List.of(new Effect.PullToFloor(CARL, 1)), effects);
        assertEquals(false, engine.isEliminated(CARL));
    }

    @Test
    void deathBeforeTheApocalypseIsJustVanillaDeath() {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000), TOPIC_ECHO, EliminationConfig.DEFAULT);
        engine.handle(new Event.CrawlerJoined(CARL));

        assertEquals(List.of(), engine.handle(new Event.CrawlerDied(CARL)));
        assertEquals(false, engine.isEliminated(CARL));
    }

    @Test
    void dyingTwiceEliminatesOnlyOnce() {
        CrawlEngine engine = engine(EliminationConfig.DEFAULT, CARL, DONUT);
        engine.handle(new Event.CrawlerDied(CARL));

        assertEquals(List.of(), engine.handle(new Event.CrawlerDied(CARL)));
    }

    @Test
    void eliminatedCrawlerRejoiningStaysASpectator() {
        CrawlEngine engine = engine(EliminationConfig.DEFAULT, CARL, DONUT);
        engine.handle(new Event.CrawlerDied(CARL));

        assertEquals(List.of(new Effect.Spectate(CARL)), engine.handle(new Event.CrawlerJoined(CARL)));
    }

    @Test
    void singlePlayerGameOverMocksAndOffersFreshCrawlOrLeaving() {
        CrawlEngine engine = engine(SINGLE_PLAYER, CARL);

        List<Effect> effects = engine.handle(new Event.CrawlerDied(CARL));

        assertEquals(List.of(new Effect.Spectate(CARL),
                new Effect.HostSays(CARL, new HostLine("game-over", "game-over")),
                new Effect.OfferGameOverChoice(CARL)), effects);
    }

    @Test
    void freshCrawlRestoresTheWorldAndRestartsTheApocalypseCycle() {
        CrawlEngine engine = engine(SINGLE_PLAYER, CARL);
        engine.handle(new Event.CrawlerDied(CARL));

        List<Effect> effects = engine.handle(new Event.FreshCrawlChosen(CARL));

        assertEquals(List.of(new Effect.RestoreWorld(CARL)), effects);
        assertEquals(false, engine.apocalypseHappened());
        assertEquals(false, engine.isEliminated(CARL));
        assertEquals(List.of(new Effect.PullToFloor(CARL, 1),
                new Effect.HostSays(CARL, new HostLine("apocalypse", "apocalypse"))),
                engine.handle(new Event.ApocalypseCommanded()));
    }

    @Test
    void freshCrawlDelayCountsFromTheChoice() {
        CrawlEngine engine = engine(SINGLE_PLAYER, CARL);
        engine.handle(new Event.CrawlerDied(CARL));
        clock.advance(100_000);
        engine.handle(new Event.FreshCrawlChosen(CARL));

        clock.advance(71_999);
        assertEquals(List.of(), withoutTimer(engine.tick()));
        clock.advance(1);
        assertEquals(2, withoutTimer(engine.tick()).size());
    }

    @Test
    void choosingToLeaveSendsTheCrawlerAway() {
        CrawlEngine engine = engine(SINGLE_PLAYER, CARL);
        engine.handle(new Event.CrawlerDied(CARL));

        assertEquals(List.of(new Effect.Leave(CARL)), engine.handle(new Event.LeaveChosen(CARL)));
    }

    @Test
    void choicesAreIgnoredUnlessTheCrawlerIsEliminatedInSinglePlayer() {
        CrawlEngine engine = engine(EliminationConfig.DEFAULT, CARL, DONUT);

        assertEquals(List.of(), engine.handle(new Event.FreshCrawlChosen(CARL)));
        assertEquals(List.of(), engine.handle(new Event.LeaveChosen(CARL)));
        assertEquals(true, engine.apocalypseHappened());
    }
}
