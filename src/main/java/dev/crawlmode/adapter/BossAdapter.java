package dev.crawlmode.adapter;

import dev.crawlmode.engine.Event;
import dev.crawlmode.engine.boss.BossDef;
import dev.crawlmode.engine.boss.BossEncounter;
import dev.crawlmode.engine.boss.BossKill;
import dev.crawlmode.engine.boss.BossPlacement;
import dev.crawlmode.engine.boss.BossPlan;
import dev.crawlmode.engine.boss.Bosses;
import dev.crawlmode.engine.boss.GateGeometry;
import dev.crawlmode.engine.faction.FactionAreas;
import dev.crawlmode.engine.faction.MobDef;
import dev.crawlmode.engine.faction.PlannedSpawn;
import dev.crawlmode.engine.faction.Rank;
import dev.crawlmode.engine.floorgen.Cell;
import dev.crawlmode.engine.floorgen.FloorLayout;
import dev.crawlmode.engine.floorgen.Gate;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Carries out the engine's Boss plan in Floor 1: spawns each Boss, fills closed gates with iron bars, opens them
 * when the Boss dies, announces a revealed Stairwell and reports the Loot Box grant. Placement, gating, reveal
 * and rewards are all decided in {@code dev.crawlmode.engine.boss}; Boss stats come from bosses.txt.
 */
final class BossAdapter {
    private static final String RESOURCE = "/data/crawlmode/faction/bosses.txt";
    private static final String TAG_BOSS = "crawlmode_boss:";
    private static final BlockState GATE_STATE = Blocks.IRON_BARS.defaultBlockState();

    private static FloorLayout layout;
    private static BossPlan plan;
    private static BossEncounter encounter;
    private static final Map<GateGeometry.Pos, Gate> GATE_BLOCKS = new HashMap<>();

    private BossAdapter() {}

    /** @param lootGranted receives the engine events of a Boss kill (Loot Box grant, "kill.boss" trigger). */
    static void register(Consumer<Event> lootGranted) {
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
            if (encounter == null || !(event.getEntity() instanceof Mob mob)
                    || !(event.getSource().getEntity() instanceof ServerPlayer killer)) {
                return;
            }
            for (String tag : mob.entityTags()) {
                if (tag.startsWith(TAG_BOSS) && mob.level() instanceof ServerLevel level) {
                    encounter.onBossKilled(tag.substring(TAG_BOSS.length()), killer.getUUID())
                            .ifPresent(kill -> applyKill(level, killer, kill, lootGranted));
                }
            }
        });
    }

    static void init(FloorLayout floorLayout, FactionAreas areas, long seed) {
        Bosses bosses;
        try (InputStream in = BossAdapter.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            bosses = Bosses.load(new InputStreamReader(in, StandardCharsets.UTF_8), FactionMobs.factions());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        layout = floorLayout;
        plan = BossPlan.generate(floorLayout, areas, FactionMobs.factions().forFloor(1), bosses, seed);
        encounter = new BossEncounter(floorLayout, areas, plan);
        GATE_BLOCKS.clear();
        for (Gate gate : floorLayout.gates()) {
            for (GateGeometry.Pos p : GateGeometry.blocks(floorLayout, gate)) {
                GATE_BLOCKS.put(p, gate);
            }
        }
    }

    static void clear() {
        layout = null;
        plan = null;
        encounter = null;
        GATE_BLOCKS.clear();
    }

    /** Closed gate blocks cannot be broken or blown up. */
    static boolean isClosedGateBlock(int x, int y, int z) {
        Gate gate = encounter == null ? null : GATE_BLOCKS.get(new GateGeometry.Pos(x, y, z));
        return gate != null && encounter.isGateClosed(gate);
    }

    /** Called after a cell's structure is written: seal a closed gate here, stand the Boss if its spot is here. */
    static void onCellBuilt(ServerLevel level, Cell cell) {
        if (encounter == null) {
            return;
        }
        for (Gate gate : layout.gates()) {
            if (gate.cell().equals(cell.pos()) && encounter.isGateClosed(gate)) {
                for (GateGeometry.Pos p : GateGeometry.blocks(layout, gate)) {
                    level.setBlock(new BlockPos(p.x(), p.y(), p.z()), GATE_STATE, 2);
                }
            }
        }
        for (BossPlacement p : plan.placements()) {
            boolean here = Math.floorDiv(p.x(), 16) == cell.pos().x() && Math.floorDiv(p.z(), 16) == cell.pos().z();
            if (here && !encounter.isDead(p.def().factionId())) {
                spawnBoss(level, p);
            }
        }
    }

    private static void spawnBoss(ServerLevel level, BossPlacement placement) {
        BossDef def = placement.def();
        // FactionMobs builds from a MobDef; a Boss has no spawn weight and the rank is not used when spawning.
        MobDef mob = new MobDef(def.factionId(), Rank.ELITE, def.entity(), def.name(), def.health(), def.damage(), 1,
                def.gear(), List.of());
        Mob spawned = FactionMobs.spawn(level, new PlannedSpawn(mob, placement.x(), placement.y(), placement.z()));
        if (spawned != null) {
            spawned.addTag(TAG_BOSS + def.factionId());
        }
    }

    private static void applyKill(ServerLevel level, ServerPlayer killer, BossKill kill, Consumer<Event> lootGranted) {
        for (Gate gate : kill.openedGates()) {
            for (GateGeometry.Pos p : GateGeometry.blocks(layout, gate)) {
                BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
                if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.IRON_BARS)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        if (!kill.openedGates().isEmpty()) {
            level.players().forEach(p -> p.sendSystemMessage(Component.literal("[The Host] A gate has opened.")));
        }
        kill.revealedStairwell().ifPresent(w -> level.players().forEach(p -> p.sendSystemMessage(
                Component.literal("[The Host] A Stairwell is revealed at " + w.x() + " " + w.y() + " " + w.z() + "."))));
        lootGranted.accept(kill.loot());
        lootGranted.accept(new Event.Triggered(killer.getUUID(), "kill.boss"));
    }
}
