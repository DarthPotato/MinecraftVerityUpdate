package varmite.verity.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent.Post;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import varmite.verity.VerityConfig;

@EventBusSubscriber(modid = "verity", value = Dist.CLIENT, bus = Bus.FORGE)
public class AudioHudRenderer {
   private static boolean wasRecording = false;
   private static long fadeStartTime = 0L;
   private static final long FADE_DURATION = 500L;

   @SubscribeEvent
   public static void onRenderOverlay(Post event) {
      if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
         Minecraft mc = Minecraft.getInstance();
         if (!mc.options.hideGui && mc.player != null) {
            if (!VerityConfig.IMMERSIVE_MODE.get()) {
               boolean isRecording = KeybindHandler.isRecording();
               long currentTime = System.currentTimeMillis();
               float alphaMultiplier = 0.0F;
               if (isRecording) {
                  alphaMultiplier = 1.0F;
                  wasRecording = true;
               } else {
                  if (wasRecording) {
                     fadeStartTime = currentTime;
                     wasRecording = false;
                  }

                  long timeSinceStop = currentTime - fadeStartTime;
                  if (timeSinceStop < 500L) {
                     alphaMultiplier = 1.0F - (float)timeSinceStop / 500.0F;
                  }
               }

               if (!(alphaMultiplier <= 0.0F)) {
                  GuiGraphics graphics = event.getGuiGraphics();
                  int screenWidth = event.getWindow().getGuiScaledWidth();
                  int screenHeight = event.getWindow().getGuiScaledHeight();
                  int barWidth = 80;
                  int barHeight = 4;
                  int x = (screenWidth - barWidth) / 2;
                  int y = screenHeight - 65;
                  double level = KeybindHandler.getRecorder().getAudioLevel();
                  int filledWidth = (int)(barWidth * level);
                  int shadowColor = applyAlpha(1711276032, alphaMultiplier);
                  int bgOutlineColor = applyAlpha(-1728053248, alphaMultiplier);
                  int bgTrackColor = applyAlpha(-14540254, alphaMultiplier);
                  int activeFillColor = applyAlpha(-16711732, alphaMultiplier);
                  graphics.fill(x, y, x + barWidth + 2, y + barHeight + 2, shadowColor);
                  graphics.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, bgOutlineColor);
                  graphics.fill(x, y, x + barWidth, y + barHeight, bgTrackColor);
                  if (filledWidth > 0) {
                     graphics.fill(x, y, x + filledWidth, y + barHeight, activeFillColor);
                  }

                  String statusText = "● RECORDING";
                  int textWidth = mc.font.width(statusText);
                  int textX = (screenWidth - textWidth) / 2;
                  int textY = y - 10;
                  int textShadowColor = applyAlpha(-1442840576, alphaMultiplier);
                  int textMainColor = applyAlpha(-52429, alphaMultiplier);
                  graphics.drawString(mc.font, statusText, textX + 1, textY + 1, textShadowColor, false);
                  graphics.drawString(mc.font, statusText, textX, textY, textMainColor, false);
               }
            }
         }
      }
   }

   private static int applyAlpha(int color, float alphaMultiplier) {
      int originalAlpha = color >> 24 & 0xFF;
      int newAlpha = (int)(originalAlpha * alphaMultiplier);
      newAlpha = Math.max(0, Math.min(255, newAlpha));
      return newAlpha << 24 | color & 16777215;
   }
}
