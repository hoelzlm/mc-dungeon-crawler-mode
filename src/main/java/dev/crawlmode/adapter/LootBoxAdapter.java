package dev.crawlmode.adapter;

import dev.crawlmode.engine.Effect;
import dev.crawlmode.engine.LootItem;
import dev.crawlmode.engine.LootTables;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/** Carries out Loot Box effects. Kept apart from CrawlAdapter; no rules live here. */
final class LootBoxAdapter {
    private static final String TABLES_RESOURCE = "/data/crawlmode/loot/tables.txt";

    private LootBoxAdapter() {}

    static LootTables loadTables() {
        try (InputStream in = LootBoxAdapter.class.getResourceAsStream(TABLES_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + TABLES_RESOURCE);
            }
            return LootTables.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void animate(MinecraftServer server, Effect.LootBoxOpening opening) {
        ServerPlayer player = server.getPlayerList().getPlayer(opening.crawler());
        if (player == null) {
            return;
        }
        player.sendSystemMessage(Component.literal("A " + opening.tier().key() + " Loot Box bursts open!"));
        player.connection.send(new ClientboundSoundPacket(
                Holder.direct(SoundEvents.CHEST_OPEN), SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(), 1.0f, 1.0f, player.getRandom().nextLong()));
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.FIREWORK, player.getX(), player.getY() + 1, player.getZ(),
                30, 0.5, 0.8, 0.5, 0.05);
    }

    static void grant(MinecraftServer server, Effect.GrantLoot grant) {
        ServerPlayer player = server.getPlayerList().getPlayer(grant.crawler());
        if (player == null) {
            return;
        }
        for (LootItem item : grant.items()) {
            ItemStack stack = stackFor(server, item);
            if (!stack.isEmpty()) {
                if (!player.getInventory().add(stack)) { if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) player.spawnAtLocation(sl, stack); }
            }
        }
    }

    private static ItemStack stackFor(MinecraftServer server, LootItem item) {
        if (item.kind() == LootItem.Kind.AID) {
            return AidAdapter.stack(item.id(), item.count());
        }
        Identifier id = Identifier.parse(item.id());
        var holder = BuiltInRegistries.ITEM.get(id);
        if (holder.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(holder.get(), item.count());
        var enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        item.enchants().forEach((enchantId, level) ->
                enchantments.get(net.minecraft.resources.ResourceKey.create(
                                Registries.ENCHANTMENT, Identifier.parse(enchantId)))
                        .ifPresent((Holder.Reference<Enchantment> e) -> stack.enchant(e, level)));
        return stack;
    }
}
