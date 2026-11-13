package varmite.verity.entity.demon.goals;

import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import varmite.verity.entity.demon.VerityDemonEntity;

public class DemonBreakDoorGoal extends BreakDoorGoal {
   private final VerityDemonEntity demon;

   public DemonBreakDoorGoal(VerityDemonEntity demon) {
      super(demon, x -> true);
      this.demon = demon;
   }

   @Override
   public boolean canUse() {
      return this.demon.getDemonState() != 1 ? false : super.canUse();
   }

   @Override
   public boolean canContinueToUse() {
      return this.demon.getDemonState() != 1 ? false : super.canContinueToUse();
   }

   @Override
   public void tick() {
      super.tick();
      if (this.demon.tickCount % 15 == 0) {
         this.demon.triggerAttack();
      }
   }
}
