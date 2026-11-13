package varmite.verity.entity.llm.store.chat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public class ChatMemoryManager {
   private static ChatMemoryStorage GLOBAL_STORAGE;

   public static void init(MinecraftServer server) {
      GLOBAL_STORAGE = new ChatMemoryStorage(server.getWorldPath(LevelResource.ROOT).toFile());
   }

   public static ChatMemoryStorage getGlobalStorage() {
      if (GLOBAL_STORAGE == null) {
         throw new IllegalStateException("ChatMemoryManager not initialized! Call init() on ServerStartingEvent");
      } else {
         return GLOBAL_STORAGE;
      }
   }

   public static MinecraftChatMemoryStore getGlobalStore() {
      return new MinecraftChatMemoryStore(getGlobalStorage());
   }
}
