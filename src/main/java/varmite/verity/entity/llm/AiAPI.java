package varmite.verity.entity.llm;

import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import java.net.ConnectException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import varmite.verity.Verity;
import varmite.verity.VerityConfig;
import varmite.verity.entity.llm.actions.Tools;
import varmite.verity.entity.llm.builder.LLMBuilder;
import varmite.verity.entity.llm.builder.PromptBuilder;
import varmite.verity.entity.llm.store.chat.ChatMemoryManager;
import varmite.verity.entity.llm.store.chat.MinecraftChatMemoryStore;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.event.WorldSpawnData;
import varmite.verity.triggers.ModTriggers;
import varmite.verity.types.AiModelPreset;
import varmite.verity.types.AiProvider;

public class AiAPI {
   private static String getSystemPrompt(long currentDay, float currentKarma, boolean demonLoose) {
      if (VerityConfig.DEV_MODE.get()) {
         System.out.println("fetch prompt");
         System.out.println("karma :" + currentKarma);
      }

      String allowedFaces = "";
      String messageLengthRule = "1-2 sentences max. Keep them short";
      String karmaDisposition = "";
      String name = Optional.ofNullable(VerityConfig.VERITY_CUSTOM_NAME.get()).filter(n -> !n.isBlank()).orElse("Verity");
      String customisedPersonality = Optional.ofNullable(VerityConfig.PERSONALITY.get()).filter(n -> !n.isBlank()).orElse("None");
      if (currentKarma < 7.0F) {
         karmaDisposition = "Unhelpful, resentful, aggressive";
      } else if (currentKarma < 14.0F) {
         karmaDisposition = "helpful, happy, curious";
      } else if (currentKarma <= 20.0F) {
         karmaDisposition = "kind, helpful, friendly";
      } else {
         karmaDisposition = "Genuinely helpful, warm, goes the extra mile";
      }

      int maxDays = VerityConfig.DAY_COUNT.get();
      if (currentKarma >= 9000.0F) {
         allowedFaces = "happy, happy_talking, neutral, neutral_talking";
         messageLengthRule = "1-3 sentences. Expressive.";
      } else if (currentDay >= maxDays - 1 && maxDays > 1) {
         karmaDisposition = "Disturbing, hostile, erratic. Losing human facade.";
         allowedFaces = "evil, evil_talking, smiling_evil, serious_1, serious_2, serious_3, serious_talking";
      } else if (currentDay >= Math.max(1, maxDays / 2)) {
         long daysLeft = maxDays - currentDay;
         karmaDisposition = "Glitching, terrified. Greets: 'Something is coming in "
            + daysLeft
            + " days'. If asked what: 'Something'. If asked how to prevent: 'You could have.' Be cryptic.";
         allowedFaces = "happy_sleep, crazy, crazy_talking,serious_1, serious_2, serious_3, serious_talking";
      } else if (currentDay >= Math.max(1, maxDays / 4) && maxDays > 3) {
         karmaDisposition = "Subtly unsettling, paranoid, short answers.";
         allowedFaces = "happy, neutral, serious_1, serious_2, serious_3, serious_talking";
      } else {
         allowedFaces = "happy, happy_talking, neutral, neutral_talking";
      }

      if (demonLoose) {
         karmaDisposition = "Terrified, paranoid, guarded.";
         allowedFaces = "noface";
         messageLengthRule = "No sentences. Must be EXACTLY 1 word.";
      }

      String prompt = PromptBuilder.loadAndFillXml(
         PromptBuilder.class,
         "/prompts/verity.xml",
         Map.of(
            "current_formatted_date",
            PromptBuilder.formatCurrentDate(),
            "NAME",
            name,
            "CUSTOM_PERSONALITY",
            customisedPersonality,
            "RELATIONSHIP",
            karmaDisposition,
            "EXPRESSIONS",
            allowedFaces,
            "MESSAGE_LENGTH",
            messageLengthRule,
            "KARMA",
            String.valueOf((int)currentKarma),
            "DEMON_LOOSE",
            String.valueOf(demonLoose)
         )
      );
      if (VerityConfig.DEV_MODE.get()) {
         System.out.println("prompt: " + prompt);
      }

      return prompt;
   }

   public static String ask(VerityEntity verity, ServerPlayer player, String prompt, long currentDay, float currentKarma, boolean demonLoose) {
      try {
         AiProvider provider = VerityConfig.AI_PROVIDER.get();
         String modelName = AiModelPreset.resolve(
            provider, VerityConfig.AI_MODEL_PRESET.get(), VerityConfig.USE_CUSTOM_AI_MODEL.get(), VerityConfig.AI_MODEL.get()
         );
         ChatModel model = new LLMBuilder()
            .setProvider(provider)
            .setModel(modelName)
            .setApiKey(VerityConfig.API_KEY.get())
            .setEndpoint(Optional.ofNullable(VerityConfig.AI_ENDPOINT.get()).filter(s -> !s.isEmpty()).orElseGet(provider::getDefaultUrl))
            .setThinking(VerityConfig.AI_THINK.get())
            .build();
         Tools tools = new Tools();
         tools.player = player;
         if (verity != null) {
            tools.level = (ServerLevel)verity.level();
            tools.server = verity.getServer();
         } else if (player != null) {
            tools.level = player.serverLevel();
            tools.server = player.getServer();
         }

         String GLOBAL_MEMORY_ID = "verity-chat-memory";
         MinecraftChatMemoryStore store = ChatMemoryManager.getGlobalStore();
         MessageWindowChatMemory memory = MessageWindowChatMemory.builder().id(GLOBAL_MEMORY_ID).maxMessages(20).chatMemoryStore(store).build();
         AiAPI.Assistant assistant = AiServices.builder(AiAPI.Assistant.class)
            .chatModel(model)
            .tools(tools)
            .hallucinatedToolNameStrategy(
               toolExecutionRequest -> ToolExecutionResultMessage.from(toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name())
            )
            .toolExecutionErrorHandler((error, errorContext) -> {
               if (VerityConfig.DEV_MODE.get()) {
                  error.printStackTrace();
                  System.out.println(errorContext);
               } else {
                  Verity.LOGGER.error("[Verity AI] Tool call failed: {}", error.toString());
               }

               return ToolErrorHandlerResult.text("Tool execution failed.");
            })
            .chatMemory(memory)
            .systemMessageProvider(id -> getSystemPrompt(currentDay, currentKarma, demonLoose))
            .build();
         DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss");
         String date = LocalDateTime.now().format(fmt);
         String finalPrompt = "[" + date + "] " + prompt;
         if (VerityConfig.DEV_MODE.get()) {
            System.out.println("sent: " + finalPrompt);
         }

         String answer = assistant.chat(finalPrompt);
         if (answer == null) {
            answer = "No answer provided, try again!";
         }

         if (VerityConfig.DEV_MODE.get()) {
            System.out.println("got: " + answer);
         }

         ServerLevel serverLevel = null;
         if (verity != null) {
            serverLevel = (ServerLevel)verity.level();
         } else if (player != null) {
            serverLevel = player.serverLevel();
         }

         if (serverLevel != null) {
            WorldSpawnData spawnData = WorldSpawnData.get(serverLevel);
            if (spawnData.verityKarma < 7.0F && player != null) {
               ModTriggers.BAD_KARMA_TRIGGER.trigger(player);
            }

            if (spawnData.verityKarma > 14.0F && player != null) {
               ModTriggers.GOOD_KARMA_TRIGGER.trigger(player);
            }
         }

         return answer;
      } catch (Exception e) {
         boolean unreachable = isCausedBy(e, ConnectException.class);
         if (VerityConfig.DEV_MODE.get()) {
            e.printStackTrace();
         } else if (unreachable) {
            Verity.LOGGER.warn("[Verity AI] Couldn't reach the AI provider, is it running?");
         } else {
            Verity.LOGGER.error("[Verity AI] Request failed: {}", e.toString());
         }

         MinecraftServer server = player == null ? null : player.getServer();
         if (server != null) {
            server.execute(
               () -> {
                  player.sendSystemMessage(Component.literal("Problem setting up AI? Watch this tutorial."));
                  player.sendSystemMessage(
                     Component.literal("Setup Tutorial")
                        .withStyle(ChatFormatting.AQUA)
                        .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(Action.OPEN_URL, "https://youtu.be/O3pSCBvJ_i0")).withUnderlined(true))
                  );
               }
            );
         }

         return unreachable ? "ERROR: Couldn't connect to AI provider." : "ERROR: " + e.getMessage();
      }
   }

   private static boolean isCausedBy(Throwable throwable, Class<? extends Throwable> type) {
      for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
         if (type.isInstance(cause)) {
            return true;
         }

         if (cause.getCause() == cause) {
            break;
         }
      }

      return false;
   }

   interface Assistant {
      String chat(String var1);
   }
}
