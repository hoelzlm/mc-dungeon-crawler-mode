package dev.crawlmode.adapter;

import dev.crawlmode.CrawlMode;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

final class Floors {
    private static final String NAMESPACE = CrawlMode.MOD_ID;
    private static final String PREFIX = "floor_";

    private Floors() {}

    static ResourceKey<Level> key(int floor) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(NAMESPACE, PREFIX + floor));
    }

    static <T> Optional<T> lookup(Level level, Map<Integer, T> byFloor) {
        return numberOf(level).stream().mapToObj(byFloor::get).filter(Objects::nonNull).findFirst();
    }

    static OptionalInt numberOf(Level level) {
        Identifier id = level.dimension().identifier();
        if (!id.getNamespace().equals(NAMESPACE) || !id.getPath().startsWith(PREFIX)) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(id.getPath().substring(PREFIX.length())));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
