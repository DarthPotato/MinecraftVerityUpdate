package varmite.verity.types;

public enum AiModelPreset {
   SMART,
   FAST;

   public String getModel(AiProvider provider) {
      return switch (this) {
         case SMART -> {
            switch (provider) {
               case OPENAI:
                  yield "gpt-5.4-mini";
               case GEMINI:
                  yield "gemini-2.5-flash";
               case GROQ:
               case OPENROUTER:
                  yield "openai/gpt-oss-120b";
               case MISTRAL:
                  yield "mistral-small-2603";
               case OLLAMA:
                  throw new IllegalArgumentException("Ollama requires a custom model name.");
               default:
                  throw new IncompatibleClassChangeError();
            }
         }
         case FAST -> {
            switch (provider) {
               case OPENAI:
                  yield "gpt-5.4-nano";
               case GEMINI:
                  yield "gemini-2.5-flash-lite";
               case GROQ:
               case OPENROUTER:
                  yield "openai/gpt-oss-20b";
               case MISTRAL:
                  yield "ministral-3b-2512";
               case OLLAMA:
                  throw new IllegalArgumentException("Ollama requires a custom model name.");
               default:
                  throw new IncompatibleClassChangeError();
            }
         }
      };
   }

   public static String resolve(AiProvider provider, AiModelPreset preset, boolean useCustomModel, String customModel) {
      if (provider != AiProvider.OLLAMA && !useCustomModel) {
         return preset.getModel(provider);
      } else if (customModel != null && !customModel.isBlank()) {
         return customModel.trim();
      } else {
         throw new IllegalStateException("Enter a custom AI model for " + provider + ".");
      }
   }
}
