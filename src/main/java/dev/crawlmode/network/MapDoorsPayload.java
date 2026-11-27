package dev.crawlmode.network;

import dev.crawlmode.CrawlMode;
import dev.crawlmode.engine.map.MapCell;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MapDoorsPayload(int floor, List<MapCell> doors) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MapDoorsPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CrawlMode.MOD_ID, "map_doors"));
    public static final StreamCodec<FriendlyByteBuf, MapDoorsPayload> CODEC =
            StreamCodec.ofMember(MapDoorsPayload::write, MapDoorsPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(floor);
        buf.writeVarInt(doors.size());
        for (MapCell door : doors) {
            buf.writeInt(door.x());
            buf.writeInt(door.z());
        }
    }

    private static MapDoorsPayload read(FriendlyByteBuf buf) {
        int floor = buf.readVarInt();
        int count = buf.readVarInt();
        List<MapCell> doors = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            doors.add(new MapCell(buf.readInt(), buf.readInt()));
        }
        return new MapDoorsPayload(floor, doors);
    }

    @Override
    public CustomPacketPayload.Type<MapDoorsPayload> type() {
        return TYPE;
    }
}
