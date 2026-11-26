package dev.crawlmode.engine.aids;

/** A rough distance, in blocks, as the Echo Chime reports it. */
public enum DistanceBand {
    VERY_CLOSE(24), CLOSE(64), FAR(160), VERY_FAR(Integer.MAX_VALUE);

    private final int below;

    DistanceBand(int below) {
        this.below = below;
    }

    public static DistanceBand of(double blocks) {
        for (DistanceBand band : values()) {
            if (blocks < band.below) {
                return band;
            }
        }
        return VERY_FAR;
    }
}
