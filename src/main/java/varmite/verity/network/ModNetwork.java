package varmite.verity.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetwork {
   // 2: Host Mode audio. Clients and servers must match.
   private static final String PROTOCOL_VERSION = "2";

   public static void register(IEventBus modEventBus) {
      modEventBus.addListener(ModNetwork::registerPayloads);
   }

   private static void registerPayloads(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
      registrar.playToClient(PlayTtsPayload.TYPE, PlayTtsPayload.STREAM_CODEC, PlayTtsPayload::handle);
      registrar.playToClient(KarmaSyncS2CPacket.TYPE, KarmaSyncS2CPacket.STREAM_CODEC, KarmaSyncS2CPacket::handle);
      registrar.playToClient(TtsAudioPayload.TYPE, TtsAudioPayload.STREAM_CODEC, TtsAudioPayload::handle);
   }
}
