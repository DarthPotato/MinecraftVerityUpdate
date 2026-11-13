package varmite.verity.environment.items;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.Item.Properties;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import varmite.verity.environment.sounds.ModSounds;

public class ModItems {
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "verity");
   public static final RegistryObject<Item> VERITY_ITEM = ITEMS.register("verity_item", () -> new VerityItem(new Properties()));
   public static final RegistryObject<Item> FLASHLIGHT = ITEMS.register("flashlight", () -> new Item(new Properties()));
   public static final RegistryObject<Item> VERITY_DISC = ITEMS.register(
      "verity_disc", () -> new RecordItem(15, ModSounds.VERITY_DISC_SOUND, new Properties().stacksTo(1).rarity(Rarity.RARE), 3000)
   );
   public static final RegistryObject<Item> VERITY_EDIT_DISC = ITEMS.register(
      "verity_edit_disc", () -> new RecordItem(15, ModSounds.VERITY_EDIT_DISC_SOUND, new Properties().stacksTo(1).rarity(Rarity.RARE), 400)
   );

   public static void register(IEventBus bus) {
      ITEMS.register(bus);
   }
}
