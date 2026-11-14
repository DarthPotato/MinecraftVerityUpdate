package varmite.verity.entity.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class SphereMesh {
   private static final float TEXTURE_SCALE = 1.3F;
   private static final float TEXTURE_SQUISH_Y = 0.9F;
   private static final String FACE_DIR = "textures/entity/";
   private static final Map<ResourceLocation, Optional<ResourceLocation>> TEETH_OVERLAYS = new ConcurrentHashMap<>();

   private SphereMesh() {
   }

   public static void render(
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      ResourceLocation texture,
      float radius,
      int latitudes,
      int longitudes,
      int packedLight,
      int r,
      int g,
      int b,
      int a
   ) {
      VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));
      Matrix4f pose = poseStack.last().pose();
      Matrix3f normal = poseStack.last().normal();
      drawSphere(pose, normal, consumer, radius, latitudes, longitudes, packedLight, r, g, b, a);
      teethOverlay(texture)
         .ifPresent(
            overlay -> drawSphere(
               pose,
               normal,
               bufferSource.getBuffer(RenderType.entityTranslucent(overlay)),
               radius * 1.002F,
               latitudes,
               longitudes,
               packedLight,
               255,
               255,
               255,
               a
            )
         );
   }

   private static Optional<ResourceLocation> teethOverlay(ResourceLocation texture) {
      return TEETH_OVERLAYS.computeIfAbsent(
         texture,
         tex -> {
            String path = tex.getPath();
            if (!path.startsWith("textures/entity/")) {
               return Optional.empty();
            }

            ResourceLocation overlay = ResourceLocation.fromNamespaceAndPath(
               tex.getNamespace(), "textures/entity/teeth/" + path.substring("textures/entity/".length())
            );
            return Minecraft.getInstance().getResourceManager().getResource(overlay).map(resource -> overlay);
         }
      );
   }

   private static void drawSphere(
      Matrix4f pose, Matrix3f normal, VertexConsumer consumer, float radius, int latitudes, int longitudes, int light, int r, int g, int b, int a
   ) {
      for (int lat = 0; lat <= latitudes; lat++) {
         float theta1 = (float)(lat * Math.PI / latitudes);
         float theta2 = (float)((lat + 1) * Math.PI / latitudes);

         for (int lon = 0; lon <= longitudes; lon++) {
            float phi1 = (float)(lon * 2 * Math.PI / longitudes);
            float phi2 = (float)((lon + 1) * 2 * Math.PI / longitudes);
            addVertex(pose, normal, consumer, radius, theta1, phi1, light, r, g, b, a);
            addVertex(pose, normal, consumer, radius, theta2, phi1, light, r, g, b, a);
            addVertex(pose, normal, consumer, radius, theta2, phi2, light, r, g, b, a);
            addVertex(pose, normal, consumer, radius, theta1, phi2, light, r, g, b, a);
         }
      }
   }

   private static void addVertex(
      Matrix4f pose, Matrix3f normalMatrix, VertexConsumer consumer, float radius, float theta, float phi, int light, int r, int g, int b, int a
   ) {
      float x = (float)(radius * Math.sin(theta) * Math.cos(phi));
      float y = (float)(radius * Math.cos(theta));
      float z = (float)(radius * Math.sin(theta) * Math.sin(phi));
      float u = (float)(phi / (Math.PI * 2));
      float v = 0.25F + (float)(theta / Math.PI) * 0.5F;
      u = Mth.clamp(0.5F + (u - 0.5F) * 1.3F, 0.0F, 1.0F);
      v = Mth.clamp(0.5F + (v - 0.5F) * 1.4444444F, 0.0F, 1.0F);
      float length = (float)Math.sqrt(x * x + y * y + z * z);
      float nx = x / length;
      float ny = y / length;
      float nz = z / length;
      consumer.vertex(pose, x, y, z)
         .color(r, g, b, a)
         .uv(u, v)
         .overlayCoords(OverlayTexture.NO_OVERLAY)
         .uv2(light)
         .normal(normalMatrix, nx, ny, nz)
         .endVertex();
   }
}
