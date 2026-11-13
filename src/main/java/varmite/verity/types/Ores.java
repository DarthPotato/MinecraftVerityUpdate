package varmite.verity.types;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public enum Ores {
   DIAMOND(BlockTags.DIAMOND_ORES),
   IRON(BlockTags.IRON_ORES),
   GOLD(BlockTags.GOLD_ORES),
   COAL(BlockTags.COAL_ORES),
   EMERALD(BlockTags.EMERALD_ORES),
   LAPIS(BlockTags.LAPIS_ORES),
   REDSTONE(BlockTags.REDSTONE_ORES),
   COPPER(BlockTags.COPPER_ORES);

   Ores(TagKey<Block> blockTag) {
   }

   public static Ores fromString(String name) {
      return valueOf(name.toUpperCase());
   }
}
