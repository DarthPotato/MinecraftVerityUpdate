package varmite.verity.environment.items;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "verity");
   public static final RegistryObject<Item> VERITY_ITEM = ITEMS.register("verity_item", () -> new VerityItem(new Properties()));
   public static final RegistryObject<Item> FLASHLIGHT = ITEMS.register("flashlight", () -> new Item(new Properties()));

   public static void register(IEventBus bus) {
      ITEMS.register(bus);
   }
}
