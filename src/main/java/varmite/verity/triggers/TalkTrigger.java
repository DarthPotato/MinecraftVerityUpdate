package varmite.verity.triggers;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class TalkTrigger extends SimpleCriterionTrigger<TalkTrigger.TriggerInstance> {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("verity", "talk");

   @Override
   public ResourceLocation getId() {
      return ID;
   }

   protected TalkTrigger.TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
      return new TalkTrigger.TriggerInstance(player);
   }

   public void trigger(ServerPlayer player) {
      this.trigger(player, instance -> true);
   }

   public static class TriggerInstance extends AbstractCriterionTriggerInstance {
      public TriggerInstance(ContextAwarePredicate player) {
         super(TalkTrigger.ID, player);
      }

      public static TalkTrigger.TriggerInstance unboxed() {
         return new TalkTrigger.TriggerInstance(ContextAwarePredicate.ANY);
      }
   }
}
