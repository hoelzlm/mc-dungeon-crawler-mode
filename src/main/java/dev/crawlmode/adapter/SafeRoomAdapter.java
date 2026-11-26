package dev.crawlmode.adapter;

import com.mojang.logging.LogUtils;
import dev.crawlmode.CrawlMode;
import dev.crawlmode.engine.DoorPos;
import dev.crawlmode.engine.Effect;
import dev.crawlmode.engine.Event;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

final class SafeRoomAdapter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ROOM_PREFIX = "safe_room_";
    private static final int ROOM_HALF_SIZE = 4;
    private static final int ROOM_FLOOR_Y = 64;
    private static final BlockPos ROOM_EXIT = new BlockPos(0, ROOM_FLOOR_Y + 1, 3);
    private static final BlockPos ROOM_SPAWN = new BlockPos(0, ROOM_FLOOR_Y + 1, 0);
    private static final int PLACEHOLDER_DOOR_FLOOR = 1;
    private static final BlockPos PLACEHOLDER_DOOR_OFFSET = new BlockPos(3, 0, 0);

    private static final Map<UUID, ServerLevel> ROOMS = new HashMap<>();
    private static final Map<Integer, BlockPos> DOORS = new HashMap<>();

    private SafeRoomAdapter() {}

    static void register(Consumer<Event> events) {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
                return;
            }
            BlockPos p = event.getPos();
            UUID crawler = event.getEntity().getUUID();
            if (isRoomOf(event.getLevel(), crawler) && p.equals(ROOM_EXIT)) {
                event.setCanceled(true);
                events.accept(new Event.SafeRoomExited(crawler));
                return;
            }
            Floors.numberOf(event.getLevel()).ifPresent(floor -> {
                if (p.equals(DOORS.get(floor))) {
                    event.setCanceled(true);
                    events.accept(new Event.SafeRoomDoorUsed(crawler, new DoorPos(floor, p.getX(), p.getY(), p.getZ())));
                }
            });
        });
    }

    static void clear() {
        ROOMS.clear();
        DOORS.clear();
    }

    static void placePlaceholderDoor(ServerLevel level, BlockPos spawn) {
        Floors.numberOf(level).stream().filter(f -> f == PLACEHOLDER_DOOR_FLOOR).forEach(f -> {
            BlockPos door = spawn.offset(PLACEHOLDER_DOOR_OFFSET);
            level.setBlockAndUpdate(door, Blocks.CRYING_OBSIDIAN.defaultBlockState());
            DOORS.put(f, door);
        });
    }

    static void enter(MinecraftServer server, Effect.EnterSafeRoom enter) {
        ServerPlayer player = server.getPlayerList().getPlayer(enter.crawler());
        if (player == null) {
            return;
        }
        ServerLevel room = enter.firstEntry() ? create(server, enter.crawler()) : ROOMS.get(enter.crawler());
        if (room == null) {
            return;
        }
        teleport(player, room, ROOM_SPAWN);
    }

    static void exit(MinecraftServer server, Effect.ExitSafeRoom exit) {
        ServerPlayer player = server.getPlayerList().getPlayer(exit.crawler());
        ServerLevel level = server.getLevel(Floors.key(exit.door().floor()));
        if (player == null || level == null) {
            return;
        }
        BlockPos door = new BlockPos(exit.door().x(), exit.door().y(), exit.door().z());
        teleport(player, level, door.above());
    }

    private static boolean isRoomOf(Level level, UUID crawler) {
        return ROOMS.get(crawler) == level;
    }

    private static void teleport(ServerPlayer player, ServerLevel level, BlockPos at) {
        player.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(at), Vec3.ZERO, 0f, 0f,
                TeleportTransition.DO_NOTHING));
    }

    private static ServerLevel create(MinecraftServer server, UUID crawler) {
        ServerLevel existing = ROOMS.get(crawler);
        if (existing != null) {
            return existing;
        }
        try {
            ServerLevel template = server.getLevel(Floors.key(PLACEHOLDER_DOOR_FLOOR));
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
    }

    private static Object field(MinecraftServer server, String name) throws ReflectiveOperationException {
        Class<?> type = MinecraftServer.class;
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(server);
    }

    private static java.util.concurrent.Executor executorOf(MinecraftServer server) throws ReflectiveOperationException {
        return (java.util.concurrent.Executor) field(server, "executor");
    }

    private static net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess storageOf(
            MinecraftServer server) throws ReflectiveOperationException {
        return (net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess) field(server, "storageSource");
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceKey<Level>, ServerLevel> levelsOf(MinecraftServer server)
            throws ReflectiveOperationException {
        return (Map<ResourceKey<Level>, ServerLevel>) field(server, "levels");
    }
}
