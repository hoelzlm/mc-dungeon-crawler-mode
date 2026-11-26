package dev.crawlmode.adapter;

import com.mojang.brigadier.CommandDispatcher;
import dev.crawlmode.engine.Effect;
import dev.crawlmode.engine.EliminationConfig;
import dev.crawlmode.engine.Event;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

final class EliminationBridge {
    private final Supplier<MinecraftServer> server;
    private final Function<Event, List<Effect>> engine;
    private final Consumer<List<Effect>> apply;

    EliminationBridge(Supplier<MinecraftServer> server, Function<Event, List<Effect>> engine, Consumer<List<Effect>> apply) {
        this.server = server;
        this.engine = engine;
        this.apply = apply;
    }

    static EliminationConfig configFor(MinecraftServer server) {
        String mode = System.getProperty("crawlmode.elimination", "permanent");
        EliminationConfig.Mode parsed = mode.equalsIgnoreCase("respawn")
                ? EliminationConfig.Mode.RESPAWN : EliminationConfig.Mode.PERMANENT;
        return new EliminationConfig(parsed, server.isSingleplayer());
    }

    void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        List<Effect> effects = engine.apply(new Event.CrawlerDied(player.getUUID()));
        if (effects.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        player.setHealth(player.getMaxHealth());
        apply.accept(effects);
    }

    void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("crawlgame")
                .then(Commands.literal("fresh").executes(ctx -> choose(ctx.getSource(), true)))
                .then(Commands.literal("leave").executes(ctx -> choose(ctx.getSource(), false))));
    }

    private int choose(CommandSourceStack source, boolean fresh) {
        if (source.getEntity() instanceof ServerPlayer player) {
            UUID id = player.getUUID();
            apply.accept(engine.apply(fresh ? new Event.FreshCrawlChosen(id) : new Event.LeaveChosen(id)));
        }
        return 1;
    }

    boolean applyEffect(Effect effect) {
        switch (effect) {
            case Effect.Spectate e -> withPlayer(e.crawler(), p -> p.setGameMode(GameType.SPECTATOR));
            case Effect.OfferGameOverChoice e -> withPlayer(e.crawler(), this::offerChoice);
            case Effect.RestoreWorld e -> withPlayer(e.crawler(), this::returnToVanilla);
            case Effect.Leave e -> withPlayer(e.crawler(),
                    p -> p.connection.disconnect(Component.literal("Thanks for playing. The Host will remember you fondly.")));
            default -> {
                return false;
            }
        }
        return true;
    }

    private void withPlayer(UUID id, Consumer<ServerPlayer> action) {
        ServerPlayer player = server.get().getPlayerList().getPlayer(id);
        if (player != null) {
            action.accept(player);
        }
    }

    private void offerChoice(ServerPlayer player) {
        Component fresh = Component.literal("[Fresh Crawl]").withStyle(Style.EMPTY
                .withColor(0x55FF55).withClickEvent(new ClickEvent.RunCommand("/crawlgame fresh")));
        Component leave = Component.literal("[Leave]").withStyle(Style.EMPTY
                .withColor(0xFF5555).withClickEvent(new ClickEvent.RunCommand("/crawlgame leave")));
        player.sendSystemMessage(Component.literal("Choose: ").append(fresh).append(Component.literal("  ")).append(leave));
    }

    private void returnToVanilla(ServerPlayer player) {
        ServerLevel overworld = server.get().getLevel(Level.OVERWORLD);
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(overworld, overworld.getRespawnData().pos().getX() + 0.5,
                overworld.getRespawnData().pos().getY(), overworld.getRespawnData().pos().getZ() + 0.5,
                Set.of(), 0f, 0f, true);
    }
}
