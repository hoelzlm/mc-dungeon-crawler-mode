package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SafeRoomRulesTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DONUT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final DoorPos DOOR = new DoorPos(1, 10, 64, 20);
    private static final String RULES = """
            # comment
            regeneration | 1
            buffet-cooldown-ticks | 600
            food | minecraft:bread | 4
            food | minecraft:cooked_beef | 2
            """;

    private final FakeClock clock = new FakeClock();

    private CrawlEngine crawl(UUID... crawlers) {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000), HostLineSource.SILENT,
                SafeRoomRules.load(new StringReader(RULES)));
        for (UUID c : crawlers) {
            engine.handle(new Event.CrawlerJoined(c));
        }
        engine.handle(new Event.ApocalypseCommanded());
        return engine;
    }

    private static List<LootItem> food() {
        return List.of(new LootItem(LootItem.Kind.FOOD, "minecraft:bread", 4, Map.of()),
                new LootItem(LootItem.Kind.FOOD, "minecraft:cooked_beef", 2, Map.of()));
    }

    @Test
    void rulesLoadFromTheDataFile() {
        SafeRoomRules rules = SafeRoomRules.load(new StringReader(RULES));

        assertEquals(1, rules.regenerationAmplifier());
        assertEquals(600, rules.buffetCooldownTicks());
        assertEquals(food(), rules.food());
    }

    @Test
    void theShippedRulesFileLoads() throws IOException {
        try (var in = getClass().getResourceAsStream("/data/crawlmode/saferoom/rules.txt")) {
            assertTrue(SafeRoomRules.load(new InputStreamReader(in)).food().size() > 0);
        }
    }

    @Test
    void badRulesNameTheLine() {
        var e = assertThrows(SafeRoomRulesFormatException.class,
                () -> SafeRoomRules.load(new StringReader("regeneration | 0\nbuffet-cooldown-ticks | soon\n")));

        assertTrue(e.getMessage().startsWith("line 2"), e.getMessage());
    }

    @Test
    void missingFoodIsRejected() {
        assertThrows(SafeRoomRulesFormatException.class,
                () -> SafeRoomRules.load(new StringReader("regeneration | 0\nbuffet-cooldown-ticks | 10\n")));
    }

    @Test
    void unknownKeyIsRejected() {
        assertThrows(SafeRoomRulesFormatException.class,
                () -> SafeRoomRules.load(new StringReader(RULES + "bogus | 1\n")));
    }

    @Test
    void enteringStartsRegenerationAndLeavingStopsIt() {
        CrawlEngine engine = crawl(CARL);

        assertEquals(List.of(new Effect.EnterSafeRoom(CARL, true), new Effect.StartSafeRoomRegeneration(CARL, 1)),
                engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR)));
        assertEquals(List.of(new Effect.ExitSafeRoom(CARL, DOOR), new Effect.StopSafeRoomRegeneration(CARL)),
                engine.handle(new Event.SafeRoomExited(CARL)));
    }

    @Test
    void crawlersInsideTheSafeRoomAreProtected() {
        CrawlEngine engine = crawl(CARL, DONUT);

        assertFalse(engine.isInSafeRoom(CARL));
        engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR));
        assertTrue(engine.isInSafeRoom(CARL));
        assertFalse(engine.isInSafeRoom(DONUT));
        engine.handle(new Event.SafeRoomExited(CARL));
        assertFalse(engine.isInSafeRoom(CARL));
    }

    @Test
    void buffetServesBasicFoodInsideTheSafeRoom() {
        CrawlEngine engine = crawl(CARL);
        engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR));

        assertEquals(List.of(new Effect.ServeBuffet(CARL, food())), engine.handle(new Event.BuffetUsed(CARL)));
    }

    @Test
    void buffetGivesNothingOutsideTheSafeRoom() {
        CrawlEngine engine = crawl(CARL);

        assertEquals(List.of(), engine.handle(new Event.BuffetUsed(CARL)));
    }

    @Test
    void buffetRefillsOnlyAfterTheCooldown() {
        CrawlEngine engine = crawl(CARL);
        engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR));
        engine.handle(new Event.BuffetUsed(CARL));

        clock.advance(599);
        assertEquals(List.of(), engine.handle(new Event.BuffetUsed(CARL)));
        clock.advance(1);
        assertEquals(1, engine.handle(new Event.BuffetUsed(CARL)).size());
    }

    @Test
    void buffetCooldownIsPerCrawler() {
        CrawlEngine engine = crawl(CARL, DONUT);
        engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR));
        engine.handle(new Event.SafeRoomDoorUsed(DONUT, DOOR));
        engine.handle(new Event.BuffetUsed(CARL));

        assertEquals(1, engine.handle(new Event.BuffetUsed(DONUT)).size());
    }

    @Test
    void theFloorTimerKeepsRunningWhileInside() {
        CrawlEngine engine = crawl(CARL);
        engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR));

        clock.advance(CrawlConfig.DEFAULT_FLOOR_TIMER_TICKS);

        assertTrue(engine.tick().contains(new Effect.Collapse(CARL, 1)));
    }

    private static final HostLineSource TOPIC_ECHO = topic -> Optional.of(new HostLine(topic, topic));

    @Test
    void clickingTheBuffetWhileItRefillsTellsTheCrawlerToWait() {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000), TOPIC_ECHO,
                SafeRoomRules.load(new StringReader(RULES)));
        engine.handle(new Event.CrawlerJoined(CARL));
        engine.handle(new Event.ApocalypseCommanded());
        engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR));
        engine.handle(new Event.BuffetUsed(CARL));

        assertEquals(List.of(new Effect.HostSays(CARL, new HostLine("saferoom.buffet-cooldown", "saferoom.buffet-cooldown"))),
                engine.handle(new Event.BuffetUsed(CARL)));
    }

    @Test
    void anEliminationInsideStopsRegenerationAndReturnsTheSpectatorToTheirDoorBeforeDeletingTheRoom() {
        CrawlEngine engine = crawl(CARL, DONUT);
        engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR));

        List<Effect> effects = engine.handle(new Event.CrawlerDied(CARL));

        assertTrue(effects.indexOf(new Effect.StopSafeRoomRegeneration(CARL)) >= 0);
        assertTrue(effects.indexOf(new Effect.ExitSafeRoom(CARL, DOOR)) < effects.indexOf(new Effect.DeleteSafeRoom(CARL)));
        assertFalse(engine.isInSafeRoom(CARL));
    }

    @Test
    void winningFromInsideStopsRegenerationAndLeavesTheRoomBeforeItIsDeleted() {
        long minute = 20 * 60;
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000, 0, 30 * minute), HostLineSource.SILENT,
                SafeRoomRules.load(new StringReader(RULES)));
        engine.handle(new Event.CrawlerJoined(CARL));
        engine.handle(new Event.ApocalypseCommanded());
        for (int floor = 0; floor < 2; floor++) {
            clock.advance(22 * minute);
            engine.tick();
            engine.handle(new Event.StairwellUsed(CARL));
            clock.advance(8 * minute);
            engine.tick();
        }
        engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR));
        clock.advance(22 * minute);
        engine.tick();

        List<Effect> effects = engine.handle(new Event.StairwellUsed(CARL));

        int stop = effects.indexOf(new Effect.StopSafeRoomRegeneration(CARL));
        int exit = effects.indexOf(new Effect.ExitSafeRoom(CARL, DOOR));
        int delete = effects.indexOf(new Effect.DeleteSafeRoom(CARL));
        assertTrue(stop >= 0 && exit >= 0 && exit < delete);
    }
}
