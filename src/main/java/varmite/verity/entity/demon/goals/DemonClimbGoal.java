package varmite.verity.entity.demon.goals;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import varmite.verity.entity.demon.VerityDemonEntity;

public class DemonClimbGoal extends Goal {
   private static final double MIN_HEIGHT_GAIN = 3.0;
   private static final double MAX_HORIZONTAL_RANGE = 16.0;
   private static final double HUG_DISTANCE = 1.6;
   private static final double CLIMB_SPEED = 0.35;
   private static final double HUG_SPEED = 0.2;
   private static final int STALL_LIMIT = 40;
   private static final int MAX_ATTEMPT_TICKS = 200;
   private static final int RETRY_COOLDOWN = 40;
   private final VerityDemonEntity demon;
   private LivingEntity target;
   private BlockPos column;
   private double lastY;
   private int stallTicks;
   private int runTicks;
   private int cooldown;

   public DemonClimbGoal(VerityDemonEntity demon) {
      this.demon = demon;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
   }

   @Override
   public boolean canUse() {
      if (this.cooldown > 0) {
         this.cooldown--;
         return false;
      }

      if (this.demon.getDemonState() != 1) {
         return false;
      }

      if (!this.demon.isEating() && !this.demon.isGrabbing()) {
         this.target = this.demon.getTarget();
         if (this.target != null && this.target.isAlive()) {
            double gain = this.target.getY() - this.demon.getY();
            if (gain < 3.0) {
               return false;
            } else {
               double dx = this.target.getX() - this.demon.getX();
               double dz = this.target.getZ() - this.demon.getZ();
               double horizontal = Math.sqrt(dx * dx + dz * dz);
               if (!(horizontal > 16.0) && !(gain < horizontal)) {
                  this.column = this.findClimbableColumn();
                  return this.column != null;
               } else {
                  return false;
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean canContinueToUse() {
      if (this.demon.getDemonState() != 1 || this.column == null) {
         return false;
      } else if (this.target == null || !this.target.isAlive()) {
         return false;
      } else if (this.demon.isEating() || this.demon.isGrabbing()) {
         return false;
      } else {
         return this.stallTicks <= 40 && this.runTicks <= 200 ? this.demon.getY() < this.target.getY() - 0.5 : false;
      }
   }

   @Override
   public void start() {
      this.lastY = this.demon.getY();
      this.stallTicks = 0;
      this.runTicks = 0;
      this.demon.setClimbGoalActive(true);
   }

   @Override
   public void stop() {
      boolean reachedThem = this.target != null && this.demon.getY() >= this.target.getY() - 0.5;
      if (reachedThem) {
         Vec3 lunge = new Vec3(this.target.getX() - this.demon.getX(), 0.0, this.target.getZ() - this.demon.getZ());
         if (lunge.lengthSqr() > 1.0E-4) {
            lunge = lunge.normalize().scale(0.45);
            this.demon.setDeltaMovement(lunge.x, 0.25, lunge.z);
            this.demon.needsSync = true;
         }
      } else {
         this.cooldown = 40;
      }

      this.demon.setClimbGoalActive(false);
      this.demon.setClimbing(false);
      this.demon.getNavigation().stop();
      this.column = null;
      this.target = null;
      this.stallTicks = 0;
      this.runTicks = 0;
   }

   @Override
   public boolean requiresUpdateEveryTick() {
      return true;
   }

   @Override
   public void tick() {
      if (this.column != null && this.target != null) {
         this.runTicks++;
         this.demon.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
         double dx = this.column.getX() + 0.5 - this.demon.getX();
         double dz = this.column.getZ() + 0.5 - this.demon.getZ();
         double horizontal = Math.sqrt(dx * dx + dz * dz);
         if (horizontal > 1.6) {
            this.approachFoot(dx, dz);
         } else {
            this.haulUp(dx, dz, horizontal);
         }
      }
   }

   private void approachFoot(double dx, double dz) {
      this.demon.setClimbing(false);
      if (this.demon.getNavigation().isDone()) {
         this.demon.getNavigation().moveTo(this.column.getX() + 0.5, this.demon.getY(), this.column.getZ() + 0.5, 1.2);
      }

      this.lastY = this.demon.getY();
   }

   private void haulUp(double dx, double dz, double horizontal) {
      this.demon.getNavigation().stop();
      this.demon.setClimbing(true);
      this.demon.fallDistance = 0.0F;
      double hugX = 0.0;
      double hugZ = 0.0;
      if (horizontal > 1.0E-4) {
         hugX = dx / horizontal * 0.2;
         hugZ = dz / horizontal * 0.2;
      }

      this.demon.setDeltaMovement(hugX, 0.35, hugZ);
      this.demon.needsSync = true;
      this.clearAscent();
      if (this.demon.getY() - this.lastY < 0.04) {
         this.stallTicks++;
      } else {
         this.stallTicks = 0;
      }

      this.lastY = this.demon.getY();
   }

   private void clearAscent() {
      Level level = this.demon.level();
      BlockPos head = this.demon.blockPosition().above((int)Math.ceil(this.demon.getBbHeight()));

      for (BlockPos pos : new BlockPos[]{head, head.above()}) {
         BlockState state = level.getBlockState(pos);
         if (!state.getCollisionShape(level, pos).isEmpty() && state.getDestroySpeed(level, pos) >= 0.0F) {
            level.destroyBlock(pos, true);
            this.demon.triggerAttack();
         }
      }
   }

   private BlockPos findClimbableColumn() {
      Level level = this.demon.level();
      BlockPos below = this.target.blockPosition().below();
      if (this.isSolid(below)) {
         return null;
      }

      int run = 0;
      int floor = Math.max(this.demon.getBlockY(), level.getMinY());

      for (int y = below.getY(); y >= floor && !this.isSolid(new BlockPos(below.getX(), y, below.getZ())); y--) {
         run++;
      }

      return run < 3.0 ? null : new BlockPos(below.getX(), this.demon.getBlockY(), below.getZ());
   }

   private boolean isSolid(BlockPos pos) {
      BlockState state = this.demon.level().getBlockState(pos);
      return !state.getCollisionShape(this.demon.level(), pos).isEmpty();
   }
}
