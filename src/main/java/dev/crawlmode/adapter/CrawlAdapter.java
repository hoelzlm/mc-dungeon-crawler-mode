package dev.crawlmode.adapter;

import dev.crawlmode.engine.Clock;
import dev.crawlmode.engine.CrawlConfig;
import dev.crawlmode.engine.CrawlEngine;
import dev.crawlmode.engine.Effect;
import dev.crawlmode.engine.Event;
import dev.crawlmode.engine.DataFileHostLineSource;
import dev.crawlmode.engine.HostLine;
import dev.crawlmode.engine.HostLineSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
    private static final String HOST_LINES_RESOURCE = "/data/crawlmode/host/lines.txt";
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
        engine = new CrawlEngine(clock, new CrawlConfig(DEFAULT_APOCALYPSE_DELAY_TICKS), loadHostLines(),
                LootBoxAdapter.loadTables(), new Random()::nextInt);
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

    private static HostLineSource loadHostLines() {
        try (InputStream in = CrawlAdapter.class.getResourceAsStream(HOST_LINES_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + HOST_LINES_RESOURCE);
            }
            Random random = new Random();
            return DataFileHostLineSource.load(new InputStreamReader(in, StandardCharsets.UTF_8), random::nextInt);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void speak(ServerPlayer player, HostLine line) {
        Component text = Component.literal(line.text());
        // Banner on screen, a sound cue, and the same line in chat history.
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("The Host")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(text));
        player.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_BELL, SoundSource.MASTER, player.getX(), player.getY(), player.getZ(), 1.0f, 1.0f, player.getRandom().nextLong()));
        player.sendSystemMessage(Component.literal("[The Host] ").append(text));
    }

    private static void apply(List<Effect> effects) {
        for (Effect effect : effects) {
            switch (effect) {
                case Effect.HostSays says -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(says.crawler());
                    if (player != null) {
                        speak(player, says.line());
                    }
                }
                case Effect.LootBoxOpening opening -> LootBoxAdapter.animate(server, opening);
                case Effect.GrantLoot grant -> LootBoxAdapter.grant(server, grant);
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
