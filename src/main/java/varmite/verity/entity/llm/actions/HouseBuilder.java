package varmite.verity.entity.llm.actions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Verity's "build a house" action. Not in the original 6.0.0-beta.9: added for the owner (2026-11-14). Places one of
 * Minecraft's own village houses, in the style of the player's biome, a few blocks in front of the player.
 *
 * It refuses rather than wreck anything: no building on very uneven ground, on water, or where there are chests,
 * furnaces or other block entities. Chests in the house come empty (village loot is stripped), and no villagers spawn.
 */
public final class HouseBuilder {
   private static final Map<String, List<String>> HOUSES = Map.of(
      "plains", List.of("small_house_1", "small_house_2", "small_house_3", "small_house_4", "small_house_5", "small_house_6", "small_house_7", "small_house_8", "medium_house_1", "medium_house_2", "big_house_1"),
      "desert", List.of("small_house_1", "small_house_2", "small_house_3", "small_house_4", "small_house_5", "small_house_6", "small_house_7", "small_house_8", "medium_house_1", "medium_house_2"),
      "savanna", List.of("small_house_1", "small_house_2", "small_house_3", "small_house_4", "small_house_5", "small_house_6", "small_house_7", "small_house_8", "medium_house_1", "medium_house_2"),
      "snowy", List.of("small_house_1", "small_house_2", "small_house_3", "small_house_4", "small_house_5", "small_house_6", "small_house_7", "small_house_8", "medium_house_1", "medium_house_2", "medium_house_3"),
      "taiga", List.of("small_house_1", "small_house_2", "small_house_3", "small_house_4", "small_house_5", "medium_house_1", "medium_house_2", "medium_house_3", "medium_house_4")
   );
   private static final int GAP = 2;
   private static final int MAX_SLOPE = 4;
   private static final int MAX_FILL = 6;
   private static final int COOLDOWN_TICKS = 1200;
   private static long lastBuildTick = Long.MIN_VALUE / 2;

   private HouseBuilder() {
   }

   public static String build(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      long now = level.getGameTime();
      if (now - lastBuildTick < COOLDOWN_TICKS) {
         return "Failed: I just built a house. Wait a minute before asking for another one.";
      }

      String style = styleFor(level.getBiome(player.blockPosition()));
      List<String> names = HOUSES.get(style);
      RandomSource random = level.getRandom();
      String name = style + "_" + names.get(random.nextInt(names.size()));
      Optional<StructureTemplate> found = level.getStructureManager().get(ResourceLocation.withDefaultNamespace("village/" + style + "/houses/" + name));
      if (found.isEmpty()) {
         return "Failed: I could not find the house design " + name + ".";
      }

      StructureTemplate template = found.get();
      Direction facing = player.getDirection();
      StructurePlaceSettings settings = new StructurePlaceSettings()
         .setRotation(rotationFacing(facing))
         .setMirror(Mirror.NONE)
         .setIgnoreEntities(true)
         .addProcessor(JigsawReplacementProcessor.INSTANCE);
      Vec3i size = template.getSize(settings.getRotation());
      int reach = GAP + Math.max(size.getX(), size.getZ()) / 2 + 1;
      BlockPos centre = player.blockPosition().relative(facing, reach);
      BoundingBox atZero = template.getBoundingBox(settings, BlockPos.ZERO);
      int originX = centre.getX() - (atZero.minX() + atZero.maxX()) / 2;
      int originZ = centre.getZ() - (atZero.minZ() + atZero.maxZ()) / 2;
      BoundingBox footprint = template.getBoundingBox(settings, new BlockPos(originX, 0, originZ));

      int lowest = Integer.MAX_VALUE;
      int highest = Integer.MIN_VALUE;

      for (int x = footprint.minX(); x <= footprint.maxX(); x++) {
         for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
            int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            lowest = Math.min(lowest, height);
            highest = Math.max(highest, height);
            if (!level.getFluidState(new BlockPos(x, height - 1, z)).isEmpty()) {
               return "Failed: there is water or lava where the house would go. Find dry land.";
            }
         }
      }

      if (highest - lowest > MAX_SLOPE) {
         return "Failed: the ground in front of you is too uneven for a house. Find a flatter spot and face it.";
      }

      // The house's bottom layer is its foundation, so it sits one block into the ground, like in a village.
      BlockPos origin = new BlockPos(originX, lowest - 1, originZ);
      BoundingBox box = template.getBoundingBox(settings, origin);
      if (box.isInside(player.blockPosition())) {
         return "Failed: you are standing where the house would go. Step back and ask again.";
      }

      for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY() + MAX_SLOPE, box.maxZ())) {
         if (level.getBlockEntity(pos) != null) {
            return "Failed: something is already built there (I saw a chest, furnace or similar). Pick an empty spot.";
         }
      }

      // Clear grass, flowers and snow layers so they don't poke through the floor.
      for (BlockPos pos : BlockPos.betweenClosed(box.minX(), origin.getY() + 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
         BlockState state = level.getBlockState(pos);
         if (!state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
         }
      }

      if (!template.placeInWorld(level, origin, origin, settings, random, Block.UPDATE_ALL)) {
         return "Failed: the house could not be placed here.";
      }

      fillUnderFoundation(level, box, origin.getY());
      emptyChests(level, box);
      lastBuildTick = now;
      return "Built a " + name.replace('_', ' ') + " at X=" + centre.getX() + " Y=" + lowest + " Z=" + centre.getZ() + ". Tell the player it is ready.";
   }

   private static String styleFor(Holder<Biome> biome) {
      if (biome.is(BiomeTags.HAS_VILLAGE_DESERT)) {
         return "desert";
      } else if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY)) {
         return "snowy";
      } else if (biome.is(BiomeTags.HAS_VILLAGE_TAIGA)) {
         return "taiga";
      } else {
         return biome.is(BiomeTags.HAS_VILLAGE_SAVANNA) ? "savanna" : "plains";
      }
   }

   /** Turns the house to match the way the player is looking. */
   private static Rotation rotationFacing(Direction facing) {
      return switch (facing) {
         case EAST -> Rotation.CLOCKWISE_90;
         case SOUTH -> Rotation.CLOCKWISE_180;
         case WEST -> Rotation.COUNTERCLOCKWISE_90;
         default -> Rotation.NONE;
      };
   }

   /** Fills dips under the foundation with dirt so the house doesn't float on a slope. */
   private static void fillUnderFoundation(ServerLevel level, BoundingBox box, int floorY) {
      for (int x = box.minX(); x <= box.maxX(); x++) {
         for (int z = box.minZ(); z <= box.maxZ(); z++) {
            if (!level.getBlockState(new BlockPos(x, floorY, z)).isAir()) {
               for (int y = floorY - 1; y >= floorY - MAX_FILL; y--) {
                  BlockPos below = new BlockPos(x, y, z);
                  BlockState state = level.getBlockState(below);
                  if (!state.isAir() && !state.canBeReplaced()) {
                     break;
                  }

                  level.setBlock(below, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
               }
            }
         }
      }
   }

   /** Village chests carry loot tables; strip them so a house is not a free loot farm. */
   private static void emptyChests(ServerLevel level, BoundingBox box) {
      for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
         BlockEntity blockEntity = level.getBlockEntity(pos);
         if (blockEntity instanceof RandomizableContainer container) {
            container.setLootTable(null);
         }
      }
   }
}
