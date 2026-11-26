package dev.crawlmode.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

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
    private FloorTimer floorTimer;
    private int timerFloor = 1;
    private boolean windowAnnounced;
    public static final int FINAL_FLOOR = 3;
    private final Set<UUID> winners = new LinkedHashSet<>();
    private final Map<UUID, Integer> floorOf = new HashMap<>();
    private final Map<UUID, DoorPos> safeRoomOrigin = new HashMap<>();
    private final Set<UUID> safeRoomsCreated = new LinkedHashSet<>();

    public CrawlEngine(Clock clock, CrawlConfig config) {
        this(clock, config, HostLineSource.SILENT);
    }

    public CrawlEngine(Clock clock, CrawlConfig config, HostLineSource host) {
        this(clock, config, host, null, bound -> 0);
    }

    public CrawlEngine(Clock clock, CrawlConfig config, HostLineSource host, EliminationConfig elimination) {
        this(clock, config, host, null, bound -> 0, elimination);
    }

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
                if (winners.contains(joined.crawler())) {
                    yield List.of();
                }
                if (eliminated.contains(joined.crawler())) {
                    yield List.of(new Effect.Spectate(joined.crawler()));
                }
                if (apocalypseHappened) {
                    int floor = Math.max(timerFloor, floorOf.getOrDefault(joined.crawler(), timerFloor));
                    floorOf.put(joined.crawler(), floor);
                    yield List.of(new Effect.PullToFloor(joined.crawler(), floor));
                }
                yield said("apocalypse-start", joined.crawler());
            }
            case Event.ApocalypseCommanded ignored -> apocalypse();
            case Event.CrawlerDied died -> died(died.crawler());
            case Event.FreshCrawlChosen chosen -> freshCrawl(chosen.crawler());
            case Event.LeaveChosen chosen -> canChoose(chosen.crawler())
                    ? List.of(new Effect.Leave(chosen.crawler())) : List.of();
            case Event.SafeRoomDoorUsed used -> enterSafeRoom(used.crawler(), used.door());
            case Event.SafeRoomExited exited -> exitSafeRoom(exited.crawler());
            case Event.BuffetUsed used -> useBuffet(used.crawler());
            case Event.LootBoxGranted granted -> giveLootBox(granted);
            case Event.LootBoxOpened opened -> openLootBox(opened);
            case Event.StairwellUsed used -> {
                boolean onTimerFloor = Integer.valueOf(timerFloor).equals(floorOf.get(used.crawler()));
                List<Effect> effects = new ArrayList<>(useStairwell(used.crawler()));
                if (onTimerFloor) {
                    effects.addAll(unlock(used.crawler(), "reach.stairwell"));
                }
                yield effects;
            }
            case Event.MobKilled killed -> unlock(killed.crawler(), "kill." + killed.rank().key());
            case Event.Triggered triggered -> unlock(triggered.crawler(), triggered.trigger());
        };
    }

    private Achievements achievements;

    public CrawlEngine withAchievements(Achievements achievements) {
        this.achievements = achievements;
        return this;
    }

    private List<Effect> unlock(UUID crawler, String trigger) {
        if (achievements == null || !crawlers.contains(crawler)) {
            return List.of();
        }
        List<Effect> effects = new ArrayList<>();
        for (Achievement a : achievements.unlock(crawler, trigger)) {
            effects.add(new Effect.HostSays(crawler, a.line()));
            effects.addAll(giveLootBox(new Event.LootBoxGranted(crawler, a.reward(), LootSource.ACHIEVEMENT)));
        }
        return effects;
    }

    public boolean isEliminated(UUID crawler) {
        return eliminated.contains(crawler);
    }

    private boolean canChoose(UUID crawler) {
        return elimination.singlePlayer() && eliminated.contains(crawler);
    }

    private List<Effect> died(UUID crawler) {
        if (!apocalypseHappened || !crawlers.contains(crawler) || eliminated.contains(crawler)
                || winners.contains(crawler)) {
            return List.of();
        }
        if (elimination.mode() == EliminationConfig.Mode.RESPAWN) {
            return List.of(new Effect.PullToFloor(crawler, floorOf.getOrDefault(crawler, timerFloor)));
        }
        return eliminate(crawler);
    }

    private List<Effect> eliminate(UUID crawler) {
        eliminated.add(crawler);
        floorOf.remove(crawler);
        safeRoomOrigin.remove(crawler);
        List<Effect> effects = new ArrayList<>();
        effects.add(new Effect.Spectate(crawler));
        if (safeRoomsCreated.remove(crawler)) {
            effects.add(new Effect.DeleteSafeRoom(crawler));
        }
        buffetServedAt.remove(crawler);
        if (elimination.singlePlayer()) {
            effects.addAll(said("game-over", crawler));
            effects.add(new Effect.OfferGameOverChoice(crawler));
        } else {
            effects.addAll(said("elimination", crawler));
        }
        return effects;
    }

    private List<Effect> enterSafeRoom(UUID crawler, DoorPos door) {
        if (!apocalypseHappened || !crawlers.contains(crawler) || eliminated.contains(crawler)
                || winners.contains(crawler) || safeRoomOrigin.containsKey(crawler)) {
            return List.of();
        }
        safeRoomOrigin.put(crawler, door);
        List<Effect> effects = new ArrayList<>();
        effects.add(new Effect.EnterSafeRoom(crawler, safeRoomsCreated.add(crawler)));
        if (safeRoomRules != null) {
            effects.add(new Effect.StartSafeRoomRegeneration(crawler, safeRoomRules.regenerationAmplifier()));
        }
        return effects;
    }

    private List<Effect> exitSafeRoom(UUID crawler) {
        DoorPos door = safeRoomOrigin.remove(crawler);
        if (door == null) {
            return List.of();
        }
        List<Effect> effects = new ArrayList<>();
        effects.add(new Effect.ExitSafeRoom(crawler, door));
        if (safeRoomRules != null) {
            effects.add(new Effect.StopSafeRoomRegeneration(crawler));
        }
        return effects;
    }

    private SafeRoomRules safeRoomRules;
    private final Map<UUID, Long> buffetServedAt = new HashMap<>();

    public CrawlEngine withSafeRoomRules(SafeRoomRules rules) {
        this.safeRoomRules = rules;
        return this;
    }

    public boolean isInSafeRoom(UUID crawler) {
        return safeRoomOrigin.containsKey(crawler);
    }

    private List<Effect> useBuffet(UUID crawler) {
        if (safeRoomRules == null || !isInSafeRoom(crawler)) {
            return List.of();
        }
        Long last = buffetServedAt.get(crawler);
        long now = clock.nowTicks();
        if (last != null && now - last < safeRoomRules.buffetCooldownTicks()) {
            return List.of();
        }
        buffetServedAt.put(crawler, now);
        return List.of(new Effect.ServeBuffet(crawler, safeRoomRules.food()));
    }

    private List<Effect> freshCrawl(UUID crawler) {
        if (!canChoose(crawler)) {
            return List.of();
        }
        eliminated.clear();
        winners.clear();
        apocalypseHappened = false;
        finalWarningGiven = false;
        floorTimer.reset();
        timerFloor = 1;
        floorOf.clear();
        safeRoomOrigin.clear();
        safeRoomsCreated.clear();
        buffetServedAt.clear();
        windowAnnounced = false;
        startedAt = clock.nowTicks();
        return List.of(new Effect.RestoreWorld(crawler));
    }

    private List<Effect> giveLootBox(Event.LootBoxGranted granted) {
        List<Effect> effects = new ArrayList<>();
        effects.add(new Effect.GiveLootBox(granted.crawler(), granted.tier()));
        host.lineFor("lootbox.granted")
                .ifPresent(line -> effects.add(new Effect.HostSays(granted.crawler(), line)));
        return effects;
    }

    private List<Effect> poofLootBox(UUID crawler) {
        List<Effect> effects = new ArrayList<>(said("lootbox.poof", crawler));
        effects.addAll(unlock(crawler, "lootbox.poof"));
        return effects;
    }

    private List<Effect> openLootBox(Event.LootBoxOpened opened) {
        if (!safeRoomOrigin.containsKey(opened.crawler())) {
            return poofLootBox(opened.crawler());
        }
        if (loot == null) {
            return List.of();
        }
        List<Effect> effects = new ArrayList<>();
        effects.add(new Effect.LootBoxOpening(opened.crawler(), opened.tier()));
        host.lineFor("lootbox." + opened.tier().key())
                .ifPresent(line -> effects.add(new Effect.HostSays(opened.crawler(), line)));
        effects.add(new Effect.GrantLoot(opened.crawler(), opened.tier(), loot.open(opened.tier(), lootPick)));
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
                effects.addAll(unlock(crawler, "reach.descent-window"));
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

    private List<Effect> collapse(List<UUID> left) {
        List<Effect> effects = new ArrayList<>();
        for (UUID crawler : left) {
            effects.add(new Effect.Collapse(crawler, timerFloor));
            effects.addAll(eliminate(crawler));
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
        if (floor == FINAL_FLOOR) {
            winners.add(crawler);
            floorOf.remove(crawler);
            List<Effect> effects = new ArrayList<>(said("win", crawler));
            if (safeRoomsCreated.remove(crawler)) {
                effects.add(new Effect.HandOverSafeRoomContents(crawler));
                effects.add(new Effect.DeleteSafeRoom(crawler));
            }
            effects.add(new Effect.ShowWinScreen(crawler));
            effects.add(new Effect.RestoreWorld(crawler));
            return effects;
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
