package dev.crawlmode.adapter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

final class Inventories {
    private Inventories() {}

    static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && player.level() instanceof ServerLevel level) {
            player.spawnAtLocation(level, stack);
        }
    }
}
