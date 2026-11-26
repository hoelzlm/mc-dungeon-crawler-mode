package dev.crawlmode.adapter;

import dev.crawlmode.engine.floorgen.Cell;
import dev.crawlmode.engine.floorgen.CellPos;
import dev.crawlmode.engine.floorgen.FloorLayout;
import dev.crawlmode.engine.floorgen.FloorTemplate;
import dev.crawlmode.engine.floorgen.GenerationPlanner;
import dev.crawlmode.engine.floorgen.PlayerBlock;
import dev.crawlmode.engine.floorgen.SpawnPoint;
import dev.crawlmode.engine.floorgen.TemplateLibrary;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/**
 * Builds Floor 1 from the pure {@link FloorLayout} a few cells at a time near Crawlers, keeps the structure
 * unbreakable and turns right-clicks on a Stairwell marker into engine events. Layout and placement rules live in
 * the engine source set; this class only writes blocks and forwards events.
 */
final class FloorOneAdapter {
    private static final String TEMPLATES_RESOURCE = "/data/crawlmode/floor/floor1_templates.txt";
    /** How far from a Crawler cells are built, in cells (3 = 48 blocks). */
    static final int RADIUS_CELLS = 3;
    /** Cells built per server tick; each is about two thousand block writes. */
    static final int CELLS_PER_TICK = 2;
    /** Cells built around the spawn before a Crawler is teleported in. */
    private static final int SPAWN_RADIUS_CELLS = 1;

    private static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static FloorLayout layout;
    private static final Map<String, BlockState> STATES = new HashMap<>();

    private FloorOneAdapter() {}

    static void register(Consumer<UUID> stairwellUsed) {
        NeoForge.EVENT_BUS.addListener((BreakBlockEvent event) -> {
            if (layout != null && isFloor1(event.getPlayer().level()) && layout.isProtected(event.getPos().getX(), event.getPos().getY(), event.getPos().getZ())) {
                event.setCanceled(true);
                event.setNotifyClient(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ExplosionEvent.Detonate event) -> {
            if (isFloor1(event.getLevel()) && layout != null) {
                event.getAffectedBlocks().removeIf(p -> layout.isProtected(p.getX(), p.getY(), p.getZ()));
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (layout == null || !isFloor1(event.getLevel())) {
                return;
            }
            BlockPos p = event.getPos();
            if (layout.isStairwell(p.getX(), p.getY(), p.getZ())) {
                event.setCanceled(true);
                if (!event.getLevel().isClientSide() && event.getHand() == InteractionHand.MAIN_HAND) {
                    stairwellUsed.accept(event.getEntity().getUUID());
                }
            }
        });
    }

    static void init(MinecraftServer server) {
        try (InputStream in = FloorOneAdapter.class.getResourceAsStream(TEMPLATES_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + TEMPLATES_RESOURCE);
            }
            TemplateLibrary library = TemplateLibrary.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            // The same world seed always gives the same Floor 1, so it survives restarts.
            layout = FloorLayout.generate(server.overworld().getSeed(), library);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void clear() {
        layout = null;
        STATES.clear();
    }

    private static boolean isFloor1(Level level) {
        return level.dimension().identifier().equals(Identifier.fromNamespaceAndPath("crawlmode", "floor_1"));
    }

    /** Build the start area's surroundings and return where to put an arriving Crawler. */
    static BlockPos prepareSpawn(ServerLevel level) {
        SpawnPoint spawn = layout.spawn();
        for (CellPos pos : GenerationPlanner.cellsToBuild(layout, List.of(new PlayerBlock(spawn.x(), spawn.z())),
                SPAWN_RADIUS_CELLS, p -> built(level, p), Integer.MAX_VALUE)) {
            build(level, pos);
        }
        return new BlockPos(spawn.x(), spawn.y(), spawn.z());
    }

    /** Called every server tick: build a few cells near Crawlers on Floor 1. */
    static void tick(MinecraftServer server) {
        if (layout == null) {
            return;
        }
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, Identifier.fromNamespaceAndPath("crawlmode", "floor_1")));
        if (level == null || level.players().isEmpty()) {
            return;
        }
        List<PlayerBlock> players = level.players().stream()
                .map((ServerPlayer p) -> new PlayerBlock(p.getBlockX(), p.getBlockZ())).toList();
        for (CellPos pos : GenerationPlanner.cellsToBuild(layout, players, RADIUS_CELLS, p -> built(level, p), CELLS_PER_TICK)) {
            build(level, pos);
        }
    }

    /** Every template's lowest corner is a solid floor block, so air there means the cell was never built. */
    private static boolean built(ServerLevel level, CellPos pos) {
        return !level.getBlockState(new BlockPos(pos.x() * 16, FloorLayout.FLOOR_Y, pos.z() * 16)).isAir();
    }

    private static void build(ServerLevel level, CellPos pos) {
        Cell cell = layout.cell(pos).orElseThrow();
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
    }

    private static BlockState state(String id) {
        return STATES.computeIfAbsent(id, k -> BuiltInRegistries.BLOCK.getValue(Identifier.parse(k)).defaultBlockState());
    }
}
