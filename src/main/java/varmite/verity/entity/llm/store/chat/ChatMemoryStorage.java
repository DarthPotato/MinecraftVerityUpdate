package varmite.verity.entity.llm.store.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatMemoryStorage {
   private static final String FILE_NAME = "verity_chat_memory.json";
   private final File storageDir;
   private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
   private final Map<String, String> memoryStore = new ConcurrentHashMap<>();

   public ChatMemoryStorage(File dataDir) {
      this.storageDir = dataDir;
      if (!this.storageDir.exists()) {
         this.storageDir.mkdirs();
      }

      this.load();
   }

   public void save() {
      try {
         File file = new File(this.storageDir, "verity_chat_memory.json");
         JsonObject root = new JsonObject();
         this.memoryStore.forEach((id, messagesJson) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id);
            entry.addProperty("messages_json", messagesJson);
            root.add(id, entry);
         });
         Files.writeString(file.toPath(), this.gson.toJson(root));
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public void load() {
      try {
         File file = new File(this.storageDir, "verity_chat_memory.json");
         if (!file.exists()) {
            return;
         }

         String json = Files.readString(file.toPath());
         JsonObject root = JsonParser.parseString(json).getAsJsonObject();
         root.entrySet().forEach(entry -> {
            JsonObject obj = ((JsonElement)entry.getValue()).getAsJsonObject();
            this.memoryStore.put(obj.get("id").getAsString(), obj.get("messages_json").getAsString());
         });
      } catch (Exception e) {
         System.err.println("[Verity] Couldn't read verity_chat_memory.json, starting from an empty chat memory: " + e);
         this.memoryStore.clear();
      }
   }

   public String getMessagesJson(String memoryId) {
      return this.memoryStore.getOrDefault(memoryId, "[]");
   }

   public void updateMessagesJson(String memoryId, String messagesJson) {
      this.memoryStore.put(memoryId, messagesJson);
      this.save();
   }

   public void deleteMessagesJson(String memoryId) {
      this.memoryStore.remove(memoryId);
      this.save();
   }

   public Map<String, String> getAllMemories() {
      return new HashMap<>(this.memoryStore);
   }
}
