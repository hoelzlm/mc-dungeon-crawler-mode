package dev.crawlmode.engine;

/** Test clock: time only moves when the test says so. */
final class FakeClock implements Clock {
    private long ticks;

    void advance(long byTicks) {
        ticks += byTicks;
    }

    @Override
    public long nowTicks() {
        return ticks;
    }
}
