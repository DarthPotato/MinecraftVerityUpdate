package varmite.verity.entity.llm.builder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Map.Entry;

public final class PromptBuilder {
   public static String loadAndFillXml(Class<?> anchorClass, String resourcePath, Map<String, String> fillerValues) {
      String out = readResourceAsString(anchorClass, resourcePath);

      for (Entry<String, String> e : fillerValues.entrySet()) {
         out = out.replace("{" + e.getKey() + "}", e.getValue());
      }

      return out;
   }

   private static String readResourceAsString(Class<?> anchorClass, String resourcePath) {
      try (InputStream is = anchorClass.getResourceAsStream(resourcePath)) {
         if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + resourcePath);
         }

         byte[] bytes = is.readAllBytes();
         return new String(bytes, StandardCharsets.UTF_8);
      } catch (Exception ex) {
         throw new RuntimeException("Failed to load resource: " + resourcePath, ex);
      }
   }

   public static String formatCurrentDate() {
      DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss");
      return LocalDateTime.now().format(fmt);
   }
}
