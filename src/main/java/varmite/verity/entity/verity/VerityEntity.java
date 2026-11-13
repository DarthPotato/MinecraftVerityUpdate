package varmite.verity.entity.verity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import varmite.verity.VerityConfig;
import varmite.verity.entity.ModEntities;
import varmite.verity.entity.VerityState;
import varmite.verity.entity.demon.VerityDemonEntity;
import varmite.verity.entity.veritybox.BoxEntity;
import varmite.verity.environment.sounds.ModSounds;
import varmite.verity.event.ModEvents;
import varmite.verity.event.WorldSpawnData;
import varmite.verity.network.ModNetwork;
import varmite.verity.network.PlayTtsPayload;

public class VerityEntity extends PathfinderMob {
   private static final EntityDataAccessor<String> TEXTURE_PATH = SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.STRING);
   public static final EntityDataAccessor<Boolean> IS_TALKING = SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> TALK_FRAME = SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);
   public static final EntityDataAccessor<Integer> BOUNCE_START_TICK = SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.OPTIONAL_UUID);
   public static final EntityDataAccessor<Boolean> IS_MONSTROUS = SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.BOOLEAN);
   public static final EntityDataAccessor<Integer> APOLOGY_COUNT = SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);
   private final List<UUID> playersWhoLooked = new ArrayList<>();
   public int pleadingTimer = -1;
   private int consecutiveBadMessages = 0;
   private boolean hasTransformed = false;
   private int talkTicks = 0;
   private int talkEndTick = -1;
   public int animationTicks = 140;
   public int clientAnimationTicks = -1;
   public int clientIntroTicks = 0;
   public int clientIntroDelay = 0;
   public volatile boolean clientIsTalking = false;
   public float clientRollAngle = 0.0F;
   public float clientRollAngleO = 0.0F;
   private boolean hasTriggeredDay2 = false;
   public static final List<String> VALID_VARIANTS = List.of(
      "crazy_talking",
      "happy",
      "happy_sleep",
      "happy_talking",
      "hurt",
      "neutral",
      "noface",
      "serious_1",
      "serious_2",
      "serious_3",
      "serious_talking",
      "evil",
      "evil_talking",
      "smiling_evil",
      "crazy",
      "neutral_talking"
   );
   public static final String VARIANT_DEFAULT = "happy";

   public VerityEntity(EntityType<? extends PathfinderMob> type, Level level) {
      super(type, level);
      this.noCulling = true;
      this.setPersistenceRequired();
   }

   @Override
   public boolean isPushable() {
      return true;
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(TEXTURE_PATH, "textures/entity/happy.png");
      this.entityData.define(IS_TALKING, false);
      this.entityData.define(TALK_FRAME, 0);
      this.entityData.define(BOUNCE_START_TICK, -1000);
      this.entityData.define(OWNER_UUID, Optional.empty());
      this.entityData.define(IS_MONSTROUS, false);
      this.entityData.define(APOLOGY_COUNT, 0);
   }

   public Optional<UUID> getOwnerUUID() {
      return this.entityData.get(OWNER_UUID);
   }

   public void setOwnerUUID(@Nullable UUID uuid) {
      this.entityData.set(OWNER_UUID, Optional.ofNullable(uuid));
   }

   @Override
   public SpawnGroupData finalizeSpawn(
      ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag
   ) {
      Player closestPlayer = pLevel.getNearestPlayer(this, 10.0);
      if (closestPlayer != null) {
         this.setOwnerUUID(closestPlayer.getUUID());
      }

      return super.finalizeSpawn(pLevel, pDifficulty, pReason, pSpawnData, pDataTag);
   }

   public void triggerBoxDrop() {
      if (!this.level().isClientSide) {
         this.getServer().execute(() -> {
            WorldSpawnData data = WorldSpawnData.get((ServerLevel)this.level());
            data.verityKarma = 10.0F;
            data.setDirty();
         });
         this.setVariant("hurt");
         this.animationTicks = 0;
         this.entityData.set(BOUNCE_START_TICK, 1);
      }
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (this.level().isClientSide && BOUNCE_START_TICK.equals(key) && this.entityData.get(BOUNCE_START_TICK) > 0) {
         this.clientAnimationTicks = 50;
      }
   }

   @Override
   public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
      if (!this.level().isClientSide) {
         if (fallDistance > 0.75F) {
            ModEvents.schedule(() -> {
               if (!this.isRemoved()) {
                  double bounceStrength = Math.min(Math.sqrt(fallDistance) * 0.22, 0.7);
                  this.setDeltaMovement(this.getDeltaMovement().x, bounceStrength, this.getDeltaMovement().z);
                  this.hasImpulse = true;
                  this.setOnGround(false);
                  Random random1 = new Random();
                  int i = random1.nextInt(3);
                  this.setVariant("hurt");
                  ModNetwork.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> this), new PlayTtsPayload(this.getId(), "Ouch"));
                  if (i == 0) {
                     this.playSound(ModSounds.IMPACT_0.get(), 1.0F, 1.0F);
                  } else if (i == 1) {
                     this.playSound(ModSounds.IMPACT_1.get(), 1.0F, 1.0F);
                  } else {
                     this.playSound(ModSounds.IMPACT_2.get(), 1.0F, 1.0F);
                  }
               }
            }, 1);
            ModEvents.schedule(() -> {
               if (!this.isRemoved()) {
                  this.setVariant("happy");
               }
            }, 20);
         }

         this.resetFallDistance();
      }

      return false;
   }

   @Override
   protected void registerGoals() {
      super.registerGoals();
      this.goalSelector.addGoal(1, new VerityEntity.FollowPlacerGoal(this, 1.25, 8.0F, 3.0F));
      this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 3.0F) {
         @Override
         public boolean canUse() {
            return this.mob.getPersistentData().getBoolean("WasThrown") ? false : super.canUse();
         }
      });
      this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
   }

   @Override
   public void tick() {
      Vec3 motionBeforeCollision = this.getDeltaMovement();
      super.tick();
      if (this.level().isClientSide) {
         this.clientRollAngleO = this.clientRollAngle;
         double dx = this.getX() - this.xo;
         double dz = this.getZ() - this.zo;
         double distanceMoved = Math.sqrt(dx * dx + dz * dz);
         if (distanceMoved > 0.005) {
            this.clientRollAngle += (float)(distanceMoved * 150.0);
         }

         if (this.clientAnimationTicks > 0) {
            this.clientAnimationTicks--;
         } else if (this.clientAnimationTicks == 0) {
            this.clientAnimationTicks = -1;
            this.setVariant("happy");
            this.clientIntroDelay = 10;
         }

         if (this.clientIntroDelay > 0) {
            this.clientIntroDelay--;
            if (this.clientIntroDelay == 0) {
               this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), ModSounds.INTRODUCTION.get(), this.getSoundSource(), 1.0F, 1.0F, false);
               this.clientIntroTicks = 90;
            }
         }

         if (this.clientIntroTicks > 0) {
            this.clientIntroTicks--;
         } else if (this.clientIntroTicks < 0) {
            this.clientIntroTicks = 0;
         }
      } else {
         if (this.isMonstrous()) {
            if (this.pleadingTimer > 0) {
               this.pleadingTimer--;
            } else if (this.pleadingTimer == 0) {
               this.setMonstrous(false);
               this.setApologyCount(0);
               this.clearPlayersWhoLooked();
               this.setVariant("evil");
               this.pleadingTimer = -1;
            }

            for (Player nearbyPlayer : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(10.0))) {
               if (this.isPlayerLookingAtMe(nearbyPlayer)) {
                  this.addPlayerWhoLooked(nearbyPlayer.getUUID());
               }
            }
         }

         if (this.horizontalCollision) {
            double newX = this.getDeltaMovement().x;
            double newZ = this.getDeltaMovement().z;
            boolean bounced = false;
            if (Math.abs(motionBeforeCollision.x) > 0.1 && Math.abs(newX) < 0.02) {
               newX = -motionBeforeCollision.x * 0.6;
               bounced = true;
            }

            if (Math.abs(motionBeforeCollision.z) > 0.1 && Math.abs(newZ) < 0.02) {
               newZ = -motionBeforeCollision.z * 0.6;
               bounced = true;
            }

            if (bounced) {
               this.setDeltaMovement(newX, this.getDeltaMovement().y, newZ);
               this.hasImpulse = true;
               Random random1 = new Random();
               int i = random1.nextInt(3);
               this.setVariant("hurt");
               if (i == 0) {
                  this.playSound(ModSounds.IMPACT_0.get(), 1.0F, 1.0F);
               } else if (i == 1) {
                  this.playSound(ModSounds.IMPACT_1.get(), 1.0F, 1.0F);
               } else {
                  this.playSound(ModSounds.IMPACT_2.get(), 1.0F, 1.0F);
               }
            }
         }

         if (this.blockPosition().getY() <= -63) {
            this.setDeltaMovement(0.0, 0.0, 0.0);
            this.hasImpulse = true;
         }

         if (this.entityData.get(BOUNCE_START_TICK) > 0 && this.tickCount > 100) {
            this.entityData.set(BOUNCE_START_TICK, -1000);
         }

         if (this.isTalking()) {
            this.talkTicks++;
         } else {
            this.talkTicks = 0;
         }

         ServerLevel serverLevel = (ServerLevel)this.level();
         long currentDay = serverLevel.getDayTime() / 24000L;
         float currentKarma = WorldSpawnData.get(serverLevel).verityKarma;
         if (currentDay >= VerityConfig.DAY_COUNT.get().intValue() && currentKarma < 9000.0F) {
            if (serverLevel.getServer().getGameRules().getRule(GameRules.RULE_DAYLIGHT).get() && !serverLevel.getServer().isHardcore()) {
               serverLevel.getServer().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, serverLevel.getServer());
            }

            if (!this.isRemoved()) {
               Player player = serverLevel.getNearestPlayer(this, 64.0);
               if (player != null && !this.hasTransformed && this.transformIntoDemon(player, false)) {
                  this.setVariant("noface");
                  this.hasTransformed = true;
               }
            }
         } else if (currentDay >= VerityState.timeWillSpawn && VerityState.transformFollowingDay && !this.isRemoved()) {
            Player player = serverLevel.getNearestPlayer(this, 64.0);
            if (player != null && !this.hasTransformed && this.transformIntoDemon(player, true)) {
               this.setVariant("noface");
               this.hasTransformed = true;
            }
         }

         if (this.animationTicks < 60) {
            this.animationTicks++;
            if (this.animationTicks == 50) {
               this.setVariant("happy");
            }
         }

         if (this.tickCount % 40 == 0) {
            currentDay = serverLevel.getDayTime() / 24000L;
            if (currentDay >= VerityConfig.DAY_COUNT.get() / 2 && !this.hasTriggeredDay2 && currentKarma < 9000.0F) {
               this.hasTriggeredDay2 = true;
               serverLevel.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, serverLevel.getServer());
               List<Entity> entitiesToKill = new ArrayList<>();

               for (Entity entity : serverLevel.getAllEntities()) {
                  if (!(entity instanceof Player)
                     && !(entity instanceof VerityEntity)
                     && !(entity instanceof BoxEntity)
                     && !(entity instanceof VerityDemonEntity)
                     && entity instanceof LivingEntity) {
                     entitiesToKill.add(entity);
                  }
               }

               for (Entity entity : entitiesToKill) {
                  entity.kill();
               }
            }
         }

         if (this.isTalking() && this.talkEndTick > 0 && this.tickCount >= this.talkEndTick) {
            this.stopTalking();
         }
      }
   }

   @Override
   public boolean isPersistenceRequired() {
      return true;
   }

   private boolean transformIntoDemon(Player player, boolean provoked) {
      if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
         return false;
      }

      ServerLevel serverLevel = (ServerLevel)this.level();
      WorldSpawnData data = WorldSpawnData.get(serverLevel);
      if (data.hasLiveDemon()) {
         return false;
      }

      if (provoked ? !data.hasProvokedSpawn : !data.hasHostileDaySpawn) {
         for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof VerityDemonEntity) {
               return false;
            }
         }

         BlockPos spawnPos = this.getCreepyDemonSpawnPos(serverLevel, player.blockPosition());
         Entity demonEntity = ModEntities.VERITY_DEMON_ENTITY.get().spawn(serverLevel, spawnPos, MobSpawnType.CONVERSION);
         if (demonEntity == null) {
            return false;
         }

         if (provoked) {
            data.hasProvokedSpawn = true;
         } else {
            data.hasHostileDaySpawn = true;
         }

         data.setActiveDemon(demonEntity.getUUID());
         VerityState.transformFollowingDay = false;
         if (VerityConfig.DAY_COUNT.get() <= (int)player.level().getDayTime() / 24000) {
            VerityConfig.DAY_COUNT.set((int)player.level().getDayTime() / 24000);
         }

         VerityState.timeWillSpawn = 0L;
         this.setMonstrous(true);
         this.setApologyCount(0);
         this.clearPlayersWhoLooked();
         this.pleadingTimer = 6000;
         player.sendSystemMessage(Component.literal("§4Verity's face goes blank... You must look at him to calm him down."));
         double dX = player.getX() - demonEntity.getX();
         double dZ = player.getZ() - demonEntity.getZ();
         double dY = player.getEyeY() - demonEntity.getEyeY();
         float yaw = (float)(Mth.atan2(dZ, dX) * (180.0 / Math.PI)) - 90.0F;
         float pitch = (float)(-(Mth.atan2(dY, Math.sqrt(dX * dX + dZ * dZ)) * (180.0 / Math.PI)));
         demonEntity.setYRot(yaw);
         demonEntity.setYHeadRot(yaw);
         demonEntity.setXRot(pitch);
         demonEntity.yRotO = yaw;
         demonEntity.xRotO = pitch;
         long currentDay = serverLevel.getDayTime() / 24000L;
         serverLevel.setDayTime(currentDay * 24000L + 18000L);
         serverLevel.playSound((Player)null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 0.5F);
         serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.5, 0.5, 0.5, 0.05);
         return true;
      } else {
         return false;
      }
   }

   private BlockPos getCreepyDemonSpawnPos(ServerLevel level, BlockPos playerPos) {
      boolean isAboveGround = level.canSeeSky(playerPos) || playerPos.getY() >= level.getHeightmapPos(Types.MOTION_BLOCKING, playerPos).getY() - 2;
      int attempts = 40;
      BlockPos verityPos = this.blockPosition();

      for (int i = 0; i < attempts; i++) {
         int dx = (this.random.nextBoolean() ? 1 : -1) * (this.random.nextInt(16) + 16);
         int dz = (this.random.nextBoolean() ? 1 : -1) * (this.random.nextInt(16) + 16);
         BlockPos checkPos = playerPos.offset(dx, 0, dz);
         if (!(checkPos.distSqr(verityPos) < 256.0)) {
            if (isAboveGround) {
               BlockPos surfacePos = level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, checkPos);
               if (level.getBlockState(surfacePos.below()).isSolidRender(level, surfacePos.below())) {
                  return surfacePos;
               }
            } else {
               for (int dy = -4; dy <= 4; dy++) {
                  BlockPos cavePos = checkPos.offset(0, dy, 0);
                  if (level.isEmptyBlock(cavePos)
                     && level.isEmptyBlock(cavePos.above())
                     && level.getBlockState(cavePos.below()).isSolidRender(level, cavePos.below())
                     && !level.canSeeSky(cavePos)) {
                     return cavePos;
                  }
               }
            }
         }
      }

      BlockPos fallbackPos = verityPos.offset(16, 0, 16);
      return isAboveGround ? level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, fallbackPos) : fallbackPos;
   }

   public void startTalking(int durationTicks) {
      if (!this.level().isClientSide) {
         this.entityData.set(IS_TALKING, true);
         this.entityData.set(TALK_FRAME, this.random.nextInt(2));
         this.talkTicks = 0;
         this.talkEndTick = durationTicks > 0 ? this.tickCount + durationTicks : -1;
      }
   }

   public void stopTalking() {
      if (!this.level().isClientSide) {
         this.entityData.set(IS_TALKING, false);
         this.talkTicks = 0;
         this.talkEndTick = -1;
      }
   }

   @Override
   public boolean hasCustomName() {
      return super.hasCustomName();
   }

   @Override
   public boolean shouldShowName() {
      return false;
   }

   public boolean isTalking() {
      return this.entityData.get(IS_TALKING);
   }

   public boolean isMonstrous() {
      return this.entityData.get(IS_MONSTROUS);
   }

   public void setMonstrous(boolean monstrous) {
      this.entityData.set(IS_MONSTROUS, monstrous);
   }

   public int getApologyCount() {
      return this.entityData.get(APOLOGY_COUNT);
   }

   public void setApologyCount(int count) {
      this.entityData.set(APOLOGY_COUNT, count);
   }

   public void addPlayerWhoLooked(UUID playerUUID) {
      if (!this.playersWhoLooked.contains(playerUUID)) {
         this.playersWhoLooked.add(playerUUID);
      }
   }

   public boolean hasPlayerLooked(UUID playerUUID) {
      return this.playersWhoLooked.contains(playerUUID);
   }

   public void clearPlayersWhoLooked() {
      this.playersWhoLooked.clear();
   }

   public String getVariant() {
      String path = this.getTexturePath();
      if (path == null) {
         return "happy";
      }

      String fileName = path.substring(path.lastIndexOf(47) + 1);
      if (fileName.endsWith(".png")) {
         fileName = fileName.substring(0, fileName.length() - 4);
      }

      return fileName;
   }

   public ResourceLocation getTextureRL() {
      String path = this.getTexturePath();
      if (path == null) {
         return ResourceLocation.fromNamespaceAndPath("verity", "textures/entity/happy.png");
      }

      path = path.replace("verity:", "");
      boolean visuallyTalking = this.isTalking() || this.clientIsTalking || this.clientIntroTicks > 0;
      if (!visuallyTalking) {
         return ResourceLocation.fromNamespaceAndPath("verity", path);
      }

      String fileName = path.substring(path.lastIndexOf(47) + 1).replace(".png", "");

      String talkingTexture = switch (fileName) {
         case "happy" -> "happy_talking";
         case "crazy" -> "crazy_talking";
         case "evil" -> "evil_talking";
         case "serious_1", "serious_2", "serious_3" -> "serious_talking";
         default -> fileName;
      };
      return ResourceLocation.fromNamespaceAndPath("verity", "textures/entity/" + talkingTexture + ".png");
   }

   public void setVariant(String variant) {
      if (VerityConfig.DEV_MODE.get()) {
         System.out.println("Changing expression to " + variant);
      }

      if (variant == null) {
         variant = "happy";
      }

      if (variant.endsWith(".png")) {
         variant = variant.replace(".png", "");
      }

      if (!VALID_VARIANTS.contains(variant)) {
         long currentDay = this.level().getDayTime() / 24000L;
         int threshold = Math.max(1, VerityConfig.DAY_COUNT.get() / 2);
         variant = currentDay >= threshold ? "evil" : "happy";
      }

      this.entityData.set(TEXTURE_PATH, "textures/entity/" + variant + ".png");
   }

   public String getTexturePath() {
      return this.entityData.get(TEXTURE_PATH);
   }

   public void setTexturePath(String path) {
      this.entityData.set(TEXTURE_PATH, path);
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putString("TexturePath", this.getTexturePath());
      tag.putBoolean("HasTriggeredDay2", this.hasTriggeredDay2);
      tag.putInt("ConsecutiveBadMessages", this.consecutiveBadMessages);
      tag.putBoolean("HasTransformed", this.hasTransformed);
      tag.putBoolean("IsMonstrous", this.isMonstrous());
      tag.putInt("ApologyCount", this.getApologyCount());
      tag.putInt("PleadingTimer", this.pleadingTimer);
      if (this.getOwnerUUID().isPresent()) {
         tag.putUUID("Owner", this.getOwnerUUID().get());
      }
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      String path = tag.getString("TexturePath");
      if (path == null || path.isBlank() || path.contains("null.png")) {
         path = "textures/entity/happy.png";
      }

      this.setTexturePath(path);
      if (tag.contains("HasTriggeredDay2")) {
         this.hasTriggeredDay2 = tag.getBoolean("HasTriggeredDay2");
      }

      if (tag.contains("ConsecutiveBadMessages")) {
         this.consecutiveBadMessages = tag.getInt("ConsecutiveBadMessages");
      }

      if (tag.contains("HasTransformed")) {
         this.hasTransformed = tag.getBoolean("HasTransformed");
      }

      if (tag.hasUUID("Owner")) {
         this.setOwnerUUID(tag.getUUID("Owner"));
      }

      if (tag.contains("IsMonstrous")) {
         this.setMonstrous(tag.getBoolean("IsMonstrous"));
      }

      if (tag.contains("ApologyCount")) {
         this.setApologyCount(tag.getInt("ApologyCount"));
      }

      if (tag.contains("PleadingTimer")) {
         this.pleadingTimer = tag.getInt("PleadingTimer");
      }
   }

   public boolean isPlayerLookingAtMe(Player player) {
      Vec3 eyePosition = player.getEyePosition();
      Vec3 lookVector = player.getViewVector(1.0F);
      Vec3 verityCenter = this.getBoundingBox().getCenter();
      Vec3 toVerity = verityCenter.subtract(eyePosition);
      Vec3 normalizedLook = lookVector.normalize();
      Vec3 normalizedToVerity = toVerity.normalize();
      double dotProduct = normalizedLook.dot(normalizedToVerity);
      return dotProduct > 0.95 && player.distanceTo(this) < 20.0;
   }

   public boolean tryOpenDoor(BlockPos pos) {
      BlockState blockState = this.level().getBlockState(pos);
      if (blockState.getBlock() instanceof DoorBlock doorBlock && !doorBlock.isOpen(blockState)) {
         doorBlock.setOpen(this, this.level(), blockState, pos, !doorBlock.isOpen(blockState));
         this.level().levelEvent(null, 1003, pos, 0);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean isInvulnerableTo(DamageSource source) {
      return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
         ? false
         : !source.is(DamageTypes.IN_WALL)
            && !source.is(DamageTypes.FALLING_BLOCK)
            && !source.is(DamageTypes.FALLING_ANVIL)
            && !source.is(DamageTypeTags.IS_FIRE)
            && !source.is(DamageTypes.LAVA)
            && !source.is(DamageTypes.FALL);
   }

   public static Builder createAttributes() {
      return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 32.0);
   }

   @Override
   public Iterable<ItemStack> getArmorSlots() {
      return Collections.emptyList();
   }

   @Override
   public ItemStack getItemBySlot(EquipmentSlot slot) {
      return ItemStack.EMPTY;
   }

   @Override
   public HumanoidArm getMainArm() {
      return HumanoidArm.RIGHT;
   }

   @Override
   public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
   }

   static class FollowPlacerGoal extends Goal {
      private final VerityEntity verity;
      private Player owner;
      private final double speedModifier;
      private final float stopDistance;
      private final float startDistance;
      private int doorOpenDelay = 0;

      public FollowPlacerGoal(VerityEntity verity, double speed, float start, float stop) {
         this.verity = verity;
         this.speedModifier = speed;
         this.startDistance = start;
         this.stopDistance = stop;
         this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
      }

      @Override
      public boolean canUse() {
         if (!VerityState.followPlayer) {
            return false;
         }

         Optional<UUID> uuid = this.verity.getOwnerUUID();
         if (uuid.isEmpty()) {
            return false;
         }

         Player player = this.verity.level().getPlayerByUUID(uuid.get());
         if (player != null && !player.isSpectator()) {
            if (this.verity.distanceToSqr(player) < this.startDistance * this.startDistance) {
               return false;
            }

            this.owner = player;
            return true;
         } else {
            return false;
         }
      }

      @Override
      public boolean canContinueToUse() {
         return this.owner != null && this.owner.isAlive() && !this.owner.isSpectator() && VerityState.followPlayer
            ? this.verity.distanceToSqr(this.owner) > this.stopDistance * this.stopDistance
            : false;
      }

      @Override
      public void start() {
      }

      @Override
      public void stop() {
         this.owner = null;
         this.verity.getNavigation().stop();
      }

      @Override
      public void tick() {
         if (this.doorOpenDelay > 0) {
            this.doorOpenDelay--;
            this.verity.getNavigation().stop();
         } else {
            BlockPos verityBlockPos = this.verity.blockPosition();
            Vec3 targetVec = this.owner.position();
            double dx = targetVec.x - this.verity.getX();
            double dz = targetVec.z - this.verity.getZ();
            double angle = Math.atan2(dz, dx);
            BlockPos frontPos = verityBlockPos.offset((int)Math.round(Math.cos(angle)), 0, (int)Math.round(Math.sin(angle)));
            boolean openedDoor = this.verity.tryOpenDoor(frontPos);
            if (this.verity.tryOpenDoor(frontPos.above())) {
               openedDoor = true;
            }

            if (openedDoor) {
               this.doorOpenDelay = 15;
               this.verity.getNavigation().stop();
            } else {
               this.verity.getLookControl().setLookAt(this.owner, 10.0F, this.verity.getMaxHeadXRot());
               this.verity.getNavigation().moveTo(this.owner, this.speedModifier);
            }
         }
      }
   }
}
