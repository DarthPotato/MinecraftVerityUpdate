package varmite.verity.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public class VerityPreviewTexture {
   private static final ResourceLocation PREVIEW_ID = ResourceLocation.fromNamespaceAndPath("verity", "dynamic/color_preview");
   private static NativeImage baseImage;
   private static DynamicTexture texture;
   private static int lastHue = -1;

   public static void init() {
      try (InputStream stream = Minecraft.getInstance()
            .getResourceManager()
            .getResource(ResourceLocation.fromNamespaceAndPath("verity", "textures/entity/preview.png"))
            .get()
            .open()) {
         baseImage = NativeImage.read(stream);
      } catch (IOException e) {
         throw new RuntimeException("Failed to load Verity preview texture", e);
      }

      texture = new DynamicTexture(new NativeImage(baseImage.getWidth(), baseImage.getHeight(), true));
      Minecraft.getInstance().getTextureManager().register(PREVIEW_ID, texture);
      applyHue(0);
   }

   public static void applyHue(int hue) {
      if (hue != lastHue) {
         lastHue = hue;
         NativeImage out = texture.getPixels();
         float[] hsb = new float[3];

         for (int y = 0; y < baseImage.getHeight(); y++) {
            for (int x = 0; x < baseImage.getWidth(); x++) {
               int abgr = baseImage.getPixelRGBA(x, y);
               int a = abgr >> 24 & 0xFF;
               int b = abgr >> 16 & 0xFF;
               int g = abgr >> 8 & 0xFF;
               int r = abgr & 0xFF;
               Color.RGBtoHSB(r, g, b, hsb);
               if (hue != 0 && !(hsb[1] < 0.15F)) {
                  int rgb = Color.HSBtoRGB(hue / 360.0F, hsb[1], hsb[2]);
                  int nr = rgb >> 16 & 0xFF;
                  int ng = rgb >> 8 & 0xFF;
                  int nb = rgb & 0xFF;
                  out.setPixelRGBA(x, y, a << 24 | nb << 16 | ng << 8 | nr);
               } else {
                  out.setPixelRGBA(x, y, abgr);
               }
            }
         }

         texture.upload();
      }
   }

   public static ResourceLocation id() {
      return PREVIEW_ID;
   }
}
