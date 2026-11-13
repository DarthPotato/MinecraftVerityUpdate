package varmite.verity.entity.demon.pathfinding;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

public class DemonPathNavigation extends GroundPathNavigation {
   private static final float PATHFINDING_RANGE = 64.0F;

   public DemonPathNavigation(Mob mob, Level level) {
      super(mob, level);
   }

   @Override
   protected PathFinder createPathFinder(int maxVisitedNodes) {
      this.nodeEvaluator = new DemonNodeEvaluator();
      this.nodeEvaluator.setCanPassDoors(true);
      this.nodeEvaluator.setCanOpenDoors(true);
      return new PathFinder(this.nodeEvaluator, maxVisitedNodes * 2);
   }

   @Override
   protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy) {
      return this.createPath(targets, regionOffset, offsetUpward, accuracy, 64.0F);
   }
}
