package dev.crawlmode.adapter;

import dev.crawlmode.CrawlMode;
import dev.crawlmode.engine.Effect;
import dev.crawlmode.engine.Event;
import dev.crawlmode.engine.LootItem;
import dev.crawlmode.engine.LootTables;
import dev.crawlmode.engine.LootTier;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

final class LootBoxAdapter {
    private static final String TABLES_RESOURCE = "/data/crawlmode/loot/tables.txt";

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CrawlMode.MOD_ID);
    private static final Map<LootTier, DeferredItem<Item>> BOXES = new EnumMap<>(LootTier.class);

    private LootBoxAdapter() {}

    static void register(IEventBus modBus, Consumer<Event> events) {
        for (LootTier tier : LootTier.values()) {
            BOXES.put(tier, ITEMS.registerItem(tier.key() + "_loot_box",
                    props -> new LootBoxItem(tier, events, props)));
        }
        ITEMS.register(modBus);
    }

    static void give(MinecraftServer server, Effect.GiveLootBox box) {
        ServerPlayer player = server.getPlayerList().getPlayer(box.crawler());
        if (player != null) {
            Inventories.giveOrDrop(player, new ItemStack(BOXES.get(box.tier()).get()));
        }
    }

    static void consume(MinecraftServer server, Effect.ConsumeLootBox consume) {
        ServerPlayer player = server.getPlayerList().getPlayer(consume.crawler());
        if (player == null || player.getAbilities().instabuild) {
            return;
        }
        Item box = BOXES.get(consume.tier()).get();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(box)) {
                stack.shrink(1);
                return;
            }
        }
    }

    private static final class LootBoxItem extends Item {
        private final LootTier tier;
        private final Consumer<Event> events;

        LootBoxItem(LootTier tier, Consumer<Event> events, Item.Properties props) {
            super(props.stacksTo(64));
            this.tier = tier;
            this.events = events;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (level.isClientSide() || !(player instanceof ServerPlayer crawler)) {
                return InteractionResult.SUCCESS;
            }
            events.accept(new Event.LootBoxOpened(crawler.getUUID(), tier));
            return InteractionResult.SUCCESS;
        }
    }

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
        if (player.level() instanceof ServerLevel level) level.sendParticles(
                ParticleTypes.FIREWORK, player.getX(), player.getY() + 1, player.getZ(),
                30, 0.5, 0.8, 0.5, 0.05);
    }

    static void grant(MinecraftServer server, Effect.GrantLoot grant) {
        handItems(server, grant.crawler(), grant.items());
    }

    static void handItems(MinecraftServer server, UUID crawler, List<LootItem> items) {
        ServerPlayer player = server.getPlayerList().getPlayer(crawler);
        if (player == null) {
            return;
        }
        for (LootItem item : items) {
            ItemStack stack = stackFor(server, item);
            if (!stack.isEmpty()) {
                Inventories.giveOrDrop(player, stack);
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
                enchantments.get(ResourceKey.create(
                                Registries.ENCHANTMENT, Identifier.parse(enchantId)))
                        .ifPresent((Holder.Reference<Enchantment> e) -> stack.enchant(e, level)));
        return stack;
    }
}
