package dev.crawlmode.adapter;

import com.mojang.logging.LogUtils;
import dev.crawlmode.CrawlMode;
import dev.crawlmode.engine.DoorPos;
import dev.crawlmode.engine.Effect;
import dev.crawlmode.engine.Event;
import dev.crawlmode.engine.SafeRoomRules;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import dev.crawlmode.engine.floorgen.CellPos;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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
    private static final int TEMPLATE_FLOOR = 1;

    private static final String RULES_RESOURCE = "/data/crawlmode/saferoom/rules.txt";
    private static final BlockPos ROOM_BUFFET = new BlockPos(-3, ROOM_FLOOR_Y + 1, 0);
    private static final List<BlockPos> ROOM_CHESTS = List.of(
            new BlockPos(-3, ROOM_FLOOR_Y + 1, -3), new BlockPos(0, ROOM_FLOOR_Y + 1, -3),
            new BlockPos(3, ROOM_FLOOR_Y + 1, -3));

    private static final Map<UUID, ServerLevel> ROOMS = new HashMap<>();
    private static final Map<Integer, List<DoorPos>> DOORS = new HashMap<>();

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
            Floors.numberOf(event.getLevel()).ifPresent(floor -> {
                DoorPos door = new DoorPos(floor, p.getX(), p.getY(), p.getZ());
                if (DOORS.getOrDefault(floor, List.of()).contains(door)) {
                    event.setCanceled(true);
                    events.accept(new Event.SafeRoomDoorUsed(crawler, door));
                }
            });
        });
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
    }

    static void plan(int floor, List<DoorPos> doors) {
        DOORS.put(floor, doors);
    }

    static void onCellBuilt(ServerLevel level, int floor, CellPos cell) {
        for (DoorPos door : DOORS.getOrDefault(floor, List.of())) {
            if (Math.floorDiv(door.x(), 16) == cell.x() && Math.floorDiv(door.z(), 16) == cell.z()) {
                level.setBlockAndUpdate(new BlockPos(door.x(), door.y(), door.z()), Blocks.CRYING_OBSIDIAN.defaultBlockState());
            }
        }
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
