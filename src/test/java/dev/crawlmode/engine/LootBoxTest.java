package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LootBoxTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final String VALID = """
            copper rolls 1
            copper gear minecraft:iron_sword weight=1 enchant=crawlmode:blazing:1
            copper food minecraft:bread weight=3 count=4
            silver rolls 2
            silver aid echo_chime weight=1
            gold rolls 3
            gold gear minecraft:diamond_axe weight=1
            radiant rolls 4
            radiant gear minecraft:netherite_sword weight=1 enchant=minecraft:sharpness:5
            """;

    private static LootTables tables(String text) {
        return LootTables.load(new StringReader(text));
    }

    private static CrawlEngine engine(LootTables tables, int pick) {
        HostLineSource host = DataFileHostLineSource.load(
                new StringReader("lootbox.copper: A shiny copper thing!"), bound -> 0);
        CrawlEngine engine = new CrawlEngine(new FakeClock(), new CrawlConfig(72_000), host, tables, bound -> Math.min(pick, bound - 1));
        engine.handle(new Event.CrawlerJoined(CARL));
        engine.handle(new Event.ApocalypseCommanded());
        engine.handle(new Event.SafeRoomDoorUsed(CARL, new DoorPos(1, 0, 64, 0)));
        return engine;
    }

    private static CrawlEngine hostEngine(LootTables tables, int pick) {
        HostLineSource host = DataFileHostLineSource.load(
                new StringReader("lootbox.copper: A shiny copper thing!\nlootbox.granted: A box is in your inventory!"),
                bound -> 0);
        CrawlEngine engine = new CrawlEngine(new FakeClock(), new CrawlConfig(72_000), host, tables, bound -> Math.min(pick, bound - 1));
        engine.handle(new Event.CrawlerJoined(CARL));
        engine.handle(new Event.ApocalypseCommanded());
        engine.handle(new Event.SafeRoomDoorUsed(CARL, new DoorPos(1, 0, 64, 0)));
        return engine;
    }

    @Test
    void grantingABoxGivesTheUnopenedItemAndTheHostSaysItIsInTheInventory() {
        var engine = hostEngine(tables(VALID), 0);

        engine.handle(new Event.LootBoxGranted(CARL, LootTier.COPPER, LootSource.BOSS_KILL));
        List<Effect> effects = engine.handle(new Event.LootBoxGranted(CARL, LootTier.GOLD, LootSource.BOSS_KILL));

        assertEquals(
                List.of(
                        new Effect.GiveLootBox(CARL, LootTier.GOLD),
                        new Effect.HostSays(CARL, new HostLine("lootbox.granted", "A box is in your inventory!"))),
                effects);
    }

    @Test
    void everyGrantSourceGivesAnUnopenedBoxAndNoLoot() {
        var engine = hostEngine(tables(VALID), 0);
        for (LootSource source : LootSource.values()) {
            List<Effect> effects = engine.handle(new Event.LootBoxGranted(CARL, LootTier.COPPER, source));
            assertEquals(new Effect.GiveLootBox(CARL, LootTier.COPPER), effects.getFirst(), source.name());
            assertTrue(effects.stream().noneMatch(e -> e instanceof Effect.GrantLoot), source.name());
        }
    }

    @Test
    void openingAnimatesThenTheHostSpeaksThenTheLootIsGranted() {
        var engine = hostEngine(tables(VALID), 0);

        List<Effect> effects = engine.handle(new Event.LootBoxOpened(CARL, LootTier.COPPER));

        assertEquals(
                List.of(
                        new Effect.ConsumeLootBox(CARL, LootTier.COPPER),
                        new Effect.LootBoxOpening(CARL, LootTier.COPPER),
                        new Effect.HostSays(CARL, new HostLine("lootbox.copper", "A shiny copper thing!")),
                        new Effect.GrantLoot(
                                CARL,
                                LootTier.COPPER,
                                List.of(new LootItem(LootItem.Kind.GEAR, "minecraft:iron_sword", 1,
                                        Map.of("crawlmode:blazing", 1))))),
                effects);
    }

    private static CrawlEngine outsideEngine(Achievements achievements) {
        HostLineSource host = DataFileHostLineSource.load(
                new StringReader("lootbox.poof: Nothing happens. Poof."), bound -> 0);
        CrawlEngine engine = new CrawlEngine(new FakeClock(), new CrawlConfig(72_000), host, tables(VALID), bound -> 0)
                .withAchievements(achievements);
        engine.handle(new Event.CrawlerJoined(CARL));
        engine.handle(new Event.ApocalypseCommanded());
        return engine;
    }

    private static final String POOF_ACHIEVEMENT =
            "wasted | lootbox.poof | copper | You opened a box in the open! Poof!";

    @Test
    void aBoxOpenedOutsideTheSafeRoomGivesNothingAndNoAnimation() {
        var engine = outsideEngine(Achievements.load(new StringReader("")));

        List<Effect> effects = engine.handle(new Event.LootBoxOpened(CARL, LootTier.GOLD));

        assertEquals(List.of(new Effect.ConsumeLootBox(CARL, LootTier.GOLD),
                new Effect.HostSays(CARL, new HostLine("lootbox.poof", "Nothing happens. Poof."))), effects);
    }

    @Test
    void thePoofAchievementRewardsACopperBoxOnlyTheFirstTime() {
        var engine = outsideEngine(Achievements.load(new StringReader(POOF_ACHIEVEMENT)));

        List<Effect> first = engine.handle(new Event.LootBoxOpened(CARL, LootTier.GOLD));
        List<Effect> second = engine.handle(new Event.LootBoxOpened(CARL, LootTier.GOLD));

        assertEquals(1, first.stream().filter(e -> e.equals(new Effect.GiveLootBox(CARL, LootTier.COPPER))).count());
        assertTrue(second.stream().noneMatch(e -> e instanceof Effect.GiveLootBox));
    }

    @Test
    void aBoxOpensAgainOutsideAfterLeavingTheSafeRoom() {
        var engine = hostEngine(tables(VALID), 0);
        engine.handle(new Event.SafeRoomExited(CARL));

        List<Effect> effects = engine.handle(new Event.LootBoxOpened(CARL, LootTier.COPPER));

        assertTrue(effects.stream().noneMatch(e -> e instanceof Effect.GrantLoot || e instanceof Effect.LootBoxOpening));
    }

    @Test
    void aBoxIsNotConsumedWhenThereAreNoLootTablesToOpenIt() {
        CrawlEngine engine = new CrawlEngine(new FakeClock(), new CrawlConfig(72_000));
        engine.handle(new Event.CrawlerJoined(CARL));
        engine.handle(new Event.ApocalypseCommanded());
        engine.handle(new Event.SafeRoomDoorUsed(CARL, new DoorPos(1, 0, 64, 0)));

        assertEquals(List.of(), engine.handle(new Event.LootBoxOpened(CARL, LootTier.COPPER)));
    }

    @Test
    void theFirstBoxGetsOneCombinedLineThatNamesTheSafeRoom() {
        HostLineSource host = DataFileHostLineSource.load(new StringReader(
                "lootbox.granted: In your inventory! Find a Safe Room!\nlootbox.first: Only opens in a Safe Room!"),
                bound -> 0);
        CrawlEngine engine = new CrawlEngine(new FakeClock(), new CrawlConfig(72_000), host);
        engine.handle(new Event.CrawlerJoined(CARL));
        engine.handle(new Event.ApocalypseCommanded());

        List<Effect> first = engine.handle(new Event.LootBoxGranted(CARL, LootTier.COPPER, LootSource.BOSS_KILL));

        assertEquals(List.of(new Effect.GiveLootBox(CARL, LootTier.COPPER),
                new Effect.HostSays(CARL, new HostLine("lootbox.first", "Only opens in a Safe Room!"))), first);
    }

    @Test
    void theShippedGrantedLineTellsTheCrawlerToFindASafeRoom() throws java.io.IOException {
        String text = new String(LootBoxTest.class.getResourceAsStream("/data/crawlmode/host/lines.txt")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.lines().anyMatch(l -> l.startsWith("lootbox.granted:") && l.contains("Safe Room")));
    }

    @Test
    void theShippedPoofAchievementAndHostLineExist() {
        var achievements = Achievements.load(new InputStreamReader(
                LootBoxTest.class.getResourceAsStream("/data/crawlmode/achievement/achievements.txt")));
        var host = DataFileHostLineSource.load(new InputStreamReader(
                LootBoxTest.class.getResourceAsStream("/data/crawlmode/host/lines.txt")), bound -> 0);
        assertTrue(achievements.all().stream()
                .anyMatch(a -> a.trigger().equals("lootbox.poof") && a.reward() == LootTier.COPPER));
        assertTrue(host.lineFor("lootbox.poof").isPresent());
    }

    @Test
    void entriesAreChosenByWeightAndRolledOncePerRoll() {
        var engine = engine(tables(VALID), 3);

        var grant = (Effect.GrantLoot) engine.handle(new Event.LootBoxOpened(CARL, LootTier.COPPER)).getLast();

        assertEquals(List.of(new LootItem(LootItem.Kind.FOOD, "minecraft:bread", 4, Map.of())), grant.items());

        var radiant = (Effect.GrantLoot) engine.handle(new Event.LootBoxOpened(CARL, LootTier.RADIANT)).getLast();
        assertEquals(4, radiant.items().size());
    }

    @Test
    void higherTiersRollAtLeastAsManyTimes() {
        var t = tables(VALID);
        assertTrue(t.rolls(LootTier.COPPER) < t.rolls(LootTier.RADIANT));
        String shrinking = VALID.replace("gold rolls 3", "gold rolls 1");
        assertThrows(LootTableFormatException.class, () -> tables(shrinking));
    }

    @Test
    void aMissingTierFailsLoading() {
        String noRadiant = VALID.lines().filter(l -> !l.startsWith("radiant")).reduce("", (a, b) -> a + b + "\n");
        assertThrows(LootTableFormatException.class, () -> tables(noRadiant));
    }

    @Test
    void malformedEntriesFailLoadingWithTheLineNumber() {
        for (String bad : List.of(
                "bronze rolls 1",
                "copper rolls 0",
                "copper gear minecraft:iron_sword weight=0",
                "copper gear minecraft:iron_sword",
                "copper aid compass weight=1",
                "copper toy minecraft:stick weight=1",
                "copper gear minecraft:stick weight=1 enchant=bad")) {
            var e = assertThrows(LootTableFormatException.class, () -> tables(bad + "\n" + VALID), bad);
            assertTrue(e.getMessage().contains("line 1"), e.getMessage());
        }
    }

    @Test
    void theShippedTablesLoadAndOnlyUseRealStairwellAids() {
        var in = LootBoxTest.class.getResourceAsStream("/data/crawlmode/loot/tables.txt");
        var t = LootTables.load(new InputStreamReader(in));
        var aids = Set.of("seekers_draught", "echo_chime", "revealing_lantern", "fragment_map");
        for (LootTier tier : LootTier.values()) {
            assertTrue(t.entries(tier).size() > 0);
        }
        assertTrue(Arrays.stream(LootTier.values())
                .flatMap(x -> t.entries(x).stream())
                .filter(i -> i.kind() == LootItem.Kind.AID)
                .allMatch(i -> aids.contains(i.id())));
    }
}
