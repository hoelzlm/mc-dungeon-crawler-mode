package dev.crawlmode.adapter;

import dev.crawlmode.engine.faction.FactionAreas;
import dev.crawlmode.engine.faction.SpawnPlanner;
import dev.crawlmode.engine.floorgen.Cell;
import dev.crawlmode.engine.floorgen.CellPos;
import dev.crawlmode.engine.floorgen.FloorLayout;
import dev.crawlmode.engine.floorgen.FloorSpec;
import dev.crawlmode.engine.floorgen.FloorTemplate;
import dev.crawlmode.engine.floorgen.GenerationPlanner;
import dev.crawlmode.engine.floorgen.PlayerBlock;
import dev.crawlmode.engine.floorgen.SafeRoomDoorPlanner;
import dev.crawlmode.engine.floorgen.SpawnPoint;
import dev.crawlmode.engine.floorgen.TemplateLibrary;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

final class TemplateFloorAdapter {
    static final int RADIUS_CELLS = 3;
    static final int CELLS_PER_TICK = 2;
    private static final int SPAWN_RADIUS_CELLS = 1;

    private static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private record FloorState(FloorSpec spec, FloorLayout layout, FactionAreas areas, long seed) {}

    private static final Map<Integer, FloorState> FLOORS = new HashMap<>();
    private static final Map<String, BlockState> STATES = new HashMap<>();

    private TemplateFloorAdapter() {}

    private static Optional<FloorState> stateOf(Level level) {
        return Floors.lookup(level, FLOORS);
    }

    static boolean generates(int floor) {
        return FLOORS.containsKey(floor);
    }

    static void register(Consumer<UUID> stairwellUsed) {
        NeoForge.EVENT_BUS.addListener((BreakBlockEvent event) -> {
            BlockPos p = event.getPos();
            Level level = event.getPlayer().level();
            if (stateOf(level).filter(f -> f.layout().isProtected(p.getX(), p.getY(), p.getZ())
                    || BossAdapter.isClosedGateBlock(f.spec().floor(), p.getX(), p.getY(), p.getZ())).isPresent()) {
                event.setCanceled(true);
                event.setNotifyClient(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ExplosionEvent.Detonate event) -> {
            stateOf(event.getLevel()).ifPresent(f -> event.getAffectedBlocks().removeIf(p -> f.layout().isProtected(p.getX(), p.getY(), p.getZ())
                    || BossAdapter.isClosedGateBlock(f.spec().floor(), p.getX(), p.getY(), p.getZ())));
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            BlockPos p = event.getPos();
            if (stateOf(event.getLevel()).filter(f -> f.layout().isStairwell(p.getX(), p.getY(), p.getZ())).isPresent()) {
                event.setCanceled(true);
                if (!event.getLevel().isClientSide() && event.getHand() == InteractionHand.MAIN_HAND) {
                    stairwellUsed.accept(event.getEntity().getUUID());
                }
            }
        });
    }

    static void init(MinecraftServer server) {
        for (FloorSpec spec : FloorSpec.ALL) {
            try (InputStream in = TemplateFloorAdapter.class.getResourceAsStream(spec.templatesResource())) {
                if (in == null) {
                    throw new IllegalStateException("Missing " + spec.templatesResource());
                }
                TemplateLibrary library = TemplateLibrary.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                long seed = spec.seedFor(server.overworld().getSeed());
                FloorLayout layout = FloorLayout.generate(seed, library, spec);
                FactionAreas areas = FactionAreas.assign(FactionMobs.factions().forFloor(spec.floor()), layout.areaCount());
                FLOORS.put(spec.floor(), new FloorState(spec, layout, areas, seed));
                BossAdapter.init(spec.floor(), layout, areas, seed);
                SafeRoomAdapter.plan(spec.floor(), SafeRoomDoorPlanner.plan(spec.floor(), layout, BossAdapter.bossCells(spec.floor())));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    static void clear() {
        FLOORS.clear();
        BossAdapter.clear();
        FactionMobs.clear();
        STATES.clear();
    }

    static Optional<FloorLayout> layoutFor(Level level) {
        return stateOf(level).map(FloorState::layout);
    }

    static BlockPos prepareSpawn(ServerLevel level) {
        FloorState floor = stateOf(level).orElseThrow();
        SpawnPoint spawn = floor.layout().spawn();
        for (CellPos pos : GenerationPlanner.cellsToBuild(floor.layout(), List.of(new PlayerBlock(spawn.x(), spawn.z())),
                SPAWN_RADIUS_CELLS, p -> built(level, p), Integer.MAX_VALUE)) {
            build(level, floor, pos);
        }
        return new BlockPos(spawn.x(), spawn.y(), spawn.z());
    }

    static void tick(MinecraftServer server) {
        for (FloorState floor : FLOORS.values()) {
            ServerLevel level = server.getLevel(Floors.key(floor.spec().floor()));
            if (level == null || level.players().isEmpty()) {
                continue;
            }
            List<PlayerBlock> players = level.players().stream()
                    .map((ServerPlayer p) -> new PlayerBlock(p.getBlockX(), p.getBlockZ())).toList();
            for (CellPos pos : GenerationPlanner.cellsToBuild(floor.layout(), players, RADIUS_CELLS, p -> built(level, p), CELLS_PER_TICK)) {
                build(level, floor, pos);
            }
            FactionMobs.tick(level);
        }
    }

    private static boolean built(ServerLevel level, CellPos pos) {
        return !level.getBlockState(new BlockPos(pos.x() * 16, FloorLayout.FLOOR_Y, pos.z() * 16)).isAir();
    }

    private static void build(ServerLevel level, FloorState floor, CellPos pos) {
        Cell cell = floor.layout().cell(pos).orElseThrow();
        FloorTemplate template = cell.template();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int y = 0; y < template.height(); y++) {
            for (int z = 0; z < FloorTemplate.SIZE; z++) {
                for (int x = 0; x < FloorTemplate.SIZE; x++) {
                    var block = template.blockAt(x, y, z, cell.open());
                    if (block.isPresent()) {
                        at.set(pos.x() * 16 + x, FloorLayout.FLOOR_Y + y, pos.z() * 16 + z);
                        level.setBlock(at, state(block.get().blockId()), WRITE_FLAGS);
                    }
                }
            }
        }
        FactionMobs.spawnAll(level, SpawnPlanner.forCell(cell, floor.areas(), floor.seed()));
        BossAdapter.onCellBuilt(level, floor.spec().floor(), cell);
        SafeRoomAdapter.onCellBuilt(level, floor.spec().floor(), pos);
    }

    private static BlockState state(String id) {
        return STATES.computeIfAbsent(id, k -> BuiltInRegistries.BLOCK.getValue(Identifier.parse(k)).defaultBlockState());
    }
}
