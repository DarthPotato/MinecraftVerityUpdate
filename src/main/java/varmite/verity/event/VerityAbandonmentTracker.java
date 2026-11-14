package varmite.verity.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = "verity")
public class VerityAbandonmentTracker {
   private static final Map<UUID, Integer> ALONE_TICKS = new HashMap<>();
   private static final Map<UUID, Boolean> WAS_ABANDONED = new HashMap<>();
   private static final double ABANDON_DISTANCE = 24.0;
   private static final double RETURN_DISTANCE = 8.0;
   private static final int FIVE_MINUTES_TICKS = 6000;

   @SubscribeEvent
   public static void onPlayerTick(PlayerTickEvent event) {
      if (event.phase == Phase.END && !event.player.level().isClientSide()) {
         ServerPlayer player = (ServerPlayer)event.player;
         if (player.tickCount % 20 == 0) {
            ServerLevel level = (ServerLevel)player.level();
            Entity nearestVerity = findNearestVerity(level, player);
            if (nearestVerity != null) {
               UUID verityId = nearestVerity.getUUID();
               double distance = player.distanceTo(nearestVerity);
               if (distance > 24.0) {
                  int currentTicks = ALONE_TICKS.getOrDefault(verityId, 0) + 20;
                  ALONE_TICKS.put(verityId, currentTicks);
                  if (currentTicks >= 6000 && currentTicks % 6000 == 0) {
                     lowerKarma(player, 1);
                     WAS_ABANDONED.put(verityId, true);
                     triggerAbandonmentSound(level, nearestVerity);
                  }
               } else if (distance <= 8.0) {
                  if (WAS_ABANDONED.getOrDefault(verityId, false)) {
                     int totalTicks = ALONE_TICKS.getOrDefault(verityId, 0);
                     int minutesAlone = Math.max(1, totalTicks / 1200);
                     triggerReturnDialogue(player, nearestVerity, minutesAlone);
                     ALONE_TICKS.put(verityId, 0);
                     WAS_ABANDONED.put(verityId, false);
                  } else {
                     ALONE_TICKS.put(verityId, 0);
                  }
               }
            }
         }
      }
   }

   private static Entity findNearestVerity(ServerLevel level, ServerPlayer player) {
      AABB searchBox = player.getBoundingBox().inflate(96.0);
      List<Entity> nearbyEntities = level.getEntitiesOfClass(Entity.class, searchBox, entityx -> {
         String name = entityx.getClass().getSimpleName().toLowerCase();
         return name.contains("sphere") || name.contains("verity");
      });
      Entity nearest = null;
      double minDistance = Double.MAX_VALUE;

      for (Entity entity : nearbyEntities) {
         double dist = player.distanceTo(entity);
         if (dist < minDistance) {
            minDistance = dist;
            nearest = entity;
         }
      }

      return nearest;
   }

   private static void lowerKarma(ServerPlayer player, int amount) {
      CompoundTag tag = player.getPersistentData();
      int currentKarma = tag.getInt("VerityKarma");
      int newKarma = Math.max(-100, currentKarma - amount);
      tag.putInt("VerityKarma", newKarma);
   }

   private static int getKarma(ServerPlayer player) {
      return player.getPersistentData().getInt("VerityKarma");
   }

   private static void triggerAbandonmentSound(ServerLevel level, Entity verity) {
      level.playSound(null, verity.getX(), verity.getY(), verity.getZ(), SoundEvents.ENDERMAN_AMBIENT, SoundSource.NEUTRAL, 0.4F, 0.5F);
   }

   private static void triggerReturnDialogue(ServerPlayer player, Entity verity, int minutesAlone) {
      int karma = getKarma(player);
      ServerLevel level = (ServerLevel)verity.level();
      level.playSound(null, verity.getX(), verity.getY(), verity.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.8F, 0.7F);
      String speech;
      if (karma >= 10) {
         speech = "§e<Verity> §f...Where have you been?! You left me alone for " + minutesAlone + " whole minutes! I thought you abandoned me!";
      } else if (karma >= 0) {
         speech = "§6<Verity> §fWhere were you...? You walked away and didn't come back. I was counting the seconds... please don't leave me alone like that.";
      } else {
         speech = "§4<Verity> §c...You left me. Again. For " + minutesAlone + " minutes I sat here in the dark. Don't ever turn your back on me again.";
      }

      player.sendSystemMessage(Component.literal(speech));
   }
}
