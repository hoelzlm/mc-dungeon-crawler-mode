package dev.crawlmode.engine;

public record CrawlConfig(long apocalypseDelayTicks, long warningLeadTicks, long floorTimerTicks) {
    public static final long DEFAULT_FLOOR_TIMER_TICKS = 90 * 60 * 20L;
    public static final long TICKS_PER_DAY = 24_000L;
    public static final CrawlConfig DEFAULT = new CrawlConfig(3 * TICKS_PER_DAY, TICKS_PER_DAY / 4, DEFAULT_FLOOR_TIMER_TICKS);

    public CrawlConfig(long apocalypseDelayTicks) {
        this(apocalypseDelayTicks, 0);
    }

    public CrawlConfig(long apocalypseDelayTicks, long warningLeadTicks) {
        this(apocalypseDelayTicks, warningLeadTicks, DEFAULT_FLOOR_TIMER_TICKS);
    }
}
