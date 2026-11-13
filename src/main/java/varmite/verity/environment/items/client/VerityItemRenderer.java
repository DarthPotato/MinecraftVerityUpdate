package varmite.verity.environment.items.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import varmite.verity.VerityConfig;
import varmite.verity.entity.VerityState;
import varmite.verity.entity.utils.SphereMesh;
import varmite.verity.environment.items.VerityVariants;

/**
 * Draws the Verity item as the same sphere as the entity. Item stacks cannot have a Java renderer
 * attached any more; the item's model file (assets/verity/items/verity_item.json) asks for this
 * renderer by id, once for first person and once for everything else.
 */
public class VerityItemRenderer implements SpecialModelRenderer<String> {
   public static final Identifier ID = Identifier.fromNamespaceAndPath("verity", "verity");
   private final boolean firstPerson;

   public VerityItemRenderer(boolean firstPerson) {
      this.firstPerson = firstPerson;
   }

   @Override
   public String extractArgument(ItemStack stack) {
      return VerityVariants.fromStack(stack);
   }

   @Override
   public void submit(String variant, PoseStack poseStack, SubmitNodeCollector collector, int packedLight, int packedOverlay, boolean hasFoil, int outlineColor) {
      if (variant == null) {
         variant = "happy";
      }

      Identifier texture = VerityVariants.entityTexture(variant);
      if (VerityState.isClientTalking) {
         String talkingFile = switch (variant) {
            case "happy" -> "happy_talking";
            case "crazy" -> "crazy_talking";
            case "evil" -> "evil_talking";
            case "serious_1", "serious_2", "serious_3" -> "serious_talking";
            default -> null;
         };
         if (talkingFile != null) {
            texture = Identifier.fromNamespaceAndPath("verity", "textures/entity/" + talkingFile + ".png");
         }
      }

      float stretchY = 1.0F;
      float stretchXZ = 1.0F;
      if (VerityState.isClientTalking) {
         Minecraft minecraft = Minecraft.getInstance();
         float talkTime = (minecraft.player != null ? minecraft.player.tickCount : 0) + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
         stretchY = 1.0F + Mth.sin(talkTime * 0.6F) * 0.12F;
         stretchXZ = 1.0F + (1.0F - stretchY) * 0.5F;
      }

      poseStack.pushPose();

      try {
         poseStack.translate(0.5, 0.5, 0.5);
         poseStack.scale(0.5F * stretchXZ, 0.5F * stretchY, 0.5F * stretchXZ);
         int hueValue = VerityConfig.COLOR.get();
         int a = 255;
         int r;
         int g;
         int b;
         if (hueValue == 0) {
            r = 255;
            g = 255;
            b = 255;
         } else {
            int rgb = Mth.hsvToRgb(hueValue / 360.0F, 1.0F, 1.0F);
            r = rgb >> 16 & 0xFF;
            g = rgb >> 8 & 0xFF;
            b = rgb & 0xFF;
         }

         poseStack.pushPose();

         try {
            if (this.firstPerson) {
               poseStack.translate(0.0, 0.25, 0.0);
               poseStack.rotate(Axis.YP.rotationDegrees(45.0F));
            }

            SphereMesh.render(poseStack, collector, texture, 0.5F, 16, 16, packedLight, r, g, b, a);
         } finally {
            poseStack.popPose();
         }
      } finally {
         poseStack.popPose();
      }
   }

   @Override
   public void getExtents(Consumer<Vector3fc> output) {
      for (float x : new float[]{0.25F, 0.75F}) {
         for (float y : new float[]{0.25F, 0.75F}) {
            for (float z : new float[]{0.25F, 0.75F}) {
               output.accept(new Vector3f(x, y, z));
            }
         }
      }
   }

   public record Unbaked(boolean firstPerson) implements SpecialModelRenderer.Unbaked<String> {
      public static final MapCodec<VerityItemRenderer.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
         instance -> instance.group(Codec.BOOL.optionalFieldOf("first_person", false).forGetter(VerityItemRenderer.Unbaked::firstPerson))
            .apply(instance, VerityItemRenderer.Unbaked::new)
      );

      @Override
      public MapCodec<VerityItemRenderer.Unbaked> type() {
         return MAP_CODEC;
      }

      @Override
      public SpecialModelRenderer<String> bake(SpecialModelRenderer.BakingContext context) {
         return new VerityItemRenderer(this.firstPerson);
      }
   }
}
