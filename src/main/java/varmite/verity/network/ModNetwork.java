package varmite.verity.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
   private static final String PROTOCOL_VERSION = "1";
   public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
      ResourceLocation.fromNamespaceAndPath("verity", "main"), () -> "1", "1"::equals, "1"::equals
   );
   private static int packetId = 0;

   public static void register() {
      INSTANCE.registerMessage(packetId++, PlayTtsPayload.class, PlayTtsPayload::encode, PlayTtsPayload::decode, PlayTtsPayload::handle);
   }
}
