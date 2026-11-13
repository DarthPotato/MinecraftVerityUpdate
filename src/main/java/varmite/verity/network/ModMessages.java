package varmite.verity.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class ModMessages {
   public static void sendToPlayer(CustomPacketPayload message, ServerPlayer player) {
      PacketDistributor.sendToPlayer(player, message);
   }
}
