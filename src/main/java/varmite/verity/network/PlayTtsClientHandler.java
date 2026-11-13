package varmite.verity.network;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import varmite.verity.client.audio.TTSHandler;
import varmite.verity.entity.verity.VerityEntity;

public class PlayTtsClientHandler {
   public static void handlePacket(PlayTtsPayload payload) {
      Player localPlayer = Minecraft.getInstance().player;
      if (localPlayer == null) {
         System.out.println("[VERITY DEBUG] Client received payload, but localPlayer is null!");
      } else {
         System.out.println("[VERITY DEBUG] Client successfully received TTS Payload: " + payload.text());
         VerityEntity verityMob = localPlayer.level().getEntity(payload.entityId()) instanceof VerityEntity v ? v : null;
         if (verityMob == null) {
            System.out.println("[VERITY DEBUG] Verity not found physically in world. Playing as 'Voice in Head'.");
         }

         TTSHandler.playTTS(payload.text(), localPlayer, verityMob);
      }
   }
}
