package varmite.verity.client.gui;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.joml.Matrix3x2fStack;
import varmite.verity.VerityConfig;
import varmite.verity.network.ClientKarmaData;

public class KarmaHudOverlay {
   private static final Identifier EMPTY_BAR = Identifier.fromNamespaceAndPath("verity", "textures/karma_bar/empty.png");
   private static final Identifier FULL_BAR = Identifier.fromNamespaceAndPath("verity", "textures/karma_bar/full.png");
   private static final Identifier FACE_HAPPY = Identifier.fromNamespaceAndPath("verity", "textures/karma_bar/happy.png");
   private static final Identifier FACE_NEUTRAL = Identifier.fromNamespaceAndPath("verity", "textures/karma_bar/neutral.png");
   private static final Identifier FACE_ANGRY = Identifier.fromNamespaceAndPath("verity", "textures/karma_bar/angry.png");
   public static final GuiLayer HUD_KARMA = (guiGraphics, deltaTracker) -> {
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
         Identifier face = karma < 7 ? FACE_ANGRY : (karma < 14 ? FACE_NEUTRAL : FACE_HAPPY);
         guiGraphics.blit(RenderPipelines.GUI_TEXTURED, face, faceX, startY, 0.0F, 0.0F, faceSize, faceSize, faceSize, faceSize);
         int fillWidth = (int)((float)karma / maxKarma * texWidth);
         Matrix3x2fStack pose = guiGraphics.pose();
         pose.pushMatrix();
         pose.translate(drawX, barBottomY);
         pose.rotate((float)Math.toRadians(-90.0));
         guiGraphics.blit(RenderPipelines.GUI_TEXTURED, EMPTY_BAR, 0, 0, 0.0F, 0.0F, texWidth, texHeight, texWidth, texHeight);
         if (fillWidth > 0) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, FULL_BAR, 0, 0, 0.0F, 0.0F, fillWidth, texHeight, texWidth, texHeight);
         }

         pose.popMatrix();
      }
   };
}
