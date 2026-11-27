package dev.crawlmode.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class FloorMapSettings {
    public enum Corner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    public static final int DEFAULT_SIZE = 128;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    static final ModConfigSpec.EnumValue<Corner> CORNER = BUILDER
            .comment("Screen corner the Floor Map sits in")
            .defineEnum("corner", Corner.TOP_RIGHT);
    static final ModConfigSpec.IntValue SIZE = BUILDER
            .comment("Width and height of the Floor Map in GUI pixels")
            .defineInRange("size", DEFAULT_SIZE, 32, 512);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private FloorMapSettings() {}
}
