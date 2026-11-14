package varmite.verity.event;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class WorldSpawnData extends SavedData {
   /** Same field names as before; the tag is just wrapped in a codec, which is what saved data uses now. */
   public static final Codec<WorldSpawnData> CODEC = CompoundTag.CODEC.xmap(WorldSpawnData::load, WorldSpawnData::save);
   public static final SavedDataType<WorldSpawnData> TYPE = new SavedDataType<>(
      Identifier.fromNamespaceAndPath("verity", "verity_world_data"), WorldSpawnData::new, CODEC
   );
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

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putBoolean("hasSpawnedEntity", this.hasSpawnedEntity);
      tag.putFloat("verityKarma", this.verityKarma);
      CompoundTag karma = new CompoundTag();
      this.playerKarma.forEach((id, value) -> karma.putFloat(id.toString(), value));
      tag.put("playerKarma", karma);
      tag.putBoolean("hasProvokedSpawn", this.hasProvokedSpawn);
      tag.putBoolean("hasHostileDaySpawn", this.hasHostileDaySpawn);
      tag.putBoolean("hostileResolved", this.hostileResolved);
      if (this.activeDemon != null) {
         tag.store("activeDemon", UUIDUtil.CODEC, this.activeDemon);
      }

      return tag;
   }

   public static WorldSpawnData get(ServerLevel level) {
      return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
   }

   public static WorldSpawnData load(CompoundTag tag) {
      WorldSpawnData data = new WorldSpawnData();
      data.hasSpawnedEntity = tag.getBooleanOr("hasSpawnedEntity", false);
      data.hasProvokedSpawn = tag.getBooleanOr("hasProvokedSpawn", false);
      data.hasHostileDaySpawn = tag.getBooleanOr("hasHostileDaySpawn", false);
      data.hostileResolved = tag.getBooleanOr("hostileResolved", false);
      data.activeDemon = tag.read("activeDemon", UUIDUtil.CODEC).orElse(null);
      data.verityKarma = tag.getFloatOr("verityKarma", data.verityKarma);
      CompoundTag karma = tag.getCompoundOrEmpty("playerKarma");
      for (String key : karma.keySet()) {
         try {
            data.playerKarma.put(UUID.fromString(key), karma.getFloatOr(key, data.verityKarma));
         } catch (IllegalArgumentException ignored) {
         }
      }
      return data;
   }
}
