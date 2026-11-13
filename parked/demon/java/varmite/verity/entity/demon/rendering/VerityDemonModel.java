package varmite.verity.entity.demon.rendering;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import varmite.verity.entity.demon.VerityDemonEntity;

public class VerityDemonModel extends GeoModel<VerityDemonEntity> {
   public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("verity", "geo/entity/verity_demon.geo.json");
   public static final ResourceLocation MESH = ResourceLocation.fromNamespaceAndPath("verity", "meshes/entity/verity_demon.json");

   public ResourceLocation getModelResource(VerityDemonEntity animatable) {
      return MODEL;
   }

   public ResourceLocation getTextureResource(VerityDemonEntity animatable) {
      return ResourceLocation.fromNamespaceAndPath("verity", "textures/entity/verity_demon.png");
   }

   public ResourceLocation getAnimationResource(VerityDemonEntity animatable) {
      return ResourceLocation.fromNamespaceAndPath("verity", "animations/entity/verity_demon.animation.json");
   }

   public void setCustomAnimations(VerityDemonEntity animatable, long instanceId, AnimationState<VerityDemonEntity> animationState) {
      super.setCustomAnimations(animatable, instanceId, animationState);
      CoreGeoBone head = this.getAnimationProcessor().getBone("Head");
      if (head != null) {
         EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
         if (entityData != null) {
            float currentPitch = head.getRotX();
            float currentYaw = head.getRotY();
            float basePitch = currentPitch == animatable.writtenHeadPitch ? animatable.animHeadPitch : currentPitch;
            float baseYaw = currentYaw == animatable.writtenHeadYaw ? animatable.animHeadYaw : currentYaw;
            animatable.animHeadPitch = basePitch;
            animatable.animHeadYaw = baseYaw;
            float pitch = basePitch + entityData.headPitch() * (float) (Math.PI / 180.0);
            float yaw = baseYaw + entityData.netHeadYaw() * (float) (Math.PI / 180.0);
            head.setRotX(pitch);
            head.setRotY(yaw);
            animatable.writtenHeadPitch = pitch;
            animatable.writtenHeadYaw = yaw;
         }
      }
   }
}
