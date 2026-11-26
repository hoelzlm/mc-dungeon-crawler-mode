package dev.crawlmode.adapter;

import dev.crawlmode.engine.Effect;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Stairwell and collapse effects. */
final class StairwellAdapter {
    private StairwellAdapter() {}

    static void sealed(MinecraftServer server, Effect.StairwellSealed sealed) {
        ServerPlayer player = server.getPlayerList().getPlayer(sealed.crawler());
        if (player != null) {
            player.sendSystemMessage(
                    Component.literal("The Stairwell is sealed. Opens in " + TimerHud.format(sealed.opensInTicks())), true);
        }
    }

    static void collapse(MinecraftServer server, Effect.Collapse collapse) {
        ServerPlayer player = server.getPlayerList().getPlayer(collapse.crawler());
        if (player != null) {
            // The engine eliminates the victim (Spectate effect); no vanilla death is needed here.
            player.sendSystemMessage(Component.literal("Floor " + collapse.floor() + " collapses!"));
        }
    }
}
