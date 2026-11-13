package varmite.verity.event;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.config.ModConfigEvent.Reloading;
import varmite.verity.VerityConfig;

@EventBusSubscriber(modid = "verity", bus = Bus.MOD, value = Dist.CLIENT)
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
