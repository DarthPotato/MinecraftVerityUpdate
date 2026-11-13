package varmite.verity.entity.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;

public final class SphereRenderHelper {
   private SphereRenderHelper() {
   }

   public static void renderEntityBillboard(
      PoseStack poseStack,
      SubmitNodeCollector collector,
      Identifier texture,
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
      poseStack.rotate(Axis.YP.rotationDegrees(finalYaw));
      poseStack.rotate(Axis.XP.rotationDegrees(finalPitch));
      poseStack.rotate(Axis.YP.rotationDegrees(90.0F));
      poseStack.scale(0.5F * stretchXZ, 0.5F * stretchY, 0.5F * stretchXZ);
      poseStack.translate(0.0, -0.25, 0.0);
      if (rollAngle != 0.0F) {
         poseStack.rotate(Axis.YP.rotationDegrees(-90.0F));
         poseStack.rotate(Axis.XP.rotationDegrees(-finalPitch));
         poseStack.rotate(Axis.YP.rotationDegrees(-finalYaw));
         poseStack.rotate(Axis.YP.rotationDegrees(-bodyYaw));
         poseStack.rotate(Axis.XP.rotationDegrees(rollAngle));
         poseStack.rotate(Axis.YP.rotationDegrees(bodyYaw));
         poseStack.rotate(Axis.YP.rotationDegrees(finalYaw));
         poseStack.rotate(Axis.XP.rotationDegrees(finalPitch));
         poseStack.rotate(Axis.YP.rotationDegrees(90.0F));
      }

      SphereMesh.render(poseStack, collector, texture, 0.5F, 16, 16, packedLight, r, g, b, a);
      poseStack.popPose();
   }
}
