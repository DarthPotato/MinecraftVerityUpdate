package varmite.verity.client.gui;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerKarmaProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
   public static final Capability<PlayerKarma> PLAYER_KARMA = CapabilityManager.get(new CapabilityToken<PlayerKarma>() {});
   private PlayerKarma karma = null;
   private final LazyOptional<PlayerKarma> optional = LazyOptional.of(this::createPlayerKarma);

   private PlayerKarma createPlayerKarma() {
      if (this.karma == null) {
         this.karma = new PlayerKarma();
      }

      return this.karma;
   }

   @NotNull
   @Override
   public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
      return cap == PLAYER_KARMA ? this.optional.cast() : LazyOptional.empty();
   }

   public CompoundTag serializeNBT() {
      CompoundTag nbt = new CompoundTag();
      this.createPlayerKarma().saveNBTData(nbt);
      return nbt;
   }

   public void deserializeNBT(CompoundTag nbt) {
      this.createPlayerKarma().loadNBTData(nbt);
   }
}
