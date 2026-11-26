package dev.crawlmode.engine;

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
