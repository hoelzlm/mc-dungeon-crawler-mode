package dev.crawlmode.network;

import dev.crawlmode.CrawlMode;
import dev.crawlmode.engine.map.MapSpan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RevealMapPayload(int floor, List<MapSpan> spans) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RevealMapPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CrawlMode.MOD_ID, "reveal_map"));
    public static final StreamCodec<FriendlyByteBuf, RevealMapPayload> CODEC =
            StreamCodec.ofMember(RevealMapPayload::write, RevealMapPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(floor);
        buf.writeVarInt(spans.size());
        for (MapSpan span : spans) {
            buf.writeInt(span.z());
            buf.writeInt(span.fromX());
            buf.writeInt(span.toX());
        }
    }

    private static RevealMapPayload read(FriendlyByteBuf buf) {
        int floor = buf.readVarInt();
        int count = buf.readVarInt();
        List<MapSpan> spans = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            spans.add(new MapSpan(buf.readInt(), buf.readInt(), buf.readInt()));
        }
        return new RevealMapPayload(floor, spans);
    }

    @Override
    public CustomPacketPayload.Type<RevealMapPayload> type() {
        return TYPE;
    }
}
