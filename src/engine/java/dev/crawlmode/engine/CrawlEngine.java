package dev.crawlmode.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Crawl rules, with no Minecraft dependency (ADR 0002). Events and ticks in, effects out.
 * This is the single test seam.
 */
public final class CrawlEngine {
    private final Clock clock;
    private final CrawlConfig config;
    private final HostLineSource host;
    private final long startedAt;
    private final Set<UUID> crawlers = new LinkedHashSet<>();
    private boolean apocalypseHappened;

    public CrawlEngine(Clock clock, CrawlConfig config) {
        this(clock, config, HostLineSource.SILENT);
    }

    public CrawlEngine(Clock clock, CrawlConfig config, HostLineSource host) {
        this.clock = clock;
        this.config = config;
        this.host = host;
        this.startedAt = clock.nowTicks();
    }

    public List<Effect> handle(Event event) {
        return switch (event) {
            case Event.CrawlerJoined joined -> {
                crawlers.add(joined.crawler());
                yield List.of();
            }
            case Event.ApocalypseCommanded ignored -> apocalypse();
        };
    }

    public List<Effect> tick() {
        if (clock.nowTicks() - startedAt >= config.apocalypseDelayTicks()) {
            return apocalypse();
        }
        return List.of();
    }

    private List<Effect> apocalypse() {
        if (apocalypseHappened) {
            return List.of();
        }
        apocalypseHappened = true;
        List<Effect> effects = new ArrayList<>();
        for (UUID crawler : crawlers) {
            effects.add(new Effect.PullToFloor(crawler, 1));
            host.lineFor("apocalypse").ifPresent(line -> effects.add(new Effect.HostSays(crawler, line)));
        }
        return effects;
    }
}
