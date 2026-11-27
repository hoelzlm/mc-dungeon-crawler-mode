package dev.crawlmode.engine;

import dev.crawlmode.engine.aids.Area;
import dev.crawlmode.engine.aids.StairwellAids;
import dev.crawlmode.engine.map.FloorMap;
import dev.crawlmode.engine.map.FloorMapConfig;
import dev.crawlmode.engine.map.MapCell;
import dev.crawlmode.engine.map.MapDot;
import dev.crawlmode.engine.map.MapRect;
import dev.crawlmode.engine.map.MapSpan;
import dev.crawlmode.engine.map.Sighting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
    private final Set<UUID> toldBoxRule = new LinkedHashSet<>();
    private final Map<UUID, Long> buffetServedAt = new HashMap<>();
    private final SafeRoomRules safeRoomRules;
    private final Map<UUID, FloorMap> floorMaps = new HashMap<>();
    private final Map<Integer, List<MapCell>> safeRoomDoorCells = new HashMap<>();
    private FloorMapConfig floorMapConfig = FloorMapConfig.DEFAULT;

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

    public CrawlEngine(Clock clock, CrawlConfig config, HostLineSource host, SafeRoomRules safeRoomRules) {
        this(clock, config, host, null, bound -> 0, EliminationConfig.DEFAULT, safeRoomRules);
    }

    public CrawlEngine(
            Clock clock, CrawlConfig config, HostLineSource host, LootTables loot, IntUnaryOperator lootPick,
            EliminationConfig elimination) {
        this(clock, config, host, loot, lootPick, elimination, SafeRoomRules.NONE);
    }

    public CrawlEngine(
            Clock clock, CrawlConfig config, HostLineSource host, LootTables loot, IntUnaryOperator lootPick,
            EliminationConfig elimination, SafeRoomRules safeRoomRules) {
        this.safeRoomRules = safeRoomRules;
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
                    List<Effect> effects = new ArrayList<>();
                    effects.add(new Effect.PullToFloor(joined.crawler(), floor));
                    effects.addAll(resendMap(joined.crawler()));
                    yield effects;
                }
                yield said("apocalypse-start", joined.crawler());
            }
            case Event.ApocalypseCommanded ignored -> apocalypse();
            case Event.CrawlerDied died -> died(died.crawler());
            case Event.FreshCrawlChosen chosen -> freshCrawl(chosen.crawler());
            case Event.LeaveChosen chosen -> canChoose(chosen.crawler())
                    ? List.of(new Effect.Leave(chosen.crawler())) : List.of();
            case Event.SafeRoomDoorUsed used -> enterSafeRoom(used.crawler(), used.door());
            case Event.SafeRoomExited exited -> leaveSafeRoom(exited.crawler());
            case Event.BuffetUsed used -> useBuffet(used.crawler());
            case Event.SafeRoomDoorsPlanned planned -> {
                safeRoomDoorCells.put(planned.floor(),
                        planned.doors().stream().map(d -> new MapCell(d.x(), d.z())).toList());
                yield List.of();
            }
            case Event.CrawlerMoved moved -> reveal(moved);
            case Event.EntitiesScanned scanned -> showDots(scanned);
            case Event.StairwellAidUsed used -> paintAid(used);
            case Event.BossKilled killed -> revealBossArea(killed);
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

    public CrawlEngine withFloorMap(FloorMapConfig floorMapConfig) {
        this.floorMapConfig = floorMapConfig;
        return this;
    }

    public Set<MapCell> revealedCells(UUID crawler) {
        FloorMap map = floorMaps.get(crawler);
        return map == null ? Set.of() : map.revealed();
    }

    private List<Effect> deleteMap(UUID crawler) {
        return floorMaps.remove(crawler) == null ? List.of() : List.of(new Effect.HideFloorMap(crawler));
    }

    private List<Effect> resendMap(UUID crawler) {
        FloorMap map = floorMaps.get(crawler);
        if (map == null || safeRoomOrigin.containsKey(crawler)) {
            return List.of();
        }
        List<Effect> effects = new ArrayList<>();
        effects.add(new Effect.ShowFloorMap(crawler, map.floor(), map.spans()));
        List<MapCell> doors = map.shownDoors();
        if (!doors.isEmpty()) {
            effects.add(new Effect.ShowMapDoors(crawler, map.floor(), doors));
        }
        for (Area area : map.painted()) {
            effects.add(new Effect.PaintMapArea(crawler, map.floor(), area));
        }
        map.forgetDots();
        return effects;
    }

    private List<Effect> showDots(Event.EntitiesScanned scanned) {
        UUID crawler = scanned.crawler();
        Integer floor = floorOf.get(crawler);
        FloorMap map = floorMaps.get(crawler);
        if (floor == null || map == null || map.floor() != floor || safeRoomOrigin.containsKey(crawler)) {
            return List.of();
        }
        long reach = (long) floorMapConfig.dotRadius() * floorMapConfig.dotRadius();
        List<MapDot> dots = new ArrayList<>();
        for (Sighting sighting : scanned.sightings()) {
            long dx = (long) sighting.x() - scanned.x();
            long dz = (long) sighting.z() - scanned.z();
            if (dx * dx + dz * dz <= reach && map.isRevealed(sighting.x(), sighting.z())) {
                dots.add(new MapDot(sighting.kind(), sighting.x(), sighting.z()));
            }
        }
        return map.showDots(dots) ? List.of(new Effect.UpdateMapDots(crawler, floor, dots)) : List.of();
    }

    private boolean canMap(UUID crawler, Integer floor) {
        return floor != null && crawlers.contains(crawler) && !safeRoomOrigin.containsKey(crawler);
    }

    private List<Effect> reveal(Event.CrawlerMoved moved) {
        UUID crawler = moved.crawler();
        Integer floor = floorOf.get(crawler);
        if (!canMap(crawler, floor)) {
            return List.of();
        }
        FloorMap map = mapFor(crawler, floor);
        return mapEffects(crawler, floor, map, map.revealAround(moved.x(), moved.z(), floorMapConfig.revealRadius()));
    }

    private List<Effect> revealBossArea(Event.BossKilled killed) {
        UUID crawler = killed.crawler();
        Integer floor = floorOf.get(crawler);
        if (!canMap(crawler, floor)) {
            return List.of();
        }
        FloorMap map = mapFor(crawler, floor);
        List<MapSpan> spans = new ArrayList<>(map.revealDisc(killed.x(), killed.z(), floorMapConfig.bossRevealRadius()));
        for (MapRect rect : killed.area()) {
            spans.addAll(map.revealRect(rect));
        }
        return mapEffects(crawler, floor, map, spans);
    }

    private List<Effect> mapEffects(UUID crawler, int floor, FloorMap map, List<MapSpan> spans) {
        List<Effect> effects = new ArrayList<>();
        if (!spans.isEmpty()) {
            effects.add(new Effect.RevealMapCells(crawler, floor, spans));
        }
        List<MapCell> doors = map.newlyVisibleDoors(safeRoomDoorCells.getOrDefault(floor, List.of()));
        if (!doors.isEmpty()) {
            effects.add(new Effect.ShowMapDoors(crawler, floor, doors));
        }
        return effects;
    }

    public List<Area> paintedAreas(UUID crawler) {
        FloorMap map = floorMaps.get(crawler);
        return map == null ? List.of() : map.painted();
    }

    private FloorMap mapFor(UUID crawler, int floor) {
        FloorMap map = floorMaps.get(crawler);
        if (map == null || map.floor() != floor) {
            map = new FloorMap(floor);
            floorMaps.put(crawler, map);
        }
        return map;
    }

    private List<Effect> paintAid(Event.StairwellAidUsed used) {
        UUID crawler = used.crawler();
        Integer floor = floorOf.get(crawler);
        if (!canMap(crawler, floor)) {
            return List.of();
        }
        return StairwellAids.paintedArea(used.aid(), used.x(), used.z(), used.stairwells(), used.salt())
                .map(area -> {
                    mapFor(crawler, floor).paint(area);
                    return List.<Effect>of(new Effect.PaintMapArea(crawler, floor, area));
                })
                .orElse(List.of());
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
        List<Effect> effects = new ArrayList<>(deleteMap(crawler));
        effects.addAll(leaveSafeRoom(crawler));
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
        boolean first = safeRoomsCreated.add(crawler);
        List<Effect> effects = new ArrayList<>();
        effects.add(new Effect.EnterSafeRoom(crawler, first));
        if (first && !safeRoomRules.pantry().isEmpty()) {
            effects.add(new Effect.StockPantry(crawler, safeRoomRules.pantry()));
        }
        if (floorMaps.containsKey(crawler)) {
            effects.add(new Effect.HideFloorMap(crawler));
        }
        if (safeRoomRules.regenerates()) {
            effects.add(new Effect.StartSafeRoomRegeneration(crawler, safeRoomRules.regenerationAmplifier()));
        }
        if (first) {
            effects.addAll(said("saferoom.welcome", crawler));
        }
        return effects;
    }

    private List<Effect> leaveSafeRoom(UUID crawler) {
        DoorPos door = safeRoomOrigin.remove(crawler);
        if (door == null) {
            return List.of();
        }
        List<Effect> effects = new ArrayList<>();
        effects.add(new Effect.ExitSafeRoom(crawler, door));
        effects.addAll(resendMap(crawler));
        if (safeRoomRules.regenerates()) {
            effects.add(new Effect.StopSafeRoomRegeneration(crawler));
        }
        return effects;
    }

    public boolean isInSafeRoom(UUID crawler) {
        return safeRoomOrigin.containsKey(crawler);
    }

    private List<Effect> useBuffet(UUID crawler) {
        if (safeRoomRules.food().isEmpty() || !isInSafeRoom(crawler)) {
            return List.of();
        }
        Long last = buffetServedAt.get(crawler);
        long now = clock.nowTicks();
        if (last != null && now - last < safeRoomRules.buffetCooldownTicks()) {
            return said("saferoom.buffet-cooldown", crawler);
        }
        buffetServedAt.put(crawler, now);
        return List.of(new Effect.ServeBuffet(crawler, safeRoomRules.food()));
    }

    private List<Effect> freshCrawl(UUID crawler) {
        if (!canChoose(crawler)) {
            return List.of();
        }
        List<Effect> effects = new ArrayList<>();
        for (UUID inside : List.copyOf(safeRoomOrigin.keySet())) {
            effects.addAll(leaveSafeRoom(inside));
        }
        for (UUID room : safeRoomsCreated) {
            effects.add(new Effect.DeleteSafeRoom(room));
        }
        eliminated.clear();
        winners.clear();
        apocalypseHappened = false;
        finalWarningGiven = false;
        floorTimer.reset();
        timerFloor = 1;
        floorOf.clear();
        for (UUID mapped : List.copyOf(floorMaps.keySet())) {
            effects.addAll(deleteMap(mapped));
        }
        safeRoomOrigin.clear();
        safeRoomsCreated.clear();
        toldBoxRule.clear();
        buffetServedAt.clear();
        windowAnnounced = false;
        startedAt = clock.nowTicks();
        effects.add(new Effect.RestoreWorld(crawler));
        return effects;
    }

    private List<Effect> giveLootBox(Event.LootBoxGranted granted) {
        List<Effect> effects = new ArrayList<>();
        effects.add(new Effect.GiveLootBox(granted.crawler(), granted.tier()));
        if (toldBoxRule.add(granted.crawler())) {
            effects.addAll(said("lootbox.first", granted.crawler()));
        } else {
            effects.addAll(said("lootbox.granted", granted.crawler()));
        }
        return effects;
    }

    private List<Effect> poofLootBox(UUID crawler, LootTier tier) {
        List<Effect> effects = new ArrayList<>();
        effects.add(new Effect.ConsumeLootBox(crawler, tier));
        effects.addAll(said("lootbox.poof", crawler));
        effects.addAll(unlock(crawler, "lootbox.poof"));
        return effects;
    }

    private List<Effect> openLootBox(Event.LootBoxOpened opened) {
        if (!safeRoomOrigin.containsKey(opened.crawler())) {
            return poofLootBox(opened.crawler(), opened.tier());
        }
        if (loot == null) {
            return List.of();
        }
        List<Effect> effects = new ArrayList<>();
        effects.add(new Effect.ConsumeLootBox(opened.crawler(), opened.tier()));
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
        floorMaps.values().removeIf(map -> map.floor() == timerFloor);
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
            List<Effect> effects = new ArrayList<>(deleteMap(crawler));
            effects.addAll(leaveSafeRoom(crawler));
            effects.addAll(said("win", crawler));
            if (safeRoomsCreated.remove(crawler)) {
                effects.add(new Effect.HandOverSafeRoomContents(crawler));
                effects.add(new Effect.DeleteSafeRoom(crawler));
            }
            effects.add(new Effect.ShowWinScreen(crawler));
            effects.add(new Effect.RestoreWorld(crawler));
            return effects;
        }
        floorOf.put(crawler, floor + 1);
        List<Effect> effects = new ArrayList<>(deleteMap(crawler));
        effects.add(new Effect.PullToFloor(crawler, floor + 1));
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
            floorOf.put(crawler, 1);
            host.lineFor("apocalypse").ifPresent(line -> effects.add(new Effect.HostSays(crawler, line)));
        }
        return effects;
    }
}
