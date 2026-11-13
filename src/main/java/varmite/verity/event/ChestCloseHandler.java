package varmite.verity.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Close;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import varmite.verity.environment.items.ModItems;

@EventBusSubscriber(modid = "verity")
public class ChestCloseHandler {
   @SubscribeEvent
   public static void onChestClose(Close event) {
      if (!event.getEntity().level().isClientSide()) {
         if (event.getContainer() instanceof ChestMenu chestMenu) {
            boolean foundVerityItem = false;
            BlockPos chestPos = null;
            if (event.getContainer().getType() != MenuType.GENERIC_9x3 && event.getContainer().getType() != MenuType.GENERIC_9x6) {
               return;
            }

            if (chestMenu.getContainer() instanceof PlayerEnderChestContainer) {
               return;
            }

            if (chestMenu.getContainer() instanceof ContainerEntity) {
               return;
            }

            for (Slot slot : chestMenu.slots) {
               if (slot.container != event.getEntity().getInventory() && slot.getItem().is(ModItems.VERITY_ITEM.get())) {
                  foundVerityItem = true;
                  slot.set(ItemStack.EMPTY);
                  slot.setChanged();
                  if (chestPos == null && slot.container instanceof BlockEntity be) {
                     chestPos = be.getBlockPos();
                  }
               }
            }

            if (foundVerityItem && chestPos != null) {
               VeritySpawnScheduler.scheduleSpawn(event.getEntity().level(), chestPos, 0);
            }
         }
      }
   }
}
