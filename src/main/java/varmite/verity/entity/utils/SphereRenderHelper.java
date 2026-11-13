package varmite.verity.entity.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

public final class SphereRenderHelper {
   private SphereRenderHelper() {
   }

   public static void renderEntityBillboard(
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      ResourceLocation texture,
      int packedLight,
      double entityX,
      double entityY,
      double entityZ,
      double viewerX,
      double viewerY,
      double viewerZ,
      float stretchY,
      float stretchXZ,
      int r,
      int g,
      int b,
      int a,
      float rollAngle,
      float bodyYaw
   ) {
      poseStack.pushPose();
      poseStack.translate(0.0, 0.5, 0.0);
      double dx = viewerX - entityX;
      double dy = viewerY - entityY;
      double dz = viewerZ - entityZ;
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      float finalYaw = (float)Math.toDegrees(Math.atan2(dx, dz));
      float finalPitch = (float)(-Math.toDegrees(Math.atan2(dy, horizontal)));
      poseStack.mulPose(Axis.YP.rotationDegrees(finalYaw));
      poseStack.mulPose(Axis.XP.rotationDegrees(finalPitch));
      poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
      poseStack.scale(0.5F * stretchXZ, 0.5F * stretchY, 0.5F * stretchXZ);
      poseStack.translate(0.0, -0.25, 0.0);
      if (rollAngle != 0.0F) {
         poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
         poseStack.mulPose(Axis.XP.rotationDegrees(-finalPitch));
         poseStack.mulPose(Axis.YP.rotationDegrees(-finalYaw));
         poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
         poseStack.mulPose(Axis.XP.rotationDegrees(rollAngle));
         poseStack.mulPose(Axis.YP.rotationDegrees(bodyYaw));
         poseStack.mulPose(Axis.YP.rotationDegrees(finalYaw));
         poseStack.mulPose(Axis.XP.rotationDegrees(finalPitch));
         poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
      }

      SphereMesh.render(poseStack, bufferSource, texture, 0.5F, 16, 16, packedLight, r, g, b, a);
      poseStack.popPose();
   }
}
