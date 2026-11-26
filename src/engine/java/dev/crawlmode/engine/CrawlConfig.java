package dev.crawlmode.engine;

/**
 * Engine settings, in ticks. {@code apocalypseDelayTicks} is measured from engine creation;
 * {@code warningLeadTicks} is how long before the Apocalypse the Host gives its last warning.
 */
public record CrawlConfig(long apocalypseDelayTicks, long warningLeadTicks) {
    public static final long TICKS_PER_DAY = 24_000L;
    public static final CrawlConfig DEFAULT = new CrawlConfig(3 * TICKS_PER_DAY, TICKS_PER_DAY / 4);

    public CrawlConfig(long apocalypseDelayTicks) {
        this(apocalypseDelayTicks, 0);
    }
}
