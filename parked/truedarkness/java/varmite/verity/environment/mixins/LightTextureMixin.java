package varmite.verity.environment.mixins;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import varmite.verity.VerityConfig;

@Mixin(LightTexture.class)
public class LightTextureMixin {
   @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V"))
   private void redirectUpload(DynamicTexture instance) {
      Player player = Minecraft.getInstance().player;
      if (player != null && player.hasEffect(MobEffects.NIGHT_VISION)) {
         instance.upload();
      } else if (VerityConfig.TRUE_DARKNESS.get() && player != null) {
         NativeImage image = instance.getPixels();
         if (image != null) {
            int ambient = image.getPixelRGBA(0, 0);
            int aR = ambient & 0xFF;
            int aG = ambient >> 8 & 0xFF;
            int aB = ambient >> 16 & 0xFF;
            int alpha = ambient >> 24 & 0xFF;
            int maxSkyColor = image.getPixelRGBA(0, 15);
            int sR = maxSkyColor & 0xFF;
            int sG = maxSkyColor >> 8 & 0xFF;
            int sB = maxSkyColor >> 16 & 0xFF;
            int maxSky = Math.max(sR, Math.max(sG, sB));
            float skyFactor = Math.max(0.0F, Math.min(1.0F, (maxSky - 120.0F) / 50.0F));

            for (int x = 0; x < 16; x++) {
               int torchColor = image.getPixelRGBA(x, 0);
               int tdR = Math.max(0, (torchColor & 0xFF) - aR);
               int tdG = Math.max(0, (torchColor >> 8 & 0xFF) - aG);
               int tdB = Math.max(0, (torchColor >> 16 & 0xFF) - aB);

               for (int y = 0; y < 16; y++) {
                  int orig = image.getPixelRGBA(x, y);
                  int vR = Math.max(0, (orig & 0xFF) - aR);
                  int vG = Math.max(0, (orig >> 8 & 0xFF) - aG);
                  int vB = Math.max(0, (orig >> 16 & 0xFF) - aB);
                  int fR = (int)(tdR * (1.0F - skyFactor) + vR * skyFactor);
                  int fG = (int)(tdG * (1.0F - skyFactor) + vG * skyFactor);
                  int fB = (int)(tdB * (1.0F - skyFactor) + vB * skyFactor);
                  int newColor = alpha << 24 | fB << 16 | fG << 8 | fR;
                  image.setPixelRGBA(x, y, newColor);
               }
            }
         }

         instance.upload();
      } else {
         instance.upload();
      }
   }
}
