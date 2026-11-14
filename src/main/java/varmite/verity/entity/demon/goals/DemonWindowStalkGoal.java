package varmite.verity.entity.demon.goals;

import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import varmite.verity.entity.demon.DemonSpawnPlacement;
import varmite.verity.entity.demon.VerityDemonEntity;

public class DemonWindowStalkGoal extends Goal {
   private final VerityDemonEntity demon;
   private Player targetPlayer;
   private int stareTicks;
   private final TargetingConditions targetingConditions;

   public DemonWindowStalkGoal(VerityDemonEntity demon) {
      this.demon = demon;
      this.stareTicks = 0;
      this.setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
      this.targetingConditions = TargetingConditions.forNonCombat().range(128.0).ignoreLineOfSight();
   }

   @Override
   public boolean canContinueToUse() {
      return this.demon.getDemonState() == 0 && this.demon.getHuntPhase() == 0
         ? this.targetPlayer != null && this.targetPlayer.isAlive() && !this.targetPlayer.isCreative() && !this.targetPlayer.isSpectator()
         : false;
   }

   @Override
   public boolean canUse() {
      if (this.demon.getDemonState() == 0 && this.demon.getHuntPhase() == 0) {
         this.targetPlayer = getServerLevel(this.demon).getNearestPlayer(this.targetingConditions, this.demon, this.demon.getX(), this.demon.getLookAnchorY(), this.demon.getZ());
         return this.targetPlayer == null || !this.targetPlayer.isCreative() && !this.targetPlayer.isSpectator() ? this.targetPlayer != null : false;
      } else {
         return false;
      }
   }

   @Override
   public void start() {
      this.stareTicks = 0;
      this.demon.setStalkingWindow(true);
   }

   @Override
   public void stop() {
      this.demon.setStalkingWindow(false);
      this.targetPlayer = null;
   }

   @Override
   public void tick() {
      if (this.targetPlayer != null) {
         this.demon.getLookControl().setLookAt(this.targetPlayer, 30.0F, 30.0F);
         this.demon.getNavigation().stop();
         if (this.isPlayerLookingAtDemon(this.targetPlayer)) {
            this.stareTicks++;
            if (this.stareTicks >= 5) {
               this.disappear();
            }
         } else {
            this.stareTicks = 0;
         }
      }
   }

   private boolean isPlayerLookingAtDemon(Player player) {
      Vec3 playerView = player.getViewVector(1.0F).normalize();
      Vec3 playerToDemon = new Vec3(this.demon.getX() - player.getX(), this.demon.getLookAnchorY() - player.getEyeY(), this.demon.getZ() - player.getZ())
         .normalize();
      return playerView.dot(playerToDemon) > 0.98 && this.demon.hasLineOfSightThroughGlass(player);
   }

   private void disappear() {
      if (this.demon.level() instanceof ServerLevel serverLevel) {
         serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, this.demon.getX(), this.demon.getY() + 1.5, this.demon.getZ(), 30, 0.5, 1.0, 0.5, 0.01);
         serverLevel.playSound((Player)null, this.demon.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.6F);
      }

      this.demon.setHuntPhase(1);
      this.teleportAway();
   }

   private void teleportAway() {
      if (this.demon.level() instanceof ServerLevel level && this.targetPlayer != null) {
         DemonSpawnPlacement.Placement placement = DemonSpawnPlacement.find(level, this.targetPlayer, this.demon);
         if (placement != null) {
            placement.apply(this.demon);
         }

         this.demon.getNavigation().stop();
         this.demon.startSpawnFootsteps(this.targetPlayer);
      }
   }
}
