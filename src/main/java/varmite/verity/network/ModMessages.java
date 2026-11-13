package varmite.verity.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkRegistry.ChannelBuilder;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {
   private static SimpleChannel INSTANCE;
   private static int packetId = 0;

   private static int id() {
      return packetId++;
   }

   public static void register() {
      SimpleChannel net = ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath("verity", "messages"))
         .networkProtocolVersion(() -> "1.0")
         .clientAcceptedVersions(s -> true)
         .serverAcceptedVersions(s -> true)
         .simpleChannel();
      INSTANCE = net;
      net.messageBuilder(KarmaSyncS2CPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
         .decoder(KarmaSyncS2CPacket::new)
         .encoder(KarmaSyncS2CPacket::toBytes)
         .consumerMainThread(KarmaSyncS2CPacket::handle)
         .add();
   }

   public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
      INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
   }
}
