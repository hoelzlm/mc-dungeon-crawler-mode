package dev.crawlmode.adapter;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import dev.crawlmode.CrawlMode;
import dev.crawlmode.engine.Clock;
import dev.crawlmode.engine.CrawlConfig;
import dev.crawlmode.engine.CrawlEngine;
import dev.crawlmode.engine.DataFileHostLineSource;
import dev.crawlmode.engine.Effect;
import dev.crawlmode.engine.Event;
import dev.crawlmode.engine.HostLine;
import dev.crawlmode.engine.HostLineSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

public final class CrawlAdapter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String HOST_LINES_RESOURCE = "/data/crawlmode/host/lines.txt";

    private static CrawlEngine engine;
    private static MinecraftServer server;
    private static TimerHud timerHud;
    private static EliminationBridge elimination;

    private CrawlAdapter() {}

    public static void register(IEventBus modBus, ModContainer container) {
        FloorMapAdapter.register(modBus, container);
        AidAdapter.register(modBus);
        LootBoxAdapter.register(modBus, e -> {
            if (engine != null) {
                apply(engine.handle(e));
            }
        });
        NeoForge.EVENT_BUS.addListener(CrawlAdapter::onServerStarting);
        NeoForge.EVENT_BUS.addListener(CrawlAdapter::onServerStopped);
        NeoForge.EVENT_BUS.addListener(CrawlAdapter::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(CrawlAdapter::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(CrawlAdapter::onServerTick);
        NeoForge.EVENT_BUS.addListener(CrawlAdapter::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(CrawlAdapter::onTravel);
        AchievementAdapter.registerSilly(ev -> {
            if (engine != null) {
                apply(engine.handle(ev));
            }
        });
        BossAdapter.register(e -> {
            if (engine != null) {
                apply(engine.handle(e));
            }
        });
        TemplateFloorAdapter.register(crawler -> {
            if (engine != null) {
                apply(engine.handle(new Event.StairwellUsed(crawler)));
            }
        });
        SafeRoomAdapter.register(e -> {
            if (engine != null) {
                apply(engine.handle(e));
            }
        });
        SafeRoomAdapter.registerRules(crawler -> engine != null && engine.isInSafeRoom(crawler));
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (engine != null) {
                AchievementAdapter.eventFor(e).ifPresent(ev -> apply(engine.handle(ev)));
                AchievementAdapter.sillyEventsFor(e).forEach(ev -> apply(engine.handle(ev)));
            }
            if (elimination != null) {
                elimination.onDeath(e);
            }
        });
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        new EliminationBridge(() -> server, e -> engine.handle(e), CrawlAdapter::apply).registerCommands(d);
        d.register(Commands.literal("crawl")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("apocalypse").executes(ctx -> {
                    if (engine != null) {
                        LOGGER.info("Apocalypse triggered by command");
                        apply(engine.handle(new Event.ApocalypseCommanded()));
                    }
                    return 1;
                })));
        d.register(Commands.literal("stairwell").executes(ctx -> {
            if (engine != null && ctx.getSource().getEntity() instanceof ServerPlayer player) {
                apply(engine.handle(new Event.StairwellUsed(player.getUUID())));
            }
            return 1;
        }));
    }

    private static void onTravel(EntityTravelToDimensionEvent event) {
        if (engine != null && engine.apocalypseHappened()
                && !event.getDimension().identifier().getNamespace().equals(CrawlMode.MOD_ID)) {
            event.setCanceled(true);
        }
    }

    private static void pullToFloor(ServerPlayer player, int floor) {
        ServerLevel level = server.getLevel(Floors.key(floor));
        if (level == null) {
            player.sendSystemMessage(Component.literal("Floor " + floor + " dimension is missing"));
            return;
        }
        if (!TemplateFloorAdapter.generates(floor)) {
            player.sendSystemMessage(Component.literal("Floor " + floor + " has no generator"));
            return;
        }
        BlockPos spawn = TemplateFloorAdapter.prepareSpawn(level);
        player.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(spawn), Vec3.ZERO, 0f, 0f, TeleportTransition.DO_NOTHING));
    }

    private static void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        TemplateFloorAdapter.init(server);
        Clock clock = () -> server.getTickCount();
        CrawlConfig config = configFromSystemProperties();
        engine = new CrawlEngine(clock, config, loadHostLines(),
                LootBoxAdapter.loadTables(), new Random()::nextInt, EliminationBridge.configFor(server),
                SafeRoomAdapter.loadRules())
                .withAchievements(AchievementAdapter.load())
                .withFloorMap(FloorMapAdapter.config());
        timerHud = new TimerHud(config.floorTimerTicks());
        elimination = new EliminationBridge(() -> server, e -> engine.handle(e), CrawlAdapter::apply);
        LOGGER.info("Crawl engine ready");
    }

    private static CrawlConfig configFromSystemProperties() {
        String days = System.getProperty("crawlmode.apocalypseDelayDays");
        CrawlConfig base = CrawlConfig.DEFAULT;
        if (days != null) {
            base = new CrawlConfig((long) (Double.parseDouble(days) * CrawlConfig.TICKS_PER_DAY), base.warningLeadTicks(), base.floorTimerTicks());
        }
        String minutes = System.getProperty("crawlmode.floorTimerMinutes");
        if (minutes != null) {
            base = new CrawlConfig(base.apocalypseDelayTicks(), base.warningLeadTicks(), (long) (Double.parseDouble(minutes) * 60 * 20));
        }
        return base;
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        if (timerHud != null) {
            timerHud.clear();
        }
        timerHud = null;
        TemplateFloorAdapter.clear();
        SafeRoomAdapter.clear();
        engine = null;
        server = null;
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (engine != null && event.getEntity() instanceof ServerPlayer player) {
            apply(engine.handle(new Event.CrawlerJoined(player.getUUID())));
        }
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (engine != null && event.getEntity() instanceof ServerPlayer player) {
            engine.handle(new Event.CrawlerLeft(player.getUUID()));
            timerHud.remove(player.getUUID());
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (engine != null) {
            apply(engine.tick());
            TemplateFloorAdapter.tick(server);
            SafeRoomAdapter.tick(server);
            FloorMapAdapter.tick(server, e -> apply(engine.handle(e)));
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
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("The Host")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(text));
        player.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_BELL, SoundSource.MASTER, player.getX(), player.getY(), player.getZ(), 1.0f, 1.0f, player.getRandom().nextLong()));
        player.sendSystemMessage(HostChat.say(text));
    }

    private static void apply(List<Effect> effects) {
        for (Effect effect : effects) {
            if (elimination != null && elimination.applyEffect(effect)) {
                continue;
            }
            switch (effect) {
                case Effect.HostSays says -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(says.crawler());
                    if (player != null) {
                        speak(player, says.line());
                    }
                }
                case Effect.ShowTimer timer -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(timer.crawler());
                    if (player != null) {
                        timerHud.show(player, timer.remainingTicks());
                    }
                }
                case Effect.StairwellSealed sealed -> StairwellAdapter.sealed(server, sealed);
                case Effect.Collapse collapse -> StairwellAdapter.collapse(server, collapse);
                case Effect.ShowWinScreen win -> WinAdapter.showWinScreen(server, win);
                case Effect.GiveLootBox box -> LootBoxAdapter.give(server, box);
                case Effect.ConsumeLootBox consume -> LootBoxAdapter.consume(server, consume);
                case Effect.LootBoxOpening opening -> LootBoxAdapter.animate(server, opening);
                case Effect.GrantLoot grant -> LootBoxAdapter.grant(server, grant);
                case Effect.EnterSafeRoom enter -> SafeRoomAdapter.enter(server, enter);
                case Effect.HandOverSafeRoomContents handOver -> SafeRoomAdapter.handOver(server, handOver);
                case Effect.DeleteSafeRoom delete -> SafeRoomAdapter.delete(server, delete);
                case Effect.ExitSafeRoom exit -> SafeRoomAdapter.exit(server, exit);
                case Effect.StartSafeRoomRegeneration start -> SafeRoomAdapter.startRegeneration(server, start);
                case Effect.StopSafeRoomRegeneration stop -> SafeRoomAdapter.stopRegeneration(server, stop);
                case Effect.RevealMapCells reveal -> FloorMapAdapter.reveal(server, reveal);
                case Effect.ServeBuffet buffet -> LootBoxAdapter.handItems(server, buffet.crawler(), buffet.items());
                case Effect.PullToFloor pull -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(pull.crawler());
                    if (player != null) {
                        pullToFloor(player, pull.floor());
                    }
                }
                default -> {}
            }
        }
    }
}
