package varmite.verity.client.sound;

import net.minecraft.client.Minecraft;
import varmite.verity.entity.demon.VerityDemonEntity;

public class ClientSoundHandler {
   public static void playDemonChaseSound(VerityDemonEntity demon) {
      Minecraft.getInstance().getSoundManager().play(new DemonChaseSoundInstance(demon));
   }
}
