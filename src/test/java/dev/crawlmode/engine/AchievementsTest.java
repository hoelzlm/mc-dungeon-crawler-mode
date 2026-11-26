package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.crawlmode.engine.faction.Rank;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AchievementsTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DONUT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final String DATA = """
            # id | trigger | tier | host line
            first-grunt | kill.grunt | copper | You killed a grunt. Hooray!
            fell | death.fall | silver | Gravity wins again!
            """;

    private static final LootTables TABLES = LootTables.load(new StringReader("""
            copper rolls 1
            copper food minecraft:bread weight=1
            silver rolls 1
            silver food minecraft:bread weight=1
            gold rolls 1
            gold food minecraft:bread weight=1
            radiant rolls 1
            radiant food minecraft:bread weight=1
            """));

    private static CrawlEngine engine(FakeClock clock, String data) {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000), HostLineSource.SILENT,
                TABLES, bound -> 0).withAchievements(Achievements.load(new StringReader(data)));
        engine.handle(new Event.CrawlerJoined(CARL));
        engine.handle(new Event.CrawlerJoined(DONUT));
        return engine;
    }

    private static CrawlEngine engine(String data) {
        return engine(new FakeClock(), data);
    }

    private static boolean unlocked(List<Effect> effects, String id) {
        return effects.stream().anyMatch(e -> e instanceof Effect.HostSays s
                && s.line().topic().equals("achievement." + id));
    }

    @Test
    void unlockingSaysTheHostLineThenGrantsALootBoxOfTheConfiguredTier() {
        var effects = engine(DATA).handle(new Event.MobKilled(CARL, Rank.GRUNT));

        assertEquals(new Effect.HostSays(CARL, new HostLine("achievement.first-grunt", "You killed a grunt. Hooray!")),
                effects.get(0));
        assertEquals(new Effect.GiveLootBox(CARL, LootTier.COPPER), effects.get(1));
    }

    @Test
    void eachUnlocksOnlyOncePerCrawler() {
        var engine = engine(DATA);
        engine.handle(new Event.MobKilled(CARL, Rank.GRUNT));

        assertTrue(engine.handle(new Event.MobKilled(CARL, Rank.GRUNT)).isEmpty());
        assertTrue(unlocked(engine.handle(new Event.MobKilled(DONUT, Rank.GRUNT)), "first-grunt"));
    }

    @Test
    void unrelatedTriggersUnlockNothing() {
        assertTrue(engine(DATA).handle(new Event.MobKilled(CARL, Rank.ELITE)).isEmpty());
    }

    @Test
    void genericTriggersUnlockToo() {
        var effects = engine(DATA).handle(new Event.Triggered(CARL, "death.fall"));

        assertEquals(new Effect.GiveLootBox(CARL, LootTier.SILVER), effects.get(1));
    }

    @Test
    void killingABossUnlocksItViaTheKillBossTrigger() {
        var engine = engine("first-boss | kill.boss | gold | A BOSS! Down!");

        assertTrue(unlocked(engine.handle(new Event.Triggered(CARL, "kill.boss")), "first-boss"));
    }

    @Test
    void reachingAStairwellUnlocksIt() {
        var engine = engine("stairs | reach.stairwell | copper | Found the stairs!");
        engine.handle(new Event.ApocalypseCommanded());

        assertTrue(unlocked(engine.handle(new Event.StairwellUsed(CARL)), "stairs"));
    }

    @Test
    void reachingTheDescentWindowUnlocksIt() {
        var clock = new FakeClock();
        var engine = engine(clock, "w | reach.descent-window | gold | Window!");
        engine.handle(new Event.ApocalypseCommanded());
        clock.advance(new CrawlConfig(72_000).floorTimerTicks());

        assertTrue(unlocked(engine.tick(), "w"));
    }

    @Test
    void malformedEntriesFailLoadingWithTheLineNumber() {
        var e = assertThrows(AchievementFormatException.class,
                () -> Achievements.load(new StringReader("ok | kill.grunt | copper | hi\nbad | only | three")));
        assertTrue(e.getMessage().contains("line 2"));
        assertThrows(AchievementFormatException.class,
                () -> Achievements.load(new StringReader("a | t | platinum | hi")));
        assertThrows(AchievementFormatException.class,
                () -> Achievements.load(new StringReader("a | t | copper | hi\na | t2 | copper | hi")));
    }

    @Test
    void theShippedCoreSetIsValid() {
        var a = Achievements.load(new InputStreamReader(
                AchievementsTest.class.getResourceAsStream("/data/crawlmode/achievement/achievements.txt")));
        var triggers = a.all().stream().map(Achievement::trigger).toList();
        assertTrue(a.all().size() >= 7);
        for (String t : List.of("kill.grunt", "kill.elite", "kill.shaman", "kill.boss", "reach.stairwell",
                "reach.descent-window", "death.fall")) {
            assertTrue(triggers.contains(t), t);
        }
    }
}
