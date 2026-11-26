package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SafeRoomLessonsTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DONUT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final DoorPos DOOR = new DoorPos(1, 10, 64, 20);
    private static final HostLine FIRST_BOX = new HostLine("lootbox.first", "Only in a Safe Room!");
    private static final HostLine WELCOME = new HostLine("saferoom.welcome", "Chests, food, boxes!");

    private final FakeClock clock = new FakeClock();

    private CrawlEngine crawl(UUID... crawlers) {
        HostLineSource host = DataFileHostLineSource.load(
                new StringReader("lootbox.first: Only in a Safe Room!\nsaferoom.welcome: Chests, food, boxes!"),
                bound -> 0);
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000), host);
        for (UUID c : crawlers) {
            engine.handle(new Event.CrawlerJoined(c));
        }
        engine.handle(new Event.ApocalypseCommanded());
        return engine;
    }

    private static Event.LootBoxGranted grant(UUID crawler) {
        return new Event.LootBoxGranted(crawler, LootTier.COPPER, LootSource.ACHIEVEMENT);
    }

    private static boolean said(List<Effect> effects, UUID crawler, HostLine line) {
        return effects.contains(new Effect.HostSays(crawler, line));
    }

    @Test
    void theFirstLootBoxRepeatsTheSafeRoomRule() {
        CrawlEngine engine = crawl(CARL);

        assertTrue(said(engine.handle(grant(CARL)), CARL, FIRST_BOX));
    }

    @Test
    void theFirstBoxLineFiresOnlyOncePerCrawler() {
        CrawlEngine engine = crawl(CARL, DONUT);
        engine.handle(grant(CARL));

        assertFalse(said(engine.handle(grant(CARL)), CARL, FIRST_BOX));
        assertTrue(said(engine.handle(grant(DONUT)), DONUT, FIRST_BOX));
    }

    @Test
    void theFirstEntryWelcomesTheCrawlerOnce() {
        CrawlEngine engine = crawl(CARL);

        assertEquals(
                List.of(new Effect.EnterSafeRoom(CARL, true), new Effect.HostSays(CARL, WELCOME)),
                engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR)));
        engine.handle(new Event.SafeRoomExited(CARL));

        assertEquals(List.of(new Effect.EnterSafeRoom(CARL, false)),
                engine.handle(new Event.SafeRoomDoorUsed(CARL, DOOR)));
    }

    @Test
    void everyShippedApocalypseLineStatesTheLootBoxRule() throws Exception {
        try (var in = getClass().getResourceAsStream("/data/crawlmode/host/lines.txt")) {
            for (String line : new String(in.readAllBytes()).split("\n")) {
                if (line.startsWith("apocalypse:")) {
                    assertTrue(line.contains("Safe Room") && line.contains("Loot Box"), line);
                }
            }
        }
    }

    @Test
    void theShippedLinesCoverTheSafeRoomLessons() throws Exception {
        try (var in = getClass().getResourceAsStream("/data/crawlmode/host/lines.txt")) {
            var host = DataFileHostLineSource.load(new InputStreamReader(in), bound -> 0);
            assertTrue(host.lineFor("lootbox.first").isPresent());
            assertTrue(host.lineFor("saferoom.welcome").isPresent());
        }
    }
}
