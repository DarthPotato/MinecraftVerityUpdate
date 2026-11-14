package varmite.verity.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import varmite.verity.entity.verity.VerityEntity;

public class VerityState {
   public static boolean hasSpawned = false;
   public static long timeWillSpawn;
   public static VerityEntity verityEntity = null;
   public static final Map<UUID, Long> HURT_COOLDOWN = new HashMap<>();
   public static boolean transformFollowingDay = false;
   public static boolean followPlayer = false;
   public static int idleChatTimer = 3600;
   public static int lonelinessTimer = 3000;
   public static boolean isClientTalking;

   /**
    * Port fix: gives a Verity item to the player, or drops it at their feet when the inventory is full (a Verity item on
    * the ground turns back into Verity a second later). The rescue paths used to call Inventory.add and ignore a full
    * inventory, which deleted him. Returns true when it went into the inventory.
    */
   public static boolean giveOrDrop(Player player, ItemStack stack) {
      if (player.getInventory().add(stack) || stack.isEmpty()) {
         return true;
      }

      player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack));
      return false;
   }
}
