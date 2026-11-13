package varmite.verity.entity.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import varmite.verity.VerityConfig;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.entity.verity.rendering.VerityAnimation;

public class SphereEntityRenderer extends EntityRenderer<VerityEntity> {
   public SphereEntityRenderer(Context context) {
      super(context);
      this.shadowRadius = 0.3F;
   }

   public ResourceLocation getTextureLocation(VerityEntity entity) {
      return entity.getTextureRL();
   }

   public void render(VerityEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
      int startTick = entity.getEntityData().get(VerityEntity.BOUNCE_START_TICK);
      if (entity.tickCount >= 3 || entity.clientAnimationTicks >= 0 || startTick <= 0) {
         super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
         LocalPlayer player = Minecraft.getInstance().player;
         float stretchY = 1.0F;
         float stretchXZ = 1.0F;
         double visualYOffset = 0.0;
         boolean isTalking = entity.clientIsTalking || entity.clientIntroTicks > 0;
         boolean isAnimating = entity.clientAnimationTicks >= 0;
         if (isAnimating) {
            float exactTick = 50 - entity.clientAnimationTicks + partialTick;
            stretchY = VerityAnimation.getScaleY(exactTick);
            stretchXZ = VerityAnimation.getScaleXZ(exactTick);
            visualYOffset = VerityAnimation.getYOffset(exactTick);
         } else if (isTalking && entity.clientIntroDelay <= 0) {
            float talkTime = entity.tickCount + partialTick;
            stretchY = 1.0F + Mth.sin(talkTime * 0.6F) * 0.12F;
            stretchXZ = 1.0F + (1.0F - stretchY) * 0.5F;
         }

         double entityX = Mth.lerp(partialTick, entity.xo, entity.getX());
         double entityY = Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getBbHeight() * 0.5 + visualYOffset;
         double entityZ = Mth.lerp(partialTick, entity.zo, entity.getZ());
         double viewerX = entityX;
         double viewerY = entityY + 1.0;
         double viewerZ = entityZ;
         if (player != null) {
            viewerX = Mth.lerp(partialTick, player.xo, player.getX());
            viewerY = Mth.lerp(partialTick, player.yo, player.getY()) + player.getEyeHeight();
            viewerZ = Mth.lerp(partialTick, player.zo, player.getZ());
         }

         int hueValue = VerityConfig.COLOR.get();
         int r = 255;
         int g = 255;
         int b = 255;
         int a = 255;
         if (hueValue != 0) {
            int rgb = Mth.hsvToRgb(hueValue / 360.0F, 1.0F, 1.0F);
            r = rgb >> 16 & 0xFF;
            g = rgb >> 8 & 0xFF;
            b = rgb & 0xFF;
         }

         float rollAngle = Mth.lerp(partialTick, entity.clientRollAngleO, entity.clientRollAngle);
         float bodyYaw = Mth.lerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
         poseStack.pushPose();
         poseStack.translate(0.0, visualYOffset, 0.0);
         SphereRenderHelper.renderEntityBillboard(
            poseStack,
            bufferSource,
            this.getTextureLocation(entity),
            packedLight,
            entityX,
            entityY,
            entityZ,
            viewerX,
            viewerY,
            viewerZ,
            stretchY,
            stretchXZ,
            r,
            g,
            b,
            a,
            rollAngle,
            bodyYaw
         );
         poseStack.popPose();
      }
   }
}
