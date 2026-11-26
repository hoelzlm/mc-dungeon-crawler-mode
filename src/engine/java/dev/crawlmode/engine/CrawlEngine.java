package dev.crawlmode.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final long startedAt;
    private final Set<UUID> crawlers = new LinkedHashSet<>();
    private final LootTables loot;
    private final IntUnaryOperator lootPick;
    private boolean apocalypseHappened;
    private boolean finalWarningGiven;
    private FloorTimer floorTimer;
    /** The Floor whose timer is running (or, before the Apocalypse, Floor 1). */
    private int timerFloor = 1;
    private boolean windowAnnounced;
    private final Map<UUID, Integer> floorOf = new HashMap<>();

    public CrawlEngine(Clock clock, CrawlConfig config) {
        this(clock, config, HostLineSource.SILENT);
    }

    public CrawlEngine(Clock clock, CrawlConfig config, HostLineSource host) {
        this(clock, config, host, null, bound -> 0);
    }

    /** @param lootPick returns a number in {@code [0, bound)}; injected so rolls can be faked. */
    public CrawlEngine(
            Clock clock, CrawlConfig config, HostLineSource host, LootTables loot, IntUnaryOperator lootPick) {
        this.loot = loot;
        this.lootPick = lootPick;
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
                floorOf.remove(left.crawler());
                yield List.of();
            }
            case Event.CrawlerJoined joined -> {
                floorTimer.advance(clock.nowTicks(), !crawlers.isEmpty());
                crawlers.add(joined.crawler());
                if (apocalypseHappened) {
                    floorOf.put(joined.crawler(), timerFloor);
                    yield List.of(new Effect.PullToFloor(joined.crawler(), timerFloor));
                }
                yield said("apocalypse-start", joined.crawler());
            }
            case Event.ApocalypseCommanded ignored -> apocalypse();
            case Event.LootBoxGranted granted -> openLootBox(granted);
            case Event.StairwellUsed used -> useStairwell(used.crawler());
        };
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
        List<Effect> effects = new ArrayList<>(apocalypseTick());
        effects.addAll(floorTick());
        return effects;
    }

    private List<Effect> floorTick() {
        List<Effect> effects = new ArrayList<>();
        floorTimer.advance(clock.nowTicks(), !crawlers.isEmpty());
        List<UUID> onTimerFloor = crawlersOn(timerFloor);
        for (String topic : floorTimer.newWarnings()) {
            for (UUID crawler : onTimerFloor) {
                effects.addAll(said(topic, crawler));
            }
        }
        if (floorTimer.windowOpen() && !windowAnnounced) {
            windowAnnounced = true;
            for (UUID crawler : onTimerFloor) {
                effects.addAll(said("descent-window", crawler));
            }
        }
        if (floorTimer.displayChanged()) {
            for (UUID crawler : onTimerFloor) {
                effects.add(new Effect.ShowTimer(crawler, floorTimer.remaining()));
            }
        }
        if (floorTimer.expired()) {
            effects.addAll(collapse(onTimerFloor));
        }
        return effects;
    }

    /** The Floor collapses: whoever is still on it dies, and the next Floor's timer starts now. */
    private List<Effect> collapse(List<UUID> left) {
        List<Effect> effects = new ArrayList<>();
        for (UUID crawler : left) {
            effects.add(new Effect.Collapse(crawler, timerFloor));
            floorOf.remove(crawler);
        }
        timerFloor++;
        long collapsedAt = clock.nowTicks() - floorTimer.overshoot();
        floorTimer = new FloorTimer(config.floorTimerTicks());
        floorTimer.start(collapsedAt);
        floorTimer.advance(clock.nowTicks(), !crawlers.isEmpty());
        windowAnnounced = false;
        return effects;
    }

    private List<UUID> crawlersOn(int floor) {
        return crawlers.stream().filter(c -> floorOf.getOrDefault(c, 0) == floor).toList();
    }

    private List<Effect> useStairwell(UUID crawler) {
        Integer floor = floorOf.get(crawler);
        if (floor == null || floor != timerFloor) {
            return List.of();
        }
        floorTimer.advance(clock.nowTicks(), !crawlers.isEmpty());
        if (!floorTimer.windowOpen()) {
            return List.of(new Effect.StairwellSealed(crawler, floorTimer.untilWindow()));
        }
        floorOf.put(crawler, floor + 1);
        return List.of(new Effect.PullToFloor(crawler, floor + 1));
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
            floorOf.put(crawler, 1);
            host.lineFor("apocalypse").ifPresent(line -> effects.add(new Effect.HostSays(crawler, line)));
        }
        return effects;
    }
}
