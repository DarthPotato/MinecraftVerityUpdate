package varmite.verity.triggers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

public class OpenBoxTrigger extends SimpleCriterionTrigger<OpenBoxTrigger.TriggerInstance> {
   public static final String ID = "open_box";

   @Override
   public Codec<OpenBoxTrigger.TriggerInstance> codec() {
      return OpenBoxTrigger.TriggerInstance.CODEC;
   }

   public void trigger(ServerPlayer player) {
      this.trigger(player, instance -> true);
   }

   public record TriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleCriterionTrigger.SimpleInstance {
      public static final Codec<OpenBoxTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
         instance -> instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(OpenBoxTrigger.TriggerInstance::player))
            .apply(instance, OpenBoxTrigger.TriggerInstance::new)
      );
   }
}
