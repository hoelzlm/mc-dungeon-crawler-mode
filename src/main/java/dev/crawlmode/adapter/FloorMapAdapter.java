package dev.crawlmode.adapter;

import dev.crawlmode.client.FloorMapClient;
import dev.crawlmode.client.FloorMapSettings;
import dev.crawlmode.engine.Effect;
import dev.crawlmode.engine.Event;
import dev.crawlmode.engine.map.DotKind;
import dev.crawlmode.engine.map.FloorMapConfig;
import dev.crawlmode.engine.map.Sighting;
import dev.crawlmode.network.MapDoorsPayload;
import dev.crawlmode.network.MapDotsPayload;
import dev.crawlmode.network.PaintMapAreaPayload;
import dev.crawlmode.network.RevealMapPayload;
import dev.crawlmode.network.ShowFloorMapPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class FloorMapAdapter {
    private static final String PROTOCOL_VERSION = "1";
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.IntValue REVEAL_RADIUS = BUILDER
            .comment("Blocks around a Crawler that the Floor Map reveals")
            .defineInRange("revealRadius", FloorMapConfig.DEFAULT_REVEAL_RADIUS, 1, 128);
    private static final ModConfigSpec.IntValue DOT_RADIUS = BUILDER
            .comment("Blocks around a Crawler within which the Floor Map shows monsters, Crawlers and NPCs")
            .defineInRange("dotRadius", FloorMapConfig.DEFAULT_DOT_RADIUS, 1, 128);
    private static final int SCAN_INTERVAL_TICKS = 5;
    private static final ModConfigSpec.IntValue BOSS_REVEAL_RADIUS = BUILDER
            .comment("Minimum blocks around a defeated Boss that the Floor Map reveals")
            .defineInRange("bossRevealRadius", FloorMapConfig.DEFAULT_BOSS_REVEAL_RADIUS, 1, 256);
    private static final ModConfigSpec SPEC = BUILDER.build();

    private FloorMapAdapter() {}

    static void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.LOCAL, SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, FloorMapSettings.SPEC);
        modBus.addListener(FloorMapAdapter::onRegisterPayloads);
    }

    static FloorMapConfig config() {
        return new FloorMapConfig(
                REVEAL_RADIUS.getAsInt(), DOT_RADIUS.getAsInt(), BOSS_REVEAL_RADIUS.getAsInt());
    }

    static void tick(MinecraftServer server, Consumer<Event> handle) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            handle.accept(new Event.CrawlerMoved(player.getUUID(), player.getBlockX(), player.getBlockZ()));
        }
        if (server.getTickCount() % SCAN_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                handle.accept(scan(player));
            }
        }
    }

    private static Event scan(ServerPlayer viewer) {
        List<Sighting> sightings = new ArrayList<>();
        for (LivingEntity entity : viewer.level().getEntitiesOfClass(LivingEntity.class,
                viewer.getBoundingBox().inflate(DOT_RADIUS.getAsInt()), e -> e != viewer && e.isAlive())) {
            if (entity instanceof ServerPlayer other) {
                if (!other.isSpectator()) {
                    sightings.add(new Sighting(DotKind.CRAWLER, other.getBlockX(), other.getBlockZ()));
                }
            } else if (entity instanceof Enemy) {
                sightings.add(new Sighting(DotKind.MONSTER, entity.getBlockX(), entity.getBlockZ()));
            }
        }
        return new Event.EntitiesScanned(viewer.getUUID(), viewer.getBlockX(), viewer.getBlockZ(), sightings);
    }

    static void reveal(MinecraftServer server, Effect.RevealMapCells reveal) {
        send(server, reveal.crawler(), new RevealMapPayload(reveal.floor(), reveal.spans()));
    }

    static void paint(MinecraftServer server, Effect.PaintMapArea paint) {
        send(server, paint.crawler(), new PaintMapAreaPayload(paint.floor(), paint.area()));
    }

    static void showDoors(MinecraftServer server, Effect.ShowMapDoors show) {
        send(server, show.crawler(), new MapDoorsPayload(show.floor(), show.doors()));
    }

    static void show(MinecraftServer server, Effect.ShowFloorMap show) {
        send(server, show.crawler(), new ShowFloorMapPayload(true, show.floor(), show.spans()));
    }

    static void hide(MinecraftServer server, Effect.HideFloorMap hide) {
        send(server, hide.crawler(), ShowFloorMapPayload.hidden());
    }

    private static void send(MinecraftServer server, UUID crawler, CustomPacketPayload payload) {
        ServerPlayer player = server.getPlayerList().getPlayer(crawler);
        if (player != null) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    static void showDots(MinecraftServer server, Effect.UpdateMapDots update) {
        send(server, update.crawler(), new MapDotsPayload(update.floor(), update.dots()));
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        toClient(registrar, RevealMapPayload.TYPE, RevealMapPayload.CODEC, FloorMapClient::receive);
        toClient(registrar, PaintMapAreaPayload.TYPE, PaintMapAreaPayload.CODEC, FloorMapClient::receive);
        toClient(registrar, MapDoorsPayload.TYPE, MapDoorsPayload.CODEC, FloorMapClient::receive);
        toClient(registrar, ShowFloorMapPayload.TYPE, ShowFloorMapPayload.CODEC, FloorMapClient::receive);
        toClient(registrar, MapDotsPayload.TYPE, MapDotsPayload.CODEC, FloorMapClient::receive);
    }

    private static <T extends CustomPacketPayload> void toClient(PayloadRegistrar registrar,
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> receive) {
        registrar.playToClient(type, codec, (payload, context) -> context.enqueueWork(() -> receive.accept(payload)));
    }
}
