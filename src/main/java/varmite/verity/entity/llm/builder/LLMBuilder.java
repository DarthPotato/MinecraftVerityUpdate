package varmite.verity.entity.llm.builder;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel.OllamaChatModelBuilder;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import varmite.verity.VerityConfig;

/**
 * Builds the chat model from the free-form endpoint, API key and model in the config. Every
 * endpoint is treated as OpenAI-compatible, with one exception: when the endpoint turns out to
 * be an Ollama server, the request goes through Ollama's native API on the same server, because
 * its OpenAI-compatible route cannot set the context window and Verity's prompt plus tools need
 * more than Ollama's default.
 */
public class LLMBuilder {
   private static final Map<String, Boolean> OLLAMA_ENDPOINTS = new ConcurrentHashMap<>();
   private String model;
   private String endpoint;
   private String apiKey;
   private boolean thinking = false;

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

   public ChatModel build() {
      if (isOllama(this.endpoint)) {
         return ((OllamaChatModelBuilder)((OllamaChatModelBuilder)((OllamaChatModelBuilder)((OllamaChatModelBuilder)((OllamaChatModelBuilder)((OllamaChatModelBuilder)OllamaChatModel.builder()
                           .baseUrl(serverRoot(this.endpoint)))
                        .modelName(this.model))
                     .numCtx(16000))
                  .think(this.thinking))
               .temperature(0.8))
            .logResponses(VerityConfig.DEV_MODE.get()))
            .build();
      }

      OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
         .baseUrl(this.endpoint)
         .modelName(this.model)
         .returnThinking(this.thinking)
         .logResponses(VerityConfig.DEV_MODE.get());
      if (this.apiKey != null && !this.apiKey.isBlank()) {
         builder.apiKey(this.apiKey);
      }

      String host = host(this.endpoint);
      if (host.endsWith("groq.com")) {
         builder.temperature(0.8);
         if (this.isGptOssModel()) {
            builder.reasoningEffort(this.thinking ? "medium" : "low").customParameters(Map.of("reasoning_format", "hidden"));
         }
      } else if (host.endsWith("openrouter.ai")) {
         builder.temperature(0.8);
         if (this.isGptOssModel()) {
            builder.customParameters(Map.of("reasoning", Map.of("effort", this.thinking ? "medium" : "low", "exclude", true)));
         }
      } else if (this.isOpenAiReasoningModel()) {
         builder.reasoningEffort(this.thinking ? "medium" : "none");
      } else {
         builder.temperature(0.8);
      }

      return builder.build();
   }

   private boolean isOpenAiReasoningModel() {
      return this.model.startsWith("gpt-5") || this.model.startsWith("o1") || this.model.startsWith("o3") || this.model.startsWith("o4");
   }

   private boolean isGptOssModel() {
      return this.model.startsWith("openai/gpt-oss-");
   }

   private static String host(String endpoint) {
      try {
         String host = URI.create(endpoint.trim()).getHost();
         return host == null ? "" : host.toLowerCase(Locale.ROOT);
      } catch (RuntimeException e) {
         return "";
      }
   }

   /** The endpoint without a trailing slash or "/v1": where Ollama's native API lives. */
   private static String serverRoot(String endpoint) {
      String root = endpoint.trim();

      while (root.endsWith("/")) {
         root = root.substring(0, root.length() - 1);
      }

      return root.endsWith("/v1") ? root.substring(0, root.length() - 3) : root;
   }

   /** Asks the server for Ollama's version route. The answer is cached once the server has replied. */
   private static boolean isOllama(String endpoint) {
      Boolean known = OLLAMA_ENDPOINTS.get(endpoint);
      if (known != null) {
         return known;
      }

      try {
         HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(serverRoot(endpoint) + "/api/version"))
            .timeout(Duration.ofSeconds(2L))
            .GET()
            .build();
         HttpResponse<String> response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2L))
            .build()
            .send(request, BodyHandlers.ofString());
         boolean ollama = response.statusCode() == 200 && response.body().contains("\"version\"");
         OLLAMA_ENDPOINTS.put(endpoint, ollama);
         return ollama;
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return false;
      } catch (Exception e) {
         return false;
      }
   }
}
