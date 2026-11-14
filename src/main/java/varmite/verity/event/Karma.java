package varmite.verity.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import javax.annotation.Nullable;
import varmite.verity.network.KarmaSyncS2CPacket;
import varmite.verity.network.ModMessages;

/**
 * Port (owner's choice, for multiplayer): Verity's karma is kept per player instead of one value for the world. What a
 * player says and does changes their own karma, and his mood, item refusals, secrets and the karma bar follow the karma
 * of the player in question. Things nobody in particular did go to the nearest player (burning) or everyone (being left
 * alone, a fresh start). {@link WorldSpawnData#verityKarma} is the starting value for players without their own yet, so
 * existing worlds start everyone at the old shared value.
 */
public final class Karma {
   private static final Map<UUID, Integer> LAST_SYNCED = new HashMap<>();

   private Karma() {
   }

   /** The player's karma; without a player (Verity talking on his own), the average over the players online. */
   public static float get(ServerLevel level, @Nullable Player player) {
      WorldSpawnData data = WorldSpawnData.get(level);
      if (player != null) {
         return data.karmaOf(player.getUUID());
      }

      List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
      if (players.isEmpty()) {
         return data.verityKarma;
      }

      float sum = 0.0F;
      for (ServerPlayer p : players) {
         sum += data.karmaOf(p.getUUID());
      }

      return sum / players.size();
   }

   public static void add(ServerLevel level, @Nullable Player player, float amount) {
      if (player != null) {
         WorldSpawnData data = WorldSpawnData.get(level);
         put(level, data, player.getUUID(), data.karmaOf(player.getUUID()) + amount);
      }
   }

   public static void set(ServerLevel level, @Nullable Player player, float value) {
      if (player != null) {
         put(level, WorldSpawnData.get(level), player.getUUID(), value);
      }
   }

   /** Everyone online, for example when they all left Verity alone. */
   public static void addAll(ServerLevel level, float amount) {
      for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
         add(level, p, amount);
      }
   }

   /** A fresh start for everyone (the box at the start, the demon defeated); also the value new players start with. */
   public static void setAll(ServerLevel level, float value) {
      WorldSpawnData data = WorldSpawnData.get(level);
      float clamped = Mth.clamp(value, 0.0F, 20.0F);
      data.verityKarma = clamped;
      data.playerKarma.replaceAll((id, old) -> clamped);
      for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
         data.playerKarma.put(p.getUUID(), clamped);
      }

      data.setDirty();
      syncAll(level.getServer());
   }

   private static void put(ServerLevel level, WorldSpawnData data, UUID id, float value) {
      data.playerKarma.put(id, Mth.clamp(value, 0.0F, 20.0F));
      data.setDirty();
      ServerPlayer online = level.getServer().getPlayerList().getPlayer(id);
      if (online != null) {
         sync(online);
      }
   }

   /** Sends the player their own karma for the karma bar. */
   public static void sync(ServerPlayer player) {
      int karma = (int)WorldSpawnData.get(player.serverLevel()).karmaOf(player.getUUID());
      LAST_SYNCED.put(player.getUUID(), karma);
      ModMessages.sendToPlayer(new KarmaSyncS2CPacket(karma), player);
   }

   public static void syncAll(MinecraftServer server) {
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         sync(p);
      }
   }

   /** Called once a second: resends to anyone whose karma changed since it was last sent. */
   public static void syncChanged(MinecraftServer server) {
      WorldSpawnData data = WorldSpawnData.get(server.overworld());
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         Integer last = LAST_SYNCED.get(p.getUUID());
         if (last == null || last != (int)data.karmaOf(p.getUUID())) {
            sync(p);
         }
      }
   }
}
