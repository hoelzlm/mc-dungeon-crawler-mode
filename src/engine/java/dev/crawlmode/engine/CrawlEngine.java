package dev.crawlmode.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/**
 * The Crawl rules, with no Minecraft dependency (ADR 0002). Events and ticks in, effects out.
 * This is the single test seam.
 */
public final class CrawlEngine {
    private final Clock clock;
    private final CrawlConfig config;
    private final HostLineSource host;
    private final EliminationConfig elimination;
    private final Set<UUID> eliminated = new LinkedHashSet<>();
    private long startedAt;
    private final Set<UUID> crawlers = new LinkedHashSet<>();
    private final LootTables loot;
    private final IntUnaryOperator lootPick;
    private boolean apocalypseHappened;
    private boolean finalWarningGiven;

    public CrawlEngine(Clock clock, CrawlConfig config) {
        this(clock, config, HostLineSource.SILENT);
    }

    public CrawlEngine(Clock clock, CrawlConfig config, HostLineSource host) {
        this(clock, config, host, null, bound -> 0);
    }

    public CrawlEngine(Clock clock, CrawlConfig config, HostLineSource host, EliminationConfig elimination) {
        this(clock, config, host, null, bound -> 0, elimination);
    }

    /** @param lootPick returns a number in {@code [0, bound)}; injected so rolls can be faked. */
    public CrawlEngine(
            Clock clock, CrawlConfig config, HostLineSource host, LootTables loot, IntUnaryOperator lootPick) {
        this(clock, config, host, loot, lootPick, EliminationConfig.DEFAULT);
    }

    public CrawlEngine(
            Clock clock, CrawlConfig config, HostLineSource host, LootTables loot, IntUnaryOperator lootPick,
            EliminationConfig elimination) {
        this.loot = loot;
        this.lootPick = lootPick;
        this.elimination = elimination;
        this.clock = clock;
        this.config = config;
        this.host = host;
        this.startedAt = clock.nowTicks();
    }

    public List<Effect> handle(Event event) {
        return switch (event) {
            case Event.CrawlerJoined joined -> {
                crawlers.add(joined.crawler());
                if (eliminated.contains(joined.crawler())) {
                    yield List.of(new Effect.Spectate(joined.crawler()));
                }
                if (apocalypseHappened) {
                    yield List.of(new Effect.PullToFloor(joined.crawler(), 1));
                }
                yield said("apocalypse-start", joined.crawler());
            }
            case Event.ApocalypseCommanded ignored -> apocalypse();
            case Event.CrawlerDied died -> died(died.crawler());
            case Event.FreshCrawlChosen chosen -> freshCrawl(chosen.crawler());
            case Event.LeaveChosen chosen -> canChoose(chosen.crawler())
                    ? List.of(new Effect.Leave(chosen.crawler())) : List.of();
            case Event.LootBoxGranted granted -> openLootBox(granted);
        };
    }

    public boolean isEliminated(UUID crawler) {
        return eliminated.contains(crawler);
    }

    private boolean canChoose(UUID crawler) {
        return elimination.singlePlayer() && eliminated.contains(crawler);
    }

    private List<Effect> died(UUID crawler) {
        if (!apocalypseHappened || !crawlers.contains(crawler) || eliminated.contains(crawler)) {
            return List.of();
        }
        if (elimination.mode() == EliminationConfig.Mode.RESPAWN) {
            return List.of(new Effect.PullToFloor(crawler, 1));
        }
        eliminated.add(crawler);
        List<Effect> effects = new ArrayList<>();
        effects.add(new Effect.Spectate(crawler));
        if (elimination.singlePlayer()) {
            effects.addAll(said("game-over", crawler));
            effects.add(new Effect.OfferGameOverChoice(crawler));
        } else {
            effects.addAll(said("elimination", crawler));
        }
        return effects;
    }

    /** A fresh Crawl: back to the untouched vanilla world, the Apocalypse cycle starting over. */
    private List<Effect> freshCrawl(UUID crawler) {
        if (!canChoose(crawler)) {
            return List.of();
        }
        eliminated.clear();
        apocalypseHappened = false;
        finalWarningGiven = false;
        startedAt = clock.nowTicks();
        return List.of(new Effect.RestoreWorld(crawler));
    }

    private List<Effect> openLootBox(Event.LootBoxGranted granted) {
        if (loot == null) {
            return List.of();
        }
        List<Effect> effects = new ArrayList<>();
        effects.add(new Effect.LootBoxOpening(granted.crawler(), granted.tier()));
        host.lineFor("lootbox." + granted.tier().key())
                .ifPresent(line -> effects.add(new Effect.HostSays(granted.crawler(), line)));
        effects.add(new Effect.GrantLoot(granted.crawler(), granted.tier(), loot.open(granted.tier(), lootPick)));
        return effects;
    }

    public boolean apocalypseHappened() {
        return apocalypseHappened;
    }

    public List<Effect> tick() {
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
        List<Effect> effects = new ArrayList<>();
        for (UUID crawler : crawlers) {
            effects.add(new Effect.PullToFloor(crawler, 1));
            host.lineFor("apocalypse").ifPresent(line -> effects.add(new Effect.HostSays(crawler, line)));
        }
        return effects;
    }
}
