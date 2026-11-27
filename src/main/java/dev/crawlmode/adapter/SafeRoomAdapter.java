package dev.crawlmode.adapter;

import com.mojang.logging.LogUtils;
import dev.crawlmode.CrawlMode;
import dev.crawlmode.engine.DoorPos;
import dev.crawlmode.engine.Effect;
import dev.crawlmode.engine.Event;
import dev.crawlmode.engine.SafeRoomRules;
import dev.crawlmode.engine.floorgen.CellPos;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

final class SafeRoomAdapter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ROOM_PREFIX = "safe_room_";
    private static final int ROOM_HALF_SIZE = 4;
    private static final int ROOM_FLOOR_Y = 64;
    private static final BlockPos ROOM_EXIT = new BlockPos(0, ROOM_FLOOR_Y + 1, 3);
    private static final BlockPos ROOM_SPAWN = new BlockPos(0, ROOM_FLOOR_Y + 1, 0);
    private static final int TEMPLATE_FLOOR = 1;

    private static final String RULES_RESOURCE = "/data/crawlmode/saferoom/rules.txt";
    private static final BlockPos ROOM_BUFFET = new BlockPos(-3, ROOM_FLOOR_Y + 1, 0);
    private static final List<BlockPos> ROOM_CHESTS = List.of(
            new BlockPos(-3, ROOM_FLOOR_Y + 1, -3), new BlockPos(0, ROOM_FLOOR_Y + 1, -3),
            new BlockPos(3, ROOM_FLOOR_Y + 1, -3));

    private static final Map<UUID, ServerLevel> ROOMS = new HashMap<>();
    private static final Map<Integer, List<DoorPos>> DOORS = new HashMap<>();
    private static final List<UUID> PENDING_ENTRIES = new ArrayList<>();

    private SafeRoomAdapter() {}

    static void register(Consumer<Event> events) {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
                return;
            }
            BlockPos p = event.getPos();
            UUID crawler = event.getEntity().getUUID();
            if (isRoomOf(event.getLevel(), crawler) && p.equals(ROOM_BUFFET)) {
                event.setCanceled(true);
                events.accept(new Event.BuffetUsed(crawler));
                return;
            }
            if (isRoomOf(event.getLevel(), crawler) && p.equals(ROOM_EXIT)) {
                event.setCanceled(true);
                events.accept(new Event.SafeRoomExited(crawler));
                return;
            }
            Floors.numberOf(event.getLevel()).stream().mapToObj(floor -> doorAt(floor, p)).flatMap(Optional::stream).findFirst().ifPresent(door -> {
                event.setCanceled(true);
                events.accept(new Event.SafeRoomDoorUsed(crawler, door));
            });
        });
    }

    private static Optional<DoorPos> doorAt(int floor, BlockPos p) {
        return DOORS.getOrDefault(floor, List.of()).stream()
                .filter(d -> d.x() == p.getX() && d.z() == p.getZ() && (d.y() == p.getY() || d.y() + 1 == p.getY()))
                .findFirst();
    }

    static void registerRules(Predicate<UUID> inSafeRoom) {
        NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent event) -> {
            boolean victimSafe = event.getEntity() instanceof ServerPlayer p && inSafeRoom.test(p.getUUID());
            boolean attackerSafe = event.getSource().getEntity() instanceof ServerPlayer a
                    && inSafeRoom.test(a.getUUID());
            if (victimSafe || attackerSafe) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((FinalizeSpawnEvent event) -> {
            if (ROOMS.containsValue(event.getLevel().getLevel())) {
                event.setSpawnCancelled(true);
            }
        });
    }

    static SafeRoomRules loadRules() {
        try (InputStream in = SafeRoomAdapter.class.getResourceAsStream(RULES_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RULES_RESOURCE);
            }
            return SafeRoomRules.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void startRegeneration(MinecraftServer server, Effect.StartSafeRoomRegeneration start) {
        ServerPlayer player = server.getPlayerList().getPlayer(start.crawler());
        if (player != null) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MobEffectInstance.INFINITE_DURATION,
                    start.amplifier(), true, false));
        }
    }

    static void stopRegeneration(MinecraftServer server, Effect.StopSafeRoomRegeneration stop) {
        ServerPlayer player = server.getPlayerList().getPlayer(stop.crawler());
        if (player != null) {
            player.removeEffect(MobEffects.REGENERATION);
        }
    }

    static void clear() {
        ROOMS.clear();
        DOORS.clear();
        PENDING_ENTRIES.clear();
    }

    static void tick(MinecraftServer server) {
        if (PENDING_ENTRIES.isEmpty()) {
            return;
        }
        for (UUID crawler : List.copyOf(PENDING_ENTRIES)) {
            PENDING_ENTRIES.remove(crawler);
            ServerPlayer player = server.getPlayerList().getPlayer(crawler);
            ServerLevel room = player == null ? null : create(server, crawler);
            if (room != null) {
                teleport(player, room, ROOM_SPAWN, 0f);
            }
        }
    }

    static Map<Integer, List<DoorPos>> plannedDoors() {
        return Map.copyOf(DOORS);
    }

    static void plan(int floor, List<DoorPos> doors) {
        DOORS.put(floor, doors);
    }

    static void onCellBuilt(ServerLevel level, int floor, CellPos cell) {
        for (DoorPos door : DOORS.getOrDefault(floor, List.of())) {
            if (door.cell().equals(cell)) {
                buildDoor(level, door);
            }
        }
    }

    private static void buildDoor(ServerLevel level, DoorPos door) {
        BlockPos lower = new BlockPos(door.x(), door.y(), door.z());
        BlockState frame = Blocks.STRIPPED_OAK_LOG.defaultBlockState();
        Direction facing = Direction.valueOf(door.facing().name());
        BlockState base = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing);
        level.setBlock(lower, base.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
        level.setBlock(lower.above(), base.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
        Direction side = facing.getClockWise();
        for (BlockPos beside : List.of(lower.relative(side), lower.relative(side.getOpposite()))) {
            for (BlockPos at : List.of(beside, beside.above())) {
                if (level.getBlockState(at).isAir()) {
                    level.setBlock(at, frame, Block.UPDATE_CLIENTS);
                }
            }
        }
        level.setBlock(lower.above(2), frame, Block.UPDATE_CLIENTS);
        level.setBlock(lower.above(3), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    static void enter(MinecraftServer server, Effect.EnterSafeRoom enter) {
        ServerPlayer player = server.getPlayerList().getPlayer(enter.crawler());
        if (player == null) {
            return;
        }
        if (enter.firstEntry()) {
            PENDING_ENTRIES.add(enter.crawler());
            return;
        }
        ServerLevel room = ROOMS.get(enter.crawler());
        if (room != null) {
            teleport(player, room, ROOM_SPAWN, 0f);
        }
    }

    static void exit(MinecraftServer server, Effect.ExitSafeRoom exit) {
        ServerPlayer player = server.getPlayerList().getPlayer(exit.crawler());
        ServerLevel level = server.getLevel(Floors.key(exit.door().floor()));
        if (player == null || level == null) {
            return;
        }
        DoorPos door = exit.door();
        BlockPos front = new BlockPos(door.frontX(), door.y(), door.frontZ());
        teleport(player, level, front, door.facing().yawDegrees());
    }

    static void handOver(MinecraftServer server, Effect.HandOverSafeRoomContents handOver) {
        ServerPlayer player = server.getPlayerList().getPlayer(handOver.crawler());
        ServerLevel room = ROOMS.get(handOver.crawler());
        if (player == null || room == null) {
            return;
        }
        for (BlockPos at : ROOM_CHESTS) {
            if (room.getBlockEntity(at) instanceof Container chest) {
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    ItemStack stack = chest.removeItemNoUpdate(slot);
                    if (!stack.isEmpty()) {
                        Inventories.giveOrDrop(player, stack);
                    }
                }
            }
        }
    }

    static void delete(MinecraftServer server, Effect.DeleteSafeRoom delete) {
        PENDING_ENTRIES.remove(delete.crawler());
        ServerLevel room = ROOMS.remove(delete.crawler());
        if (room == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        for (ServerPlayer inside : List.copyOf(room.players())) {
            var spawn = overworld.getRespawnData().pos();
            inside.teleport(new TeleportTransition(overworld, Vec3.atBottomCenterOf(spawn), Vec3.ZERO, 0f, 0f,
                    TeleportTransition.DO_NOTHING));
        }
        try {
            levelsOf(server).remove(room.dimension());
            room.close();
            deleteRecursively(storageOf(server).getDimensionPath(room.dimension()));
        } catch (ReflectiveOperationException | IOException | RuntimeException e) {
            LOGGER.error("Could not delete Safe Room of {}", delete.crawler(), e);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static boolean isRoomOf(Level level, UUID crawler) {
        return ROOMS.get(crawler) == level;
    }

    private static void teleport(ServerPlayer player, ServerLevel level, BlockPos at, float yaw) {
        player.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(at), Vec3.ZERO, yaw, 0f,
                TeleportTransition.DO_NOTHING));
    }

    private static ServerLevel create(MinecraftServer server, UUID crawler) {
        ServerLevel existing = ROOMS.get(crawler);
        if (existing != null) {
            return existing;
        }
        try {
            ServerLevel template = server.getLevel(Floors.key(TEMPLATE_FLOOR));
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(CrawlMode.MOD_ID, ROOM_PREFIX + crawler));
            LevelStem stem = new LevelStem(template.dimensionTypeRegistration(), template.getChunkSource().getGenerator());
            ServerLevel room = new ServerLevel(server, executorOf(server), storageOf(server),
                    new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData()), key, stem, false,
                    0L, List.of(), false);
            levelsOf(server).put(key, room);
            ROOMS.put(crawler, room);
            build(room);
            return room;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Could not create Safe Room for {}", crawler, e);
            return null;
        }
    }

    private static void build(ServerLevel room) {
        for (int x = -ROOM_HALF_SIZE; x <= ROOM_HALF_SIZE; x++) {
            for (int z = -ROOM_HALF_SIZE; z <= ROOM_HALF_SIZE; z++) {
                room.setBlockAndUpdate(new BlockPos(x, ROOM_FLOOR_Y, z), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        room.setBlockAndUpdate(ROOM_EXIT, Blocks.CRYING_OBSIDIAN.defaultBlockState());
        room.setBlockAndUpdate(ROOM_BUFFET, Blocks.HAY_BLOCK.defaultBlockState());
        BlockState chest = Blocks.CHEST.defaultBlockState();
        ROOM_CHESTS.forEach(at -> room.setBlockAndUpdate(at, chest));
    }

    private static Object field(MinecraftServer server, String name) throws ReflectiveOperationException {
        Class<?> type = MinecraftServer.class;
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(server);
    }

    private static Executor executorOf(MinecraftServer server) throws ReflectiveOperationException {
        return (Executor) field(server, "executor");
    }

    private static LevelStorageAccess storageOf(
            MinecraftServer server) throws ReflectiveOperationException {
        return (LevelStorageAccess) field(server, "storageSource");
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceKey<Level>, ServerLevel> levelsOf(MinecraftServer server)
            throws ReflectiveOperationException {
        return (Map<ResourceKey<Level>, ServerLevel>) field(server, "levels");
    }
}
