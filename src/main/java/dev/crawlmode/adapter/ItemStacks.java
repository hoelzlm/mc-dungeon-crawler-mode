package dev.crawlmode.adapter;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

final class ItemStacks {
    private ItemStacks() {}

    static Optional<ItemStack> of(String itemId, int count) {
        return BuiltInRegistries.ITEM.get(Identifier.parse(itemId)).map(holder -> new ItemStack(holder.value(), count));
    }
}
