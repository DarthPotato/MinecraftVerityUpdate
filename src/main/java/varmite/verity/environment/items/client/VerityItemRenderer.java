package varmite.verity.environment.items.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import varmite.verity.VerityConfig;
import varmite.verity.entity.VerityState;
import varmite.verity.entity.utils.SphereMesh;
import varmite.verity.environment.items.VerityVariants;

public class VerityItemRenderer extends BlockEntityWithoutLevelRenderer {
   public VerityItemRenderer() {
      super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
   }

   @Override
   public void renderByItem(
      ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
   ) {
      String variant = VerityVariants.fromStack(stack);
      ResourceLocation texture = VerityVariants.entityTexture(variant);
      if (VerityState.isClientTalking) {
         String talkingFile = switch (variant) {
            case "happy" -> "happy_talking";
            case "crazy" -> "crazy_talking";
            case "evil" -> "evil_talking";
            case "serious_1", "serious_2", "serious_3" -> "serious_talking";
            default -> null;
         };
         if (talkingFile != null) {
            texture = ResourceLocation.fromNamespaceAndPath("verity", "textures/entity/" + talkingFile + ".png");
         }
      }

      float stretchY = 1.0F;
      float stretchXZ = 1.0F;
      if (VerityState.isClientTalking) {
         float talkTime = (Minecraft.getInstance().player != null ? Minecraft.getInstance().player.tickCount : 0) + Minecraft.getInstance().getFrameTime();
         stretchY = 1.0F + Mth.sin(talkTime * 0.6F) * 0.12F;
         stretchXZ = 1.0F + (1.0F - stretchY) * 0.5F;
      }

      poseStack.pushPose();

      try {
         poseStack.translate(0.5, 0.5, 0.5);
         poseStack.scale(0.5F * stretchXZ, 0.5F * stretchY, 0.5F * stretchXZ);
         int colorConfig = (Integer)VerityConfig.COLOR.get();
         int a = 255;
         int r;
         int g;
         int b;
         if (colorConfig == 0) {
            r = 255;
            g = 225;
            b = 30;
         } else if (colorConfig > 360) {
            r = colorConfig >> 16 & 0xFF;
            g = colorConfig >> 8 & 0xFF;
            b = colorConfig & 0xFF;
         } else {
            int rgb = Mth.hsvToRgb(colorConfig / 360.0F, 1.0F, 1.0F);
            r = rgb >> 16 & 0xFF;
            g = rgb >> 8 & 0xFF;
            b = rgb & 0xFF;
         }

         poseStack.pushPose();

         try {
            if (displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
               poseStack.translate(0.0, 0.25, 0.0);
               poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
            }

            SphereMesh.render(poseStack, bufferSource, texture, 0.5F, 16, 16, packedLight, r, g, b, a);
         } finally {
            poseStack.popPose();
         }
      } finally {
         poseStack.popPose();
      }
   }
}
