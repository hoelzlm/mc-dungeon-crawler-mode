package dev.crawlmode.network;

import dev.crawlmode.CrawlMode;
import dev.crawlmode.engine.map.DotKind;
import dev.crawlmode.engine.map.MapDot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MapDotsPayload(int floor, List<MapDot> dots) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MapDotsPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CrawlMode.MOD_ID, "map_dots"));
    public static final StreamCodec<FriendlyByteBuf, MapDotsPayload> CODEC =
            StreamCodec.ofMember(MapDotsPayload::write, MapDotsPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(floor);
        buf.writeVarInt(dots.size());
        for (MapDot dot : dots) {
            buf.writeEnum(dot.kind());
            buf.writeInt(dot.x());
            buf.writeInt(dot.z());
        }
    }

    private static MapDotsPayload read(FriendlyByteBuf buf) {
        int floor = buf.readVarInt();
        int count = buf.readVarInt();
        List<MapDot> dots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            dots.add(new MapDot(buf.readEnum(DotKind.class), buf.readInt(), buf.readInt()));
        }
        return new MapDotsPayload(floor, dots);
    }

    @Override
    public CustomPacketPayload.Type<MapDotsPayload> type() {
        return TYPE;
    }
}
