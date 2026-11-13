package varmite.verity.triggers;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class OpenBoxTrigger extends SimpleCriterionTrigger<OpenBoxTrigger.TriggerInstance> {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("verity", "open_box");

   @Override
   public ResourceLocation getId() {
      return ID;
   }

   protected OpenBoxTrigger.TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
      return new OpenBoxTrigger.TriggerInstance(player);
   }

   public void trigger(ServerPlayer player) {
      this.trigger(player, instance -> true);
   }

   public static class TriggerInstance extends AbstractCriterionTriggerInstance {
      public TriggerInstance(ContextAwarePredicate player) {
         super(OpenBoxTrigger.ID, player);
      }

      public static OpenBoxTrigger.TriggerInstance unboxed() {
         return new OpenBoxTrigger.TriggerInstance(ContextAwarePredicate.ANY);
      }
   }
}
