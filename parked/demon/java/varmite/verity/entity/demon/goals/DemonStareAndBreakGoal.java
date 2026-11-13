package varmite.verity.entity.demon.goals;

import java.util.EnumSet;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import varmite.verity.entity.demon.VerityDemonEntity;
import varmite.verity.environment.sounds.ModSounds;

public class DemonStareAndBreakGoal extends Goal {
   private final VerityDemonEntity demon;
   private Player targetPlayer;
   private boolean hasTriggeredTransformation;
   private int delayTicks;
   private final TargetingConditions targetingConditions;

   public DemonStareAndBreakGoal(VerityDemonEntity demon) {
      this.demon = demon;
      this.hasTriggeredTransformation = false;
      this.delayTicks = 0;
      this.setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
      this.targetingConditions = TargetingConditions.forNonCombat().range(64.0).ignoreLineOfSight();
   }

   @Override
   public boolean canUse() {
      if (this.demon.getDemonState() != 0 || this.demon.getHuntPhase() != 1) {
         return false;
      }

      if (this.hasTriggeredTransformation && this.delayTicks <= 0) {
         return false;
      }

      this.targetPlayer = this.demon
         .level()
         .getNearestPlayer(this.targetingConditions, this.demon, this.demon.getX(), this.demon.getLookAnchorY(), this.demon.getZ());
      return this.targetPlayer == null || !this.targetPlayer.isCreative() && !this.targetPlayer.isSpectator() ? this.targetPlayer != null : false;
   }

   @Override
   public boolean canContinueToUse() {
      if (this.demon.getDemonState() != 0 || this.demon.getHuntPhase() != 1) {
         return false;
      } else {
         return this.hasTriggeredTransformation
            ? this.delayTicks > 0
            : this.targetPlayer != null && this.targetPlayer.isAlive() && !this.targetPlayer.isCreative() && !this.targetPlayer.isSpectator();
      }
   }

   @Override
   public void start() {
      this.demon.getNavigation().stop();
      if (this.demon.getDemonState() == 0) {
         this.hasTriggeredTransformation = false;
         this.delayTicks = 0;
      }
   }

   @Override
   public void tick() {
      if (this.hasTriggeredTransformation) {
         this.demon.getNavigation().stop();
         this.delayTicks--;
      } else if (this.targetPlayer != null) {
         this.demon.getLookControl().setLookAt(this.targetPlayer, 30.0F, 30.0F);
         double distanceSqr = this.demon.distanceToSqr(this.targetPlayer);
         if (distanceSqr > 1024.0) {
            this.demon.getNavigation().moveTo(this.targetPlayer, 1.0);
         } else {
            this.demon.getNavigation().stop();
         }

         if (this.isPlayerLookingAtDemon(this.targetPlayer)) {
            this.demon.level().playSound((Player)null, this.demon.blockPosition(), ModSounds.BONE_0.get(), SoundSource.HOSTILE, 1.5F, 1.0F);
            this.hasTriggeredTransformation = true;
            this.delayTicks = VerityDemonEntity.PREPARE_FOR_CHASE_TICKS - 1;
            this.demon.triggerPrepareForChase();
         }
      }
   }

   @Override
   public void stop() {
      if (this.hasTriggeredTransformation && this.targetPlayer != null && this.targetPlayer.isAlive()) {
         this.demon.setDemonState(1);
         this.demon.setTarget(this.targetPlayer);
         this.targetPlayer.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 1200, 0, false, false, false));
      }
   }

   private boolean isPlayerLookingAtDemon(Player player) {
      Vec3 playerView = player.getViewVector(1.0F).normalize();
      Vec3 playerToDemon = new Vec3(this.demon.getX() - player.getX(), this.demon.getLookAnchorY() - player.getEyeY(), this.demon.getZ() - player.getZ())
         .normalize();
      return playerView.dot(playerToDemon) > 0.98 && this.demon.hasLineOfSightThroughGlass(player);
   }
}
