package varmite.verity.entity.demon.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import varmite.verity.client.render.BedrockPolyMesh;
import varmite.verity.entity.demon.VerityDemonEntity;

public class VerityDemonRenderer extends GeoEntityRenderer<VerityDemonEntity> {
   private static final float SCALE = 1.5F;

   public VerityDemonRenderer(Context context) {
      super(context, new VerityDemonModel());
      this.withScale(1.5F);
   }

   public void render(VerityDemonEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
      int actualDeathTime = entity.deathTime;
      entity.deathTime = 0;
      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      entity.deathTime = actualDeathTime;
   }

   @Override
   public void renderCubesOfBone(
      PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour
   ) {
      super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
      Map<String, float[]> meshes = BedrockPolyMesh.forModel(VerityDemonModel.MESH);
      float[] vertices = meshes.get(bone.getName());
      if (vertices != null) {
         Pose pose = poseStack.last();

         for (int i = 0; i < vertices.length; i += BedrockPolyMesh.STRIDE) {
            buffer.addVertex(pose, vertices[i], vertices[i + 1], vertices[i + 2])
               .setColor(colour)
               .setUv(vertices[i + 3], vertices[i + 4])
               .setOverlay(packedOverlay)
               .setLight(packedLight)
               .setNormal(pose, vertices[i + 5], vertices[i + 6], vertices[i + 7]);
         }
      }
   }
}
