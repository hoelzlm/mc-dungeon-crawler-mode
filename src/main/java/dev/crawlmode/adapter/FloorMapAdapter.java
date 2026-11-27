package dev.crawlmode.adapter;

import dev.crawlmode.client.FloorMapClient;
import dev.crawlmode.client.MinimapSettings;
import dev.crawlmode.engine.Effect;
import dev.crawlmode.engine.Event;
import dev.crawlmode.engine.map.FloorMapConfig;
import dev.crawlmode.network.MapDoorsPayload;
import dev.crawlmode.network.PaintMapAreaPayload;
import dev.crawlmode.network.RevealMapPayload;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

final class FloorMapAdapter {
    private static final String PROTOCOL_VERSION = "1";
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.IntValue REVEAL_RADIUS = BUILDER
            .comment("Blocks around a Crawler that the Floor Map reveals")
            .defineInRange("revealRadius", FloorMapConfig.DEFAULT_REVEAL_RADIUS, 1, 128);
    private static final ModConfigSpec SPEC = BUILDER.build();

    private FloorMapAdapter() {}

    static void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.LOCAL, SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, MinimapSettings.SPEC);
        modBus.addListener(FloorMapAdapter::onRegisterPayloads);
    }

    static FloorMapConfig config() {
        return new FloorMapConfig(REVEAL_RADIUS.getAsInt());
    }

    static void tick(MinecraftServer server, Consumer<Event> handle) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            handle.accept(new Event.CrawlerMoved(player.getUUID(), player.getBlockX(), player.getBlockZ()));
        }
    }

    static void reveal(MinecraftServer server, Effect.RevealMapCells reveal) {
        ServerPlayer player = server.getPlayerList().getPlayer(reveal.crawler());
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new RevealMapPayload(reveal.floor(), reveal.spans()));
        }
    }

    static void paint(MinecraftServer server, Effect.PaintMapArea paint) {
        ServerPlayer player = server.getPlayerList().getPlayer(paint.crawler());
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new PaintMapAreaPayload(paint.floor(), paint.area()));
        }
    }

    static void showDoors(MinecraftServer server, Effect.ShowMapDoors show) {
        ServerPlayer player = server.getPlayerList().getPlayer(show.crawler());
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new MapDoorsPayload(show.floor(), show.doors()));
        }
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(RevealMapPayload.TYPE, RevealMapPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> FloorMapClient.receive(payload)));
        registrar.playToClient(PaintMapAreaPayload.TYPE, PaintMapAreaPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> FloorMapClient.receive(payload)));
        registrar.playToClient(MapDoorsPayload.TYPE, MapDoorsPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> FloorMapClient.receive(payload)));
    }
}
