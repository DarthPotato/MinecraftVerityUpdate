package varmite.verity.entity.demon;

import com.mojang.logging.LogUtils;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar;
import com.geckolib.animation.object.LoopType;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import varmite.verity.client.sound.ClientSoundHandler;
import varmite.verity.entity.demon.goals.DemonAttackGoal;
import varmite.verity.entity.demon.goals.DemonBreakDoorGoal;
import varmite.verity.entity.demon.goals.DemonClimbGoal;
import varmite.verity.entity.demon.goals.DemonGlassBreakAndLeapGoal;
import varmite.verity.entity.demon.goals.DemonStareAndBreakGoal;
import varmite.verity.entity.demon.goals.DemonWindowStalkGoal;
import varmite.verity.entity.demon.pathfinding.DemonPathNavigation;

public class VerityDemonEntity extends PathfinderMob implements GeoEntity, Enemy {
   private ChunkPos lastForcedChunk = null;
   private int crawlTimer = 0;
   private int stuckTicks = 0;
   private int eatTicks = 0;
   private int grabTicks = 0;
   private static final int GRAB_LIFT_TICKS = 20;
   private static final int GRAB_HOLD_TICKS = 28;
   private static final double GRAB_MAX_CARRY = 1.5;
   private Vec3 lastPos = null;
   public boolean isPlayingChaseSound = false;
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private static final boolean LOG_ANIMATION_CHANGES = false;
   private static final Logger ANIM_LOG = LogUtils.getLogger();
   private String lastLoggedAnimation = "";
   private double lastLoggedAt = -1.0;
   public float animHeadPitch;
   public float animHeadYaw;
   public float writtenHeadPitch;
   public float writtenHeadYaw;
   private static final int IDLE_DELAY_TICKS = 15;
   private double stoppedSinceTick = -1.0;
   private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
   private static final RawAnimation WINDOW = RawAnimation.begin().thenLoop("window");
   private static final RawAnimation CHASE = RawAnimation.begin().thenLoop("chase");
   private static final RawAnimation CLIMB = RawAnimation.begin().thenLoop("climb");
   private static final RawAnimation EAT = RawAnimation.begin().thenLoop("eat");
   private static final RawAnimation ATTACK = RawAnimation.begin().then("attack", LoopType.PLAY_ONCE);
   private static final RawAnimation THROW = RawAnimation.begin().then("attack_throw", LoopType.PLAY_ONCE);
   public static final String THROW_TRIGGER = "attack_throw";
   private static final RawAnimation PREPARE_FOR_CHASE = RawAnimation.begin().then("prepare_for_chase", LoopType.PLAY_ONCE);
   public static final String PREPARE_FOR_CHASE_TRIGGER = "prepare_for_chase";
   private static final int MOVEMENT_TRANSITION_TICKS = 5;
   private static final double PREPARE_FOR_CHASE_SECONDS = 2.25;
   public static final int PREPARE_FOR_CHASE_TICKS = (int)Math.round(45.0);
   private static final EntityDataAccessor<Integer> DEMON_STATE = SynchedEntityData.defineId(VerityDemonEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> IS_CLIMBING = SynchedEntityData.defineId(VerityDemonEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> HUNT_PHASE = SynchedEntityData.defineId(VerityDemonEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> IS_CRAWLING = SynchedEntityData.defineId(VerityDemonEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> IS_EATING = SynchedEntityData.defineId(VerityDemonEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> IS_GRABBING = SynchedEntityData.defineId(VerityDemonEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> GRABBED_ENTITY_ID = SynchedEntityData.defineId(VerityDemonEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> IS_STALKING_WINDOW = SynchedEntityData.defineId(VerityDemonEntity.class, EntityDataSerializers.BOOLEAN);
   private boolean climbGoalActive;
   private static final float STANDING_HEIGHT = 4.8F;
   private static final float CRAWLING_HEIGHT = 1.8F;
   private static final int STUCK_REPATH_TICKS = 20;
   private static final int STUCK_CHEW_TICKS = 40;
   private static final int STUCK_SHOVE_TICKS = 100;

   public boolean isStuck() {
      return this.stuckTicks > 40;
   }

   public void resetStuckTimer() {
      this.stuckTicks = 0;
   }

   public VerityDemonEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
      super(entityType, level);
      this.setPathfindingMalus(PathType.WATER, 0.0F);
      this.setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
      this.setPersistenceRequired();
   }

   @Override
   protected PathNavigation createNavigation(Level level) {
      return new DemonPathNavigation(this, level);
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DEMON_STATE, 0);
      builder.define(IS_CLIMBING, false);
      builder.define(IS_CRAWLING, false);
      builder.define(HUNT_PHASE, 0);
      builder.define(IS_EATING, false);
      builder.define(IS_GRABBING, false);
      builder.define(GRABBED_ENTITY_ID, 0);
      builder.define(IS_STALKING_WINDOW, false);
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      if (IS_CRAWLING.equals(key)) {
         this.refreshDimensions();
      }

      super.onSyncedDataUpdated(key);
   }

   public void setHuntPhase(int phase) {
      this.entityData.set(HUNT_PHASE, phase);
   }

   public int getHuntPhase() {
      return this.entityData.get(HUNT_PHASE);
   }

   public void setDemonState(int state) {
      this.entityData.set(DEMON_STATE, state);
   }

   public int getDemonState() {
      return this.entityData.get(DEMON_STATE);
   }

   public boolean isEating() {
      return this.entityData.get(IS_EATING);
   }

   public boolean isGrabbing() {
      return this.entityData.get(IS_GRABBING);
   }

   public void setClimbing(boolean climbing) {
      this.entityData.set(IS_CLIMBING, climbing);
   }

   public void setClimbGoalActive(boolean active) {
      this.climbGoalActive = active;
   }

   public void setStalkingWindow(boolean stalking) {
      this.entityData.set(IS_STALKING_WINDOW, stalking);
   }

   public boolean isStalkingWindow() {
      return this.entityData.get(IS_STALKING_WINDOW);
   }

   public void triggerPrepareForChase() {
      this.triggerAnim("movement_controller", "prepare_for_chase");
   }

   public void startEating() {
      this.eatTicks = 60;
      this.entityData.set(IS_EATING, true);
   }

   public void startGrabbing(LivingEntity target) {
      this.entityData.set(IS_GRABBING, true);
      this.entityData.set(GRABBED_ENTITY_ID, target.getId());
      this.grabTicks = 0;
      this.triggerAnim("movement_controller", "attack_throw");
   }

   @Nullable
   public LivingEntity getGrabbedEntity() {
      int id = this.entityData.get(GRABBED_ENTITY_ID);
      if (id == 0) {
         return null;
      }

      Entity entity = this.level().getEntity(id);
      return entity instanceof LivingEntity ? (LivingEntity)entity : null;
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(1, new DemonGlassBreakAndLeapGoal(this));
      this.goalSelector.addGoal(2, new DemonBreakDoorGoal(this));
      this.goalSelector.addGoal(3, new DemonWindowStalkGoal(this));
      this.goalSelector.addGoal(4, new DemonStareAndBreakGoal(this));
      this.goalSelector.addGoal(4, new DemonClimbGoal(this));
      this.goalSelector.addGoal(5, new DemonAttackGoal(this));
      this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0));
      this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
      this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
      this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<Villager>(this, Villager.class, false) {
         @Override
         public boolean canUse() {
            return !VerityDemonEntity.this.isEating() && !VerityDemonEntity.this.isGrabbing() && super.canUse();
         }

         @Override
         public void start() {
            super.start();
            VerityDemonEntity.this.setDemonState(1);
         }
      });
      this.targetSelector
         .addGoal(
            2,
            new NearestAttackableTargetGoal<Player>(this, Player.class, false) {
               @Override
               public boolean canUse() {
                  boolean noVillagers = VerityDemonEntity.this.level()
                     .getEntitiesOfClass(Villager.class, VerityDemonEntity.this.getBoundingBox().inflate(64.0))
                     .isEmpty();
                  return VerityDemonEntity.this.getDemonState() == 1
                     && noVillagers
                     && !VerityDemonEntity.this.isEating()
                     && !VerityDemonEntity.this.isGrabbing()
                     && super.canUse();
               }
            }
         );
   }

   public void forceCrawl(int ticks) {
      this.crawlTimer = Math.max(this.crawlTimer, ticks);
   }

   public static Builder createAttributes() {
      return LivingEntity.createLivingAttributes()
         .add(Attributes.MAX_HEALTH, 400.0)
         .add(Attributes.MOVEMENT_SPEED, 0.52)
         .add(Attributes.FOLLOW_RANGE, 512.0)
         .add(Attributes.ATTACK_DAMAGE, 19.0)
         .add(Attributes.ATTACK_KNOCKBACK, 1.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.STEP_HEIGHT, 1.5);
   }

   @Override
   public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
      if (!source.is(DamageTypes.FALL) && !source.is(DamageTypes.IN_WALL)) {
         boolean wasHurt = super.hurtServer(level, source, amount);
         if (wasHurt
            && source.getEntity() instanceof LivingEntity attacker
            && !(attacker instanceof Player player && (player.isCreative() || player.isSpectator()))) {
            if (this.isEating()) {
               this.eatTicks = 0;
               this.entityData.set(IS_EATING, false);
            }

            if (this.isGrabbing()) {
               this.entityData.set(IS_GRABBING, false);
               this.entityData.set(GRABBED_ENTITY_ID, 0);
            }

            if (this.getDemonState() == 0) {
               this.setDemonState(1);
               this.setHuntPhase(1);
            }
         }

         return wasHurt;
      } else {
         return false;
      }
   }

   @Override
   public boolean killedEntity(ServerLevel level, LivingEntity killedEntity, DamageSource source) {
      this.startEating();
      return super.killedEntity(level, killedEntity, source);
   }

   public boolean hasLineOfSightThroughGlass(Player player) {
      Vec3 start = new Vec3(player.getX(), player.getEyeY(), player.getZ());
      Vec3 end = new Vec3(this.getX(), this.getLookAnchorY(), this.getZ());
      double distance = start.distanceTo(end);
      if (distance > 128.0) {
         return false;
      }

      Vec3 dir = end.subtract(start).normalize();
      Level level = this.level();

      for (double d = 0.0; d < distance; d++) {
         Vec3 point = start.add(dir.scale(d));
         BlockPos pos = BlockPos.containing(point);
         BlockState state = level.getBlockState(pos);
         if (!state.getCollisionShape(level, pos).isEmpty()) {
            String name = state.getBlock().getDescriptionId().toLowerCase();
            if (!name.contains("glass") && !name.contains("pane") && !state.is(BlockTags.LEAVES)) {
               return false;
            }
         }
      }

      return true;
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide()) {
         if (this.getDemonState() == 1 && !this.isPlayingChaseSound) {
            this.isPlayingChaseSound = true;
            ClientSoundHandler.playDemonChaseSound(this);
         }
      } else {
         ServerLevel level = (ServerLevel)this.level();
         ChunkPos currentChunk = this.chunkPosition();
         if (this.getDemonState() != 1 || this.getTarget() == null) {
            this.releaseForcedChunk();
         } else if (this.lastForcedChunk == null || !this.lastForcedChunk.equals(currentChunk)) {
            if (this.lastForcedChunk != null) {
               level.setChunkForced(this.lastForcedChunk.x(), this.lastForcedChunk.z(), false);
            }

            level.setChunkForced(currentChunk.x(), currentChunk.z(), true);
            this.lastForcedChunk = currentChunk;
         }

         if (this.isGrabbing()) {
            this.grabTicks++;
            LivingEntity grabbed = this.getGrabbedEntity();
            if (grabbed != null && grabbed.isAlive()) {
               this.getNavigation().stop();
               this.setDeltaMovement(0.0, 0.0, 0.0);
               double liftProgress = Math.min(1.0, this.grabTicks / 20.0);
               Vec3 held = this.position().add(this.getLookAngle().scale(1.5)).add(0.0, 2.0 + liftProgress, 0.0);
               Vec3 carry = held.subtract(grabbed.position());
               if (carry.lengthSqr() > 2.25) {
                  carry = carry.normalize().scale(1.5);
               }

               grabbed.setDeltaMovement(carry);
               grabbed.needsSync = true;
               grabbed.fallDistance = 0.0F;
               if (this.grabTicks >= 28) {
                  Vec3 launchVector = grabbed.position().subtract(this.position()).normalize().multiply(4.0, 1.2, 4.0).add(0.0, 0.2, 0.0);
                  grabbed.setDeltaMovement(launchVector);
                  if (grabbed instanceof ServerPlayer serverPlayer) {
                     serverPlayer.level().playSound((Player)null, this.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1.5F, 1.0F);
                     serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
                  }

                  this.getGrabbedEntity().hurt(this.getGrabbedEntity().damageSources().fall(), 5.0F);
                  this.entityData.set(IS_GRABBING, false);
                  this.entityData.set(GRABBED_ENTITY_ID, 0);
               }
            } else {
               this.entityData.set(IS_GRABBING, false);
               this.entityData.set(GRABBED_ENTITY_ID, 0);
            }
         } else {
            if (this.isEating()) {
               level.sendParticles(DustParticleOptions.REDSTONE, this.getX(), this.getY(), this.getZ(), 20, 0.5, 0.5, 0.5, 0.05);
               BlockPos centerPos = this.blockPosition();

               for (int i = -1; i <= 1; i++) {
                  for (int j = -1; j <= 1; j++) {
                     BlockPos currentPos = centerPos.offset(i, 0, j);
                     BlockPos belowPos = currentPos.below();
                     if (level.getBlockState(currentPos).canBeReplaced() && level.getBlockState(belowPos).isFaceSturdy(level, belowPos, Direction.UP)) {
                        level.setBlock(currentPos, Blocks.REDSTONE_WIRE.defaultBlockState(), 3);
                     }
                  }
               }
            }

            if (this.eatTicks > 0) {
               this.eatTicks--;
               this.getNavigation().stop();
               this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
               this.setYRot(this.yRotO);
               this.setXRot(this.xRotO);
               this.setYHeadRot(this.yHeadRotO);
               this.setYBodyRot(this.yBodyRotO);
               if (this.eatTicks <= 0) {
                  this.entityData.set(IS_EATING, false);
               }
            }

            LivingEntity target = this.getTarget();
            if (this.getDemonState() != 1 || target != null && target.isAlive()) {
               if (target != null) {
                  if (this.lastPos == null) {
                     this.lastPos = this.position();
                  }

                  if (this.tickCount % 10 == 0) {
                     if (this.position().distanceToSqr(this.lastPos) < 0.05) {
                        this.stuckTicks += 10;
                        if (this.getDemonState() == 1) {
                           this.tryUnstick(target);
                        }
                     } else {
                        this.stuckTicks = 0;
                     }

                     this.lastPos = this.position();
                  }
               } else {
                  this.stuckTicks = 0;
               }

               BlockPos feet = this.blockPosition();
               if (this.getDemonState() == 1 && target != null) {
                  AABB box = this.getBoundingBox().inflate(0.5, 0.5, 0.5);
                  BlockPos.betweenClosedStream(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                     .forEach(pos -> {
                        BlockState state = this.level().getBlockState(pos);
                        String blockName = state.getBlock().getDescriptionId().toLowerCase();
                        if (state.is(BlockTags.LEAVES) || blockName.contains("glass") || blockName.contains("pane")) {
                           this.level().destroyBlock(pos, true);
                        }
                     });
               }

               this.updateClimbing(target);
               boolean needsToCrouch = false;
               Predicate<BlockState> isHardCeiling = state -> {
                  String name = state.getBlock().getDescriptionId().toLowerCase();
                  return !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()
                     && !state.is(BlockTags.LEAVES)
                     && !name.contains("glass")
                     && !name.contains("pane");
               };
               if (isHardCeiling.test(this.level().getBlockState(feet.above(2))) || isHardCeiling.test(this.level().getBlockState(feet.above(3)))) {
                  needsToCrouch = true;
               }

               if (!needsToCrouch && this.getDemonState() == 1 && target != null) {
                  Vec3 dir = target.position().subtract(this.position());
                  Vec3 horizDir = new Vec3(dir.x, 0.0, dir.z);
                  if (horizDir.lengthSqr() > 0.01) {
                     horizDir = horizDir.normalize();

                     for (int i = 1; i <= 5; i++) {
                        BlockPos forwardPos = BlockPos.containing(this.getX() + horizDir.x * i, this.getY(), this.getZ() + horizDir.z * i);
                        if (isHardCeiling.test(this.level().getBlockState(forwardPos)) || isHardCeiling.test(this.level().getBlockState(forwardPos.above(1)))) {
                           break;
                        }

                        if (isHardCeiling.test(this.level().getBlockState(forwardPos.above(2)))
                           || isHardCeiling.test(this.level().getBlockState(forwardPos.above(3)))) {
                           needsToCrouch = true;
                           break;
                        }
                     }
                  }
               }

               boolean wasCrawling = this.entityData.get(IS_CRAWLING);
               if (needsToCrouch && !this.entityData.get(IS_CLIMBING)) {
                  this.crawlTimer = 15;
               }

               if (this.crawlTimer > 0) {
                  this.crawlTimer--;
               }

               this.entityData.set(IS_CRAWLING, this.crawlTimer > 0);
               if (wasCrawling != this.crawlTimer > 0) {
                  this.refreshDimensions();
               }
            } else {
               this.setTarget(null);
               this.setHuntPhase(0);
               this.getNavigation().stop();
            }
         }
      }
   }

   private void updateClimbing(@Nullable LivingEntity target) {
      if (!this.climbGoalActive) {
         boolean climbing = false;
         if (target != null && this.getDemonState() == 1 && !this.isEating() && !this.isGrabbing()) {
            boolean targetIsAbove = target.getY() > this.getY() + 1.0;
            climbing = this.horizontalCollision && (targetIsAbove || this.getNavigation().isDone());
            if (climbing) {
               if (targetIsAbove) {
                  this.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.2);
               }

               this.clearOverhead();
            }
         }

         this.entityData.set(IS_CLIMBING, climbing);
      }
   }

   private void clearOverhead() {
      Level level = this.level();
      BlockPos head = this.blockPosition().above((int)Math.ceil(this.getBbHeight()));

      for (BlockPos pos : new BlockPos[]{head, head.above()}) {
         BlockState state = level.getBlockState(pos);
         if (!state.getCollisionShape(level, pos).isEmpty() && state.getDestroySpeed(level, pos) >= 0.0F) {
            level.destroyBlock(pos, true);
            this.triggerAttack();
         }
      }
   }

   private void tryUnstick(LivingEntity target) {
      if (this.climbGoalActive) {
         this.resetStuckTimer();
      } else if (this.stuckTicks >= 20) {
         this.getNavigation().stop();
         this.getNavigation().moveTo(target, 1.0);
         if (this.stuckTicks >= 40) {
            this.chewForward(target);
            if (this.stuckTicks >= 100) {
               Vec3 dir = target.position().subtract(this.position());
               Vec3 horizontal = new Vec3(dir.x, 0.0, dir.z);
               if (horizontal.lengthSqr() > 1.0E-4) {
                  horizontal = horizontal.normalize();
                  this.setDeltaMovement(horizontal.x * 0.5, 0.55, horizontal.z * 0.5);
                  this.needsSync = true;
               }

               this.resetStuckTimer();
            }
         }
      }
   }

   private void chewForward(LivingEntity target) {
      Vec3 dir = target.position().subtract(this.position());
      Vec3 horizontal = new Vec3(dir.x, 0.0, dir.z);
      if (!(horizontal.lengthSqr() < 1.0E-4)) {
         horizontal = horizontal.normalize();
         Level level = this.level();
         BlockPos ahead = BlockPos.containing(this.getX() + horizontal.x, this.getY(), this.getZ() + horizontal.z);

         for (int y = 0; y <= 2; y++) {
            BlockPos pos = ahead.above(y);
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() && state.getDestroySpeed(level, pos) >= 0.0F) {
               level.destroyBlock(pos, true);
               this.triggerAttack();
            }
         }
      }
   }

   @Override
   public boolean isPersistenceRequired() {
      return true;
   }

   @Override
   public void addAdditionalSaveData(ValueOutput output) {
      super.addAdditionalSaveData(output);
      output.putInt("DemonState", this.getDemonState());
      output.putInt("HuntPhase", this.getHuntPhase());
   }

   @Override
   public void readAdditionalSaveData(ValueInput input) {
      super.readAdditionalSaveData(input);
      this.setDemonState(input.getIntOr("DemonState", 0));
      this.setHuntPhase(input.getIntOr("HuntPhase", 0));
   }

   @Override
   public void remove(RemovalReason reason) {
      this.releaseForcedChunk();
      super.remove(reason);
   }

   private void releaseForcedChunk() {
      if (this.lastForcedChunk != null && this.level() instanceof ServerLevel serverLevel) {
         serverLevel.setChunkForced(this.lastForcedChunk.x(), this.lastForcedChunk.z(), false);
         this.lastForcedChunk = null;
      }
   }

   @Override
   protected EntityDimensions getDefaultDimensions(Pose pose) {
      return EntityDimensions.fixed(0.4F, this.entityData.get(IS_CRAWLING) ? 1.8F : 4.8F);
   }

   public double getLookAnchorY() {
      return this.getY() + 2.4F;
   }

   @Override
   public boolean onClimbable() {
      return this.entityData.get(IS_CLIMBING);
   }

   public void triggerAttack() {
      this.triggerAnim("action_controller", "attack_trigger");
   }

   private PlayState play(AnimationTest<VerityDemonEntity> state, RawAnimation animation, String name) {
      return state.setAndContinue(animation);
   }

   @Override
   public void registerControllers(ControllerRegistrar controllers) {
      AnimationController<VerityDemonEntity> movementController = new AnimationController<>("movement_controller", 5, state -> {
         if (this.isDeadOrDying()) {
            return PlayState.STOP;
         }

         if (this.isEating()) {
            return PlayState.STOP;
         }

         if (this.entityData.get(IS_CLIMBING)) {
            return this.play(state, CLIMB, "climb");
         }

         if (state.isMoving()) {
            this.stoppedSinceTick = -1.0;
            return this.play(state, CHASE, "chase");
         }

         double now = state.renderState().getAnimatableAge();
         if (this.stoppedSinceTick < 0.0) {
            this.stoppedSinceTick = now;
         }

         if (this.getDemonState() == 1 && now - this.stoppedSinceTick < 15.0) {
            return this.play(state, CHASE, "chase");
         }

         state.controller().setAnimationSpeed(1.0);
         return this.isStalkingWindow() ? this.play(state, WINDOW, "window") : this.play(state, IDLE, "idle");
      });
      movementController.triggerableAnim("prepare_for_chase", PREPARE_FOR_CHASE);
      movementController.triggerableAnim("attack_throw", THROW);
      controllers.add(movementController);
      AnimationController<VerityDemonEntity> actionController = new AnimationController<>(
         "action_controller", 5, state -> this.entityData.get(IS_EATING) ? state.setAndContinue(EAT) : PlayState.STOP
      );
      actionController.triggerableAnim("attack_trigger", ATTACK);
      controllers.add(actionController);
   }

   @Override
   public boolean canStandOnFluid(FluidState f) {
      return f.is(FluidTags.WATER);
   }

   @Override
   public boolean hasCustomName() {
      return false;
   }

   @Override
   public boolean shouldShowName() {
      return false;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public void push(@NotNull Entity e) {
   }

   @Override
   public ItemStack getItemBySlot(EquipmentSlot s) {
      return ItemStack.EMPTY;
   }

   @Override
   public HumanoidArm getMainArm() {
      return HumanoidArm.RIGHT;
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
