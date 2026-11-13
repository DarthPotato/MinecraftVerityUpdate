package varmite.verity.client.audio;

import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.Mixer.Info;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class MicrophoneManager {
   private static final List<Info> AVAILABLE_MICS = new ArrayList<>();
   private static int currentIndex = -1;

   public static void scanForMicrophones(AudioFormat format) {
      AVAILABLE_MICS.clear();
      javax.sound.sampled.DataLine.Info lineInfo = new javax.sound.sampled.DataLine.Info(TargetDataLine.class, format);

      for (Info info : AudioSystem.getMixerInfo()) {
         Mixer mixer = AudioSystem.getMixer(info);
         if (mixer.isLineSupported(lineInfo)) {
            AVAILABLE_MICS.add(info);
         }
      }

      if (!AVAILABLE_MICS.isEmpty() && currentIndex == -1) {
         currentIndex = 0;
      }
   }

   public static Info getSelectedMicrophone() {
      return !AVAILABLE_MICS.isEmpty() && currentIndex >= 0 && currentIndex < AVAILABLE_MICS.size() ? AVAILABLE_MICS.get(currentIndex) : null;
   }

   public static void cycleMicrophone() {
      if (AVAILABLE_MICS.isEmpty()) {
         sendClientMessage("No compatible microphones found on this system.");
      } else {
         currentIndex = (currentIndex + 1) % AVAILABLE_MICS.size();
         Info selected = AVAILABLE_MICS.get(currentIndex);
         sendClientMessage("Microphone switched to: " + selected.getName());
      }
   }

   private static void sendClientMessage(String text) {
      if (Minecraft.getInstance().player != null) {
         Minecraft.getInstance().player.sendSystemMessage(Component.literal(text));
      }
   }
}
