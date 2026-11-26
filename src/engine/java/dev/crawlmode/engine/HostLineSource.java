package dev.crawlmode.engine;

import java.util.Optional;

/**
 * Where The Host's lines come from. The v1 implementation reads data files; an LLM-backed source
 * can be added later without touching the engine.
 */
public interface HostLineSource {
    /** A line for the topic, or empty when The Host has nothing to say about it. */
    Optional<HostLine> lineFor(String topic);

    /** A source that never speaks. */
    HostLineSource SILENT = topic -> Optional.empty();
}
