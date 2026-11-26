package dev.crawlmode.adapter;

import dev.crawlmode.engine.Effect;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The Host's win screen for a Crawler who passed the last Stairwell. */
final class WinAdapter {
    private WinAdapter() {}

    static void showWinScreen(MinecraftServer server, Effect.ShowWinScreen win) {
        ServerPlayer player = server.getPlayerList().getPlayer(win.crawler());
        if (player != null) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 200, 40));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("YOU WIN THE CRAWL")));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal("The Host is, grudgingly, impressed. Returning you to the world, loot intact.")));
        }
    }
}
