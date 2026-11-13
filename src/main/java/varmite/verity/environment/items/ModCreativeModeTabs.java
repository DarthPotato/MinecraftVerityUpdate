package varmite.verity.environment.items;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

public class ModCreativeModeTabs {
   public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "verity");
   public static final Supplier<CreativeModeTab> VERITY_TAB = CREATIVE_MODE_TABS.register(
      "verity_tab",
      () -> CreativeModeTab.builder()
         .title(Component.translatable("creativetab.verity.verity_tab"))
         .icon(() -> new ItemStack(ModItems.VERITY_ITEM.get()))
         .displayItems((parameters, output) -> {
            output.accept(ModItems.VERITY_ITEM.get());
            output.accept(ModItems.VERITY_DISC.get());
            output.accept(ModItems.VERITY_EDIT_DISC.get());
            output.accept(ModItems.FLASHLIGHT.get());
         })
         .build()
   );

   public static void register(IEventBus eventBus) {
      CREATIVE_MODE_TABS.register(eventBus);
   }
}
