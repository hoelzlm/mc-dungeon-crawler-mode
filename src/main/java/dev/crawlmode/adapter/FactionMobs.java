package dev.crawlmode.adapter;

import dev.crawlmode.engine.faction.Ability;
import dev.crawlmode.engine.faction.Factions;
import dev.crawlmode.engine.faction.GearSlot;
import dev.crawlmode.engine.faction.MobDef;
import dev.crawlmode.engine.faction.PlannedSpawn;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Turns {@link PlannedSpawn}s from the engine into vanilla mobs with custom names, stats and gear, and runs Shaman
 * abilities. What spawns where, and every number, is decided by the engine and the faction data file. A mob carries
 * its Faction and abilities as entity tags, so nothing has to be remembered across restarts.
 */
final class FactionMobs {
    private static final String RESOURCE = "/data/crawlmode/faction/factions.txt";
    private static final String TAG_MOB = "crawlmode_mob";
    private static final String TAG_FACTION = "crawlmode_faction:";
    private static final String TAG_ABILITY = "crawlmode_ability:";
    /** Shamans act this often (ticks); each is staggered by its entity id. */
    private static final int HEAL_EVERY = 80;
    private static final int RALLY_EVERY = 240;
    private static final int FIREBALL_EVERY = 70;
    private static final double AWARE_RADIUS = 48;
    private static final double SUPPORT_RADIUS = 12;

    private static Factions factions;

    private FactionMobs() {}

    static Factions factions() {
        if (factions == null) {
            try (InputStream in = FactionMobs.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("Missing " + RESOURCE);
                }
                factions = Factions.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return factions;
    }

    static void clear() {
        factions = null;
    }

    static void spawnAll(ServerLevel level, List<PlannedSpawn> spawns) {
        for (PlannedSpawn spawn : spawns) {
            spawn(level, spawn);
        }
    }

    private static void spawn(ServerLevel level, PlannedSpawn spawn) {
        MobDef def = spawn.mob();
        var type = BuiltInRegistries.ENTITY_TYPE.get(Identifier.parse(def.entity()));
        if (type.isEmpty()) {
            return;
        }
        BlockPos pos = new BlockPos(spawn.x(), spawn.y(), spawn.z());
        // Pillars and furniture may stand where the planner picked: nudge up to two blocks, else skip.
        for (int i = 0; i < 3 && !(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()); i++) {
            pos = pos.above();
        }
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return;
        }
        Object created = type.get().value().create(level, EntitySpawnReason.EVENT);
        if (!(created instanceof Mob mob)) {
            return;
        }
        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        mob.setCustomName(Component.literal(def.name()));
        mob.setCustomNameVisible(true);
        mob.setPersistenceRequired();
        stat(mob, Attributes.MAX_HEALTH, def.health());
        mob.setHealth(def.health());
        stat(mob, Attributes.ATTACK_DAMAGE, def.damage());
        def.gear().forEach((slot, itemId) -> equip(mob, slot, itemId));
        mob.addTag(TAG_MOB);
        mob.addTag(TAG_FACTION + def.factionId());
        def.abilities().forEach(a -> mob.addTag(TAG_ABILITY + a.key()));
        level.addFreshEntity(mob);
    }

    private static void stat(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        var instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static void equip(Mob mob, GearSlot slot, String itemId) {
        var item = BuiltInRegistries.ITEM.get(Identifier.parse(itemId));
        if (item.isEmpty()) {
            return;
        }
        EquipmentSlot target = switch (slot) {
            case MAINHAND -> EquipmentSlot.MAINHAND;
            case OFFHAND -> EquipmentSlot.OFFHAND;
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
        };
        mob.setItemSlot(target, new ItemStack(item.get().value()));
        mob.setDropChance(target, 0.05f);
    }

    /** Runs Shaman abilities for Shamans near a Crawler. Call every server tick; cheap when nobody is near. */
    static void tick(ServerLevel level) {
        long time = level.getGameTime();
        for (var player : level.players()) {
            AABB area = player.getBoundingBox().inflate(AWARE_RADIUS);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, area, m -> m.isAlive() && m.entityTags().contains(TAG_MOB))) {
                long phase = time + mob.getId();
                for (Ability ability : Ability.values()) {
                    if (!mob.entityTags().contains(TAG_ABILITY + ability.key())) {
                        continue;
                    }
                    switch (ability) {
                        case HEAL_ALLIES -> {
                            if (phase % HEAL_EVERY == 0) {
                                healAllies(level, mob);
                            }
                        }
                        case RALLY -> {
                            if (phase % RALLY_EVERY == 0) {
                                rally(level, mob);
                            }
                        }
                        case FIREBALL -> {
                            if (phase % FIREBALL_EVERY == 0) {
                                fireball(level, mob);
                            }
                        }
                    }
                }
            }
        }
    }

    private static List<Mob> allies(ServerLevel level, Mob shaman) {
        String faction = shaman.entityTags().stream().filter(t -> t.startsWith(TAG_FACTION)).findFirst().orElse("");
        return level.getEntitiesOfClass(Mob.class, shaman.getBoundingBox().inflate(SUPPORT_RADIUS),
                m -> m != shaman && m.isAlive() && m.entityTags().contains(faction));
    }

    private static void healAllies(ServerLevel level, Mob shaman) {
        for (Mob ally : allies(level, shaman)) {
            if (ally.getHealth() < ally.getMaxHealth()) {
                ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            }
        }
    }

    private static void rally(ServerLevel level, Mob shaman) {
        if (shaman.getTarget() == null) {
            return;
        }
        for (Mob ally : allies(level, shaman)) {
            ally.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 0));
            ally.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0));
        }
    }

    private static void fireball(ServerLevel level, Mob shaman) {
        LivingEntity target = shaman.getTarget();
        if (target == null || shaman.distanceToSqr(target) > 24 * 24 || !shaman.hasLineOfSight(target)) {
            return;
        }
        Vec3 direction = target.getEyePosition().subtract(shaman.getEyePosition()).normalize().scale(0.8);
        SmallFireball ball = new SmallFireball(level, shaman, direction);
        ball.setPos(shaman.getX() + direction.x, shaman.getEyeY() - 0.2, shaman.getZ() + direction.z);
        level.addFreshEntity(ball);
    }
}
