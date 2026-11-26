package dev.crawlmode.adapter;

import net.minecraft.network.chat.Component;

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
