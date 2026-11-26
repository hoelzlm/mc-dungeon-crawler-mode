package dev.crawlmode.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

public final class DataFileHostLineSource implements HostLineSource {
    private final Map<String, List<HostLine>> byTopic;
    private final IntUnaryOperator pick;

    private DataFileHostLineSource(Map<String, List<HostLine>> byTopic, IntUnaryOperator pick) {
        this.byTopic = byTopic;
        this.pick = pick;
    }

    public static DataFileHostLineSource load(Reader reader, IntUnaryOperator pick) {
        Map<String, List<HostLine>> byTopic = new HashMap<>();
        try (BufferedReader in = new BufferedReader(reader)) {
            String raw;
            int number = 0;
            while ((raw = in.readLine()) != null) {
                number++;
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon < 0) {
                    throw new HostLineFormatException("line " + number + ": expected 'topic: text'");
                }
                String topic = line.substring(0, colon).strip();
                String text = line.substring(colon + 1).strip();
                if (topic.isEmpty()) {
                    throw new HostLineFormatException("line " + number + ": missing topic");
                }
                if (text.isEmpty()) {
                    throw new HostLineFormatException("line " + number + ": missing text");
                }
                byTopic.computeIfAbsent(topic, t -> new ArrayList<>()).add(new HostLine(topic, text));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new DataFileHostLineSource(byTopic, pick);
    }

    @Override
    public Optional<HostLine> lineFor(String topic) {
        List<HostLine> lines = byTopic.get(topic);
        if (lines == null) {
            return Optional.empty();
        }
        return Optional.of(lines.get(pick.applyAsInt(lines.size())));
    }
}
