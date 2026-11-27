package dev.crawlmode.network;

import dev.crawlmode.CrawlMode;
import dev.crawlmode.engine.map.MapSpan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ShowFloorMapPayload(boolean visible, int floor, List<MapSpan> spans) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ShowFloorMapPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CrawlMode.MOD_ID, "show_floor_map"));
    public static final StreamCodec<FriendlyByteBuf, ShowFloorMapPayload> CODEC =
            StreamCodec.ofMember(ShowFloorMapPayload::write, ShowFloorMapPayload::read);

    public static ShowFloorMapPayload hidden() {
        return new ShowFloorMapPayload(false, 0, List.of());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(visible);
        buf.writeVarInt(floor);
        buf.writeVarInt(spans.size());
        for (MapSpan span : spans) {
            buf.writeInt(span.z());
            buf.writeInt(span.fromX());
            buf.writeInt(span.toX());
        }
    }

    private static ShowFloorMapPayload read(FriendlyByteBuf buf) {
        boolean visible = buf.readBoolean();
        int floor = buf.readVarInt();
        int count = buf.readVarInt();
        List<MapSpan> spans = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            spans.add(new MapSpan(buf.readInt(), buf.readInt(), buf.readInt()));
        }
        return new ShowFloorMapPayload(visible, floor, spans);
    }

    @Override
    public CustomPacketPayload.Type<ShowFloorMapPayload> type() {
        return TYPE;
    }
}
