package dev.crawlmode.network;

import dev.crawlmode.CrawlMode;
import dev.crawlmode.engine.aids.Area;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PaintMapAreaPayload(int floor, Area area) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PaintMapAreaPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CrawlMode.MOD_ID, "paint_map_area"));
    public static final StreamCodec<FriendlyByteBuf, PaintMapAreaPayload> CODEC =
            StreamCodec.ofMember(PaintMapAreaPayload::write, PaintMapAreaPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(floor);
        buf.writeInt(area.minX());
        buf.writeInt(area.minZ());
        buf.writeInt(area.maxX());
        buf.writeInt(area.maxZ());
    }

    private static PaintMapAreaPayload read(FriendlyByteBuf buf) {
        int floor = buf.readVarInt();
        return new PaintMapAreaPayload(floor, new Area(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
    }

    @Override
    public CustomPacketPayload.Type<PaintMapAreaPayload> type() {
        return TYPE;
    }
}
