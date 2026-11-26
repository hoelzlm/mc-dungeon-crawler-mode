package dev.crawlmode;

import dev.crawlmode.adapter.CrawlAdapter;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(CrawlMode.MOD_ID)
public final class CrawlMode {
    public static final String MOD_ID = "crawlmode";

    public CrawlMode(IEventBus modBus) {
        CrawlAdapter.register(modBus);
    }
}
