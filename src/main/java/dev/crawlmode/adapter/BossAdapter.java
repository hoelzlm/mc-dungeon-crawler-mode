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

    /** Everything the adapter holds for one Floor; each Floor is its own dimension with its own plan. */
    private record FloorBosses(FloorLayout layout, BossPlan plan, BossEncounter encounter,
            Map<GateGeometry.Pos, Gate> gateBlocks) {}

    private static final Map<Integer, FloorBosses> FLOORS = new HashMap<>();

    private BossAdapter() {}

    /** @param lootGranted receives the engine events of a Boss kill (Loot Box grant, "kill.boss" trigger). */
    static void register(Consumer<Event> lootGranted) {
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
            if (!(event.getEntity() instanceof Mob mob) || !(event.getSource().getEntity() instanceof ServerPlayer killer)
                    || !(mob.level() instanceof ServerLevel level)) {
                return;
            }
            FloorBosses floor = FLOORS.get(floorOf(level));
            if (floor == null) {
                return;
            }
            for (String tag : mob.entityTags()) {
                if (tag.startsWith(TAG_BOSS)) {
                    floor.encounter().onBossKilled(tag.substring(TAG_BOSS.length()), killer.getUUID())
                            .ifPresent(kill -> applyKill(floor, level, killer, kill, lootGranted));
                }
            }
        });
    }

    /** The Floor number of a level, from its dimension id {@code crawlmode:floor_N}; 0 for anything else. */
    static int floorOf(net.minecraft.world.level.Level level) {
        String path = level.dimension().identifier().getPath();
        return path.startsWith("floor_") ? Integer.parseInt(path.substring("floor_".length())) : 0;
    }

    static void init(FloorLayout floorLayout, FactionAreas areas, long seed) {
        init(1, floorLayout, areas, seed);
    }

    static void init(int floor, FloorLayout floorLayout, FactionAreas areas, long seed) {
        Bosses bosses;
        try (InputStream in = BossAdapter.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            bosses = Bosses.load(new InputStreamReader(in, StandardCharsets.UTF_8), FactionMobs.factions());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        BossPlan plan = BossPlan.generate(floorLayout, areas, FactionMobs.factions().forFloor(floor), bosses, seed);
        Map<GateGeometry.Pos, Gate> gateBlocks = new HashMap<>();
        for (Gate gate : floorLayout.gates()) {
            for (GateGeometry.Pos p : GateGeometry.blocks(floorLayout, gate)) {
                gateBlocks.put(p, gate);
            }
        }
        FLOORS.put(floor, new FloorBosses(floorLayout, plan, new BossEncounter(floorLayout, areas, plan), gateBlocks));
    }

    static void clear() {
        FLOORS.clear();
    }

    /** Closed gate blocks cannot be broken or blown up. */
    static boolean isClosedGateBlock(int x, int y, int z) {
        return isClosedGateBlock(1, x, y, z);
    }

    static boolean isClosedGateBlock(int floor, int x, int y, int z) {
        FloorBosses f = FLOORS.get(floor);
        Gate gate = f == null ? null : f.gateBlocks().get(new GateGeometry.Pos(x, y, z));
        return gate != null && f.encounter().isGateClosed(gate);
    }

    /** Called after a cell's structure is written: seal a closed gate here, stand the Boss if its spot is here. */
    static void onCellBuilt(ServerLevel level, Cell cell) {
        FloorBosses floor = FLOORS.get(floorOf(level));
        if (floor == null) {
            return;
        }
        for (Gate gate : floor.layout().gates()) {
            if (gate.cell().equals(cell.pos()) && floor.encounter().isGateClosed(gate)) {
                for (GateGeometry.Pos p : GateGeometry.blocks(floor.layout(), gate)) {
                    level.setBlock(new BlockPos(p.x(), p.y(), p.z()), GATE_STATE, 2);
                }
            }
        }
        for (BossPlacement p : floor.plan().placements()) {
            boolean here = Math.floorDiv(p.x(), 16) == cell.pos().x() && Math.floorDiv(p.z(), 16) == cell.pos().z();
            if (here && !floor.encounter().isDead(p.def().factionId())) {
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

    private static void applyKill(FloorBosses floor, ServerLevel level, ServerPlayer killer, BossKill kill, Consumer<Event> lootGranted) {
        for (Gate gate : kill.openedGates()) {
            for (GateGeometry.Pos p : GateGeometry.blocks(floor.layout(), gate)) {
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
