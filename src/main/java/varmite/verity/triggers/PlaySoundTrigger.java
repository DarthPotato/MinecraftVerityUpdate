package varmite.verity.triggers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class PlaySoundTrigger extends SimpleCriterionTrigger<PlaySoundTrigger.TriggerInstance> {
   public static final String ID = "playsound";

   @Override
   public Codec<PlaySoundTrigger.TriggerInstance> codec() {
      return PlaySoundTrigger.TriggerInstance.CODEC;
   }

   public void trigger(ServerPlayer player) {
      this.trigger(player, instance -> true);
   }

   public record TriggerInstance(Optional<Holder<LootItemCondition>> player) implements SimpleCriterionTrigger.SimpleInstance {
      public static final Codec<PlaySoundTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
         instance -> instance.group(LootItemCondition.CODEC.optionalFieldOf("player").forGetter(PlaySoundTrigger.TriggerInstance::player))
            .apply(instance, PlaySoundTrigger.TriggerInstance::new)
      );
   }
}
