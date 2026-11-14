package varmite.verity.environment.items;

import varmite.verity.voice.HostVoice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.neoforged.neoforge.network.PacketDistributor;
import varmite.verity.VerityConfig;
import varmite.verity.entity.ModEntities;
import varmite.verity.entity.VerityState;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.environment.sounds.ModSounds;
import varmite.verity.event.ModEvents;
import varmite.verity.event.Karma;
import varmite.verity.event.WorldSpawnData;
import varmite.verity.network.PlayTtsPayload;

public class VerityItem extends Item {
   public static WorldSpawnData data;

   public VerityItem(Properties properties) {
      super(properties);
      properties.stacksTo(1);
   }

   @Override
   public boolean isDamageable(ItemStack stack) {
      return super.isDamageable(stack);
   }

   @Override
   public int getMaxStackSize(ItemStack stack) {
      return 1;
   }

   @Override
   public boolean onDroppedByPlayer(ItemStack item, Player player) {
      return super.onDroppedByPlayer(item, player);
   }

   @Override
   public Component getName(ItemStack stack) {
      String name = VerityConfig.VERITY_CUSTOM_NAME.get();
      if (name == null || name.isBlank()) {
         name = "Verity";
      }

      if (!name.endsWith("™")) {
         name = name + "™";
      }

      return Component.literal(name);
   }

   @Override
   public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
      if (!entity.level().isClientSide()) {
         if (entity.onGround()) {
            CompoundTag data = entity.getPersistentData();
            int groundTicks = data.getIntOr("VerityGroundTicks", 0);
            data.putInt("VerityGroundTicks", ++groundTicks);
            if (groundTicks >= 20) {
               ServerLevel level = (ServerLevel)entity.level();
               BlockPos pos = entity.blockPosition();
               String variantToSpawn = "happy";
               if (StackData.has(stack, "VerityVariant")) {
                  variantToSpawn = StackData.getString(stack, "VerityVariant");
               }

               VerityEntity spawnedEntity = ModEntities.VERITY_ENTITY.get().create(level, EntitySpawnReason.MOB_SUMMONED);
               if (spawnedEntity != null) {
                  spawnedEntity.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
                  spawnedEntity.setVariant(variantToSpawn);
                  if (stack.has(DataComponents.CUSTOM_DATA)) {
                     spawnedEntity.loadFromTag(StackData.copy(stack));
                  }

                  level.addFreshEntity(spawnedEntity);
               }

               level.playSound((Player)null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 0.8F);
               entity.discard();
               return true;
            }
         } else if (entity.getPersistentData().contains("VerityGroundTicks")) {
            entity.getPersistentData().putInt("VerityGroundTicks", 0);
         }

         if (entity.blockPosition().getY() <= -63) {
            entity.setDeltaMovement(0.0, 1.0, 0.0);
            entity.needsSync = true;
         }
      }

      return false;
   }

   @Override
   public void onDestroyed(ItemEntity itemEntity, DamageSource damageSource) {
      if (damageSource.is(DamageTypeTags.IS_FIRE)) {
         if (itemEntity.level() instanceof ServerLevel serverLevel) {
            BlockPos itemPos = itemEntity.blockPosition();
            BlockPos safePos = this.findClosestSafeSpawnLocation(serverLevel, itemPos);
            VerityEntity spawnedEntity = ModEntities.VERITY_ENTITY.get().create(serverLevel, EntitySpawnReason.MOB_SUMMONED);
            if (spawnedEntity != null) {
               spawnedEntity.snapTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, 0.0F, 0.0F);
               serverLevel.addFreshEntity(spawnedEntity);
               spawnedEntity.setVariant("serious_1");
               spawnedEntity.level().playSound((Player)null, safePos, ModSounds.BONE_0.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
               HostVoice.say(spawnedEntity, "DO NOT DO THAT!");
               serverLevel.getServer().execute(() -> Karma.add(serverLevel, serverLevel.getNearestPlayer(spawnedEntity, 256.0), -1.0F));
               data = WorldSpawnData.get(serverLevel);
               serverLevel.playSound((Player)null, spawnedEntity.blockPosition(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.8F);
               if (VerityConfig.IMMERSIVE_MODE.get()) {
                  return;
               }

               serverLevel.getServer()
                  .getPlayerList()
                  .broadcastSystemMessage(Component.literal("<%s> §4DO NOT DO THAT.".formatted(VerityConfig.VERITY_CUSTOM_NAME.get())), false);
            }
         }
      } else if (damageSource.is(DamageTypeTags.IS_EXPLOSION)) {
         if (itemEntity.level() instanceof ServerLevel serverLevel && serverLevel.getNearestPlayer(itemEntity, 256.0) instanceof ServerPlayer p) {
            ItemStack stack = new ItemStack(ModItems.VERITY_ITEM.get());
            StackData.update(stack, tag -> tag.putString("VerityVariant", "serious_3"));
            VerityState.giveOrDrop(p, stack);
            p.sendSystemMessage(Component.literal("<Verity> Ayo chat why u let me explode"));
            HostVoice.sayTo(p, "Ayo chat why u let me explode");
            serverLevel.getServer().execute(() -> Karma.add(serverLevel, p, -1.0F));
            data = WorldSpawnData.get(serverLevel);
            serverLevel.playSound((Player)null, p.blockPosition(), SoundEvents.GHAST_SCREAM, SoundSource.PLAYERS, 1.0F, 1.3F);
         }
      } else if (damageSource.is(DamageTypes.CACTUS) && itemEntity.level() instanceof ServerLevel serverLevel) {
         if (serverLevel.getNearestPlayer(itemEntity, 256.0) instanceof ServerPlayer p) {
            ItemStack stack = new ItemStack(ModItems.VERITY_ITEM.get());
            StackData.update(stack, tag -> tag.putString("VerityVariant", "serious_3"));
            VerityState.giveOrDrop(p, stack);
            p.sendSystemMessage(Component.literal("<Verity> DON'T DO THAT."));
            HostVoice.sayTo(p, "DO NOT DO THAT!");
            serverLevel.getServer().execute(() -> Karma.add(serverLevel, p, -1.0F));
            data = WorldSpawnData.get(serverLevel);
            serverLevel.playSound((Player)null, p.blockPosition(), ModSounds.BONE_0.get(), SoundSource.PLAYERS, 1.0F, 0.8F);
         } else {
            System.out.println("[VERITY DEBUG] Cactus destruction fired, but couldn't find a valid ServerPlayer nearby.");
         }
      }

      super.onDestroyed(itemEntity, damageSource);
   }

   private BlockPos findClosestSafeSpawnLocation(ServerLevel level, BlockPos startPos) {
      BlockPos closestSafePos = null;
      double minDistanceSqr = Double.MAX_VALUE;

      for (int x = -3; x <= 3; x++) {
         for (int y = -3; y <= 3; y++) {
            for (int z = -3; z <= 3; z++) {
               BlockPos checkPos = startPos.offset(x, y, z);
               boolean hasSolidFloor = level.getBlockState(checkPos.below()).isSolidRender();
               boolean isFeetEmpty = level.getBlockState(checkPos).getCollisionShape(level, checkPos).isEmpty() && level.getFluidState(checkPos).isEmpty();
               boolean isHeadEmpty = level.getBlockState(checkPos.above()).getCollisionShape(level, checkPos.above()).isEmpty()
                  && level.getFluidState(checkPos.above()).isEmpty();
               if (hasSolidFloor && isFeetEmpty && isHeadEmpty) {
                  double distanceSqr = startPos.distSqr(checkPos);
                  if (distanceSqr < minDistanceSqr) {
                     minDistanceSqr = distanceSqr;
                     closestSafePos = checkPos;
                  }
               }
            }
         }
      }

      return closestSafePos != null ? closestSafePos : startPos;
   }
}
