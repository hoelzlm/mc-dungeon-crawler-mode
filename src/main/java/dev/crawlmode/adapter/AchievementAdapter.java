package dev.crawlmode.adapter;

import dev.crawlmode.engine.Achievements;
import dev.crawlmode.engine.Event;
import dev.crawlmode.engine.SillyTriggers;
import dev.crawlmode.engine.faction.Rank;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

final class AchievementAdapter {
    private static final String RESOURCE = "/data/crawlmode/achievement/achievements.txt";
    static final String TAG_RANK = "crawlmode_rank:";

    private AchievementAdapter() {}

    static Achievements load() {
        try (InputStream in = AchievementAdapter.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            return Achievements.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Optional<Event> eventFor(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof ServerPlayer player) {
            return event.getSource().is(DamageTypeTags.IS_FALL)
                    ? Optional.of(new Event.Triggered(player.getUUID(), "death.fall")) : Optional.empty();
        }
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof ServerPlayer player)) {
            return Optional.empty();
        }
        for (String tag : victim.entityTags()) {
            if (tag.startsWith(TAG_RANK)) {
                for (Rank rank : Rank.values()) {
                    if (rank.key().equals(tag.substring(TAG_RANK.length()))) {
                        return Optional.of(new Event.MobKilled(player.getUUID(), rank));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static final SillyTriggers.Tally TALLY = new SillyTriggers.Tally();

    static void registerSilly(Consumer<Event> sink) {
        NeoForge.EVENT_BUS.addListener((LivingEvent.LivingJumpEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                TALLY.count(player.getUUID(), "jump").forEach(t -> sink.accept(new Event.Triggered(player.getUUID(), t)));
            }
        });
        NeoForge.EVENT_BUS.addListener((LivingEntityUseItemEvent.Finish e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                String item = BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).toString();
                SillyTriggers.eat(item).ifPresent(t -> sink.accept(new Event.Triggered(player.getUUID(), t)));
            }
        });
    }

    static List<Event> sillyEventsFor(LivingDeathEvent event) {
        List<Event> out = new ArrayList<>();
        LivingEntity victim = event.getEntity();
        if (victim instanceof ServerPlayer player) {
            String type = event.getSource().typeHolder().getRegisteredName();
            SillyTriggers.death(type).ifPresent(t -> out.add(new Event.Triggered(player.getUUID(), t)));
        } else if (event.getSource().getEntity() instanceof ServerPlayer player) {
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
            SillyTriggers.kill(type).ifPresent(t -> out.add(new Event.Triggered(player.getUUID(), t)));
            List<String> kinds = new ArrayList<>(List.of("kill.any"));
            if (victim.entityTags().contains(TAG_RANK + Rank.GRUNT.key())) {
                kinds.add("kill.grunt");
            }
            for (String kind : kinds) {
                TALLY.count(player.getUUID(), kind).forEach(t -> out.add(new Event.Triggered(player.getUUID(), t)));
            }
        }
        return out;
    }
}
