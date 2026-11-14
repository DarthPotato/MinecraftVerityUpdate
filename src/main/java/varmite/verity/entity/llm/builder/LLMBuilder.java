package varmite.verity.entity.llm.builder;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel.Builder;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.model.ollama.OllamaChatModel.OllamaChatModelBuilder;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder;
import java.time.Duration;
import java.util.Map;
import varmite.verity.VerityConfig;
import varmite.verity.types.AiProvider;

public class LLMBuilder {
   private AiProvider provider;
   private String model;
   private String endpoint;
   private String apiKey;
   private boolean thinking = false;
   private Boolean ollamaThink;
   private int contextSize = 8192;

   public LLMBuilder setProvider(AiProvider provider) {
      this.provider = provider;
      return this;
   }

   public LLMBuilder setModel(String model) {
      this.model = model;
      return this;
   }

   public LLMBuilder setEndpoint(String endpoint) {
      this.endpoint = endpoint == null ? null : endpoint.replace("localhost", "127.0.0.1");
      return this;
   }

   public LLMBuilder setApiKey(String apiKey) {
      this.apiKey = apiKey;
      return this;
   }

   public LLMBuilder setThinking(boolean thinking) {
      this.thinking = thinking;
      return this;
   }

   public LLMBuilder setOllamaThink(Boolean think) {
      this.ollamaThink = think;
      return this;
   }

   public LLMBuilder setContextSize(int contextSize) {
      this.contextSize = contextSize;
      return this;
   }

   public ChatModel build() {
      return (ChatModel)(switch (this.provider) {
         case OLLAMA -> ((OllamaChatModelBuilder)((OllamaChatModelBuilder)((OllamaChatModelBuilder)((OllamaChatModelBuilder)((OllamaChatModelBuilder)((OllamaChatModelBuilder)((OllamaChatModelBuilder)((OllamaChatModelBuilder)OllamaChatModel.builder()
                                    .baseUrl(this.endpoint))
                                 .modelName(this.model))
                              .numCtx(this.contextSize))
                           .numPredict(Boolean.TRUE.equals(this.ollamaThink) ? 3072 : 1024))
                        .think(this.ollamaThink))
                     .timeout(Duration.ofMinutes(5L)))
                  .maxRetries(0)
                  .defaultRequestParameters(OllamaChatRequestParameters.builder().keepAlive(1800).build()))
               .logResponses((Boolean)VerityConfig.DEV_MODE.get()))
            .build();
         case OPENAI -> {
            OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
               .baseUrl(this.endpoint)
               .apiKey(this.apiKey)
               .modelName(this.model)
               .returnThinking(this.thinking)
               .logResponses((Boolean)VerityConfig.DEV_MODE.get());
            if (this.isOpenAiReasoningModel()) {
               builder.reasoningEffort(this.thinking ? "medium" : "none");
            } else {
               builder.temperature(0.8);
            }

            yield builder.build();
         }
         case GEMINI -> {
            Builder builder = GoogleGenAiChatModel.builder()
               .apiKey(this.apiKey)
               .modelName(this.model)
               .temperature(0.8)
               .logResponses((Boolean)VerityConfig.DEV_MODE.get());
            if (this.model.startsWith("gemini-3.5-")) {
               builder.thinkingBudget(this.thinking ? -1 : 0);
            }

            yield builder.build();
         }
         case GROQ -> {
            OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
               .baseUrl("https://api.groq.com/openai/v1/")
               .apiKey(this.apiKey)
               .modelName(this.model)
               .returnThinking(this.thinking)
               .temperature(0.8)
               .logResponses((Boolean)VerityConfig.DEV_MODE.get());
            if (this.isGptOssModel()) {
               builder.reasoningEffort(this.thinking ? "medium" : "low").customParameters(Map.of("reasoning_format", "hidden"));
            }

            yield builder.build();
         }
         case OPENROUTER -> {
            OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
               .baseUrl("https://openrouter.ai/api/v1/")
               .apiKey(this.apiKey)
               .modelName(this.model)
               .returnThinking(this.thinking)
               .temperature(0.8)
               .logResponses((Boolean)VerityConfig.DEV_MODE.get());
            if (this.isGptOssModel()) {
               builder.customParameters(Map.of("reasoning", Map.of("effort", this.thinking ? "medium" : "low", "exclude", true)));
            }

            yield builder.build();
         }
         case MISTRAL -> MistralAiChatModel.builder()
            .apiKey(this.apiKey)
            .modelName(this.model)
            .temperature(0.8)
            .logResponses((Boolean)VerityConfig.DEV_MODE.get())
            .build();
      });
   }

   private boolean isOpenAiReasoningModel() {
      return this.model.startsWith("gpt-5") || this.model.startsWith("o1") || this.model.startsWith("o3") || this.model.startsWith("o4");
   }

   private boolean isGptOssModel() {
      return this.model.startsWith("openai/gpt-oss-");
   }
}
