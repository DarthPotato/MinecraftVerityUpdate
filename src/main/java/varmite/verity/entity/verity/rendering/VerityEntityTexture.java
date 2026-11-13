package varmite.verity.entity.verity.rendering;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public class VerityEntityTexture {
   private static final Identifier ENTITY_ID = Identifier.fromNamespaceAndPath("verity", "dynamic/entity");
   private static NativeImage baseImage;
   private static DynamicTexture texture;
   private static int lastHue = -1;
   private static Identifier currentTexture = null;

   public static void init() {
      texture = new DynamicTexture(() -> "verity entity tint", new NativeImage(1, 1, true));
      Minecraft.getInstance().getTextureManager().register(ENTITY_ID, texture);
   }

   public static void setBaseTexture(Identifier textureLocation) {
      if (!textureLocation.equals(currentTexture)) {
         currentTexture = textureLocation;

         try (InputStream stream = Minecraft.getInstance().getResourceManager().getResource(textureLocation).orElseThrow().open()) {
            baseImage = NativeImage.read(stream);
         } catch (IOException e) {
            throw new RuntimeException("Failed to load " + textureLocation, e);
         }

         if (texture == null || texture.getPixels().getWidth() != baseImage.getWidth() || texture.getPixels().getHeight() != baseImage.getHeight()) {
            texture = new DynamicTexture(() -> "verity entity tint", new NativeImage(baseImage.getWidth(), baseImage.getHeight(), true));
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
                  int argb = baseImage.getPixel(x, y);
                  int a = argb >> 24 & 0xFF;
                  int r = argb >> 16 & 0xFF;
                  int g = argb >> 8 & 0xFF;
                  int b = argb & 0xFF;
                  Color.RGBtoHSB(r, g, b, hsb);
                  if (hue != 0 && !(hsb[1] < 0.15F)) {
                     int rgb = Color.HSBtoRGB(hue / 360.0F, hsb[1], hsb[2]);
                     out.setPixel(x, y, a << 24 | rgb & 0xFFFFFF);
                  } else {
                     out.setPixel(x, y, argb);
                  }
               }
            }

            texture.upload();
         }
      }
   }

   public static Identifier id() {
      return ENTITY_ID;
   }
}
