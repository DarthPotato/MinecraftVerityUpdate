package varmite.verity.environment.block;

import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
   public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "verity");
   public static final RegistryObject<Block> FLASHLIGHT_LIGHT = BLOCKS.register("flashlight_light", FlashlightLightBlock::new);

   public static void register(IEventBus eventBus) {
      BLOCKS.register(eventBus);
   }
}
