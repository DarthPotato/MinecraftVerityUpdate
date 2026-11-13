package varmite.verity.triggers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class FavoriteSongTrigger extends SimpleCriterionTrigger<FavoriteSongTrigger.TriggerInstance> {
   public static final String ID = "favoritesong";

   @Override
   public Codec<FavoriteSongTrigger.TriggerInstance> codec() {
      return FavoriteSongTrigger.TriggerInstance.CODEC;
   }

   public void trigger(ServerPlayer player) {
      this.trigger(player, instance -> true);
   }

   public record TriggerInstance(Optional<Holder<LootItemCondition>> player) implements SimpleCriterionTrigger.SimpleInstance {
      public static final Codec<FavoriteSongTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
         instance -> instance.group(LootItemCondition.CODEC.optionalFieldOf("player").forGetter(FavoriteSongTrigger.TriggerInstance::player))
            .apply(instance, FavoriteSongTrigger.TriggerInstance::new)
      );
   }
}
