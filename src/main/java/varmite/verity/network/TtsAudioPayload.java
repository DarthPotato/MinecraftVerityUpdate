package varmite.verity.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import varmite.verity.client.audio.SharedVoicePlayer;

/** One piece of a voice line made by the server in Host Mode: 16-bit signed little-endian PCM. */
public record TtsAudioPayload(int entityId, int lineId, int index, boolean last, int sampleRate, int sampleBits, int channels, byte[] data)
   implements CustomPacketPayload {
   public static final int MAX_DATA = 64 * 1024;
   public static final CustomPacketPayload.Type<TtsAudioPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("verity", "tts_audio"));
   public static final StreamCodec<RegistryFriendlyByteBuf, TtsAudioPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
      buf.writeVarInt(payload.entityId);
      buf.writeVarInt(payload.lineId);
      buf.writeVarInt(payload.index);
      buf.writeBoolean(payload.last);
      buf.writeVarInt(payload.sampleRate);
      buf.writeVarInt(payload.sampleBits);
      buf.writeVarInt(payload.channels);
      buf.writeByteArray(payload.data);
   }, buf -> new TtsAudioPayload(
      buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(MAX_DATA)
   ));

   @Override
   public CustomPacketPayload.Type<TtsAudioPayload> type() {
      return TYPE;
   }

   public static void handle(TtsAudioPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> SharedVoicePlayer.accept(payload));
   }
}
