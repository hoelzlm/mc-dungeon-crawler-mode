package dev.crawlmode.adapter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

final class TimerHud {
    private final Map<UUID, ServerBossEvent> bars = new HashMap<>();
    private final long totalTicks;

    TimerHud(long totalTicks) {
        this.totalTicks = totalTicks;
    }

    void show(ServerPlayer player, long remainingTicks) {
        ServerBossEvent bar = bars.computeIfAbsent(player.getUUID(), id -> {
            ServerBossEvent created = new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            created.addPlayer(player);
            return created;
        });
        bar.setName(Component.literal(format(remainingTicks)));
        bar.setProgress(totalTicks <= 0 ? 0f : Math.min(1f, (float) remainingTicks / totalTicks));
    }

    void remove(UUID crawler) {
        ServerBossEvent bar = bars.remove(crawler);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    void clear() {
        bars.values().forEach(ServerBossEvent::removeAllPlayers);
        bars.clear();
    }

    static String format(long remainingTicks) {
        long seconds = (remainingTicks + 19) / 20;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
