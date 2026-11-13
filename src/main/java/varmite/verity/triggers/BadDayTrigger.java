package varmite.verity.triggers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

public class BadDayTrigger extends SimpleCriterionTrigger<BadDayTrigger.TriggerInstance> {
   public static final String ID = "badday";

   @Override
   public Codec<BadDayTrigger.TriggerInstance> codec() {
      return BadDayTrigger.TriggerInstance.CODEC;
   }

   public void trigger(ServerPlayer player) {
      this.trigger(player, instance -> true);
   }

   public record TriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleCriterionTrigger.SimpleInstance {
      public static final Codec<BadDayTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
         instance -> instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(BadDayTrigger.TriggerInstance::player))
            .apply(instance, BadDayTrigger.TriggerInstance::new)
      );
   }
}
