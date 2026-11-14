package varmite.verity.event;

import varmite.verity.environment.items.StackData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Player.BedSleepingProblem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import varmite.verity.VerityConfig;
import varmite.verity.command.ChangeKarmaCommand;
import varmite.verity.command.DevAskCommand;
import varmite.verity.command.RecoverVerityCommand;
import varmite.verity.command.VerityFaceCommand;
import varmite.verity.entity.ModEntities;
import varmite.verity.entity.VerityState;
import varmite.verity.entity.llm.AiAPI;
import varmite.verity.entity.llm.AiManager;
import varmite.verity.entity.demon.VerityDemonEntity;
import varmite.verity.entity.llm.store.memory.VerityMemoryManager;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.entity.veritybox.BoxEntity;
import varmite.verity.environment.items.ModItems;
import varmite.verity.environment.sounds.ModSounds;
import varmite.verity.network.KarmaSyncS2CPacket;
import varmite.verity.network.ModMessages;
import varmite.verity.network.PlayTtsPayload;

@EventBusSubscriber(modid = "verity")
public class ModEvents {
   private static int lastSyncedKarma = Integer.MIN_VALUE;
   private static final List<ModEvents.ScheduledTask> PENDING_TASKS = Collections.synchronizedList(new ArrayList<>());
   private static final List<ModEvents.ScheduledTask> ACTIVE_TASKS = new ArrayList<>();

   public static void updateAndSyncKarma(ServerLevel level, float amount) {
      WorldSpawnData data = WorldSpawnData.get(level);
      data.verityKarma += amount;
      data.verityKarma = Mth.clamp(data.verityKarma, 0.0F, 20.0F);
      data.setDirty();
      syncKarma(level.getServer());
   }

   public static void setAndSyncKarma(ServerLevel level, float amount) {
      System.out.println("Changing karma to " + amount);
      WorldSpawnData data = WorldSpawnData.get(level);
      data.verityKarma = amount;
      data.verityKarma = Mth.clamp(data.verityKarma, 0.0F, 20.0F);
      data.setDirty();
      syncKarma(level.getServer());
   }

   public static void syncKarma(MinecraftServer server) {
      int karma = (int)WorldSpawnData.get(server.overworld()).verityKarma;
      lastSyncedKarma = karma;

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         ModMessages.sendToPlayer(new KarmaSyncS2CPacket(karma), player);
      }
   }

   private static void syncKarma(ServerPlayer player) {
      ModMessages.sendToPlayer(new KarmaSyncS2CPacket((int)WorldSpawnData.get(player.serverLevel()).verityKarma), player);
   }

   @SubscribeEvent
   public static void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         syncKarma(player);
      }
   }

   @SubscribeEvent
   public static void onPlayerRespawnSyncKarma(PlayerRespawnEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         syncKarma(player);
      }
   }

   /** 6.0.0 warms the AI model up at mod setup; on a server setup that would load models on every player's PC, so it waits for the server. */
   @SubscribeEvent
   public static void onServerStarted(ServerStartedEvent event) {
      AiAPI.warmUpOllamaAsync();
   }

   @SubscribeEvent
   public static void registerCommands(RegisterCommandsEvent event) {
      RecoverVerityCommand.register(event.getDispatcher());
      ChangeKarmaCommand.register(event.getDispatcher());
      DevAskCommand.register(event.getDispatcher());
      VerityFaceCommand.register(event.getDispatcher());
   }

   @SubscribeEvent
   public static void onPlayerJoinWorld(PlayerLoggedInEvent event) {
      if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
         ServerLevel level = player.serverLevel();
         WorldSpawnData data = WorldSpawnData.get(level);
         ModMessages.sendToPlayer(new KarmaSyncS2CPacket((int)data.verityKarma), player);
      }
   }

   @SubscribeEvent
   public static void onPlayerLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         schedule(() -> {
            ServerLevel level = player.serverLevel();
            WorldSpawnData data = WorldSpawnData.get(level);
            boolean foundJson = false;

            for (int i = 0; i < data.chatHistory.size(); i++) {
               CompoundTag messageTag = data.chatHistory.getCompound(i);
               String content = messageTag.getString("content");
               if (content.toLowerCase().contains("json")) {
                  foundJson = true;
                  break;
               }
            }

            if (foundJson) {
               data.chatHistory.clear();
            }

            ModMessages.sendToPlayer(new KarmaSyncS2CPacket((int)data.verityKarma), player);
         }, 20);
      }
   }

   @SubscribeEvent
   public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         VerityMemoryManager.get().addMemory("last_logout_" + player.getStringUUID(), String.valueOf(System.currentTimeMillis()));
      }
   }

   @SubscribeEvent
   public static void onPlayerReturn(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         CompletableFuture.runAsync(
            () -> {
               MinecraftServer server = player.getServer();
               if (server != null) {
                  server.execute(
                     () -> {
                        if (player.isAlive() && !player.hasDisconnected()) {
                           boolean isSpawned = VerityState.hasSpawned && VerityState.verityEntity != null && !VerityState.verityEntity.isRemoved();
                           boolean isInInventory = hasVerityInInventory(player);
                           if (isSpawned || isInInventory) {
                              String storedTime = VerityMemoryManager.get().getMemory("last_logout_" + player.getStringUUID());
                              String timeAway = "a while";
                              if (storedTime != null) {
                                 try {
                                    long logoutTimestamp = Long.parseLong(storedTime);
                                    long awayMs = System.currentTimeMillis() - logoutTimestamp;
                                    long minutes = awayMs / 60000L;
                                    long hours = minutes / 60L;
                                    long days = hours / 24L;
                                    if (days > 0L) {
                                       timeAway = days + " day" + (days > 1L ? "s" : "");
                                    } else if (hours > 0L) {
                                       timeAway = hours + " hour" + (hours > 1L ? "s" : "");
                                    } else if (minutes > 1L) {
                                       timeAway = minutes + " minutes";
                                    } else {
                                       timeAway = "a few moments";
                                    }
                                 } catch (Exception var15) {
                                 }
                              }

                              if (isSpawned) {
                                 VerityState.verityEntity.startTalking(80);
                              }

                              String triggerPrompt = "<System> "
                                 + player.getName().getString()
                                 + " just loaded back into the world after being gone for "
                                 + timeAway
                                 + ". Say something in character reacting to their return, like complaining they took so long, being relieved, or asking where they were.";
                              AiManager.queryAI(isSpawned ? VerityState.verityEntity : null, triggerPrompt, player);
                           }
                        }
                     }
                  );
               }
            },
            CompletableFuture.delayedExecutor(4L, TimeUnit.SECONDS)
         );
      }
   }

   @SubscribeEvent
   public static void onVerityTakeDamage(LivingDamageEvent.Pre event) {
      if (event.getEntity() instanceof VerityEntity verity && !verity.level().isClientSide()) {
         if (event.getSource().is(DamageTypeTags.IS_FIRE) || event.getSource().is(DamageTypes.LAVA)) {
            event.setNewDamage(0.0F);
            System.out.println("Verity is on fire");
            ServerLevel serverLevel = (ServerLevel)verity.level();
            long currentTime = serverLevel.getGameTime();
            long lastHurt = VerityState.HURT_COOLDOWN.getOrDefault(verity.getUUID(), 0L);
            if (currentTime - lastHurt < 100L) {
               return;
            }

            VerityState.HURT_COOLDOWN.put(verity.getUUID(), currentTime);
            String[] messages = new String[]{"IT BURNS", "GET ME OUT OF HERE", "HELP ME IT BURNS", "AGH IT BURNS", "AGH"};
            Random rand = new Random();
            String answer = messages[rand.nextInt(messages.length)];
            verity.getServer()
               .execute(
                  () -> {
                     if (VerityState.verityEntity != null && !VerityState.verityEntity.isRemoved()) {
                        PacketDistributor.sendToPlayersTrackingEntityAndSelf(VerityState.verityEntity, new PlayTtsPayload(VerityState.verityEntity.getId(), answer)
                           );
                     }

                     updateAndSyncKarma(serverLevel, -2.0F);
                     verity.getServer().getPlayerList().broadcastSystemMessage(Component.literal("<Verity> " + answer), false);
                  }
               );
         }

         if (event.getSource().is(DamageTypes.IN_WALL) || event.getSource().is(DamageTypes.FALLING_BLOCK) || event.getSource().is(DamageTypes.FALLING_ANVIL)) {
            System.out.println("Verity is suffocating");
            event.setNewDamage(0.0F);
            ServerLevel serverLevel = (ServerLevel)verity.level();
            long currentTime = serverLevel.getGameTime();
            long lastHurt = VerityState.HURT_COOLDOWN.getOrDefault(verity.getUUID(), 0L);
            if (currentTime - lastHurt < 100L) {
               return;
            }

            String triggerPrompt;
            if (!event.getSource().is(DamageTypeTags.IS_FIRE) && !event.getSource().is(DamageTypes.LAVA)) {
               triggerPrompt = "A heavy block has dropped on verity during this turn";
            } else {
               triggerPrompt = "Verity is burning during this turn";
            }

            VerityState.HURT_COOLDOWN.put(verity.getUUID(), currentTime);
            String message = "<SYSTEM> " + triggerPrompt;
            AiManager.queryAI(VerityState.verityEntity, message, null);
         }
      }
   }

   @SubscribeEvent
   public static void onDespawn(ItemExpireEvent event) {
      if (event.getEntity().getItem().is(ModItems.VERITY_ITEM.get()) && event.getEntity().level() instanceof ServerLevel serverLevel) {
         Player p = serverLevel.getNearestPlayer(event.getEntity(), 256.0);
         if (p != null) {
            p.getInventory().add(new ItemStack(ModItems.VERITY_ITEM.get()));
            serverLevel.playSound((Player)null, p.blockPosition(), SoundEvents.GHAST_HURT, SoundSource.PLAYERS, 1.0F, 1.0F);
            p.sendSystemMessage(Component.literal("<Verity> Ayo chat why u lettin me despawn like that"));
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new PlayTtsPayload(p.getId(), "Ayo chat why u lettin me despawn like that"));
         }
      }
   }

   @SubscribeEvent
   public static void rightClickAir(RightClickItem event) {
      if (!event.getLevel().isClientSide()) {
         Player player = event.getEntity();
         if (player.isCrouching()) {
            ItemStack stack = player.getItemInHand(event.getHand());
            if (stack.is(ModItems.VERITY_ITEM.get())) {
               String variantToSpawn = "default";
               if (StackData.has(stack, "VerityVariant")) {
                  variantToSpawn = StackData.getString(stack, "VerityVariant");
               }

               stack.shrink(1);
               Vec3 launchVelocity = player.getLookAngle().normalize().scale(1.5);
               VerityEntity newVerity = ModEntities.VERITY_ENTITY.get().create(player.level());
               if (newVerity != null) {
                  newVerity.moveTo(player.blockPosition().offset(0, 1, 0).getCenter());
                  newVerity.setVariant(variantToSpawn);
                  newVerity.getPersistentData().putBoolean("WasThrown", true);
                  newVerity.setOwnerUUID(player.getUUID());
                  player.level().addFreshEntity(newVerity);
                  VerityState.verityEntity = newVerity;
                  PacketDistributor.sendToPlayersTrackingEntityAndSelf(VerityState.verityEntity, new PlayTtsPayload(VerityState.verityEntity.getId(), "AAAAAAAAHHH")
                     );
                  player.level().playSound((Player)null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 1.0F);
                  newVerity.setDeltaMovement(launchVelocity);
                  newVerity.hurtMarked = true;
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void rightClickItem(RightClickItem event) {
      ItemStack stack = event.getItemStack();
      if (stack.is(ModItems.FLASHLIGHT.get())) {
         event.setCanceled(true);
         event.setCancellationResult(InteractionResult.SUCCESS);
         if (!event.getLevel().isClientSide()) {
            Player p = event.getEntity();
            boolean isNowOn = !StackData.getBoolean(stack, "FlashlightOn");
            StackData.update(stack, tag -> tag.putBoolean("FlashlightOn", isNowOn));
            p.swing(event.getHand(), true);
            if (isNowOn) {
               p.level().playSound((Player)null, p.blockPosition(), SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON, SoundSource.PLAYERS, 1.0F, 1.0F);
            } else {
               p.level().playSound((Player)null, p.blockPosition(), SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_OFF, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onDeath(LivingDeathEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         if (event.getSource().getEntity() instanceof VerityDemonEntity && VerityConfig.CAN_CRASH.get()) {
            // One tick later, so the kick doesn't pull the player out of the world in the middle of dying.
            schedule(
               () -> player.connection.disconnect(
                  Component.literal("Farewell, " + player.getName().getString()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
               ),
               1
            );
         }

         return;
      }

      LivingEntity standIn = event.getEntity();
      if (standIn.level() instanceof ServerLevel level) {
         WorldSpawnData data = WorldSpawnData.get(level);
         if (standIn.getUUID().equals(data.activeDemon)) {
            HostileEvents.resolve(level);
            setAndSyncKarma(level, 20.0F);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, standIn.getX(), standIn.getY() + 1.0, standIn.getZ(), 100, 0.5, 1.0, 0.5, 0.2);
            level.playSound((Player)null, standIn.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 1.0F, 1.0F);
            VerityEntity verity = VerityState.verityEntity;
            if (verity != null && !verity.isRemoved()) {
               PacketDistributor.sendToPlayersTrackingEntityAndSelf(verity, new PlayTtsPayload(verity.getId(), "The darkness... it's gone. Thank you."));
               if (VerityConfig.IMMERSIVE_MODE.get()) {
                  return;
               }

               level.getServer()
                  .getPlayerList()
                  .broadcastSystemMessage(Component.literal("<" + VerityConfig.VERITY_CUSTOM_NAME.get() + "> The darkness... it's gone. Thank you."), false);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onStandInLeaveLevel(EntityLeaveLevelEvent event) {
      if (event.getLevel() instanceof ServerLevel level && event.getEntity().getUUID().equals(WorldSpawnData.get(level).activeDemon)) {
         Entity standIn = event.getEntity();
         RemovalReason reason = standIn.getRemovalReason();
         if (reason != null && reason.shouldDestroy()) {
            HostileEvents.resolve(level);
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerDropsDrop(LivingDropsEvent event) {
      if (!event.getEntity().level().isClientSide) {
         if (event.getEntity() instanceof Player) {
            event.getDrops().removeIf(itemEntity -> itemEntity.getItem().is(ModItems.FLASHLIGHT.get()));
         }
      }
   }

   public static void schedule(Runnable action, int delayInTicks) {
      PENDING_TASKS.add(new ModEvents.ScheduledTask(action, delayInTicks));
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent.Post event) {
      synchronized (PENDING_TASKS) {
         if (!PENDING_TASKS.isEmpty()) {
            ACTIVE_TASKS.addAll(PENDING_TASKS);
            PENDING_TASKS.clear();
         }
      }

      Iterator<ModEvents.ScheduledTask> iterator = ACTIVE_TASKS.iterator();

      while (iterator.hasNext()) {
         ModEvents.ScheduledTask st = iterator.next();
         st.ticksRemaining--;
         if (st.ticksRemaining <= 0) {
            st.task.run();
            iterator.remove();
         }
      }

      MinecraftServer tickServer = event.getServer();
      if (tickServer != null && tickServer.getTickCount() % 20 == 0) {
         int karma = (int)WorldSpawnData.get(tickServer.overworld()).verityKarma;
         if (karma != lastSyncedKarma) {
            syncKarma(tickServer);
         }
      }

      if (VerityState.verityEntity != null && !VerityState.verityEntity.isRemoved() && !VerityState.verityEntity.level().isClientSide()) {
         if (VerityState.lonelinessTimer > 0) {
            VerityState.lonelinessTimer--;
         } else {
            VerityState.lonelinessTimer = 3000;
            ServerLevel level = (ServerLevel)VerityState.verityEntity.level();
            Player nearestPlayer = level.getNearestPlayer(VerityState.verityEntity, 32.0);
            if (nearestPlayer == null) {
               updateAndSyncKarma(level, -1.0F);
               if (!VerityState.verityEntity.isRemoved()) {
                  VerityState.verityEntity.setVariant("serious_1");
                  PacketDistributor.sendToPlayersTrackingEntityAndSelf(VerityState.verityEntity, new PlayTtsPayload(VerityState.verityEntity.getId(), "I'm alone... where did you go?")
                     );
                  if (VerityConfig.IMMERSIVE_MODE.get()) {
                     return;
                  }

                  VerityState.verityEntity
                     .getServer()
                     .getPlayerList()
                     .broadcastSystemMessage(
                        Component.literal("<%s> I'm alone... where did you go?".formatted(VerityConfig.VERITY_CUSTOM_NAME.get())), false
                     );
               }
            } else {
               VerityState.lonelinessTimer = 3000;
            }
         }
      }

      if (VerityState.hasSpawned
         && VerityState.verityEntity != null
         && !VerityState.verityEntity.isRemoved()
         && !VerityState.verityEntity.level().isClientSide()) {
         if (VerityState.idleChatTimer > 0) {
            if (!VerityState.verityEntity.isOnFire()) {
               VerityState.idleChatTimer--;
            }
         } else {
            VerityState.idleChatTimer = 2400 + new Random().nextInt(2400);
            ServerLevel level = (ServerLevel)VerityState.verityEntity.level();
            if (level.getNearestPlayer(VerityState.verityEntity, 32.0) instanceof ServerPlayer serverPlayer
               && !VerityState.verityEntity.getEntityData().get(VerityEntity.IS_TALKING)) {
               VerityState.verityEntity.startTalking(80);
               String idlePrompt = "<SYSTEM> You decide to start a random conversation with the user. Ask them a question, comment the environment or even give random facts.\n";
               AiManager.queryAI(VerityState.verityEntity, idlePrompt, serverPlayer);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerSleep(CanPlayerSleepEvent event) {
      Player p = event.getEntity();
      if (!p.level().isClientSide()) {
         UUID standInId = WorldSpawnData.get((ServerLevel)p.level()).activeDemon;
         Entity standIn = standInId == null ? null : ((ServerLevel)p.level()).getEntity(standInId);
         if (standIn != null && standIn.distanceToSqr(p) < 4096.0) {
            event.setProblem(BedSleepingProblem.OTHER_PROBLEM);
            p.displayClientMessage(Component.literal("You cannot rest now, Verity is nearby..."), true);
         }
      }
   }

   @SubscribeEvent
   public static void entitySpawnEvent(EntityJoinLevelEvent event) {
      if (!event.getLevel().isClientSide()) {
         if (event.getEntity().getType() == ModEntities.VERITY_ENTITY.get()) {
            VerityState.hasSpawned = true;
            VerityState.verityEntity = (VerityEntity)event.getEntity();
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerInteract(EntityInteract event) {
      Player player = event.getEntity();
      InteractionHand hand = event.getHand();
      if (hand == InteractionHand.MAIN_HAND) {
         if (event.getTarget() instanceof VerityEntity vEntity) {
            if (!player.getMainHandItem().isEmpty()) {
               return;
            }

            if (vEntity.getEntityData().get(VerityEntity.IS_TALKING)) {
               if (!event.getLevel().isClientSide()) {
                  player.sendSystemMessage(Component.literal("§cYou can't do this while he's talking."));
                  player.level().playSound((Player)null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 0.9F);
               }

               event.setCanceled(true);
               event.setCancellationResult(InteractionResult.FAIL);
               return;
            }

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
            if (!event.getLevel().isClientSide()) {
               MinecraftServer server = vEntity.getServer();
               if (server != null) {
                  ResourceLocation soundToStop = ResourceLocation.fromNamespaceAndPath("verity", "verity_disc");
                  ClientboundStopSoundPacket stopSoundPacket = new ClientboundStopSoundPacket(soundToStop, SoundSource.VOICE);
                  server.getPlayerList().broadcastAll(stopSoundPacket);
               }

               ItemStack stack = new ItemStack(ModItems.VERITY_ITEM.get());
               CompoundTag itemNbt = new CompoundTag();
               vEntity.addAdditionalSaveData(itemNbt);
               itemNbt.putString("VerityVariant", vEntity.getVariant());
               itemNbt.putString("VerityName", VerityConfig.VERITY_CUSTOM_NAME.get());
               String name = VerityConfig.VERITY_CUSTOM_NAME.get();
               if (!name.endsWith("™")) {
                  name = name + "™";
               }

               StackData.set(stack, itemNbt);
               stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
               vEntity.discard();
               VerityState.hasSpawned = false;
               vEntity.level().playSound((Player)null, vEntity.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
               player.setItemInHand(hand, stack);
               player.swing(hand, true);
               vEntity.level().playSound((Player)null, vEntity.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
         } else if (event.getTarget() instanceof BoxEntity bEntity) {
            if (bEntity.getEntityData().get(BoxEntity.HAS_CLICKED)) {
               return;
            }

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
            if (!event.getLevel().isClientSide()) {
               bEntity.triggerOpen();
               player.swing(hand, true);
               bEntity.getEntityData().set(BoxEntity.HAS_CLICKED, true);
               player.level().playSound((Player)null, bEntity.blockPosition(), ModSounds.BOX_CLICK.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
               schedule(
                  () -> {
                     Level level = event.getLevel();
                     VerityEntity verity = ModEntities.VERITY_ENTITY
                        .get()
                        .spawn((ServerLevel)level, (ItemStack)null, null, bEntity.blockPosition(), MobSpawnType.MOB_SUMMONED, true, true);
                     if (verity != null) {
                        VerityState.verityEntity = verity;
                        verity.moveTo(bEntity.blockPosition().getX() + 0.5, bEntity.blockPosition().getY(), bEntity.blockPosition().getZ() + 0.5, 0.0F, 0.0F);
                        verity.triggerBoxDrop();
                        verity.getPersistentData().putBoolean("WasThrown", false);
                        ServerLevel verityLevel = (ServerLevel)verity.level();
                        verityLevel.sendParticles(ParticleTypes.CLOUD, bEntity.getX(), bEntity.getY() + 1.0, bEntity.getZ(), 20, 0.25, 0.25, 0.25, 0.02);
                     }

                     player.level().playSound((Player)null, bEntity.blockPosition(), ModSounds.BOX_OPEN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                     bEntity.discard();
                  },
                  40
               );
               schedule(
                  () -> {
                     if (VerityState.verityEntity != null) {
                        VerityState.verityEntity
                           .level()
                           .playSound((Player)null, VerityState.verityEntity.blockPosition(), ModSounds.IMPACT_1.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                     }
                  },
                  55
               );
               schedule(
                  () -> {
                     if (VerityState.verityEntity != null) {
                        VerityState.verityEntity
                           .level()
                           .playSound((Player)null, VerityState.verityEntity.blockPosition(), ModSounds.IMPACT_0.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                     }
                  },
                  75
               );
               schedule(
                  () -> {
                     if (VerityState.verityEntity != null) {
                        VerityState.verityEntity
                           .level()
                           .playSound((Player)null, VerityState.verityEntity.blockPosition(), ModSounds.IMPACT_2.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                     }
                  },
                  90
               );
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerFirstJoin(PlayerLoggedInEvent event) {
      Player player = event.getEntity();
      if (player.level() instanceof ServerLevel level) {
         boolean hasFlashlight = player.getInventory().items.stream().anyMatch(stack -> stack.is(ModItems.FLASHLIGHT.get()))
            || player.getInventory().offhand.stream().anyMatch(stack -> stack.is(ModItems.FLASHLIGHT.get()));
         if (!hasFlashlight) {
            player.getInventory().add(new ItemStack(ModItems.FLASHLIGHT.get()));
         }

         if (level.dimension() == Level.OVERWORLD) {
            WorldSpawnData data = WorldSpawnData.get(level);
            if (!data.hasSpawnedEntity) {
               setAndSyncKarma(level, 10.0F);
               player.sendSystemMessage(Component.literal("Need help setting up this mod? Watch this tutorial."));
               Component message = Component.literal("Setup Tutorial")
                  .withStyle(ChatFormatting.AQUA)
                  .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(Action.OPEN_URL, "https://youtu.be/O3pSCBvJ_i0")).withUnderlined(true));
               player.sendSystemMessage(message);
               BlockPos safeSpawnPos = findNearestLand(level, player.blockPosition());
               ModEntities.BOX_ENTITY.get().spawn(level, (ItemStack)null, null, safeSpawnPos, MobSpawnType.MOB_SUMMONED, true, true);
               data.hasSpawnedEntity = true;
               data.setDirty();
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerRespawn(PlayerRespawnEvent event) {
      if (!event.getEntity().level().isClientSide) {
         Player player = event.getEntity();
         if (!player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            VerityEntity verity = VerityState.verityEntity;
            if (verity != null && !verity.isRemoved() && verity.getOwnerUUID().isPresent() && player.getUUID().equals(verity.getOwnerUUID().get())) {
               ItemStack stack = new ItemStack(ModItems.VERITY_ITEM.get());
               CompoundTag itemNbt = new CompoundTag();
               verity.addAdditionalSaveData(itemNbt);
               itemNbt.putString("VerityVariant", verity.getVariant());
               itemNbt.putString("VerityName", VerityConfig.VERITY_CUSTOM_NAME.get());
               String name = VerityConfig.VERITY_CUSTOM_NAME.get();
               if (!name.endsWith("™")) {
                  name = name + "™";
               }

               StackData.set(stack, itemNbt);
               stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
               player.getInventory().add(stack);
               VerityState.hasSpawned = false;
               verity.discard();
            }

            boolean hasFlashlight = player.getInventory().items.stream().anyMatch(stack -> stack.is(ModItems.FLASHLIGHT.get()))
               || player.getInventory().offhand.stream().anyMatch(stack -> stack.is(ModItems.FLASHLIGHT.get()));
            if (!hasFlashlight) {
               player.getInventory().add(new ItemStack(ModItems.FLASHLIGHT.get()));
            }
         }
      }
   }

   private static BlockPos findNearestLand(ServerLevel level, BlockPos center) {
      int radius = 15;
      BlockPos bestPos = null;
      double shortestDist = Double.MAX_VALUE;

      for (int x = -radius; x <= radius; x++) {
         for (int z = -radius; z <= radius; z++) {
            BlockPos searchPos = center.offset(x, 0, z);
            BlockPos topPos = level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, searchPos);
            BlockPos groundPos = topPos.below();
            BlockState groundState = level.getBlockState(groundPos);
            if (groundState.getFluidState().isEmpty()) {
               double dist = center.distSqr(topPos);
               if (dist > 3.0 && dist < shortestDist) {
                  shortestDist = dist;
                  bestPos = topPos;
               }
            }
         }
      }

      return bestPos == null ? level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, center.offset(3, 0, 3)) : bestPos;
   }

   @SubscribeEvent
   public static void onPlayerBlockInteract(RightClickBlock event) {
      Player player = event.getEntity();
      InteractionHand hand = event.getHand();
      ItemStack stack = player.getItemInHand(hand);
      if (stack.getItem() == ModItems.VERITY_ITEM.get()) {
         event.setCanceled(true);
         event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
         String name = VerityConfig.VERITY_CUSTOM_NAME.get();
         if (!event.getLevel().isClientSide()) {
            Direction face = event.getFace();
            BlockPos spawnPos = face != null ? event.getPos().relative(face) : event.getPos().above();
            ServerLevel level = (ServerLevel)event.getLevel();
            if (!level.getBlockState(spawnPos).isAir()) {
               event.setCanceled(true);
               return;
            }

            String variantToSpawn = "default";
            if (StackData.has(stack, "VerityVariant")) {
               variantToSpawn = StackData.getString(stack, "VerityVariant");
            }

            player.swing(hand, true);
            stack.shrink(1);
            player.level().playSound((Player)null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 0.8F);
            VerityEntity spawnedVerity = ModEntities.VERITY_ENTITY.get().spawn(level, spawnPos, MobSpawnType.MOB_SUMMONED);
            if (spawnedVerity != null) {
               spawnedVerity.setVariant(variantToSpawn);
               spawnedVerity.getPersistentData().putBoolean("WasThrown", false);
               if (StackData.has(stack, "VerityName")) {
                  spawnedVerity.setCustomName(Component.literal(StackData.getString(stack, "VerityName")));
                  spawnedVerity.setCustomNameVisible(true);
               }
            }
         }
      }
   }

   public static boolean hasVerityInInventory(Player player) {
      if (player == null) {
         return false;
      }

      boolean inMain = player.getInventory().items.stream().anyMatch(stack -> !stack.isEmpty() && stack.is(ModItems.VERITY_ITEM.get()));
      return inMain ? true : !player.getOffhandItem().isEmpty() && player.getOffhandItem().is(ModItems.VERITY_ITEM.get());
   }

   @SubscribeEvent
   public static void onPlayerChat(ServerChatEvent event) {
      ServerPlayer player = event.getPlayer();
      boolean isSpawned = VerityState.hasSpawned && VerityState.verityEntity != null && !VerityState.verityEntity.isRemoved();
      boolean isInInventory = hasVerityInInventory(player);
      if (!isSpawned && !isInInventory) {
         System.out.println("Verity is neither spawned nor in player's inventory");
      } else {
         if (VerityConfig.REQUIRE_VERITY.get()) {
            System.out.println("Require verity is on");
            if (!event.getRawText().toLowerCase().contains(VerityConfig.VERITY_CUSTOM_NAME.get().toLowerCase())) {
               System.out.println("verity keyword not found");
               return;
            }
         }

         String message = event.getMessage().getString();
         VerityState.idleChatTimer = 2400 + new Random().nextInt(2400);
         if (isSpawned) {
            VerityState.verityEntity.startTalking(80);
         }

         System.out.println("run ask");
         String finalMessage = "<" + player.getName().getString() + "> " + message;
         AiManager.queryAI(isSpawned ? VerityState.verityEntity : null, finalMessage, player);
         System.out.println("ran ask");
      }
   }

   private static class ScheduledTask {
      int ticksRemaining;
      final Runnable task;

      ScheduledTask(Runnable task, int ticksRemaining) {
         this.task = task;
         this.ticksRemaining = ticksRemaining;
      }
   }
}
