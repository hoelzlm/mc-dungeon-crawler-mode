package dev.crawlmode.adapter;

import dev.crawlmode.engine.aids.Area;
import dev.crawlmode.engine.aids.StairwellAids;
import dev.crawlmode.engine.floorgen.FloorLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import dev.crawlmode.CrawlMode;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The four Stairwell Aid items and their effects. All direction, distance, area and reveal maths lives in
 * {@link StairwellAids}; this class registers the items, feeds the maths the Floor's Stairwells, and shows the
 * answer as chat text, sound and particles. Every aid is spent on use and only works on a generated Floor.
 */
final class AidAdapter {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CrawlMode.MOD_ID);
    private static final Map<String, DeferredItem<Item>> BY_ID = new HashMap<>();
    /** Short-lived particle marks (Lantern blocks, Map outline), shown every few ticks until they expire. */
    private static final List<Marks> ACTIVE = new ArrayList<>();
    private static final int PULSE_TICKS = 10;

    private record Marks(ServerLevel level, List<StairwellAids.Pos> points, ParticleOptions particle, long untilTick) {}

    private AidAdapter() {}

    static void register(IEventBus modBus) {
        for (String id : StairwellAids.ids()) {
            BY_ID.put(id, ITEMS.registerItem(id, props -> new AidItem(id, props)));
        }
        ITEMS.register(modBus);
        NeoForge.EVENT_BUS.addListener(AidAdapter::onTick);
    }

    /** A stack of the aid with this bare id, or empty if the id is not an aid. */
    static ItemStack stack(String id, int count) {
        DeferredItem<Item> item = BY_ID.get(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item.get(), count);
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        for (Iterator<Marks> it = ACTIVE.iterator(); it.hasNext();) {
            Marks marks = it.next();
            if (now >= marks.untilTick()) {
                it.remove();
            } else if (now % PULSE_TICKS == 0) {
                for (StairwellAids.Pos p : marks.points()) {
                    marks.level().sendParticles(marks.particle(), p.x() + 0.5, p.y() + 0.5, p.z() + 0.5, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private static final class AidItem extends Item {
        private final String id;

        AidItem(String id, Item.Properties props) {
            super(props.stacksTo(16));
            this.id = id;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer crawler)) {
                return InteractionResult.SUCCESS;
            }
            Optional<FloorLayout> layout = TemplateFloorAdapter.layoutFor(level);
            if (layout.isEmpty() || !activate(id, server, crawler, layout.get())) {
                crawler.sendSystemMessage(Component.literal("The aid fizzles. Nothing answers."), true);
                return InteractionResult.FAIL;
            }
            if (StairwellAids.CONSUMED_ON_USE && !crawler.getAbilities().instabuild) {
                player.getItemInHand(hand).shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
    }

    private static boolean activate(String id, ServerLevel level, ServerPlayer player, FloorLayout layout) {
        int x = player.getBlockX();
        int y = player.getBlockY();
        int z = player.getBlockZ();
        var wells = layout.stairwells();
        switch (id) {
            case StairwellAids.SEEKERS_DRAUGHT -> {
                var direction = StairwellAids.seekersDraught(x, z, wells);
                if (direction.isEmpty()) {
                    return false;
                }
                String name = direction.get().name().toLowerCase(Locale.ROOT).replace('_', '-');
                player.sendSystemMessage(Component.literal("The draught pulls you " + name + "."), true);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1f);
            }
            case StairwellAids.ECHO_CHIME -> {
                var band = StairwellAids.echoChime(x, z, wells);
                if (band.isEmpty()) {
                    return false;
                }
                float pitch = 2.0f - 0.5f * band.get().ordinal();
                player.sendSystemMessage(Component.literal("The chime rings "
                        + band.get().name().toLowerCase(Locale.ROOT).replace('_', ' ') + "."), true);
                level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1f, pitch);
            }
            case StairwellAids.REVEALING_LANTERN -> {
                var blocks = StairwellAids.revealingLantern(layout, x, y, z);
                player.sendSystemMessage(Component.literal(blocks.isEmpty()
                        ? "The lantern glows, but nothing hides nearby." : "The lantern shows what the walls hide."), true);
                mark(level, blocks, ParticleTypes.END_ROD, StairwellAids.LANTERN_REVEAL_TICKS);
            }
            case StairwellAids.FRAGMENT_MAP -> {
                Optional<Area> area = StairwellAids.fragmentMap(x, z, wells, level.getSeed());
                if (area.isEmpty()) {
                    return false;
                }
                player.sendSystemMessage(Component.literal("The map outlines an area. A Stairwell hides within."), true);
                mark(level, outline(area.get(), y + 1), ParticleTypes.HAPPY_VILLAGER, StairwellAids.LANTERN_REVEAL_TICKS);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void mark(ServerLevel level, List<StairwellAids.Pos> points, ParticleOptions particle, int ticks) {
        ACTIVE.add(new Marks(level, points, particle, level.getServer().overworld().getGameTime() + ticks));
    }

    private static List<StairwellAids.Pos> outline(Area area, int y) {
        List<StairwellAids.Pos> points = new ArrayList<>();
        for (int x = area.minX(); x <= area.maxX(); x += 2) {
            points.add(new StairwellAids.Pos(x, y, area.minZ()));
            points.add(new StairwellAids.Pos(x, y, area.maxZ()));
        }
        for (int z = area.minZ(); z <= area.maxZ(); z += 2) {
            points.add(new StairwellAids.Pos(area.minX(), y, z));
            points.add(new StairwellAids.Pos(area.maxX(), y, z));
        }
        return points;
    }
}
