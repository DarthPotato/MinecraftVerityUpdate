package varmite.verity.environment.items;

import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** What the Forge build kept in the item stack's NBT tag now lives in the custom data component. */
public final class StackData {
   private StackData() {
   }

   public static boolean has(ItemStack stack, String key) {
      CustomData data = stack.get(DataComponents.CUSTOM_DATA);
      return data != null && data.contains(key);
   }

   public static String getString(ItemStack stack, String key) {
      return copy(stack).getString(key);
   }

   public static boolean getBoolean(ItemStack stack, String key) {
      return copy(stack).getBoolean(key);
   }

   /** A copy of the stack's data, empty if it has none. */
   public static CompoundTag copy(ItemStack stack) {
      CustomData data = stack.get(DataComponents.CUSTOM_DATA);
      return data == null ? new CompoundTag() : data.copyTag();
   }

   public static void update(ItemStack stack, Consumer<CompoundTag> updater) {
      CustomData.update(DataComponents.CUSTOM_DATA, stack, updater);
   }

   public static void set(ItemStack stack, CompoundTag tag) {
      CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
   }
}
