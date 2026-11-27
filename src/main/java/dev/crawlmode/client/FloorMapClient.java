package dev.crawlmode.client;

import dev.crawlmode.CrawlMode;
import dev.crawlmode.engine.aids.Area;
import dev.crawlmode.network.PaintMapAreaPayload;
import dev.crawlmode.network.RevealMapPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = CrawlMode.MOD_ID, value = Dist.CLIENT)
public final class FloorMapClient {
    private static final FloorMapView VIEW = new FloorMapView();
    private static final int PIXELS_PER_BLOCK = 2;
    private static final int MARGIN = 4;
    private static final int SCAN_DEPTH = 16;
    private static final int OPAQUE = 0xFF000000;
    private static final int UNLOADED = 0xFF202020;
    private static final int VOID = 0xFF101010;
    private static final int UNREVEALED = 0xB0000000;
    private static final int FRAME = 0xFFFFFFFF;
    private static final int SELF = 0xFFFFFFFF;
    private static final int PAINTED = 0x6040C0FF;

    private FloorMapClient() {}

    public static void receive(RevealMapPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            VIEW.reveal(payload.floor(), level.dimension(), payload.spans());
        }
    }

    public static void receive(PaintMapAreaPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            VIEW.paint(payload.floor(), level.dimension(), payload.area());
        }
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(CrawlMode.MOD_ID, "floor_map"),
                FloorMapClient::drawMinimap);
    }

    private static void drawMinimap(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || minecraft.gui.screen() != null || VIEW.isEmpty()
                || !level.dimension().equals(VIEW.dimension())) {
            return;
        }
        int size = MinimapSettings.SIZE.getAsInt();
        int left = switch (MinimapSettings.CORNER.get()) {
            case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
            case TOP_RIGHT, BOTTOM_RIGHT -> graphics.guiWidth() - size - MARGIN;
        };
        int top = switch (MinimapSettings.CORNER.get()) {
            case TOP_LEFT, TOP_RIGHT -> MARGIN;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> graphics.guiHeight() - size - MARGIN;
        };
        int centreX = minecraft.player.getBlockX();
        int centreZ = minecraft.player.getBlockZ();
        int surfaceY = minecraft.player.getBlockY() + 1;
        int blocks = size / PIXELS_PER_BLOCK;
        int west = centreX - blocks / 2;
        int north = centreZ - blocks / 2;

        graphics.fill(left - 1, top - 1, left + size + 1, top + size + 1, FRAME);
        graphics.fill(left, top, left + size, top + size, UNREVEALED);
        for (int row = 0; row < blocks; row++) {
            int z = north + row;
            int runStart = -1;
            int runColour = 0;
            for (int column = 0; column <= blocks; column++) {
                int x = west + column;
                int colour = column < blocks && VIEW.isRevealed(x, z) ? colourOf(level, x, z, surfaceY) : 0;
                if (colour != runColour || column == blocks) {
                    if (runColour != 0) {
                        graphics.fill(left + runStart * PIXELS_PER_BLOCK, top + row * PIXELS_PER_BLOCK,
                                left + column * PIXELS_PER_BLOCK, top + (row + 1) * PIXELS_PER_BLOCK, runColour);
                    }
                    runStart = column;
                    runColour = colour;
                }
            }
        }
        for (Area area : VIEW.painted()) {
            int fromX = Math.max(area.minX(), west);
            int toX = Math.min(area.maxX(), west + blocks - 1);
            int fromZ = Math.max(area.minZ(), north);
            int toZ = Math.min(area.maxZ(), north + blocks - 1);
            if (fromX <= toX && fromZ <= toZ) {
                graphics.fill(left + (fromX - west) * PIXELS_PER_BLOCK, top + (fromZ - north) * PIXELS_PER_BLOCK,
                        left + (toX - west + 1) * PIXELS_PER_BLOCK, top + (toZ - north + 1) * PIXELS_PER_BLOCK, PAINTED);
            }
        }
        int middle = size / 2;
        graphics.fill(left + middle - 1, top + middle - 1, left + middle + 2, top + middle + 2, SELF);
    }

    private static int colourOf(ClientLevel level, int x, int z, int surfaceY) {
        int known = VIEW.colourAt(x, z);
        if (known != FloorMapView.UNSAMPLED) {
            return known;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) {
            return UNLOADED;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, surfaceY, z);
        int bottom = Math.max(level.getMinY(), surfaceY - SCAN_DEPTH);
        int colour = VOID;
        for (int y = surfaceY; y >= bottom; y--) {
            pos.setY(y);
            BlockState state = chunk.getBlockState(pos);
            if (!state.isAir()) {
                colour = OPAQUE | state.getMapColor(level, pos).col;
                break;
            }
        }
        VIEW.setColour(x, z, colour);
        return colour;
    }
}
