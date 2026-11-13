package varmite.verity;

import net.minecraft.network.FriendlyByteBuf;

public record SetEntityTalkingPacket(int entityId, int durationTicks) {
   public void encode(FriendlyByteBuf buffer) {
      buffer.writeVarInt(this.entityId);
      buffer.writeVarInt(this.durationTicks);
   }

   public static SetEntityTalkingPacket decode(FriendlyByteBuf buffer) {
      return new SetEntityTalkingPacket(buffer.readVarInt(), buffer.readVarInt());
   }
}
