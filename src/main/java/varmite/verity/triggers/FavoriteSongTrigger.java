package varmite.verity.triggers;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class FavoriteSongTrigger extends SimpleCriterionTrigger<FavoriteSongTrigger.TriggerInstance> {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("verity", "favoritesong");

   @Override
   public ResourceLocation getId() {
      return ID;
   }

   protected FavoriteSongTrigger.TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
      return new FavoriteSongTrigger.TriggerInstance(player);
   }

   public void trigger(ServerPlayer player) {
      this.trigger(player, instance -> true);
   }

   public static class TriggerInstance extends AbstractCriterionTriggerInstance {
      public TriggerInstance(ContextAwarePredicate player) {
         super(FavoriteSongTrigger.ID, player);
      }

      public static FavoriteSongTrigger.TriggerInstance unboxed() {
         return new FavoriteSongTrigger.TriggerInstance(ContextAwarePredicate.ANY);
      }
   }
}
