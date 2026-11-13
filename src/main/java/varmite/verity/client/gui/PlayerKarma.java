package varmite.verity.client.gui;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

public class PlayerKarma implements ValueIOSerializable {
   private int karma;
   private final int MIN_KARMA = 0;
   private final int MAX_KARMA = 20;

   public int getKarma() {
      return this.karma;
   }

   public void addKarma(int amount) {
      this.karma = Math.min(this.karma + amount, 20);
   }

   public void subKarma(int amount) {
      this.karma = Math.max(this.karma - amount, 0);
   }

   public void setKarma(int amount) {
      this.karma = Math.max(0, Math.min(amount, 20));
   }

   public void copyFrom(PlayerKarma source) {
      this.karma = source.karma;
   }

   @Override
   public void serialize(ValueOutput output) {
      output.putInt("player_karma", this.karma);
   }

   @Override
   public void deserialize(ValueInput input) {
      this.karma = input.getIntOr("player_karma", 0);
   }
}
