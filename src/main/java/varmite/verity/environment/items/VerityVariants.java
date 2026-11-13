package varmite.verity.environment.items;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class VerityVariants {
   private static final List<String> VALID_VARIANTS = List.of(
      "crazy_talking",
      "happy",
      "happy_sleep",
      "happy_talking",
      "hurt",
      "neutral",
      "noface",
      "serious_1",
      "serious_2",
      "serious_3",
      "serious_talking",
      "evil",
      "evil_talking",
      "smiling_evil",
      "crazy",
      "neutral_talking"
   );

   private VerityVariants() {
   }

   public static String fromStack(ItemStack stack) {
      if (StackData.has(stack, "VerityVariant")) {
         return sanitize(StackData.getString(stack, "VerityVariant"));
      }

      return "happy";
   }

   public static Identifier entityTexture(String variant) {
      String safeVariant = sanitize(variant);
      if (!VALID_VARIANTS.contains(safeVariant)) {
         safeVariant = "happy";
      }

      return Identifier.fromNamespaceAndPath("verity", "textures/entity/" + safeVariant + ".png");
   }

   private static String sanitize(String variant) {
      if (variant != null && !variant.isBlank()) {
         if (variant.endsWith(".png")) {
            variant = variant.substring(0, variant.length() - 4);
         }

         return variant;
      } else {
         return "happy";
      }
   }
}
