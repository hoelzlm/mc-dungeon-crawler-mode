package dev.crawlmode.adapter;

import net.minecraft.network.chat.Component;

/** The Host's chat voice: every chat line it speaks carries the same {@code [The Host]} prefix. */
final class HostChat {
    private static final String PREFIX = "[The Host] ";

    private HostChat() {}

    static Component say(String text) {
        return say(Component.literal(text));
    }

    static Component say(Component text) {
        return Component.literal(PREFIX).append(text);
    }
}
