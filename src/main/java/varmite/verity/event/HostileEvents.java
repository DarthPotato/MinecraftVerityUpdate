package varmite.verity.event;

import net.minecraft.server.level.ServerLevel;
import varmite.verity.VerityConfig;
import varmite.verity.entity.VerityState;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.types.HostileEvent;

/** Shared state checks for the hostile event that replaces the parked demon. It runs once per world. */
public class HostileEvents {
   public static boolean enabled() {
      return VerityConfig.HOSTILE_EVENT.get() != HostileEvent.OFF;
   }

   /** The day-based personality shift and the event itself only run while this is true. */
   public static boolean progressionActive(ServerLevel level) {
      return enabled() && !WorldSpawnData.get(level).hostileResolved;
   }

   /** Ends the event for good and puts Verity back to normal. */
   public static void resolve(ServerLevel level) {
      WorldSpawnData data = WorldSpawnData.get(level);
      data.activeDemon = null;
      data.hostileResolved = true;
      data.setDirty();
      VerityState.transformFollowingDay = false;
      VerityState.timeWillSpawn = 0L;
      VerityEntity verity = VerityState.verityEntity;
      if (verity != null && !verity.isRemoved()) {
         verity.setMonstrous(false);
         verity.setApologyCount(0);
         verity.clearPlayersWhoLooked();
         verity.pleadingTimer = -1;
         verity.setVariant("happy");
      }
   }
}
