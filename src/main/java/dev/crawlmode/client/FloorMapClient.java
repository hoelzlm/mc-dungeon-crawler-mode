package dev.crawlmode.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.crawlmode.CrawlMode;
import dev.crawlmode.engine.aids.Area;
import dev.crawlmode.engine.map.MapCell;
import dev.crawlmode.engine.map.MapDot;
import dev.crawlmode.network.MapDoorsPayload;
import dev.crawlmode.network.MapDotsPayload;
import dev.crawlmode.network.PaintMapAreaPayload;
import dev.crawlmode.network.RevealMapPayload;
import dev.crawlmode.network.ShowFloorMapPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
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
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
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
    private static final int DOOR = 0xFFFFC832;
    private static final int DOOR_OUTLINE = 0xFF000000;
    private static final int DOOR_RADIUS = 3;
    private static final int DOT_SIZE = 3;
    private static final int MONSTER_DOT = 0xFFFF2020;
    private static final int CRAWLER_DOT = 0xFF3060FF;
    private static final int NPC_DOT = 0xFFFFFFFF;

    private static final int FULL_SCREEN_PIXELS_PER_BLOCK = 3;
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(CrawlMode.MOD_ID, "crawl"));
    private static final KeyMapping FULL_SCREEN_KEY =
            new KeyMapping("key.crawlmode.floor_map", InputConstants.KEY_M, CATEGORY);

    private FloorMapClient() {}

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(FULL_SCREEN_KEY);
    }

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

    public static void receive(MapDoorsPayload payload) {
        VIEW.showDoors(payload.floor(), payload.doors());
    }

    public static void receive(ShowFloorMapPayload payload) {
        VIEW.clear();
        ClientLevel level = Minecraft.getInstance().level;
        if (payload.visible() && level != null) {
            VIEW.reveal(payload.floor(), level.dimension(), payload.spans());
        }
    }

    public static void receive(MapDotsPayload payload) {
        VIEW.showDots(payload.floor(), payload.dots());
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(CrawlMode.MOD_ID, "floor_map"),
                FloorMapClient::drawFloorMap);
    }

    private static void drawFloorMap(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!mapVisible(minecraft)) {
            return;
        }
        if (FULL_SCREEN_KEY.isDown()) {
            drawMap(graphics, minecraft, 0, 0, graphics.guiWidth(), graphics.guiHeight(), FULL_SCREEN_PIXELS_PER_BLOCK);
            return;
        }
        int size = FloorMapSettings.SIZE.getAsInt();
        int left = switch (FloorMapSettings.CORNER.get()) {
            case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
            case TOP_RIGHT, BOTTOM_RIGHT -> graphics.guiWidth() - size - MARGIN;
        };
        int top = switch (FloorMapSettings.CORNER.get()) {
            case TOP_LEFT, TOP_RIGHT -> MARGIN;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> graphics.guiHeight() - size - MARGIN;
        };
        drawMap(graphics, minecraft, left, top, size, size, PIXELS_PER_BLOCK);
    }

    private static boolean mapVisible(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        return level != null && minecraft.player != null && !minecraft.player.isSpectator()
                && minecraft.gui.screen() == null && !VIEW.isEmpty() && level.dimension().equals(VIEW.dimension());
    }

    private static void drawMap(GuiGraphicsExtractor graphics, Minecraft minecraft, int left, int top, int width,
            int height, int pixelsPerBlock) {
        ClientLevel level = minecraft.level;
        int centreX = minecraft.player.getBlockX();
        int centreZ = minecraft.player.getBlockZ();
        int surfaceY = minecraft.player.getBlockY() + 1;
        int blocksWide = width / pixelsPerBlock;
        int blocksHigh = height / pixelsPerBlock;
        int west = centreX - blocksWide / 2;
        int north = centreZ - blocksHigh / 2;

        graphics.fill(left - 1, top - 1, left + width + 1, top + height + 1, FRAME);
        graphics.fill(left, top, left + width, top + height, UNREVEALED);
        for (int row = 0; row < blocksHigh; row++) {
            int z = north + row;
            int runStart = -1;
            int runColour = 0;
            for (int column = 0; column <= blocksWide; column++) {
                int x = west + column;
                int colour = column < blocksWide && VIEW.isRevealed(x, z) ? colourOf(level, x, z, surfaceY) : 0;
                if (colour != runColour || column == blocksWide) {
                    if (runColour != 0) {
                        graphics.fill(left + runStart * pixelsPerBlock, top + row * pixelsPerBlock,
                                left + column * pixelsPerBlock, top + (row + 1) * pixelsPerBlock, runColour);
                    }
                    runStart = column;
                    runColour = colour;
                }
            }
        }
        for (Area area : VIEW.painted()) {
            int fromX = Math.max(area.minX(), west);
            int toX = Math.min(area.maxX(), west + blocksWide - 1);
            int fromZ = Math.max(area.minZ(), north);
            int toZ = Math.min(area.maxZ(), north + blocksHigh - 1);
            if (fromX <= toX && fromZ <= toZ) {
                graphics.fill(left + (fromX - west) * pixelsPerBlock, top + (fromZ - north) * pixelsPerBlock,
                        left + (toX - west + 1) * pixelsPerBlock, top + (toZ - north + 1) * pixelsPerBlock, PAINTED);
            }
        }
        for (MapCell door : VIEW.doors()) {
            int doorX = left + (door.x() - west) * pixelsPerBlock;
            int doorY = top + (door.z() - north) * pixelsPerBlock;
            if (doorX >= left && doorX < left + width && doorY >= top && doorY < top + height) {
                graphics.fill(doorX - DOOR_RADIUS - 1, doorY - DOOR_RADIUS - 1, doorX + DOOR_RADIUS + 2,
                        doorY + DOOR_RADIUS + 2, DOOR_OUTLINE);
                graphics.fill(doorX - DOOR_RADIUS, doorY - DOOR_RADIUS, doorX + DOOR_RADIUS + 1,
                        doorY + DOOR_RADIUS + 1, DOOR);
            }
        }
        for (MapDot dot : VIEW.dots()) {
            int dotX = (dot.x() - west) * pixelsPerBlock;
            int dotZ = (dot.z() - north) * pixelsPerBlock;
            if (dotX >= 0 && dotZ >= 0 && dotX + DOT_SIZE <= width && dotZ + DOT_SIZE <= height) {
                graphics.fill(left + dotX, top + dotZ, left + dotX + DOT_SIZE, top + dotZ + DOT_SIZE, colourOf(dot));
            }
        }
        int middleX = width / 2;
        int middleY = height / 2;
        graphics.fill(left + middleX - 1, top + middleY - 1, left + middleX + 2, top + middleY + 2, SELF);
    }

    private static int colourOf(MapDot dot) {
        return switch (dot.kind()) {
            case MONSTER -> MONSTER_DOT;
            case CRAWLER -> CRAWLER_DOT;
            case NPC -> NPC_DOT;
        };
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
