package dev.crawlmode.engine.boss;

import dev.crawlmode.engine.Event;
import dev.crawlmode.engine.LootSource;
import dev.crawlmode.engine.faction.FactionAreas;
import dev.crawlmode.engine.floorgen.FloorLayout;
import dev.crawlmode.engine.floorgen.Gate;
import dev.crawlmode.engine.floorgen.Stairwell;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BossEncounter {
    private final FloorLayout layout;
    private final FactionAreas areas;
    private final BossPlan plan;
    private final Set<String> dead = new LinkedHashSet<>();
    private final Set<Stairwell> revealed = new HashSet<>();

    public BossEncounter(FloorLayout layout, FactionAreas areas, BossPlan plan) {
        this.layout = layout;
        this.areas = areas;
        this.plan = plan;
    }

    public Optional<BossKill> onBossKilled(String factionId, UUID killer) {
        Optional<BossPlacement> placement = plan.of(factionId);
        if (placement.isEmpty() || !dead.add(factionId)) {
            return Optional.empty();
        }
        List<Gate> opened = new ArrayList<>();
        for (Gate g : layout.gates()) {
            if (areas.factionOf(g.parentArea()).map(f -> f.id().equals(factionId)).orElse(false)) {
                opened.add(g);
            }
        }
        placement.get().reveals().ifPresent(revealed::add);
        return Optional.of(new BossKill(List.copyOf(opened), placement.get().reveals(),
                new Event.LootBoxGranted(killer, placement.get().def().tier(), LootSource.BOSS_KILL)));
    }

    public boolean isGateClosed(Gate gate) {
        return !Reachability.isOpen(gate, areas, dead);
    }

    public boolean isRevealed(Stairwell stairwell) {
        return revealed.contains(stairwell);
    }

    public boolean isDead(String factionId) {
        return dead.contains(factionId);
    }

    public Set<Integer> reachableAreas() {
        return Reachability.areas(layout, areas, dead);
    }
}
