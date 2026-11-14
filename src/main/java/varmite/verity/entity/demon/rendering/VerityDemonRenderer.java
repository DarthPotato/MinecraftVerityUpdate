package varmite.verity.entity.demon.rendering;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import varmite.verity.client.render.BedrockPolyMesh;
import varmite.verity.entity.demon.VerityDemonEntity;

/**
 * GeckoLib 5 version of the 1.20.1 demon renderer. Most of the demon's body is Bedrock poly_mesh geometry, which GeckoLib
 * parses but does not draw; it is drawn here as a per-bone render task, in the same model space as the bone's cubes.
 */
public class VerityDemonRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<VerityDemonEntity, R> {
   private static final float SCALE = 1.5F;

   public VerityDemonRenderer(Context context) {
      super(context, new VerityDemonModel());
      this.withScale(SCALE);
   }

   /** The demon does not tip over when it dies (the 1.20.1 renderer hid deathTime for the same effect). */
   @Override
   protected float getDeathMaxRotation(GeoRenderState renderState) {
      return 0.0F;
   }

   /** Turns the head toward where the demon is looking, on top of whatever the animation does. */
   @Override
   public void adjustModelBonesForRender(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots) {
      R renderState = renderPassInfo.renderState();
      snapshots.ifPresent("Head", head -> {
         head.setRotX(head.getRotX() + renderState.xRot * Mth.DEG_TO_RAD);
         head.setRotY(head.getRotY() + renderState.yRot * Mth.DEG_TO_RAD);
      });
   }

   @Override
   public void preRenderPass(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector renderTasks) {
      super.preRenderPass(renderPassInfo, renderTasks);
      if (!renderPassInfo.willRender()) {
         return;
      }

      Map<String, float[]> meshes = BedrockPolyMesh.forModel(VerityDemonModel.MESH);
      if (meshes.isEmpty()) {
         return;
      }

      R renderState = renderPassInfo.renderState();
      RenderType renderType = this.getRenderType(renderState, this.getTextureLocation(renderState));
      if (renderType == null) {
         return;
      }

      Map<String, GeoBone> bones = renderPassInfo.model().boneLookup().get();

      for (Entry<String, float[]> mesh : meshes.entrySet()) {
         GeoBone bone = bones.get(mesh.getKey());
         if (bone != null) {
            float[] vertices = mesh.getValue();
            renderPassInfo.addPerBoneRender(bone, (pass, target, tasks) -> submitMesh(pass, target, tasks, renderType, vertices));
         }
      }
   }

   /** Per-bone tasks start at the bone's pivot; step back to model space, where the baked mesh coordinates live. */
   private static void submitMesh(RenderPassInfo<?> pass, GeoBone bone, SubmitNodeCollector tasks, RenderType renderType, float[] vertices) {
      PoseStack poseStack = pass.poseStack();
      bone.translateAwayFromPivotPoint(poseStack);
      int color = pass.renderColor();
      int overlay = pass.packedOverlay();
      int light = pass.packedLight();
      tasks.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
         for (int i = 0; i < vertices.length; i += BedrockPolyMesh.STRIDE) {
            buffer.addVertex(pose, vertices[i], vertices[i + 1], vertices[i + 2])
               .setColor(color)
               .setUv(vertices[i + 3], vertices[i + 4])
               .setOverlay(overlay)
               .setLight(light)
               .setNormal(pose, vertices[i + 5], vertices[i + 6], vertices[i + 7]);
         }
      });
   }
}
