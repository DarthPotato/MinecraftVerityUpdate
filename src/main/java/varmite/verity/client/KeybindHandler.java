package varmite.verity.client;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import varmite.verity.client.audio.MicrophoneManager;
import varmite.verity.client.audio.MicrophoneRecorder;
import varmite.verity.client.audio.TTSHandler;
import varmite.verity.client.gui.OllamaProgressHud;

@EventBusSubscriber(modid = "verity", value = Dist.CLIENT)
public class KeybindHandler {
   private static boolean isRecording = false;
   private static int releasePaddingTicks = 0;
   private static final MicrophoneRecorder RECORDER = new MicrophoneRecorder();

   public static boolean isRecording() {
      return isRecording;
   }

   public static MicrophoneRecorder getRecorder() {
      return RECORDER;
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         if (KeybindRegistry.PUSH_TO_TALK != null && KeybindRegistry.CYCLE_MIC != null) {
            if (KeybindRegistry.CYCLE_MIC.consumeClick()) {
               MicrophoneManager.cycleMicrophone();
            }

            while (KeybindRegistry.TOGGLE_OLLAMA_PROGRESS.consumeClick()) {
               OllamaProgressHud.toggle();
            }

            boolean isKeyDown = KeybindRegistry.PUSH_TO_TALK.isDown();
            if (isKeyDown) {
               releasePaddingTicks = 0;
               if (!isRecording) {
                  isRecording = true;
                  TTSHandler.interruptSpeech();
                  RECORDER.startRecording();
               }
            } else if (isRecording) {
               if (releasePaddingTicks < 5) {
                  releasePaddingTicks++;
                  return;
               }

               isRecording = false;
               releasePaddingTicks = 0;
               byte[] recordedAudio = RECORDER.stopRecording();
               if (recordedAudio != null && recordedAudio.length > 0) {
                  CompletableFuture.<String>supplyAsync(() -> TTSHandler.transcribeAudio(recordedAudio, RECORDER.getAudioFormat()))
                     .thenAccept(KeybindHandler::accept);
               }
            }
         }
      }
   }

   private static void accept(String transcribedText) {
      if (transcribedText != null && !transcribedText.trim().isEmpty()) {
         String sanitized = TTSHandler.sanitizeTranscript(transcribedText);
         String lowerCaseText = sanitized.trim().toLowerCase();
         if (lowerCaseText.equals("thank you.")) {
            return;
         }

         Minecraft.getInstance().execute(() -> {
            String safeText = sanitized.length() > 256 ? sanitized.substring(0, 256) : sanitized;
            if (Minecraft.getInstance().getConnection() != null) {
               Minecraft.getInstance().getConnection().sendChat(safeText);
            }
         });
      }
   }
}
