package dev.crawlmode.engine;

import java.util.UUID;

/** What the engine wants done in the world, for the adapter to carry out. */
public sealed interface Effect {
    record PullToFloor(UUID crawler, int floor) implements Effect {}
}
