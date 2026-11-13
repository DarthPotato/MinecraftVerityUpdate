package varmite.verity.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

public class KarmaSyncS2CPacket {
   private final int karma;

   public KarmaSyncS2CPacket(int karma) {
      this.karma = karma;
   }

   public KarmaSyncS2CPacket(FriendlyByteBuf buf) {
      this.karma = buf.readInt();
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeInt(this.karma);
   }

   public void handle(Supplier<Context> supplier) {
      Context context = supplier.get();
      context.enqueueWork(() -> ClientKarmaData.set(this.karma));
   }
}
