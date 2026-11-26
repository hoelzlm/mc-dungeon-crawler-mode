package dev.crawlmode.adapter;

import dev.crawlmode.engine.Clock;
import dev.crawlmode.engine.CrawlConfig;
import dev.crawlmode.engine.CrawlEngine;
import dev.crawlmode.engine.Effect;
import dev.crawlmode.engine.Event;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Thin bridge between the game and the Minecraft-free {@link CrawlEngine}: translates game
 * callbacks into engine events and applies the effects the engine returns. No rules live here.
 */
public final class CrawlAdapter {
    private static final long DEFAULT_APOCALYPSE_DELAY_TICKS = 3L * 24_000L;

    private static CrawlEngine engine;
    private static MinecraftServer server;

    private CrawlAdapter() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CrawlAdapter::onServerStarting);
        NeoForge.EVENT_BUS.addListener(CrawlAdapter::onServerStopped);
        NeoForge.EVENT_BUS.addListener(CrawlAdapter::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(CrawlAdapter::onServerTick);
    }

    private static void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        Clock clock = () -> server.getTickCount();
        engine = new CrawlEngine(clock, new CrawlConfig(DEFAULT_APOCALYPSE_DELAY_TICKS));
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        engine = null;
        server = null;
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (engine != null && event.getEntity() instanceof ServerPlayer player) {
            apply(engine.handle(new Event.CrawlerJoined(player.getUUID())));
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (engine != null) {
            apply(engine.tick());
        }
    }

    private static void apply(List<Effect> effects) {
        for (Effect effect : effects) {
            switch (effect) {
                case Effect.PullToFloor pull -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(pull.crawler());
                    if (player != null) {
                        // Floor dimensions arrive in a later ticket; for now just announce.
                        player.sendSystemMessage(Component.literal("Pulled to floor " + pull.floor()));
                    }
                }
            }
        }
    }
}
