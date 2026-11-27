package dev.crawlmode.adapter;

import dev.crawlmode.client.FloorMapClient;
import dev.crawlmode.client.MinimapSettings;
import dev.crawlmode.engine.Effect;
import dev.crawlmode.engine.Event;
import dev.crawlmode.engine.map.DotKind;
import dev.crawlmode.engine.map.FloorMapConfig;
import dev.crawlmode.engine.map.Sighting;
import dev.crawlmode.network.MapDotsPayload;
import dev.crawlmode.network.RevealMapPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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
    private static final ModConfigSpec SPEC = BUILDER.build();

    private FloorMapAdapter() {}

    static void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.LOCAL, SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, MinimapSettings.SPEC);
        modBus.addListener(FloorMapAdapter::onRegisterPayloads);
    }

    static FloorMapConfig config() {
        return new FloorMapConfig(REVEAL_RADIUS.getAsInt(), DOT_RADIUS.getAsInt());
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
        ServerPlayer player = server.getPlayerList().getPlayer(reveal.crawler());
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new RevealMapPayload(reveal.floor(), reveal.spans()));
        }
    }

    static void showDots(MinecraftServer server, Effect.UpdateMapDots update) {
        ServerPlayer player = server.getPlayerList().getPlayer(update.crawler());
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new MapDotsPayload(update.floor(), update.dots()));
        }
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(RevealMapPayload.TYPE, RevealMapPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> FloorMapClient.receive(payload)));
        registrar.playToClient(MapDotsPayload.TYPE, MapDotsPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> FloorMapClient.receive(payload)));
    }
}
