package varmite.verity.triggers;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class PlaySoundTrigger extends SimpleCriterionTrigger<PlaySoundTrigger.TriggerInstance> {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("verity", "playsound");

   @Override
   public ResourceLocation getId() {
      return ID;
   }

   protected PlaySoundTrigger.TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
      return new PlaySoundTrigger.TriggerInstance(player);
   }

   public void trigger(ServerPlayer player) {
      this.trigger(player, instance -> true);
   }

   public static class TriggerInstance extends AbstractCriterionTriggerInstance {
      public TriggerInstance(ContextAwarePredicate player) {
         super(PlaySoundTrigger.ID, player);
      }

      public static PlaySoundTrigger.TriggerInstance unboxed() {
         return new PlaySoundTrigger.TriggerInstance(ContextAwarePredicate.ANY);
      }
   }
}
