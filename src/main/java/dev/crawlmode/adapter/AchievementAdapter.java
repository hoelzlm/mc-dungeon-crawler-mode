package dev.crawlmode.adapter;

import dev.crawlmode.engine.Achievements;
import dev.crawlmode.engine.Event;
import dev.crawlmode.engine.faction.Rank;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Turns game deaths into Achievement events. Kept apart from CrawlAdapter; no rules live here. */
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

    /** The engine event for a death, if it matters to Achievements. Bosses are reported by their own code. */
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
}
