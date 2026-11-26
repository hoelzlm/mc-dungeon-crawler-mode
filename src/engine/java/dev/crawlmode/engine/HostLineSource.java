package dev.crawlmode.engine;

import java.util.Optional;

public interface HostLineSource {
    Optional<HostLine> lineFor(String topic);

    HostLineSource SILENT = topic -> Optional.empty();
}
