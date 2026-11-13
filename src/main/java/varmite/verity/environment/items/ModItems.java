package varmite.verity.environment.items;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Item.Properties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModItems {
   public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("verity");
   public static final DeferredHolder<Item, Item> VERITY_ITEM = ITEMS.<Item>registerItem("verity_item", VerityItem::new);
   public static final DeferredHolder<Item, Item> FLASHLIGHT = ITEMS.registerSimpleItem("flashlight");
   public static final DeferredHolder<Item, Item> VERITY_DISC = ITEMS.registerSimpleItem(
      "verity_disc", properties -> properties.stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(song("verity_disc"))
   );
   public static final DeferredHolder<Item, Item> VERITY_EDIT_DISC = ITEMS.registerSimpleItem(
      "verity_edit_disc", properties -> properties.stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(song("verity_edit_disc"))
   );

   /** The discs are data-driven since 1.21: see data/verity/jukebox_song. */
   private static ResourceKey<JukeboxSong> song(String name) {
      return ResourceKey.create(Registries.JUKEBOX_SONG, Identifier.fromNamespaceAndPath("verity", name));
   }

   public static void register(IEventBus bus) {
      ITEMS.register(bus);
   }
}
