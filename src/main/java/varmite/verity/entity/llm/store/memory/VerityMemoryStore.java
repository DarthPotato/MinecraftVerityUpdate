package varmite.verity.entity.llm.store.memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class VerityMemoryStore {
   private static final String FILE_NAME = "verity_memory.json";
   private final File serverDataDir;
   private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
   private final Map<String, String> memories = new ConcurrentHashMap<>();

   public VerityMemoryStore(File serverDir) {
      this.serverDataDir = serverDir;
      if (!this.serverDataDir.exists()) {
         this.serverDataDir.mkdirs();
      }

      this.load();
   }

   public void save() {
      try {
         File file = new File(this.serverDataDir, "verity_memory.json");
         JsonObject root = new JsonObject();
         this.memories.forEach(root::addProperty);
         Files.writeString(file.toPath(), this.gson.toJson(root));
      } catch (IOException e) {
         System.err.println("Failed to save VerityMemoryStore: " + e.getMessage());
      }
   }

   public void load() {
      try {
         File file = new File(this.serverDataDir, "verity_memory.json");
         if (!file.exists()) {
            return;
         }

         String json = Files.readString(file.toPath());
         JsonObject root = JsonParser.parseString(json).getAsJsonObject();
         root.entrySet().forEach(entry -> this.memories.put((String)entry.getKey(), ((JsonElement)entry.getValue()).getAsString()));
      } catch (Exception e) {
         System.err.println("Failed to load VerityMemoryStore, starting empty: " + e);
         this.memories.clear();
      }
   }

   public void addMemory(String id, String text) {
      this.memories.put(id, text);
      this.save();
   }

   public String getMemory(String id) {
      return this.memories.get(id);
   }

   public void removeMemory(String id) {
      this.memories.remove(id);
      this.save();
   }

   public Set<String> getAllKeys() {
      return new HashSet<>(this.memories.keySet());
   }

   public Map<String, String> getAllMemories() {
      return new ConcurrentHashMap<>(this.memories);
   }
}
