package varmite.verity.environment.block;

import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModBlocks {
   public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "verity");
   public static final DeferredHolder<Block, Block> FLASHLIGHT_LIGHT = BLOCKS.register("flashlight_light", FlashlightLightBlock::new);

   public static void register(IEventBus eventBus) {
      BLOCKS.register(eventBus);
   }
}
