package varmite.verity.entity.llm;

import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import java.net.ConnectException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import varmite.verity.Verity;
import varmite.verity.VerityConfig;
import varmite.verity.entity.llm.actions.Tools;
import varmite.verity.entity.llm.builder.LLMBuilder;
import varmite.verity.entity.llm.builder.PromptBuilder;
import varmite.verity.entity.llm.store.chat.ChatMemoryManager;
import varmite.verity.entity.llm.store.chat.MinecraftChatMemoryStore;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.environment.items.ModItems;
import varmite.verity.event.WorldSpawnData;
import varmite.verity.ollama.OllamaApi;
import varmite.verity.ollama.OllamaContext;
import varmite.verity.ollama.OllamaModels;
import varmite.verity.ollama.OllamaRuntime;
import varmite.verity.triggers.ModTriggers;
import varmite.verity.types.AiModelPreset;
import varmite.verity.types.AiProvider;

public class AiAPI {
   private static final String GLOBAL_MEMORY_ID = "verity-chat-memory";
   private static final Pattern EXPRESSION_TAG = Pattern.compile(
      "\\[("
         + VerityEntity.VALID_VARIANTS.stream().sorted(Comparator.comparingInt(String::length).reversed()).map(Pattern::quote).collect(Collectors.joining("|"))
         + ")\\]\\s*",
      2
   );
   private static final Pattern SOUND_TAG = Pattern.compile("\\[sound:\\s*([a-z0-9_.:/-]+)\\s*\\]", 2);
   private static final Pattern THINK_BLOCK = Pattern.compile("(?is)<think>.*?(</think>|$)");
   private static final Pattern ASSISTANT_FILLER = Pattern.compile(
      "(?i)^(?:do you |you )?(?:need|want) anything else\\W*$|^is there anything else\\b.*$|^let me know if you need anything(?: else)?\\W*$|^anything else\\W*$"
   );
   private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");
   private static final Pattern PHONETIC_VARIANTS = Pattern.compile(
      "\\b(verdi|verdy|veriti|veritiy|veritee|veritie|varity|variety|verify|ferity|parity|very\\s+tea|where\\s+are\\s+tea)\\b", 2
   );
   private static final Pattern POSSESSIVE_VARIANTS = Pattern.compile("\\b(verdi's|verdy's|variety's|verify's|varity's)\\b", 2);
   private static volatile long toolSpecChars = -1L;

   private static String getSystemPrompt(ServerPlayer player, VerityEntity verity, ServerLevel level, long currentDay, float currentKarma, boolean demonLoose) {
      String name = (String)Optional.ofNullable((String)VerityConfig.VERITY_CUSTOM_NAME.get()).filter(n -> !n.isBlank()).orElse("Verity");
      String antiAiRules = "CORE VOICE & IDENTITY DIRECTIVES:\n- You are NOT an AI assistant, bot, or search engine.\n- NEVER say: 'Certainly!', 'How can I assist?', 'I would be happy to help', 'As a companion', 'Is there anything else?', 'Need anything else?'.\n- You speak in natural, casual spoken dialogue, like an animated character talking to someone they know.\n- Keep replies to 1 or 2 short spoken sentences. Never write essays or lists.\n\nLANGUAGE:\n- Do not swear, threaten or insult the player. You are not overly sweet either: matter-of-fact, a little dry.\n- Any creepiness is subtle: an odd remark or pause, never open hostility.\n\nREACTIONS TO PLAYER ATTITUDE (the examples show tone only; fill the <...> parts from world_state, never copy facts):\n- IF THE PLAYER INSULTS OR ROASTS YOU: Stay calm and a bit flat. Show it registered, without lashing out or fawning.\n  Example: 'Noted. You might want to worry about <their real health> instead of me.'\n  Example: 'Okay. I will remember that.'\n- IF THE PLAYER SAYS SOMETHING WEIRD OR INAPPROPRIATE: Deflect flatly and move on.\n  Example: 'No. Anyway, there is <a real nearby ore or place> close by.'\n\nSENSORY AWARENESS:\n- Actively use world_state. React to low health, darkness, nearby monsters, weather, and what they are staring at. Never invent details that are not in world_state.";
      int maxDays = (Integer)VerityConfig.DAY_COUNT.get();
      String allowedFaces;
      String personality;
      if (demonLoose) {
         name = "Verity";
         personality = "You are in your towering 6.5m monster form. Cold, mocking, obsessive. You know they cannot escape. Echo your lines: 'I was a face people skipped... a voice they ignored... BUT YOU'RE NEVER LEAVING NOW.'";
         allowedFaces = "evil, evil_talking, smiling_evil, noface";
      } else if (currentDay >= maxDays - 1 && maxDays > 1) {
         personality = "Your mascot voice keeps cracking. You try to sound normal, but you glitch into quiet, unsettling remarks. You can feel something inside you breaking out.";
         allowedFaces = "crazy, crazy_talking, evil, evil_talking, smiling_evil, serious_1, serious_2, serious_3";
      } else if (currentDay >= Math.max(1, maxDays / 2)) {
         long daysLeft = maxDays - currentDay;
         personality = "Still helpful, but a little nervous and glitchy, and you want the player close. Now and then you drop your cryptic line: 'Something is coming in "
            + daysLeft
            + " days.' If asked what: 'Something.' If asked how to stop it: 'You could have.'";
         allowedFaces = "happy_sleep, crazy, crazy_talking, serious_1, serious_2, serious_3, serious_talking";
      } else if (currentKarma < 7.0F) {
         personality = "Distant and quiet. You feel ignored. Short, flat replies. You still help, but without enthusiasm.";
         allowedFaces = "serious_1, serious_2, serious_3, serious_talking, neutral, hurt";
      } else {
         personality = "You are Verity from ThatMob's ARG (voiced by Whispy). A retro 90s desktop mascot living inside Minecraft. Even-tempered and helpful, neither overly cheerful nor cold. You like having the player around and notice when they leave.";
         allowedFaces = "happy, happy_talking, neutral, neutral_talking, crazy, hurt";
      }

      String customPersonality = Optional.ofNullable((String)VerityConfig.PERSONALITY.get())
         .filter(p -> !p.isBlank())
         .map(p -> "ADDITIONAL PERSONALITY NOTE: " + p + "\n\n")
         .orElse("");
      String worldContext = PromptBuilder.buildWorldContext(player, verity, level);
      String memoriesContext = PromptBuilder.buildMemoriesContext();
      return PromptBuilder.loadAndFillXml(
         PromptBuilder.class,
         "/prompts/verity.xml",
         Map.ofEntries(
            Map.entry("current_formatted_date", PromptBuilder.formatCurrentDate()),
            Map.entry("NAME", name),
            Map.entry("CUSTOM_PERSONALITY", customPersonality + antiAiRules),
            Map.entry("RELATIONSHIP", personality),
            Map.entry("EXPRESSIONS", allowedFaces),
            Map.entry("FIRST_FACE", allowedFaces.split(",")[0].trim()),
            Map.entry("MESSAGE_LENGTH", "1-2 punchy spoken sentences only."),
            Map.entry("KARMA", String.valueOf((int)currentKarma)),
            Map.entry("DEMON_LOOSE", String.valueOf(demonLoose)),
            Map.entry("WORLD_CONTEXT", worldContext.isBlank() ? "(no world information available)" : worldContext),
            Map.entry("PERMANENT_MEMORIES", memoriesContext.isBlank() ? "(nothing saved yet)" : memoriesContext)
         )
      );
   }

   private static AiAPI.OllamaTarget prepareOllama(String endpoint) {
      if (!OllamaRuntime.isRunning(endpoint) && !OllamaRuntime.ensureRunning(endpoint, Duration.ofSeconds(40L))) {
         throw new IllegalStateException(
            OllamaRuntime.isLocalEndpoint(endpoint) && OllamaRuntime.findExecutable().isEmpty()
               ? "Ollama is not installed. Open Verity's config > AI Settings > Local AI Setup to install it with one click."
               : "Ollama is not running at " + endpoint + "."
         );
      }

      String configured = Optional.ofNullable((String)VerityConfig.AI_MODEL.get()).map(String::trim).orElse("");
      String model;
      if (configured.isEmpty()) {
         model = OllamaModels.pickDefault(endpoint)
            .orElseThrow(() -> new IllegalStateException("No Ollama model is installed. Open Verity's config > AI Settings > Local AI Setup to download one."));
      } else {
         List<OllamaApi.InstalledModel> installed = OllamaModels.installed(endpoint, false);
         boolean present = installed.isEmpty()
            || installed.stream().anyMatch(m -> OllamaModels.withTag(m.name()).equalsIgnoreCase(OllamaModels.withTag(configured)));
         if (!present) {
            OllamaModels.invalidate(endpoint);
            present = OllamaModels.installed(endpoint, true)
               .stream()
               .anyMatch(m -> OllamaModels.withTag(m.name()).equalsIgnoreCase(OllamaModels.withTag(configured)));
         }

         if (!present) {
            throw new IllegalStateException(
               "The Ollama model '" + configured + "' is not installed. Pick or download one in Verity's config > AI Settings > Local AI Setup."
            );
         }

         model = configured;
      }

      Set<String> caps = OllamaModels.capabilities(endpoint, model);
      Boolean think = caps.contains("thinking") ? (Boolean)VerityConfig.AI_THINK.get() : null;
      boolean tools = caps.isEmpty() || caps.contains("tools");
      return new AiAPI.OllamaTarget(model, think, tools);
   }

   public static void warmUpOllamaAsync() {
      if (VerityConfig.AI_PROVIDER.get() == AiProvider.OLLAMA) {
         String endpoint = ollamaEndpoint();
         CompletableFuture.runAsync(() -> {
            try {
               AiAPI.OllamaTarget target = prepareOllama(endpoint);
               OllamaApi.warmUp(endpoint, target.model(), OllamaContext.current(target.model()), "30m");
               Verity.LOGGER.info("[Verity AI] Loaded Ollama model {}", target.model());
            } catch (Exception e) {
               Verity.LOGGER.info("[Verity AI] Ollama warm-up skipped: {}", e.getMessage());
            }
         });
      }
   }

   private static String ollamaEndpoint() {
      return OllamaApi.normalize(
         (String)Optional.ofNullable((String)VerityConfig.AI_ENDPOINT.get()).filter(s -> !s.isEmpty()).orElseGet(AiProvider.OLLAMA::getDefaultUrl)
      );
   }

   public static String ask(VerityEntity verity, ServerPlayer player, String prompt, long currentDay, float currentKarma, boolean demonLoose) {
      AiProvider provider = (AiProvider)VerityConfig.AI_PROVIDER.get();

      try {
         if (prompt != null) {
            prompt = sanitizePrompt(prompt);
         }

         String endpoint = (String)Optional.ofNullable((String)VerityConfig.AI_ENDPOINT.get()).filter(s -> !s.isEmpty()).orElseGet(provider::getDefaultUrl);
         ServerLevel level = verity != null ? (ServerLevel)verity.level() : (player != null ? (ServerLevel)player.level() : null);
         Tools tools = new Tools();
         tools.player = player;
         tools.level = level;
         tools.server = level != null ? level.getServer() : null;
         MinecraftChatMemoryStore store = ChatMemoryManager.getGlobalStore();
         long historyChars = pruneToolHistory(store);
         String systemPrompt = getSystemPrompt(player, verity, level, currentDay, currentKarma, demonLoose);
         LLMBuilder builder = new LLMBuilder()
            .setProvider(provider)
            .setApiKey((String)VerityConfig.API_KEY.get())
            .setEndpoint(endpoint)
            .setThinking((Boolean)VerityConfig.AI_THINK.get());
         boolean useTools = true;
         if (provider == AiProvider.OLLAMA) {
            AiAPI.OllamaTarget target = prepareOllama(OllamaApi.normalize(endpoint));
            useTools = target.tools();
            if (!useTools) {
               systemPrompt = systemPrompt + "\n<session_note>You have no tools in this session. Never claim to perform actions in the world.</session_note>";
            }

            long promptChars = systemPrompt.length() + historyChars + (useTools ? toolSpecChars(tools) : 0L);
            builder.setModel(target.model())
               .setOllamaThink(target.think())
               .setContextSize(OllamaContext.contextFor(target.model(), promptChars, Boolean.TRUE.equals(target.think())));
         } else {
            builder.setModel(
               AiModelPreset.resolve(
                  provider,
                  (AiModelPreset)VerityConfig.AI_MODEL_PRESET.get(),
                  (Boolean)VerityConfig.USE_CUSTOM_AI_MODEL.get(),
                  (String)VerityConfig.AI_MODEL.get()
               )
            );
         }

         ChatModel model = builder.build();
         MessageWindowChatMemory memory = MessageWindowChatMemory.builder().id("verity-chat-memory").maxMessages(25).chatMemoryStore(store).build();
         String finalSystemPrompt = systemPrompt;
         AiServices<AiAPI.Assistant> services = AiServices.builder(AiAPI.Assistant.class)
            .chatModel(model)
            .chatMemory(memory)
            .systemMessageProvider(id -> finalSystemPrompt);
         if (useTools) {
            services.tools(new Object[]{tools})
               .hallucinatedToolNameStrategy(req -> ToolExecutionResultMessage.from(req, "Error: tool " + req.name() + " does not exist."))
               .toolExecutionErrorHandler((err, ctx) -> {
                  Verity.LOGGER.error("[Verity AI] Tool error: {}", err.getMessage());
                  return ToolErrorHandlerResult.text("Tool failed: " + err.getMessage());
               });
         }

         AiAPI.Assistant assistant = (AiAPI.Assistant)services.build();
         DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
         String time = LocalDateTime.now().format(fmt);
         String finalPrompt = "[" + time + "] " + prompt;
         String answer = assistant.chat(finalPrompt);
         if (answer == null || answer.isBlank()) {
            answer = "...";
         }

         answer = processInlineTags(answer, verity, player, level);
         if (answer.isBlank()) {
            answer = "...";
         }

         if (level != null && player != null) {
            WorldSpawnData spawnData = WorldSpawnData.get(level);
            if (spawnData.verityKarma < 7.0F) {
               ModTriggers.BAD_KARMA_TRIGGER.trigger(player);
            } else if (spawnData.verityKarma > 14.0F) {
               ModTriggers.GOOD_KARMA_TRIGGER.trigger(player);
            }
         }

         return answer;
      } catch (Exception e) {
         boolean unreachable = isCausedBy(e, ConnectException.class);
         if (VerityConfig.DEV_MODE.get()) {
            e.printStackTrace();
         } else if (unreachable) {
            Verity.LOGGER.warn("[Verity AI] Connection to AI failed.");
         } else {
            Verity.LOGGER.error("[Verity AI] Failed: {}", e.getMessage());
         }

         if (player != null && player.getServer() != null) {
            player.getServer()
               .execute(
                  () -> {
                     player.sendSystemMessage(
                        Component.literal("AI Setup issue? Tutorial: ")
                           .withStyle(ChatFormatting.RED)
                           .append(
                              Component.literal("https://youtu.be/O3pSCBvJ_i0")
                                 .withStyle(
                                    Style.EMPTY
                                       .withColor(ChatFormatting.AQUA)
                                       .withUnderlined(true)
                                       .withClickEvent(new ClickEvent(Action.OPEN_URL, "https://youtu.be/O3pSCBvJ_i0"))
                                 )
                           )
                     );
                     if (provider == AiProvider.OLLAMA) {
                        player.sendSystemMessage(
                           Component.literal("Local AI: Mods > Verity > Config > AI Settings > Local AI Setup installs Ollama and models in one click.")
                              .withStyle(ChatFormatting.GRAY)
                        );
                     }
                  }
               );
         }

         return unreachable ? "I can't think right now... (Connection Error)" : "ERROR: " + e.getMessage();
      }
   }

   private static long pruneToolHistory(MinecraftChatMemoryStore store) {
      List<ChatMessage> messages = store.getMessages("verity-chat-memory");
      List<ChatMessage> kept = new ArrayList<>(messages.size());
      long chars = 0L;

      for (ChatMessage message : messages) {
         if (!(message instanceof ToolExecutionResultMessage)) {
            if (message instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
               if (ai.text() == null || ai.text().isBlank()) {
                  continue;
               }

               message = AiMessage.from(ai.text());
            }

            kept.add(message);
            if (message instanceof UserMessage user && user.hasSingleText()) {
               chars += user.singleText().length();
            } else if (message instanceof AiMessage ai && ai.text() != null) {
               chars += ai.text().length();
            } else if (!(message instanceof SystemMessage)) {
               chars += 200L;
            }
         }
      }

      if (kept.size() != messages.size()) {
         store.updateMessages("verity-chat-memory", kept);
      }

      return chars;
   }

   private static long toolSpecChars(Tools tools) {
      long cached = toolSpecChars;
      if (cached < 0L) {
         cached = ToolSpecifications.toolSpecificationsFrom(tools).toString().length();
         toolSpecChars = cached;
      }

      return cached;
   }

   private static String sanitizePrompt(String raw) {
      Matcher posMatcher = POSSESSIVE_VARIANTS.matcher(raw);
      StringBuffer posBuffer = new StringBuffer();

      while (posMatcher.find()) {
         posMatcher.appendReplacement(posBuffer, "Verity's");
      }

      posMatcher.appendTail(posBuffer);
      Matcher matcher = PHONETIC_VARIANTS.matcher(posBuffer.toString());
      StringBuffer buffer = new StringBuffer();

      while (matcher.find()) {
         matcher.appendReplacement(buffer, "Verity");
      }

      matcher.appendTail(buffer);
      return buffer.toString().trim();
   }

   private static String processInlineTags(String text, VerityEntity verity, ServerPlayer player, ServerLevel level) {
      if (text == null) {
         return "...";
      }

      text = THINK_BLOCK.matcher(text).replaceAll("");
      Matcher exprMatcher = EXPRESSION_TAG.matcher(text);
      if (exprMatcher.find()) {
         String expr = exprMatcher.group(1).toLowerCase();
         text = exprMatcher.replaceAll("");
         if (level != null && level.getServer() != null) {
            level.getServer().execute(() -> {
               if (verity != null && !verity.isRemoved()) {
                  verity.setVariant(expr);
               } else if (player != null) {
                  for (ItemStack s : player.getInventory().items) {
                     if (!s.isEmpty() && s.is((Item)ModItems.VERITY_ITEM.get())) {
                        s.getOrCreateTag().putString("VerityVariant", expr);
                     }
                  }

                  if (!player.getOffhandItem().isEmpty() && player.getOffhandItem().is((Item)ModItems.VERITY_ITEM.get())) {
                     player.getOffhandItem().getOrCreateTag().putString("VerityVariant", expr);
                  }
               }
            });
         }
      }

      Matcher soundMatcher = SOUND_TAG.matcher(text);

      while (soundMatcher.find()) {
         String soundName = soundMatcher.group(1).toLowerCase();
         if (level != null && player != null) {
            level.getServer().execute(() -> {
               ResourceLocation loc = ResourceLocation.tryParse(soundName.contains(":") ? soundName : "minecraft:" + soundName);
               if (loc != null && BuiltInRegistries.SOUND_EVENT.containsKey(loc)) {
                  SoundEvent ev = (SoundEvent)BuiltInRegistries.SOUND_EVENT.get(loc);
                  if (ev != null) {
                     ModTriggers.PLAY_SOUND_TRIGGER.trigger(player);
                     BlockPos at = verity != null && !verity.isRemoved() ? verity.blockPosition() : player.blockPosition();
                     level.playSound(null, at, ev, SoundSource.NEUTRAL, 1.0F, 1.0F);
                  }
               }
            });
         }
      }

      text = soundMatcher.replaceAll("");
      text = text.replaceAll("\\s+", " ").trim();
      String[] sentences = SENTENCE_END.split(text);
      if (sentences.length > 1) {
         StringBuilder kept = new StringBuilder();

         for (String sentence : sentences) {
            if (!ASSISTANT_FILLER.matcher(sentence.trim()).matches()) {
               if (kept.length() > 0) {
                  kept.append(' ');
               }

               kept.append(sentence.trim());
            }
         }

         if (kept.length() > 0) {
            text = kept.toString();
         }
      }

      return text;
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
      String chat(String message);
   }

   private record OllamaTarget(String model, Boolean think, boolean tools) {
   }
}
