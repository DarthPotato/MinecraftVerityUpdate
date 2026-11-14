package varmite.verity.ollama;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OllamaContext {
   private static final int[] SIZES = new int[]{8192, 12288, 16384, 24576, 32768};
   private static final double CHARS_PER_TOKEN = 3.5;
   private static final Map<String, Integer> CHOSEN = new ConcurrentHashMap<>();

   private OllamaContext() {
   }

   public static int contextFor(String model, long promptChars, boolean thinking) {
      long needed = (long)(promptChars / 3.5) + (thinking ? 1536 : 768);
      int size = SIZES[SIZES.length - 1];

      for (int candidate : SIZES) {
         if (candidate >= needed) {
            size = candidate;
            break;
         }
      }

      return CHOSEN.merge(OllamaModels.withTag(model), size, Math::max);
   }

   public static int current(String model) {
      return CHOSEN.getOrDefault(OllamaModels.withTag(model), SIZES[0]);
   }
}
