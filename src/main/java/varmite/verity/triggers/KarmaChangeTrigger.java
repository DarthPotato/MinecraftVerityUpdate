package varmite.verity.triggers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

public class KarmaChangeTrigger extends SimpleCriterionTrigger<KarmaChangeTrigger.TriggerInstance> {
   public static final String ID = "karmachange";

   @Override
   public Codec<KarmaChangeTrigger.TriggerInstance> codec() {
      return KarmaChangeTrigger.TriggerInstance.CODEC;
   }

   public void trigger(ServerPlayer player) {
      this.trigger(player, instance -> true);
   }

   public record TriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleCriterionTrigger.SimpleInstance {
      public static final Codec<KarmaChangeTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
         instance -> instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(KarmaChangeTrigger.TriggerInstance::player))
            .apply(instance, KarmaChangeTrigger.TriggerInstance::new)
      );
   }
}
