package varmite.verity.event;

import varmite.verity.environment.items.StackData;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import varmite.verity.environment.block.ModBlocks;
import varmite.verity.environment.items.ModItems;

@EventBusSubscriber(modid = "verity")
public class FlashlightServerLogic {
   private static final int LIGHT_UPDATE_FLAGS = 18;
   private static final Map<UUID, Set<BlockPos>> activeLights = new HashMap<>();

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent.Post event) {
      if (event.getServer() != null) {
         for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            UUID uuid = player.getUUID();
            boolean isFlashlightOn = false;
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            if (main.is(ModItems.FLASHLIGHT.get()) && StackData.getBoolean(main, "FlashlightOn")
               || off.is(ModItems.FLASHLIGHT.get()) && StackData.getBoolean(off, "FlashlightOn")) {
               isFlashlightOn = true;
            }

            if (isFlashlightOn) {
               Vec3 eyePos = player.getEyePosition();
               Vec3 lookVec = player.getViewVector(1.0F);
               double maxDist = 25.0;
               Vec3 endPos = eyePos.add(lookVec.scale(maxDist));
               HitResult hitResult = level.clip(new ClipContext(eyePos, endPos, Block.COLLIDER, Fluid.NONE, player));
               double hitDistance = maxDist;
               if (hitResult.getType() == Type.BLOCK) {
                  hitDistance = hitResult.getLocation().distanceTo(eyePos);
               }

               Set<BlockPos> desiredPositions = new LinkedHashSet<>();

               for (double i = 2.0; i <= hitDistance - 1.5; i += 2.5) {
                  desiredPositions.add(BlockPos.containing(eyePos.add(lookVec.scale(i))));
               }

               double finalHitDist = Math.max(0.5, hitDistance - 0.5);
               Vec3 hitVec = eyePos.add(lookVec.scale(finalHitDist));
               BlockPos hitCenter = BlockPos.containing(hitVec);
               desiredPositions.add(hitCenter);
               if (hitDistance > 4.0) {
                  Vec3 up = player.getUpVector(1.0F);
                  Vec3 right = lookVec.cross(up).normalize();
                  if (right.lengthSqr() < 0.001) {
                     float yawRad = player.getYRot() * (float) (Math.PI / 180.0);
                     right = new Vec3(-Math.cos(yawRad), 0.0, -Math.sin(yawRad)).normalize();
                  }

                  Vec3 trueUp = right.cross(lookVec).normalize();
                  desiredPositions.add(BlockPos.containing(hitVec.add(right.scale(1.5))));
                  desiredPositions.add(BlockPos.containing(hitVec.subtract(right.scale(1.5))));
                  desiredPositions.add(BlockPos.containing(hitVec.add(trueUp.scale(1.5))));
                  desiredPositions.add(BlockPos.containing(hitVec.subtract(trueUp.scale(1.5))));
               }

               Set<BlockPos> currentPositions = activeLights.getOrDefault(uuid, Set.of());
               if (!desiredPositions.equals(currentPositions)) {
                  for (BlockPos pos : currentPositions) {
                     if (!desiredPositions.contains(pos)) {
                        BlockState state = level.getBlockState(pos);
                        if (state.is(ModBlocks.FLASHLIGHT_LIGHT.get())) {
                           boolean wasWater = state.getValue(BlockStateProperties.WATERLOGGED);
                           level.setBlock(pos, wasWater ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
                        }
                     }
                  }

                  for (BlockPos pos : desiredPositions) {
                     if (!currentPositions.contains(pos)) {
                        BlockState targetState = level.getBlockState(pos);
                        boolean isAir = targetState.isAir();
                        boolean isWaterSource = targetState.is(Blocks.WATER) && targetState.getFluidState().isSource();
                        if (isAir || isWaterSource) {
                           level.setBlock(
                              pos, ModBlocks.FLASHLIGHT_LIGHT.get().defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, isWaterSource), 18
                           );
                        }
                     }
                  }

                  activeLights.put(uuid, desiredPositions);
               }
            } else if (activeLights.containsKey(uuid)) {
               cleanupLight(player, uuid);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerLogout(PlayerLoggedOutEvent event) {
      cleanupLight((ServerPlayer)event.getEntity(), event.getEntity().getUUID());
   }

   private static void cleanupLight(ServerPlayer player, UUID uuid) {
      Set<BlockPos> positions = activeLights.get(uuid);
      if (positions != null) {
         ServerLevel level = player.serverLevel();

         for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.FLASHLIGHT_LIGHT.get())) {
               boolean wasWater = state.getValue(BlockStateProperties.WATERLOGGED);
               level.setBlock(pos, wasWater ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
            }
         }

         activeLights.remove(uuid);
      }
   }
}
