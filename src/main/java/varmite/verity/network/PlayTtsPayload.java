package varmite.verity.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlayTtsPayload(int entityId, String text) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<PlayTtsPayload> TYPE = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath("verity", "play_tts")
   );
   public static final StreamCodec<RegistryFriendlyByteBuf, PlayTtsPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.INT, PlayTtsPayload::entityId, ByteBufCodecs.STRING_UTF8, PlayTtsPayload::text, PlayTtsPayload::new
   );

   @Override
   public CustomPacketPayload.Type<PlayTtsPayload> type() {
      return TYPE;
   }

   public static void handle(PlayTtsPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> PlayTtsClientHandler.handlePacket(payload));
   }
}
