package varmite.verity.triggers;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class ThreeDaysTrigger extends SimpleCriterionTrigger<ThreeDaysTrigger.TriggerInstance> {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("verity", "threedays");

   @Override
   public ResourceLocation getId() {
      return ID;
   }

   protected ThreeDaysTrigger.TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
      return new ThreeDaysTrigger.TriggerInstance(player);
   }

   public void trigger(ServerPlayer player) {
      this.trigger(player, instance -> true);
   }

   public static class TriggerInstance extends AbstractCriterionTriggerInstance {
      public TriggerInstance(ContextAwarePredicate player) {
         super(ThreeDaysTrigger.ID, player);
      }

      public static ThreeDaysTrigger.TriggerInstance unboxed() {
         return new ThreeDaysTrigger.TriggerInstance(ContextAwarePredicate.ANY);
      }
   }
}
