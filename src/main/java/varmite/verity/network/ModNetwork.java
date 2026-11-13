package varmite.verity.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetwork {
   private static final String PROTOCOL_VERSION = "1";

   public static void register(IEventBus modEventBus) {
      modEventBus.addListener(ModNetwork::registerPayloads);
   }

   private static void registerPayloads(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
      registrar.playToClient(PlayTtsPayload.TYPE, PlayTtsPayload.STREAM_CODEC, PlayTtsPayload::handle);
      registrar.playToClient(KarmaSyncS2CPacket.TYPE, KarmaSyncS2CPacket.STREAM_CODEC, KarmaSyncS2CPacket::handle);
   }
}
