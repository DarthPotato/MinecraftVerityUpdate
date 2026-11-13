package varmite.verity.entity.verity.rendering;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public class VerityEntityTexture {
   private static final ResourceLocation ENTITY_ID = ResourceLocation.fromNamespaceAndPath("verity", "dynamic/entity");
   private static NativeImage baseImage;
   private static DynamicTexture texture;
   private static int lastHue = -1;
   private static ResourceLocation currentTexture = null;

   public static void init() {
      texture = new DynamicTexture(new NativeImage(1, 1, true));
      Minecraft.getInstance().getTextureManager().register(ENTITY_ID, texture);
   }

   public static void setBaseTexture(ResourceLocation textureLocation) {
      if (!textureLocation.equals(currentTexture)) {
         currentTexture = textureLocation;

         try (InputStream stream = Minecraft.getInstance().getResourceManager().getResource(textureLocation).orElseThrow().open()) {
            baseImage = NativeImage.read(stream);
         } catch (IOException e) {
            throw new RuntimeException("Failed to load " + textureLocation, e);
         }

         if (texture == null || texture.getPixels().getWidth() != baseImage.getWidth() || texture.getPixels().getHeight() != baseImage.getHeight()) {
            texture = new DynamicTexture(new NativeImage(baseImage.getWidth(), baseImage.getHeight(), true));
            Minecraft.getInstance().getTextureManager().register(ENTITY_ID, texture);
         }

         lastHue = -1;
      }
   }

   public static void applyHue(int hue) {
      if (baseImage != null) {
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
   }

   public static ResourceLocation id() {
      return ENTITY_ID;
   }
}
