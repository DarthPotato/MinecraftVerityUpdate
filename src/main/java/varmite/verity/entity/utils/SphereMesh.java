package varmite.verity.entity.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class SphereMesh {
   private static final float TEXTURE_SCALE = 1.3F;
   private static final float TEXTURE_SQUISH_Y = 0.9F;
   private static final String FACE_DIR = "textures/entity/";
   private static final Map<Identifier, Optional<Identifier>> TEETH_OVERLAYS = new ConcurrentHashMap<>();

   private SphereMesh() {
   }

   /** Queues the sphere with the pose stack as it is right now. */
   public static void render(
      PoseStack poseStack,
      SubmitNodeCollector collector,
      Identifier texture,
      float radius,
      int latitudes,
      int longitudes,
      int packedLight,
      int r,
      int g,
      int b,
      int a
   ) {
      collector.submitCustomGeometry(
         poseStack, RenderTypes.entityCutout(texture), (pose, consumer) -> drawSphere(pose, consumer, radius, latitudes, longitudes, packedLight, r, g, b, a)
      );
      teethOverlay(texture)
         .ifPresent(
            overlay -> collector.submitCustomGeometry(
               poseStack,
               RenderTypes.entityTranslucent(overlay),
               (pose, consumer) -> drawSphere(pose, consumer, radius * 1.002F, latitudes, longitudes, packedLight, 255, 255, 255, a)
            )
         );
   }

   /** 6.0.0: some faces have a teeth layer in textures/entity/teeth/, drawn untinted just above the face. */
   private static Optional<Identifier> teethOverlay(Identifier texture) {
      return TEETH_OVERLAYS.computeIfAbsent(
         texture,
         tex -> {
            String path = tex.getPath();
            if (!path.startsWith(FACE_DIR)) {
               return Optional.empty();
            }

            Identifier overlay = Identifier.fromNamespaceAndPath(tex.getNamespace(), FACE_DIR + "teeth/" + path.substring(FACE_DIR.length()));
            return Minecraft.getInstance().getResourceManager().getResource(overlay).map(resource -> overlay);
         }
      );
   }

   private static void drawSphere(PoseStack.Pose pose, VertexConsumer consumer, float radius, int latitudes, int longitudes, int light, int r, int g, int b, int a) {
      for (int lat = 0; lat <= latitudes; lat++) {
         float theta1 = (float)(lat * Math.PI / latitudes);
         float theta2 = (float)((lat + 1) * Math.PI / latitudes);

         for (int lon = 0; lon <= longitudes; lon++) {
            float phi1 = (float)(lon * 2 * Math.PI / longitudes);
            float phi2 = (float)((lon + 1) * 2 * Math.PI / longitudes);
            addVertex(pose, consumer, radius, theta1, phi1, light, r, g, b, a);
            addVertex(pose, consumer, radius, theta2, phi1, light, r, g, b, a);
            addVertex(pose, consumer, radius, theta2, phi2, light, r, g, b, a);
            addVertex(pose, consumer, radius, theta1, phi2, light, r, g, b, a);
         }
      }
   }

   private static void addVertex(PoseStack.Pose pose, VertexConsumer consumer, float radius, float theta, float phi, int light, int r, int g, int b, int a) {
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
      consumer.addVertex(pose, x, y, z)
         .setColor(r, g, b, a)
         .setUv(u, v)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(light)
         .setNormal(pose, nx, ny, nz);
   }
}
