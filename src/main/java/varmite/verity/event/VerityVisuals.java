package varmite.verity.event;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent.ComputeFogColor;
import net.minecraftforge.client.event.ViewportEvent.RenderFog;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = "verity", value = Dist.CLIENT)
public class VerityVisuals {
   @SubscribeEvent
   public static void onComputeFogColor(ComputeFogColor event) {
      Level level = Minecraft.getInstance().level;
      if (level != null && level.dimension() == Level.OVERWORLD) {
         float partialTick = (float)event.getPartialTick();
         float angle = level.getTimeOfDay(partialTick);
         float brightness = Mth.cos(angle * (float) (Math.PI * 2)) * 2.0F + 0.5F;
         brightness = Mth.clamp(brightness, 0.0F, 1.0F);
         float targetRed = 0.55F;
         float targetGreen = 0.75F;
         float targetBlue = 1.0F;
         event.setRed(targetRed * brightness);
         event.setGreen(targetGreen * brightness);
         event.setBlue(targetBlue * brightness);
      }
   }

   @SubscribeEvent
   public static void onRenderFog(RenderFog event) {
      if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.dimension() == Level.OVERWORLD) {
         float farDistance = event.getFarPlaneDistance();
         event.setNearPlaneDistance(farDistance * 0.1F);
         event.setFarPlaneDistance(farDistance * 0.75F);
         event.setCanceled(true);
      }
   }
}
