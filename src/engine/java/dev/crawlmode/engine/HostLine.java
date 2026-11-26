package dev.crawlmode.engine;

/** One thing The Host says, filed under the topic that triggers it. */
public record HostLine(String topic, String text) {}
