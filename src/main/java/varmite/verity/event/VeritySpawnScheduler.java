package varmite.verity.event;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import varmite.verity.entity.ModEntities;
import varmite.verity.network.PlayTtsPayload;

@EventBusSubscriber(modid = "verity")
public class VeritySpawnScheduler {
   private static final List<VeritySpawnScheduler.ScheduledSpawn> SCHEDULED_SPAWNS = new ArrayList<>();

   public static void scheduleSpawn(Level level, BlockPos pos, int delayTicks) {
      if (level instanceof ServerLevel serverLevel) {
         long executeAt = serverLevel.getServer().getTickCount() + delayTicks;
         SCHEDULED_SPAWNS.add(new VeritySpawnScheduler.ScheduledSpawn(serverLevel, pos, executeAt));
      }
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent.Post event) {
      long currentTick = event.getServer().getTickCount();
      Iterator<VeritySpawnScheduler.ScheduledSpawn> iterator = SCHEDULED_SPAWNS.iterator();

      while (iterator.hasNext()) {
         VeritySpawnScheduler.ScheduledSpawn task = iterator.next();
         if (currentTick >= task.executeTick) {
            executeVerityEvent(task.level, task.pos);
            iterator.remove();
         }
      }
   }

   private static void executeVerityEvent(ServerLevel level, BlockPos chestPos) {
      BlockPos abovePos = chestPos.above();
      if (!level.getBlockState(abovePos).isAir()) {
         level.destroyBlock(abovePos, true);
      }

      Entity verity = ModEntities.VERITY_ENTITY.get().create(level);
      if (verity != null) {
         verity.moveTo(chestPos.getX() + 0.5, chestPos.getY() + 1.0, chestPos.getZ() + 0.5, 0.0F, 0.0F);
         level.addFreshEntity(verity);
         PacketDistributor.sendToPlayersTrackingEntityAndSelf(verity, new PlayTtsPayload(verity.getId(), "You can't trap me lil bro."));
         level.playSound((Player)null, verity.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
      }
   }

   private record ScheduledSpawn(ServerLevel level, BlockPos pos, long executeTick) {
   }
}
