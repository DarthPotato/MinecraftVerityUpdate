package varmite.verity.event;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent.Reloading;
import varmite.verity.VerityConfig;

@EventBusSubscriber(modid = "verity", value = Dist.CLIENT)
public class ConfigEventHandler {
   @SubscribeEvent
   public static void onConfigReload(Reloading event) {
      if (Minecraft.getInstance().player != null) {
         if (event.getConfig().getSpec() == VerityConfig.SPEC) {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().levelRenderer.allChanged());
            System.out.println("Your mod config was saved and reloaded!");
         }
      }
   }
}
