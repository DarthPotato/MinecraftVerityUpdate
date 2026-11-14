package varmite.verity.entity.demon.goals;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import varmite.verity.entity.demon.VerityDemonEntity;

public class DemonAttackGoal extends MeleeAttackGoal {
   private final VerityDemonEntity demon;
   private int attackCooldown = 0;

   public DemonAttackGoal(VerityDemonEntity demon) {
      super(demon, 1.0, true);
      this.demon = demon;
   }

   @Override
   public boolean canUse() {
      return super.canUse() && !this.demon.isEating() && !this.demon.isGrabbing();
   }

   @Override
   public boolean canContinueToUse() {
      return super.canContinueToUse() && !this.demon.isEating() && !this.demon.isGrabbing();
   }

   @Override
   public void start() {
      super.start();
      this.attackCooldown = 0;
   }

   @Override
   public void stop() {
      super.stop();
   }

   @Override
   public void tick() {
      super.tick();
      if (this.attackCooldown > 0) {
         this.attackCooldown--;
      }
   }

   @Override
   protected void checkAndPerformAttack(LivingEntity target) {
      if (this.mob.isWithinMeleeAttackRange(target) && this.attackCooldown <= 0) {
         this.resetAttackCooldown();
         if (this.demon.getRandom().nextFloat() < 0.5F) {
            this.demon.startGrabbing(target);
         } else {
            this.demon.triggerAttack();
            this.mob.doHurtTarget(target);
         }
      }
   }

   @Override
   protected void resetAttackCooldown() {
      this.attackCooldown = this.adjustedTickDelay(20);
   }
}
