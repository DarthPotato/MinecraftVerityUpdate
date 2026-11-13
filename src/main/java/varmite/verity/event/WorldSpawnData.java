package varmite.verity.event;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class WorldSpawnData extends SavedData {
   public float verityKarma = 0.0F;
   public boolean hasSpawnedEntity = false;
   public boolean hasProvokedSpawn = false;
   public boolean hasHostileDaySpawn = false;
   public UUID activeDemon = null;
   public final ListTag chatHistory = new ListTag();

   public boolean hasLiveDemon() {
      return this.activeDemon != null;
   }

   public void setActiveDemon(UUID demon) {
      this.activeDemon = demon;
      this.setDirty();
   }

   public void clearActiveDemon(UUID demon) {
      if (demon.equals(this.activeDemon)) {
         this.activeDemon = null;
         this.setDirty();
      }
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      tag.putBoolean("hasSpawnedEntity", this.hasSpawnedEntity);
      tag.putFloat("verityKarma", this.verityKarma);
      tag.putBoolean("hasProvokedSpawn", this.hasProvokedSpawn);
      tag.putBoolean("hasHostileDaySpawn", this.hasHostileDaySpawn);
      if (this.activeDemon != null) {
         tag.putUUID("activeDemon", this.activeDemon);
      }

      return tag;
   }

   public static WorldSpawnData get(ServerLevel level) {
      return level.getServer().overworld().getDataStorage().computeIfAbsent(WorldSpawnData::load, WorldSpawnData::new, "verity_world_data");
   }

   public static WorldSpawnData load(CompoundTag tag) {
      WorldSpawnData data = new WorldSpawnData();
      data.hasSpawnedEntity = tag.getBoolean("hasSpawnedEntity");
      data.hasProvokedSpawn = tag.getBoolean("hasProvokedSpawn");
      data.hasHostileDaySpawn = tag.getBoolean("hasHostileDaySpawn");
      if (tag.hasUUID("activeDemon")) {
         data.activeDemon = tag.getUUID("activeDemon");
      }

      if (tag.contains("verityKarma")) {
         data.verityKarma = tag.getFloat("verityKarma");
      }

      return data;
   }
}
