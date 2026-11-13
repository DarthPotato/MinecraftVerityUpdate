package varmite.verity.entity.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import varmite.verity.VerityConfig;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.entity.verity.rendering.VerityAnimation;

public class SphereEntityRenderer extends EntityRenderer<VerityEntity, VerityRenderState> {
   public SphereEntityRenderer(Context context) {
      super(context);
      this.shadowRadius = 0.3F;
   }

   @Override
   public VerityRenderState createRenderState() {
      return new VerityRenderState();
   }

   /** Verity used to set noCulling on the entity; this is where that lives now. */
   @Override
   protected boolean affectedByCulling(VerityEntity entity) {
      return false;
   }

   @Override
   public void extractRenderState(VerityEntity entity, VerityRenderState state, float partialTick) {
      super.extractRenderState(entity, state, partialTick);
      int startTick = entity.getEntityData().get(VerityEntity.BOUNCE_START_TICK);
      state.visible = entity.tickCount >= 3 || entity.clientAnimationTicks >= 0 || startTick <= 0;
      state.texture = entity.getTextureRL();
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

      state.stretchY = stretchY;
      state.stretchXZ = stretchXZ;
      state.visualYOffset = visualYOffset;
      state.entityX = Mth.lerp(partialTick, entity.xo, entity.getX());
      state.entityY = Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getBbHeight() * 0.5 + visualYOffset;
      state.entityZ = Mth.lerp(partialTick, entity.zo, entity.getZ());
      state.viewerX = state.entityX;
      state.viewerY = state.entityY + 1.0;
      state.viewerZ = state.entityZ;
      if (player != null) {
         state.viewerX = Mth.lerp(partialTick, player.xo, player.getX());
         state.viewerY = Mth.lerp(partialTick, player.yo, player.getY()) + player.getEyeHeight();
         state.viewerZ = Mth.lerp(partialTick, player.zo, player.getZ());
      }

      int hueValue = VerityConfig.COLOR.get();
      state.red = 255;
      state.green = 255;
      state.blue = 255;
      if (hueValue != 0) {
         int rgb = Mth.hsvToRgb(hueValue / 360.0F, 1.0F, 1.0F);
         state.red = rgb >> 16 & 0xFF;
         state.green = rgb >> 8 & 0xFF;
         state.blue = rgb & 0xFF;
      }

      state.rollAngle = Mth.lerp(partialTick, entity.clientRollAngleO, entity.clientRollAngle);
      state.bodyYaw = Mth.lerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
   }

   @Override
   public void submit(VerityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
      if (state.visible) {
         super.submit(state, poseStack, collector, camera);
         poseStack.pushPose();
         poseStack.translate(0.0, state.visualYOffset, 0.0);
         SphereRenderHelper.renderEntityBillboard(
            poseStack,
            collector,
            state.texture,
            state.lightCoords,
            state.entityX,
            state.entityY,
            state.entityZ,
            state.viewerX,
            state.viewerY,
            state.viewerZ,
            state.stretchY,
            state.stretchXZ,
            state.red,
            state.green,
            state.blue,
            255,
            state.rollAngle,
            state.bodyYaw
         );
         poseStack.popPose();
      }
   }
}
