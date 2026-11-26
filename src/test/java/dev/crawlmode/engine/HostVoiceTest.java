package dev.crawlmode.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HostVoiceTest {
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final FakeClock clock = new FakeClock();

    private static DataFileHostLineSource load(String text) {
        return DataFileHostLineSource.load(new StringReader(text), bound -> 0);
    }

    @Test
    void apocalypseIsAnnouncedByTheHostToEveryCrawler() {
        HostLineSource host = load("apocalypse: Surprise! The world is closed.");
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000), host);
        engine.handle(new Event.CrawlerJoined(CARL));

        List<Effect> effects = engine.handle(new Event.ApocalypseCommanded());

        assertEquals(
                List.of(
                        new Effect.PullToFloor(CARL, 1),
                        new Effect.HostSays(CARL, new HostLine("apocalypse", "Surprise! The world is closed."))),
                effects);
    }

    @Test
    void anUnscriptedTopicIsSimplySilent() {
        CrawlEngine engine = new CrawlEngine(clock, new CrawlConfig(72_000), load("other: hi"));
        engine.handle(new Event.CrawlerJoined(CARL));

        assertEquals(List.of(new Effect.PullToFloor(CARL, 1)), engine.handle(new Event.ApocalypseCommanded()));
    }

    @Test
    void severalLinesForOneTopicAreChosenByTheInjectedPicker() {
        String text = "apocalypse: first\napocalypse: second\n";
        var host = DataFileHostLineSource.load(new StringReader(text), bound -> bound - 1);

        assertEquals("second", host.lineFor("apocalypse").orElseThrow().text());
    }

    @Test
    void commentsAndBlankLinesAreIgnored() {
        var host = load("# a comment\n\n  apocalypse :  Hello  \n");

        assertEquals(new HostLine("apocalypse", "Hello"), host.lineFor("apocalypse").orElseThrow());
    }

    @Test
    void malformedLinesAreRejectedWithTheirLineNumber() {
        for (String bad : List.of("no separator here", ": text without topic", "topic:   ")) {
            var e = assertThrows(HostLineFormatException.class, () -> load("# ok\n" + bad));
            assertTrue(e.getMessage().contains("line 2"), e.getMessage());
        }
    }

    @Test
    void theShippedSampleLinesAreValidAndCoverApocalypse() throws Exception {
        try (var in = getClass().getResourceAsStream("/data/crawlmode/host/lines.txt")) {
            var host = DataFileHostLineSource.load(new InputStreamReader(in), bound -> 0);
            assertTrue(host.lineFor("apocalypse").isPresent());
        }
    }
}
