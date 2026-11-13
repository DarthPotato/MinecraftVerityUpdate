package varmite.verity.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public record PlayTtsPayload(int entityId, String text) {
   public static void encode(PlayTtsPayload payload, FriendlyByteBuf buf) {
      buf.writeInt(payload.entityId);
      buf.writeUtf(payload.text);
   }

   public static PlayTtsPayload decode(FriendlyByteBuf buf) {
      int entityId = buf.readInt();
      String text = buf.readUtf(32767);
      return new PlayTtsPayload(entityId, text);
   }

   public static void handle(PlayTtsPayload payload, Supplier<Context> ctxSupplier) {
      Context ctx = ctxSupplier.get();
      ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PlayTtsClientHandler.handlePacket(payload)));
      ctx.setPacketHandled(true);
   }
}
