package varmite.verity.entity.demon.goals;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import varmite.verity.entity.demon.VerityDemonEntity;
import varmite.verity.environment.sounds.ModSounds;

public class DemonGlassBreakAndLeapGoal extends Goal {
   private final VerityDemonEntity demon;
   private LivingEntity target;
   private int leapCooldown = 0;
   private BlockPos targetedGlass = null;

   public DemonGlassBreakAndLeapGoal(VerityDemonEntity demon) {
      this.demon = demon;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
   }

   @Override
   public boolean canUse() {
      if (this.demon.getDemonState() != 1) {
         return false;
      } else {
         this.target = this.demon.getTarget();
         if (this.target == null || !this.target.isAlive()) {
            return false;
         } else if (this.leapCooldown > 0) {
            this.leapCooldown--;
            return false;
         } else {
            this.targetedGlass = this.findGlassInWay();
            return this.targetedGlass != null;
         }
      }
   }

   @Override
   public void start() {
      if (this.targetedGlass != null) {
         this.shatterGlassArea(this.targetedGlass);
         this.demon.triggerAttack();
         this.demon.level().playSound((Player)null, this.demon.blockPosition(), ModSounds.JUMPSCARE.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
         double heightDiff = this.targetedGlass.getY() - this.demon.getY();
         double yBoost = 0.5;
         if (heightDiff > 0.0) {
            yBoost += heightDiff * 0.25;
         }

         Vec3 jumpDir = this.target.position().subtract(this.demon.position()).normalize();
         this.demon.setDeltaMovement(jumpDir.x * 1.8, yBoost, jumpDir.z * 1.8);
         this.demon.forceCrawl(30);
         this.leapCooldown = 100;
      }
   }

   private void shatterGlassArea(BlockPos center) {
      Level level = this.demon.level();

      for (int x = -1; x <= 2; x++) {
         for (int y = -1; y <= 2; y++) {
            for (int z = -1; z <= 2; z++) {
               BlockPos pos = center.offset(x, y, z);
               if (this.isGlass(pos)) {
                  level.destroyBlock(pos, false);
               }
            }
         }
      }
   }

   private BlockPos findGlassInWay() {
      Vec3 dir = this.target.position().subtract(this.demon.position()).normalize();
      BlockPos feet = this.demon.blockPosition();

      for (int i = 1; i <= 4; i++) {
         for (int yOffset = 0; yOffset <= 3; yOffset++) {
            BlockPos scanPos = BlockPos.containing(this.demon.getX() + dir.x * i, feet.getY() + yOffset, this.demon.getZ() + dir.z * i);
            if (this.isGlass(scanPos)) {
               return scanPos;
            }
         }
      }

      return null;
   }

   private boolean isGlass(BlockPos pos) {
      BlockState state = this.demon.level().getBlockState(pos);
      String blockName = state.getBlock().getDescriptionId().toLowerCase();
      return state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE) || blockName.contains("glass") || blockName.contains("pane");
   }
}
