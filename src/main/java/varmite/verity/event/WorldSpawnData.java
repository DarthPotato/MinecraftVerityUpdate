package varmite.verity.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class WorldSpawnData extends SavedData {
   public float verityKarma = 0.0F;
   /** Port: karma per player (see Karma); verityKarma is the starting value for players without one. */
   public final Map<UUID, Float> playerKarma = new HashMap<>();
   public boolean hasSpawnedEntity = false;
   public boolean hasProvokedSpawn = false;
   public boolean hasHostileDaySpawn = false;
   public boolean hostileResolved = false;
   public UUID activeDemon = null;
   public final ListTag chatHistory = new ListTag();

   public float karmaOf(UUID player) {
      return this.playerKarma.getOrDefault(player, this.verityKarma);
   }

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
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      tag.putBoolean("hasSpawnedEntity", this.hasSpawnedEntity);
      tag.putFloat("verityKarma", this.verityKarma);
      CompoundTag karma = new CompoundTag();
      this.playerKarma.forEach((id, value) -> karma.putFloat(id.toString(), value));
      tag.put("playerKarma", karma);
      tag.putBoolean("hasProvokedSpawn", this.hasProvokedSpawn);
      tag.putBoolean("hasHostileDaySpawn", this.hasHostileDaySpawn);
      tag.putBoolean("hostileResolved", this.hostileResolved);
      if (this.activeDemon != null) {
         tag.putUUID("activeDemon", this.activeDemon);
      }

      return tag;
   }

   public static WorldSpawnData get(ServerLevel level) {
      return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(WorldSpawnData::new, WorldSpawnData::load), "verity_world_data");
   }

   public static WorldSpawnData load(CompoundTag tag, HolderLookup.Provider registries) {
      WorldSpawnData data = new WorldSpawnData();
      data.hasSpawnedEntity = tag.getBoolean("hasSpawnedEntity");
      data.hasProvokedSpawn = tag.getBoolean("hasProvokedSpawn");
      data.hasHostileDaySpawn = tag.getBoolean("hasHostileDaySpawn");
      data.hostileResolved = tag.getBoolean("hostileResolved");
      if (tag.hasUUID("activeDemon")) {
         data.activeDemon = tag.getUUID("activeDemon");
      }

      if (tag.contains("verityKarma")) {
         data.verityKarma = tag.getFloat("verityKarma");
      }

      CompoundTag karma = tag.getCompound("playerKarma");
      for (String key : karma.getAllKeys()) {
         try {
            data.playerKarma.put(UUID.fromString(key), karma.getFloat(key));
         } catch (IllegalArgumentException ignored) {
         }
      }

      return data;
   }
}
