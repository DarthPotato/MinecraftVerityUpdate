package varmite.verity.types;

import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public enum Ores {
   DIAMOND(BlockItemTags.DIAMOND_ORES.block()),
   IRON(BlockTags.IRON_ORES),
   GOLD(BlockTags.GOLD_ORES),
   COAL(BlockItemTags.COAL_ORES.block()),
   EMERALD(BlockItemTags.EMERALD_ORES.block()),
   LAPIS(BlockItemTags.LAPIS_ORES.block()),
   REDSTONE(BlockItemTags.REDSTONE_ORES.block()),
   COPPER(BlockTags.COPPER_ORES);

   Ores(TagKey<Block> blockTag) {
   }

   public static Ores fromString(String name) {
      return valueOf(name.toUpperCase());
   }
}
