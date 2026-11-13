package varmite.verity.triggers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

public class GoodKarmaTrigger extends SimpleCriterionTrigger<GoodKarmaTrigger.TriggerInstance> {
   public static final String ID = "goodkarma";

   @Override
   public Codec<GoodKarmaTrigger.TriggerInstance> codec() {
      return GoodKarmaTrigger.TriggerInstance.CODEC;
   }

   public void trigger(ServerPlayer player) {
      this.trigger(player, instance -> true);
   }

   public record TriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleCriterionTrigger.SimpleInstance {
      public static final Codec<GoodKarmaTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
         instance -> instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(GoodKarmaTrigger.TriggerInstance::player))
            .apply(instance, GoodKarmaTrigger.TriggerInstance::new)
      );
   }
}
