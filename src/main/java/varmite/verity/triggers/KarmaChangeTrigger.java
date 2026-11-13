package varmite.verity.triggers;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class KarmaChangeTrigger extends SimpleCriterionTrigger<KarmaChangeTrigger.TriggerInstance> {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("verity", "karmachange");

   @Override
   public ResourceLocation getId() {
      return ID;
   }

   protected KarmaChangeTrigger.TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
      return new KarmaChangeTrigger.TriggerInstance(player);
   }

   public void trigger(ServerPlayer player) {
      this.trigger(player, instance -> true);
   }

   public static class TriggerInstance extends AbstractCriterionTriggerInstance {
      public TriggerInstance(ContextAwarePredicate player) {
         super(KarmaChangeTrigger.ID, player);
      }

      public static KarmaChangeTrigger.TriggerInstance unboxed() {
         return new KarmaChangeTrigger.TriggerInstance(ContextAwarePredicate.ANY);
      }
   }
}
