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
    private boolean finalWarningGiven;
    private final FloorTimer floorTimer;

    public CrawlEngine(Clock clock, CrawlConfig config) {
        this(clock, config, HostLineSource.SILENT);
    }

    public CrawlEngine(Clock clock, CrawlConfig config, HostLineSource host) {
        this.clock = clock;
        this.config = config;
        this.host = host;
        this.startedAt = clock.nowTicks();
        this.floorTimer = new FloorTimer(config.floorTimerTicks());
    }

    public List<Effect> handle(Event event) {
        return switch (event) {
            case Event.CrawlerLeft left -> {
                floorTimer.advance(clock.nowTicks(), !crawlers.isEmpty());
                crawlers.remove(left.crawler());
                yield List.of();
            }
            case Event.CrawlerJoined joined -> {
                floorTimer.advance(clock.nowTicks(), !crawlers.isEmpty());
                crawlers.add(joined.crawler());
                if (apocalypseHappened) {
                    yield List.of(new Effect.PullToFloor(joined.crawler(), 1));
                }
                yield said("apocalypse-start", joined.crawler());
            }
            case Event.ApocalypseCommanded ignored -> apocalypse();
        };
    }

    public boolean apocalypseHappened() {
        return apocalypseHappened;
    }

    public List<Effect> tick() {
        List<Effect> effects = new ArrayList<>(apocalypseTick());
        floorTimer.advance(clock.nowTicks(), !crawlers.isEmpty());
        for (String topic : floorTimer.newWarnings()) {
            for (UUID crawler : crawlers) {
                effects.addAll(said(topic, crawler));
            }
        }
        if (floorTimer.displayChanged()) {
            for (UUID crawler : crawlers) {
                effects.add(new Effect.ShowTimer(crawler, floorTimer.remaining()));
            }
        }
        return effects;
    }

    private List<Effect> apocalypseTick() {
        long elapsed = clock.nowTicks() - startedAt;
        if (!apocalypseHappened && !finalWarningGiven && config.warningLeadTicks() > 0
                && elapsed >= config.apocalypseDelayTicks() - config.warningLeadTicks()
                && elapsed < config.apocalypseDelayTicks()) {
            finalWarningGiven = true;
            List<Effect> effects = new ArrayList<>();
            for (UUID crawler : crawlers) {
                effects.addAll(said("apocalypse-soon", crawler));
            }
            return effects;
        }
        if (clock.nowTicks() - startedAt >= config.apocalypseDelayTicks()) {
            return apocalypse();
        }
        return List.of();
    }

    private List<Effect> said(String topic, UUID crawler) {
        return host.lineFor(topic).map(line -> List.<Effect>of(new Effect.HostSays(crawler, line))).orElse(List.of());
    }

    private List<Effect> apocalypse() {
        if (apocalypseHappened) {
            return List.of();
        }
        apocalypseHappened = true;
        floorTimer.start(clock.nowTicks());
        List<Effect> effects = new ArrayList<>();
        for (UUID crawler : crawlers) {
            effects.add(new Effect.PullToFloor(crawler, 1));
            host.lineFor("apocalypse").ifPresent(line -> effects.add(new Effect.HostSays(crawler, line)));
        }
        return effects;
    }
}
