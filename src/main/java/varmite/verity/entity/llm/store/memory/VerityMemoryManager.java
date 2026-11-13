package varmite.verity.entity.llm.store.memory;

import java.io.File;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public class VerityMemoryManager {
   private static VerityMemoryStore INSTANCE;

   public static void init(MinecraftServer server) {
      File serverDir = server.getWorldPath(LevelResource.ROOT).toFile();
      INSTANCE = new VerityMemoryStore(serverDir);
   }

   public static VerityMemoryStore get() {
      if (INSTANCE == null) {
         throw new IllegalStateException("ServerMemoryManager not initialized!");
      } else {
         return INSTANCE;
      }
   }
}
