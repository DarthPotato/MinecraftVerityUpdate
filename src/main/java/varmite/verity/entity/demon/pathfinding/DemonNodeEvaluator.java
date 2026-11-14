package varmite.verity.entity.demon.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.neoforged.neoforge.common.Tags;

public class DemonNodeEvaluator extends WalkNodeEvaluator {
   private static final int CRAWL_CLEARANCE = 2;

   @Override
   public void prepare(PathNavigationRegion region, Mob mob) {
      super.prepare(region, mob);
      this.entityHeight = Math.min(this.entityHeight, 2);
   }

   @Override
   public PathType getPathType(PathfindingContext context, int x, int y, int z) {
      BlockState state = context.getBlockState(new BlockPos(x, y, z));
      return isSoftCover(state) ? PathType.OPEN : super.getPathType(context, x, y, z);
   }

   private static boolean isSoftCover(BlockState state) {
      return state.is(BlockTags.LEAVES) || state.is(Tags.BlockItems.GLASS_BLOCKS.block()) || state.is(Tags.BlockItems.GLASS_PANES.block());
   }
}
