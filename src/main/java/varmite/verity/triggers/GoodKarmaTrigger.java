package varmite.verity.triggers;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class GoodKarmaTrigger extends SimpleCriterionTrigger<GoodKarmaTrigger.TriggerInstance> {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("verity", "goodkarma");

   @Override
   public ResourceLocation getId() {
      return ID;
   }

   protected GoodKarmaTrigger.TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
      return new GoodKarmaTrigger.TriggerInstance(player);
   }

   public void trigger(ServerPlayer player) {
      this.trigger(player, instance -> true);
   }

   public static class TriggerInstance extends AbstractCriterionTriggerInstance {
      public TriggerInstance(ContextAwarePredicate player) {
         super(GoodKarmaTrigger.ID, player);
      }

      public static GoodKarmaTrigger.TriggerInstance unboxed() {
         return new GoodKarmaTrigger.TriggerInstance(ContextAwarePredicate.ANY);
      }
   }
}
