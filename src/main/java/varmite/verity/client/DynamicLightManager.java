package varmite.verity.client;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.phys.Vec3;

public class DynamicLightManager {
   private static final AtomicReference<List<DynamicLightManager.Beam>> CURRENT = new AtomicReference<>(Collections.emptyList());

   public static void updateBeams(List<DynamicLightManager.Beam> newBeams) {
      CURRENT.set(newBeams == null ? Collections.emptyList() : List.copyOf(newBeams));
   }

   public record Beam(Vec3 start, Vec3 end, UUID owner) {
   }
}
