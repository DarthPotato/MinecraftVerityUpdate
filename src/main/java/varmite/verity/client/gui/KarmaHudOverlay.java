package varmite.verity.client.gui;

import com.mojang.math.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.LayeredDraw;
import varmite.verity.VerityConfig;
import varmite.verity.network.ClientKarmaData;

public class KarmaHudOverlay {
   private static final Identifier EMPTY_BAR = Identifier.fromNamespaceAndPath("verity", "textures/karma_bar/empty.png");
   private static final Identifier FULL_BAR = Identifier.fromNamespaceAndPath("verity", "textures/karma_bar/full.png");
   private static final Identifier FACE_HAPPY = Identifier.fromNamespaceAndPath("verity", "textures/karma_bar/happy.png");
   private static final Identifier FACE_NEUTRAL = Identifier.fromNamespaceAndPath("verity", "textures/karma_bar/neutral.png");
   private static final Identifier FACE_ANGRY = Identifier.fromNamespaceAndPath("verity", "textures/karma_bar/angry.png");
   public static final LayeredDraw.Layer HUD_KARMA = (guiGraphics, deltaTracker) -> {
      int width = guiGraphics.guiWidth();
      int height = guiGraphics.guiHeight();
      int karma = ClientKarmaData.getPlayerKarma();
      int maxKarma = 20;
      if (!VerityConfig.IMMERSIVE_MODE.get()) {
         int texWidth = 182;
         int texHeight = 5;
         int faceSize = 16;
         int paddingRight = 15;
         int gap = 2;
         int totalVisualHeight = texWidth + faceSize + gap;
         int startY = (height - totalVisualHeight) / 2;
         int drawX = width - paddingRight - texHeight;
         int faceX = drawX + texHeight / 2 - faceSize / 2;
         int barBottomY = startY + faceSize + gap + texWidth;
         if (karma < 7) {
            guiGraphics.blit(FACE_ANGRY, faceX, startY, 0.0F, 0.0F, faceSize, faceSize, faceSize, faceSize);
         } else if (karma < 14) {
            guiGraphics.blit(FACE_NEUTRAL, faceX, startY, 0.0F, 0.0F, faceSize, faceSize, faceSize, faceSize);
         } else {
            guiGraphics.blit(FACE_HAPPY, faceX, startY, 0.0F, 0.0F, faceSize, faceSize, faceSize, faceSize);
         }

         int fillWidth = (int)((float)karma / maxKarma * texWidth);
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(drawX, barBottomY, 0.0F);
         guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(-90.0F));
         guiGraphics.blit(EMPTY_BAR, 0, 0, 0.0F, 0.0F, texWidth, texHeight, texWidth, texHeight);
         if (fillWidth > 0) {
            guiGraphics.blit(FULL_BAR, 0, 0, 0.0F, 0.0F, fillWidth, texHeight, texWidth, texHeight);
         }

         guiGraphics.pose().popPose();
      }
   };
}
