package varmite.verity.entity.demon.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraftforge.common.Tags.Blocks;

public class DemonNodeEvaluator extends WalkNodeEvaluator {
   private static final int CRAWL_CLEARANCE = 2;

   @Override
   public void prepare(PathNavigationRegion region, Mob mob) {
      super.prepare(region, mob);
      this.entityHeight = Math.min(this.entityHeight, 2);
   }

   @Override
   public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z) {
      BlockState state = level.getBlockState(new BlockPos(x, y, z));
      return isSoftCover(state) ? BlockPathTypes.OPEN : super.getBlockPathType(level, x, y, z);
   }

   private static boolean isSoftCover(BlockState state) {
      return state.is(BlockTags.LEAVES) || state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANES);
   }
}
