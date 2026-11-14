package varmite.verity.entity.demon;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import varmite.verity.entity.ModEntities;

public final class DemonSpawnPlacement {
   private static final int VERTICAL_SEARCH_RANGE = 8;

   private DemonSpawnPlacement() {
   }

   @Nullable
   public static DemonSpawnPlacement.Placement find(ServerLevel level, Player player, @Nullable VerityDemonEntity demon) {
      // Path types need a mob on 1.20.5+; before the demon exists, an unspawned one stands in.
      VerityDemonEntity probe = demon != null ? demon : ModEntities.VERITY_DEMON_ENTITY.get().create(level, EntitySpawnReason.LOAD);
      if (probe == null) {
         return null;
      }

      DemonSpawnPlacement.Placement placement = findInRing(level, player, demon, probe, 16.0, 32.0, 64);
      return placement != null ? placement : findInRing(level, player, demon, probe, 10.0, 16.0, 32);
   }

   @Nullable
   private static DemonSpawnPlacement.Placement findInRing(
      ServerLevel level, Player player, @Nullable VerityDemonEntity demon, VerityDemonEntity probe, double minDistance, double maxDistance, int attempts
   ) {
      DemonSpawnPlacement.Placement best = null;
      double bestScore = Double.NEGATIVE_INFINITY;
      double startAngle = level.getRandom().nextDouble() * Math.PI * 2.0;
      Vec3 view = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();

      for (int i = 0; i < attempts; i++) {
         double angle = startAngle + (i + level.getRandom().nextDouble()) * Math.PI * 2.0 / attempts;
         double distance = minDistance + level.getRandom().nextDouble() * (maxDistance - minDistance);
         BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * distance, player.getY(), player.getZ() + Math.sin(angle) * distance);

         for (int offset = 0; offset <= 16; offset++) {
            int dy = offset == 0 ? 0 : (offset + 1) / 2 * (offset % 2 == 0 ? -1 : 1);
            BlockPos pos = column.above(dy);
            Vec3 feet = Vec3.atBottomCenterOf(pos);
            Vec3 direction = feet.subtract(player.position()).multiply(1.0, 0.0, 1.0);
            double horizontalDistance = direction.length();
            if (!(horizontalDistance < minDistance) && !(horizontalDistance > maxDistance) && (demon == null || !(feet.distanceToSqr(demon.position()) < 64.0))
               )
             {
               DemonSpawnPlacement.Placement candidate = checkPosition(level, pos, demon, probe);
               if (candidate != null) {
                  Vec3 lookAnchor = feet.add(0.0, VerityDemonEntity.getLookAnchorHeight(candidate.crawling()), 0.0);
                  if (VerityDemonEntity.hasLineOfSightThroughGlass(player, lookAnchor)
                     && !level.players().stream().anyMatch(other -> other.isAlive() && !other.isSpectator() && other.distanceToSqr(feet) < 64.0)) {
                     double score = -Math.abs(horizontalDistance - 22.0) * 0.5
                        - Math.abs(dy) * 2.0
                        - view.dot(direction.normalize()) * 4.0
                        - (candidate.crawling() ? 1.0 : 0.0);
                     if (score > bestScore) {
                        bestScore = score;
                        best = candidate;
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   @Nullable
   private static DemonSpawnPlacement.Placement checkPosition(ServerLevel level, BlockPos pos, @Nullable VerityDemonEntity demon, VerityDemonEntity probe) {
      if (pos.getY() > level.getMinY() && level.hasChunkAt(pos)) {
         BlockPos floor = pos.below();
         BlockState ground = level.getBlockState(floor);
         if (!ground.isFaceSturdy(level, floor, Direction.UP)
            || ground.is(BlockTags.LEAVES)
            || !ground.getFluidState().isEmpty()
            || WalkNodeEvaluator.getPathTypeStatic(probe, pos) != PathType.WALKABLE) {
            return null;
         } else if (hasClearance(level, pos, demon, false)) {
            return new DemonSpawnPlacement.Placement(pos, false);
         } else {
            return hasClearance(level, pos, demon, true) ? new DemonSpawnPlacement.Placement(pos, true) : null;
         }
      } else {
         return null;
      }
   }

   private static boolean hasClearance(ServerLevel level, BlockPos pos, @Nullable VerityDemonEntity demon, boolean crawling) {
      EntityDimensions dimensions = VerityDemonEntity.getSpawnDimensions(crawling);
      AABB body = dimensions.makeBoundingBox(Vec3.atBottomCenterOf(pos));
      if (!(body.maxY > level.getMaxY() + 1)
         && !(body.minX < level.getWorldBorder().getMinX())
         && !(body.maxX > level.getWorldBorder().getMaxX())
         && !(body.minZ < level.getWorldBorder().getMinZ())
         && !(body.maxZ > level.getWorldBorder().getMaxZ())) {
         BlockPos min = BlockPos.containing(body.minX, body.minY, body.minZ);
         BlockPos max = BlockPos.containing(body.maxX, body.maxY, body.maxZ);
         if (level.hasChunksAt(min.offset(-1, 0, -1), max.offset(1, 0, 1)) && level.noCollision(demon, body)) {
            for (BlockPos occupied : BlockPos.betweenClosed(min, max)) {
               BlockState state = level.getBlockState(occupied);
               if (!state.getFluidState().isEmpty() || ModEntities.VERITY_DEMON_ENTITY.get().isBlockDangerous(state)) {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public record Placement(BlockPos pos, boolean crawling) {
      public void apply(VerityDemonEntity demon) {
         demon.setCrawling(this.crawling);
         demon.setPos(Vec3.atBottomCenterOf(this.pos));
      }
   }
}
