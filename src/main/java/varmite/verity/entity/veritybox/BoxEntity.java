package varmite.verity.entity.veritybox;

import java.util.Collections;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import varmite.verity.environment.sounds.ModSounds;
import varmite.verity.triggers.ModTriggers;

public class BoxEntity extends LivingEntity implements GeoEntity {
   private static final Logger log = LoggerFactory.getLogger(BoxEntity.class);
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private int soundEffectTimer = 0;
   private int randInt = 0;
   private Player p;
   private boolean isOpened = false;
   public static final EntityDataAccessor<Boolean> HAS_CLICKED = SynchedEntityData.defineId(BoxEntity.class, EntityDataSerializers.BOOLEAN);

   public BoxEntity(EntityType<? extends LivingEntity> entityType, Level level) {
      super(entityType, level);
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(HAS_CLICKED, false);
   }

   @Override
   public void tick() {
      if (!this.level().isClientSide()) {
         if (this.soundEffectTimer < 60) {
            this.soundEffectTimer++;
         } else if (!this.isOpened) {
            this.soundEffectTimer = 0;
            if (this.randInt == 0) {
               this.playSound(ModSounds.BOX_VERITY_0.get(), 4.0F, 1.0F);
            } else if (this.randInt == 1) {
               this.playSound(ModSounds.BOX_VERITY_1.get(), 4.0F, 1.0F);
            } else {
               this.playSound(ModSounds.BOX_VERITY_2.get(), 4.0F, 1.0F);
            }

            this.randInt++;
            if (this.randInt > 2) {
               this.randInt = 0;
            }
         }
      }

      super.tick();
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
   public void push(@NotNull Entity entity) {
   }

   public static Builder createAttributes() {
      return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0);
   }

   public void playDelayedSound(Player player, SoundEvent soundEvent, BlockPos blockPos, SoundSource source, float volume, float pitch) {
      int delayTicks = -1;
   }

   public void triggerOpen() {
      this.triggerAnim("action_controller", "open");
      MinecraftServer server = this.getServer();

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         ModTriggers.UNBOX_VERITY_TRIGGER.get().trigger(player);
      }

      this.isOpened = true;
   }

   @Override
   public boolean isInvulnerableTo(DamageSource source) {
      return !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL);
   }

   @Override
   public Iterable<ItemStack> getArmorSlots() {
      return Collections.singleton(ItemStack.EMPTY);
   }

   @Override
   public ItemStack getItemBySlot(EquipmentSlot slot) {
      return ItemStack.EMPTY;
   }

   @Override
   public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
   }

   @Override
   public HumanoidArm getMainArm() {
      return HumanoidArm.RIGHT;
   }

   @Override
   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController<>(this, "idle_controller", 5, state -> {
         state.getController().setAnimation(RawAnimation.begin().thenLoop("hover"));
         return PlayState.CONTINUE;
      }));
      AnimationController<BoxEntity> actionController = new AnimationController<>(this, "action_controller", 0, state -> PlayState.STOP);
      actionController.triggerableAnim("open", RawAnimation.begin().thenPlayAndHold("open"));
      controllers.add(actionController);
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
